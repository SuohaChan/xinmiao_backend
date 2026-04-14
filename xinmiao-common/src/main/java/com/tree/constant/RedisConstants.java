package com.tree.constant;

import java.time.Duration;

/**
 * Access Token + Refresh Token 方案：
 * - Access Token：短效，请求头携带，调业务接口用
 * - Refresh Token：长效，仅用于调用刷新接口换取新 Access Token
 */
public class RedisConstants {
    private static final String PREFIX = "xinmiao:";

    /** Access Token 有效期（短，用于 JWT 与刷新逻辑） */
    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    /** Refresh Token 键前缀 */
    public static final String REFRESH_TOKEN_KEY = PREFIX + "refresh:token:";
    /** Refresh Token 有效期（长） */
    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    /** JWT 黑名单（登出后使 token 立即失效），key = 此前缀 + jti */
    public static final String JWT_BLACKLIST_KEY = PREFIX + "jwt:blacklist:";

    // ===================== 缓存（Cache-Aside，读穿透 + 防穿透） =====================

    /** 资讯详情缓存 key 前缀，key = 此前缀 + id */
    public static final String CACHE_INFORMATION_KEY = PREFIX + "cache:information:";
    /** 资讯详情缓存 TTL（分钟） */
    public static final long CACHE_INFORMATION_TTL_MINUTES = 30L;
    /** 空值缓存 TTL（分钟），用于防穿透 */
    public static final long CACHE_NULL_TTL_MINUTES = 2L;
    /** 资讯缓存重建锁 key 前缀（防击穿），lockKey = 此前缀 + id */
    public static final String LOCK_CACHE_INFORMATION_KEY = PREFIX + "lock:cache:information:";
    /** 缓存重建锁持有时间（秒），防止死锁 */
    public static final long LOCK_CACHE_TTL_SECONDS = 10L;
    /** 缓存重建锁等待时间（秒），Redisson tryLock 有界等待 */
    public static final long LOCK_CACHE_WAIT_SECONDS = 3L;
    /** CacheClient 互斥锁未抢到时最多重试次数 */
    public static final int LOCK_CACHE_MUTEX_MAX_RETRIES = 3;
    /** CacheClient 互斥锁重试间隔（毫秒） */
    public static final long LOCK_CACHE_MUTEX_INTERVAL_MS = 50L;

    /** 通知详情缓存 key 前缀，key = 此前缀 + id */
    public static final String CACHE_NOTICE_KEY = PREFIX + "cache:notice:";
    /** 通知详情缓存 TTL（分钟） */
    public static final long CACHE_NOTICE_TTL_MINUTES = 30L;
    /** 通知缓存重建锁 key 前缀（防击穿），lockKey = 此前缀 + id */
    public static final String LOCK_CACHE_NOTICE_KEY = PREFIX + "lock:cache:notice:";

    /** AI 对话幂等缓存 key 前缀，key = 此前缀 + requestId，防重复请求（双击/重试） */
    public static final String CACHE_AI_REPLY_KEY = PREFIX + "ai:reply:";
    /** AI 幂等缓存 TTL（分钟），过期后自动清理 */
    public static final long CACHE_AI_REPLY_TTL_MINUTES = 10L;

    // ===================== 签到 Bitmap（Redis 优化签到） =====================

    /** 签到 Bitmap key 前缀，key = 此前缀 + studentId + ":" + yyyyMM，如 xinmiao:sign:1001:202503 */
    public static final String SIGN_KEY_PREFIX = PREFIX + "sign:";
    /** 签到 Bitmap 过期天数（覆盖当月 + 上月，便于跨周查询） */
    public static final long SIGN_KEY_TTL_DAYS = 35L;

    // ===================== 排行榜 ZSet（按周期换 key） =====================

    /** 排行榜 ZSet 默认 TTL（天），用于 buildRealRankZSet 未指定 ttl 时 */
    public static final long RANK_REAL_TOTAL_TTL_DAYS = 400L;

    /** 学生任务完成 Set key 前缀，key = 此前缀 + studentId + ":done"，member=taskId，防重复加分 */
    public static final String STU_TASK_DONE_KEY_PREFIX = PREFIX + "stu:task:stu_";
    /** 学生任务完成 Set TTL（天），约一学期（半年） */
    public static final long STU_TASK_DONE_TTL_DAYS = 200L;

    // ===================== 任务发布推送幂等（MQ 消费去重） =====================
    /** 任务发布推送幂等 key 前缀，key = 此前缀 + taskId，防止重复消费导致重复 WebSocket 推送 */
    public static final String TASK_PUBLISH_DONE_KEY_PREFIX = PREFIX + "task:publish:done:";
    /** 任务发布推送幂等占位 TTL（天）；通常只需覆盖重试窗口 */
    public static final long TASK_PUBLISH_DONE_TTL_DAYS = 7L;

    /** 今日榜 key 前缀，实际 key = 此前缀 + yyyyMMdd，如 xinmiao:rank:today:20250302 */
    public static final String RANK_TODAY_KEY_PREFIX = PREFIX + "rank:today:";

    /** 周榜 key 前缀，实际 key = 此前缀 + 年份和周次，例如 xinmiao:rank:week:2025W10 */
    public static final String RANK_WEEK_KEY_PREFIX = PREFIX + "rank:week:";

    /**
     * 学院榜 key 前缀，实际 key = 此前缀 + 学年 + ":" + college，
     * 例如 xinmiao:rank:college:2024-2025:计算机学院
     */
    public static final String RANK_COLLEGE_YEAR_KEY_PREFIX = PREFIX + "rank:college:";

    /** 今日榜 key 的 TTL（天），用于旧 key 自动过期，释放内存 */
    public static final long RANK_TODAY_TTL_DAYS = 2L;

    /** 周榜 key 的 TTL（天），用于旧 key 自动过期，释放内存 */
    public static final long RANK_WEEK_TTL_DAYS = 21L;

    /** 学院榜（按学年）的 TTL（天），略大于一个学年，旧学年自动过期 */
    public static final long RANK_COLLEGE_YEAR_TTL_DAYS = 400L;

    /** 空榜缓存 TTL（分钟），防穿透 */
    public static final long RANK_EMPTY_TTL_MINUTES = 2L;

    /** 排行榜重建锁 key 前缀，lockKey = 此前缀 + lockSuffix（如 today:20250302 / week:2025W10 / college:2024-2025:计算机学院） */
    public static final String LOCK_RANK_KEY_PREFIX = PREFIX + "lock:rank:";

    /** 排行榜重建锁持有时间（秒），需覆盖一次 DB 查询 + ZSet 写入耗时 */
    public static final long LOCK_RANK_TTL_SECONDS = 10L;
    /** 排行榜锁未抢到时最多重试次数 */
    public static final int LOCK_RANK_WAIT_MAX_RETRIES = 3;
    /** 排行榜锁重试间隔（毫秒） */
    public static final long LOCK_RANK_WAIT_INTERVAL_MS = 100L;
}
