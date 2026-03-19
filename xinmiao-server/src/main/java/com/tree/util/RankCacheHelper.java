package com.tree.util;

import com.tree.dto.RankDto;
import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.ZSetOperations;

import static com.tree.constant.RedisConstants.LOCK_RANK_KEY_PREFIX;
import static com.tree.constant.RedisConstants.LOCK_RANK_TTL_SECONDS;
import static com.tree.constant.RedisConstants.LOCK_RANK_WAIT_INTERVAL_MS;
import static com.tree.constant.RedisConstants.LOCK_RANK_WAIT_MAX_RETRIES;
import static com.tree.constant.RedisConstants.RANK_REAL_TOTAL_TTL_DAYS;

/**
 * 排行榜缓存：ZSet 统一为 member=studentId、score=积分；防击穿（互斥锁 + Lua 删锁）、防雪崩（TTL 抖动）。
 * 未拿到锁的请求：限制重试次数，超时后直接回源返回且不写 Redis，避免长时间占用线程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankCacheHelper {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT;

    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
        script.setResultType(Long.class);
        RELEASE_SCRIPT = script;
    }

    @Qualifier("scriptRedisTemplate")
    private final StringRedisTemplate stringRedisTemplate;
    private final DbFallbackLimiter dbFallbackLimiter;

    private static long withJitter(long time, TimeUnit unit) {
        long jitterRange = Math.max(1, (long) (time * 0.1));
        long add = ThreadLocalRandom.current().nextLong(0, jitterRange + 1);
        return time + add;
    }

    /**
     * 将 List&lt;RankDto&gt; 写入 ZSet，member=studentId，score=totalScore（与总榜 ZINCRBY 格式一致）。
     */
    public void buildRealRankZSet(String key, List<RankDto> list) {
        buildRealRankZSet(key, list, RANK_REAL_TOTAL_TTL_DAYS);
    }

    /**
     * 同上，指定 TTL（天），用于今日榜/周榜/学院榜等周期榜。
     */
    public void buildRealRankZSet(String key, List<RankDto> list, long ttlDays) {
        stringRedisTemplate.delete(key);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (RankDto dto : list) {
            String member = String.valueOf(dto.getStudentId());
            double score = (dto.getTotalScore() != null ? dto.getTotalScore() : 0);
            stringRedisTemplate.opsForZSet().add(key, member, score);
        }
        long ttl = withJitter(ttlDays, TimeUnit.DAYS);
        stringRedisTemplate.expire(key, ttl, TimeUnit.DAYS);
    }

    /**
     * 从实时榜 ZSet 读出 TopN（member=studentId，score=积分），返回的 RankDto 仅含 studentId、totalScore、rank，需调用方补全昵称等。
     * reverseRangeWithScores 已按 score 降序，无需再排序。
     */
    public List<RankDto> getRealRankListFromZSet(String key, int topN) {
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, topN - 1);
        if (tuples == null || tuples.isEmpty()) {
            return null;
        }
        List<RankDto> list = tuples.stream()
                .map(t -> {
                    RankDto dto = new RankDto();
                    try {
                        dto.setStudentId(Long.valueOf(t.getValue()));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    Double score = t.getScore();
                    dto.setTotalScore(score != null ? score.intValue() : 0);
                    return dto;
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            return null;
        }
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRank(i + 1);
        }
        return list;
    }

    /**
     * 实时榜读穿透 + 防击穿：先读 ZSet（member=studentId），未命中时加锁回源 DB 并 buildRealRankZSet。
     */
    public List<RankDto> getOrRebuildReal(String key, String lockSuffix, int topN, Supplier<List<RankDto>> dbFallback) {
        return getOrRebuildReal(key, lockSuffix, topN, RANK_REAL_TOTAL_TTL_DAYS, dbFallback);
    }

    /**
     * 同上，指定回源写入时的 TTL（天），用于周榜/学院榜等周期榜。
     */
    public List<RankDto> getOrRebuildReal(String key, String lockSuffix, int topN, long ttlDays, Supplier<List<RankDto>> dbFallback) {
        try {
            List<RankDto> list = getRealRankListFromZSet(key, topN);
            if (list != null) {
                return list;
            }
            String lockKey = LOCK_RANK_KEY_PREFIX + lockSuffix;
            String lockValue = UUID.randomUUID().toString();
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_RANK_TTL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    list = getRealRankListFromZSet(key, topN);
                    if (list != null) {
                        return list;
                    }
                    list = dbFallbackLimiter.runWithFallbackPermit(dbFallback);
                    if (list == null) {
                        list = Collections.emptyList();
                    }
                    buildRealRankZSet(key, list, ttlDays);
                    return list;
                } finally {
                    try {
                        stringRedisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(lockKey), lockValue);
                    } catch (Exception e) {
                        log.warn("释放排行榜锁异常 key={}", lockKey, e);
                    }
                }
            }
            // 未拿到锁：有限次重试，超时后直接回源返回，不写 Redis，避免长时间占用线程
            for (int retry = 0; retry < LOCK_RANK_WAIT_MAX_RETRIES; retry++) {
                try {
                    Thread.sleep(LOCK_RANK_WAIT_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 榜单为展示型能力：锁等待被中断时快速失败，避免回源 DB 放大抖动
                    return Collections.emptyList();
                }
                list = getRealRankListFromZSet(key, topN);
                if (list != null) {
                    return list;
                }
            }
            log.debug("排行榜回源等待超时，回源限流内查 DB key={} lockSuffix={}", key, lockSuffix);
            // 榜单为展示型能力：锁竞争超时快速失败，避免回源 DB（数据量大）
            return Collections.emptyList();
        } catch (Exception e) {
            // Redis 基础设施异常：对外体现为服务繁忙（503），避免“空榜”误导；同时不回源 DB 放大故障面
            log.warn("排行榜 Redis 访问异常，降级为 503 key={} lockSuffix={}", key, lockSuffix, e);
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "排行榜服务繁忙，请稍后重试");
        }
    }
}
