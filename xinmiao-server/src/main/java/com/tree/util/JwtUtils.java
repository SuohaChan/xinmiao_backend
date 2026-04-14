package com.tree.util;

import com.tree.constant.RedisConstants;
import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT Access Token 生成与校验（HS256）。
 * 校验时先验签与过期，再查黑名单（登出后立即失效）。
 */
@Slf4j
@Component
public class JwtUtils {

    private final SecretKey secretKey;
    private final StringRedisTemplate redis;
    /** 签发（HS256 签名 + compact），不含 BCrypt / DB */
    private final Timer generateTimer;
    /** 验签 + 过期（JJWT parseSignedClaims），不含 Redis */
    private final Timer verifyCryptoTimer;
    /** refresh 路径不经过；登出后校验时 Redis 黑名单 hasKey */
    private final Timer verifyBlacklistTimer;

    public JwtUtils(
            @Value("${jwt.secret}") String secret,
            StringRedisTemplate redis,
            MeterRegistry meterRegistry) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.redis = redis;
        // publishPercentileHistogram + 显式 SLO 桶：导出 Prometheus 的 jwt_token_seconds_bucket，便于 histogram_quantile 算 P95/P99
        Duration[] sloMs = new Duration[] {
                Duration.ofMillis(1), Duration.ofMillis(2), Duration.ofMillis(3), Duration.ofMillis(4),
                Duration.ofMillis(5), Duration.ofMillis(7), Duration.ofMillis(10), Duration.ofMillis(25)
        };
        this.generateTimer = Timer.builder("jwt.token")
                .description("JWT access token: sign (generate) or verify crypto / blacklist")
                .tag("operation", "generate")
                .publishPercentileHistogram()
                .serviceLevelObjectives(sloMs)
                .register(meterRegistry);
        this.verifyCryptoTimer = Timer.builder("jwt.token")
                .tag("operation", "verify_crypto")
                .publishPercentileHistogram()
                .serviceLevelObjectives(sloMs)
                .register(meterRegistry);
        this.verifyBlacklistTimer = Timer.builder("jwt.token")
                .tag("operation", "verify_blacklist")
                .publishPercentileHistogram()
                .serviceLevelObjectives(sloMs)
                .register(meterRegistry);
    }

    /**
     * 生成 JWT Access Token（sub=userId, type=Student|Counselor, jti=唯一id 用于黑名单）
     */
    public String generateAccessToken(Long userId, String type, long expiresInMillis) {
        return generateAccessToken(userId, type, expiresInMillis, null);
    }

    /**
     * 生成 JWT，辅导员可带 isAdmin 用于学院/班级管理权限
     */
    public String generateAccessToken(Long userId, String type, long expiresInMillis, Boolean isAdmin) {
        return generateTimer.record(() -> {
            Date now = new Date();
            Date exp = new Date(now.getTime() + expiresInMillis);
            String jti = java.util.UUID.randomUUID().toString().replace("-", "");
            var builder = Jwts.builder()
                    .subject(String.valueOf(userId))
                    .claim("type", type)
                    .id(jti)
                    .issuedAt(now)
                    .expiration(exp);
            if (isAdmin != null) {
                builder.claim("isAdmin", isAdmin);
            }
            return builder.signWith(secretKey).compact();
        });
    }

    /**
     * 校验 JWT：先查黑名单，再验签与过期。返回 payload，无效则返回 null。
     */
    public Claims verifyAndGetClaims(String token) {
        if (token == null || token.isBlank())
            return null;
        try {
            Claims claims = verifyCryptoTimer.record(() -> Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
            if (verifyBlacklistTimer.record(() -> isBlacklisted(claims.getId()))) {
                log.debug("JWT in blacklist");
                return null;
            }
            return claims;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.debug("JWT verify failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 仅解析并验签、过期，不查黑名单（供登出时拿 jti/exp 写入黑名单用）
     */
    public Claims parseClaims(String token) {
        if (token == null || token.isBlank())
            return null;
        try {
            return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            log.debug("JWT parse failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 JWT 加入黑名单（登出时调用），TTL 为剩余有效秒数
     */
    public void blacklist(String token) {
        Claims claims = parseClaims(token);
        if (claims == null)
            return;
        String jti = claims.getId();
        Date exp = claims.getExpiration();
        if (jti != null && exp != null) {
            long ttlSeconds = Math.max(1, (exp.getTime() - System.currentTimeMillis()) / 1000);
            try {
                redis.opsForValue().set(RedisConstants.JWT_BLACKLIST_KEY + jti, "1",
                        java.time.Duration.ofSeconds(ttlSeconds));
            } catch (Exception e) {
                log.warn("Redis unavailable when writing JWT blacklist, fail with 503. jti={}", jti, e);
                throw new BusinessException(ErrorCode.SERVICE_BUSY, "服务繁忙，请稍后重试");
            }
        }
    }

    private boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(RedisConstants.JWT_BLACKLIST_KEY + jti));
        } catch (Exception e) {
            log.warn("Redis unavailable when checking JWT blacklist, fail with 503. jti={}", jti, e);
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "服务繁忙，请稍后重试");
        }
    }
}
