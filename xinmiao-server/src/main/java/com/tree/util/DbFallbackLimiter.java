package com.tree.util;

import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 回源限流：Redis 不可用时，限制同时回源 DB 的并发数，超出则快速失败（503），优先保障数据库存活。
 * 供 CacheClient、RankCacheHelper、NoticeServiceImpl 等缓存回源路径使用。
 */
@Slf4j
@Component
public class DbFallbackLimiter {

    private final Semaphore permit;
    private final long acquireTimeoutMillis;

    public DbFallbackLimiter(
            @Value("${app.cache.fallback.max-concurrent:20}") int maxConcurrent,
            @Value("${app.cache.fallback.acquire-timeout-millis:200}") long acquireTimeoutMillis) {
        this.permit = new Semaphore(maxConcurrent);
        this.acquireTimeoutMillis = acquireTimeoutMillis;
    }

    /**
     * 在持有回源许可时执行回源逻辑，执行完毕后释放许可。
     * 若在超时时间内未拿到许可，抛出 SERVICE_BUSY，由上层返回 503。
     *
     * @param supplier 回源逻辑（如查 DB）
     * @return 回源结果
     * @throws BusinessException 当限流触发时（ErrorCode.SERVICE_BUSY）
     */
    public <T> T runWithFallbackPermit(Supplier<T> supplier) {
        boolean acquired = false;
        try {
            acquired = permit.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("回源限流触发：未在 {}ms 内拿到许可，拒绝回源", acquireTimeoutMillis);
                throw new BusinessException(ErrorCode.SERVICE_BUSY, "服务繁忙，请稍后重试");
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("回源许可等待被中断");
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "服务繁忙，请稍后重试");
        } finally {
            if (acquired) {
                permit.release();
            }
        }
    }
}
