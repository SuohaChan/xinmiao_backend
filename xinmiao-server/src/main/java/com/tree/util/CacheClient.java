package com.tree.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.tree.constant.RedisConstants.LOCK_CACHE_MUTEX_INTERVAL_MS;

/**
 * 通用缓存客户端：Cache-Aside 读穿透 + 空值缓存防穿透 + 互斥锁防击穿。
 * 互斥锁使用持有者 value（UUID）+ Lua 原子释放，避免误删他人锁。
 * 写入缓存时对 TTL 加随机抖动，缓解缓存雪崩（大量 key 同时过期）。
 * 序列化使用 Jackson（与项目约定一致）。
 *
 * @author tree
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheClient {

    private static final String RELEASE_LOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT;

    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RELEASE_LOCK_LUA);
        script.setResultType(Long.class);
        RELEASE_SCRIPT = script;
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DbFallbackLimiter dbFallbackLimiter;

    /**
     * 对 TTL 加随机抖动（约 0～10% 基础时间），缓解缓存雪崩。
     */
    private long withJitter(long time, TimeUnit unit) {
        long jitterRange = Math.max(1, (long) (time * 0.1));
        long add = ThreadLocalRandom.current().nextLong(0, jitterRange + 1);
        return time + add;
    }

    /**
     * 按 keyPrefix + id 写入缓存，与 exists/get 约定一致。
     *
     * @param keyPrefix 缓存 key 前缀
     * @param id 主键 ID
     * @param value 缓存值对象
     * @param time 缓存过期时间（基础值）
     * @param unit 时间单位
     */
    public void set(String keyPrefix, Object id, Object value, long time, TimeUnit unit) {
        set(keyPrefix + id, value, time, unit);
    }

    /**
     * 写入缓存，value 序列化为 JSON 字符串。过期时间会加随机抖动以缓解雪崩。
     * Redis 异常时仅记录日志，不抛出，降级为跳过写缓存。
     *
     * @param key 缓存键
     * @param value 缓存值对象
     * @param time 缓存过期时间（基础值）
     * @param unit 时间单位
     */
    public void set(String key, Object value, long time, TimeUnit unit) {
        try {
            String json = value == null ? "" : objectMapper.writeValueAsString(value);
            long actualTime = withJitter(time, unit);
            stringRedisTemplate.opsForValue().set(key, json, actualTime, unit);
        } catch (JsonProcessingException e) {
            log.error("缓存序列化失败 key={}", key, e);
        } catch (Exception e) {
            log.warn("Redis 写入缓存失败，降级跳过 key={}", key, e);
        }
    }

    /**
     * 读穿透 + 防穿透 + 防击穿：缓存未命中时用互斥锁，只放一个请求查 DB 并回填，其余等待后重试读缓存。
     *
     * @param <R> 返回值类型
     * @param <ID> 主键类型
     * @param keyPrefix 缓存key前缀
     * @param id 主键ID
     * @param type 返回值类型Class
     * @param dbFallback 数据库查询回调函数
     * @param time 缓存过期时间
     * @param unit 时间单位
     * @param nullTtl 空值缓存过期时间
     * @param nullTtlUnit 空值缓存时间单位
     * @param lockKeyPrefix 锁 key 前缀，lockKey = lockKeyPrefix + id
     * @param lockTimeSec 锁过期时间（秒），防止死锁
     * @param maxRetries 未抢到锁时最多重试次数，超过后降级为直接查 DB
     * @return 查询结果
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback,
            long time, TimeUnit unit,
            long nullTtl, TimeUnit nullTtlUnit,
            String lockKeyPrefix, long lockTimeSec, int maxRetries) {
        try {
            return doQueryWithMutex(keyPrefix, id, type, dbFallback, time, unit, nullTtl, nullTtlUnit,
                    lockKeyPrefix, lockTimeSec, maxRetries);
        } catch (Exception e) {
            log.warn("Redis 访问异常，回源限流内查 DB keyPrefix={} id={}", keyPrefix, id, e);
            return dbFallbackLimiter.runWithFallbackPermit(() -> dbFallback.apply(id));
        }
    }

    private <R, ID> R doQueryWithMutex(
            String keyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback,
            long time, TimeUnit unit,
            long nullTtl, TimeUnit nullTtlUnit,
            String lockKeyPrefix, long lockTimeSec, int maxRetries) {
        // 构造缓存key并尝试从缓存获取数据
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        // 缓存命中且非空值，直接反序列化返回
        if (json != null && !json.isEmpty()) {
            try {
                return objectMapper.readValue(json, type);
            } catch (JsonProcessingException e) {
                log.warn("缓存反序列化失败 key={}", key, e);
            }
        }
        // 缓存命中但为空字符串，说明是空值缓存，直接返回null
        if (json != null) {
            return null;
        }

        // 缓存未命中，尝试获取分布式锁（value 为持有者唯一标识，释放时仅删自己的锁）
        String lockKey = lockKeyPrefix + id;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, lockTimeSec, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(locked)) {
            try {
                // 双重检查：再次确认缓存是否已存在
                json = stringRedisTemplate.opsForValue().get(key);
                if (json != null && !json.isEmpty()) {
                    try {
                        return objectMapper.readValue(json, type);
                    } catch (JsonProcessingException e) {
                        log.warn("缓存反序列化失败 key={}", key, e);
                    }
                }
                if (json != null) {
                    return null;
                }

                R r = dbFallback.apply(id);
                if (r == null) {
                    long actualNullTtl = withJitter(nullTtl, nullTtlUnit);
                    stringRedisTemplate.opsForValue().set(key, "", actualNullTtl, nullTtlUnit);
                    return null;
                }
                set(key, r, time, unit);
                return r;
            } finally {
                // Lua 原子释放：仅当 key 的 value 仍为当前持有者时才 DEL，避免误删他人锁
                try {
                    Long removed = stringRedisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(lockKey), lockValue);
                    if (removed != null && removed == 0) {
                        log.debug("锁已过期或非本线程持有，未执行删除 key={}", lockKey);
                    }
                } catch (Exception e) {
                    log.warn("释放锁异常 key={}", lockKey, e);
                }
            }
        }

        // 未获取到锁且重试次数已用完，回源限流内查 DB
        if (maxRetries <= 0) {
            return dbFallbackLimiter.runWithFallbackPermit(() -> dbFallback.apply(id));
        }

        // 未获取到锁但还有重试机会，等待后递归重试
        try {
            Thread.sleep(LOCK_CACHE_MUTEX_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return dbFallbackLimiter.runWithFallbackPermit(() -> dbFallback.apply(id));
        }
        return doQueryWithMutex(keyPrefix, id, type, dbFallback, time, unit, nullTtl, nullTtlUnit,
                lockKeyPrefix, lockTimeSec, maxRetries - 1);
    }

    /**
     * 判断缓存 key 是否存在（用于外部锁逻辑下的双重检查）。
     * Redis 异常时返回 false，降级为视为未命中。
     *
     * @param keyPrefix 缓存 key 前缀
     * @param id 主键 ID
     * @return true 表示 key 已存在（命中或空值缓存），false 表示未命中或 Redis 异常
     */
    public boolean exists(String keyPrefix, Object id) {
        try {
            Boolean has = stringRedisTemplate.hasKey(keyPrefix + id);
            return Boolean.TRUE.equals(has);
        } catch (Exception e) {
            log.warn("Redis hasKey 异常，降级为未命中 keyPrefix={} id={}", keyPrefix, id, e);
            return false;
        }
    }

    /**
     * 从缓存读取并反序列化，仅当 key 存在时调用有效。
     * Redis 异常时返回 null，降级为缓存未命中。
     *
     * @param keyPrefix 缓存 key 前缀
     * @param id 主键 ID
     * @param type 返回值类型
     * @param <R> 返回值类型
     * @return key 对应 value 为空字符串时返回 null（空值缓存），否则返回反序列化对象
     */
    public <R> R get(String keyPrefix, Object id, Class<R> type) {
        try {
            String key = keyPrefix + id;
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            if (json.isEmpty()) {
                return null;
            }
            try {
                return objectMapper.readValue(json, type);
            } catch (JsonProcessingException e) {
                log.warn("缓存反序列化失败 key={}", key, e);
                return null;
            }
        } catch (Exception e) {
            log.warn("Redis get 异常，降级为未命中 keyPrefix={} id={}", keyPrefix, id, e);
            return null;
        }
    }

    /**
     * 删除缓存（更新/删除数据后调用，保证下次读穿透时拿到新数据）。
     * Redis 异常时仅记录日志，不抛出，降级为跳过删除。
     *
     * @param keyPrefix 缓存key前缀
     * @param id 主键ID
     */
    public void delete(String keyPrefix, Object id) {
        try {
            stringRedisTemplate.delete(keyPrefix + id);
        } catch (Exception e) {
            log.warn("Redis delete 异常，降级跳过 keyPrefix={} id={}", keyPrefix, id, e);
        }
    }
}

