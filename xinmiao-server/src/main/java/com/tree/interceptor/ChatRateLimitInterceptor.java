package com.tree.interceptor;

import com.tree.context.StudentHolder;
import com.tree.result.ErrorCode;
import com.tree.util.InterceptorResponseHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;
import java.util.UUID;

/**
 * AI 对话接口限流：按用户滑动窗口内最多 N 次/分钟，超限返回 429。
 * 使用 Redis ZSET + Lua 实现滑动窗口，避免固定窗口边界双倍问题，支持多实例。
 */
@Slf4j
@Component
public class ChatRateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "chat:rl:user:";

    /** 滑动窗口：删除窗口外记录 → 计数 → 未超限则写入当前请求并设置 TTL，原子执行 */
    private static final String SLIDING_WINDOW_LUA =
            "local key = KEYS[1]\n"
            + "local windowMs = tonumber(ARGV[1])\n"
            + "local limit = tonumber(ARGV[2])\n"
            + "local nowMs = tonumber(ARGV[3])\n"
            + "local member = ARGV[4]\n"
            + "local windowStart = nowMs - windowMs\n"
            + "redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)\n"
            + "local count = redis.call('ZCARD', key)\n"
            + "if count >= limit then\n"
            + "  return 0\n"
            + "end\n"
            + "redis.call('ZADD', key, nowMs, member)\n"
            + "redis.call('PEXPIRE', key, windowMs + 2000)\n"
            + "return 1\n";

    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT;

    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(SLIDING_WINDOW_LUA);
        script.setResultType(Long.class);
        SLIDING_WINDOW_SCRIPT = script;
    }

    private final StringRedisTemplate redisTemplate;
    private final InterceptorResponseHelper responseHelper;

    @Value("${app.chat.rate-limit.enabled:true}")
    private boolean enabled;
    @Value("${app.chat.rate-limit.per-user-per-minute:5}")
    private int perUserPerMinute;
    @Value("${app.chat.rate-limit.window-seconds:60}")
    private int windowSeconds;

    public ChatRateLimitInterceptor(
            @Qualifier("scriptRedisTemplate") StringRedisTemplate redisTemplate,
            InterceptorResponseHelper responseHelper) {
        this.redisTemplate = redisTemplate;
        this.responseHelper = responseHelper;
    }

    private boolean isSseRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.contains("/student/chat/stream")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/event-stream");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled) {
            return true;
        }
        // CORS 预检 OPTIONS 直接放行，不跑限流/Redis，避免预检返回非 2xx 导致跨域报错
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Long userId = StudentHolder.getStudent() != null ? StudentHolder.getStudent().getId() : null;
        String key = userId != null ? KEY_PREFIX + userId : "chat:rl:global";
        long nowMs = System.currentTimeMillis();
        String member = UUID.randomUUID().toString();
        

        long windowMs = windowSeconds * 1000L;
        Long allowed = redisTemplate.execute(
                SLIDING_WINDOW_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(windowMs),
                String.valueOf(perUserPerMinute),
                String.valueOf(nowMs),
                member
        );

        if (allowed == null || allowed == 0) {
            log.warn("Chat rate limit exceeded (sliding window): userId={}, limit={}", userId, perUserPerMinute);
            // SSE：只返回状态码，不写 JSON body，避免 text/event-stream 已开始输出后再写 writer
            if (isSseRequest(request)) {
                if (!response.isCommitted()) {
                    response.setStatus(429);
                }
                return false;
            }
            // 非 SSE：继续写统一 JSON
            responseHelper.writeResultFail(response, 429, ErrorCode.RATE_LIMIT.getCode(), ErrorCode.RATE_LIMIT.getDefaultMessage());
            return false;
        }
        return true;
    }
}
