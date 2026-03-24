package com.tree.config.mq;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RabbitMQ 发布可靠性配置：
 * - ConfirmCallback：确认消息是否到达 broker（exchange）
 * - ReturnsCallback：确认消息是否成功路由到队列（mandatory=true 时生效）
 *
 * 注意：默认情况下仍保留 ReturnsCallback 记录（unroutable），但本项目“少改代码”的目标是：
 * - 对每条消息进行 confirm 同步等待（exchange ack）
 * - 失败/超时进行有限重试
 *
 * 若你需要严格的“保证不丢”（包括 unroutable），通常要引入 Outbox 或消息落库+后台重投机制。
 */
@Slf4j
@Configuration
public class RabbitPublisherConfirmConfig {

    /**
     * 等待中的 confirm：key=correlationId, value=ack 是否成功。
     * 用于将异步 confirm 回调转为同步等待。
     */
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingConfirms = new ConcurrentHashMap<>();

    private final RabbitTemplate rabbitTemplate;

    // 最少改动：使用硬编码默认值（可根据需要再改成配置项）
    private static final int CONFIRM_MAX_ATTEMPTS = 3;
    private static final long CONFIRM_TIMEOUT_MS = 500L;

    public RabbitPublisherConfirmConfig(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostConstruct
    public void initCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            String id = correlationData != null ? correlationData.getId() : null;
            if (id == null) {
                log.warn("Rabbit publish confirm callback missing correlationId. ack={}, cause={}", ack, cause);
                return;
            }

            CompletableFuture<Boolean> future = pendingConfirms.remove(id);
            if (future == null) {
                // 说明发布端没有等待这条消息的 confirm（通常不应发生）
                if (!Boolean.TRUE.equals(ack)) {
                    log.warn("Rabbit publish NOT ACKed (confirm failed), but no pending future. correlationId={}, cause={}", id, cause);
                }
                return;
            }

            future.complete(Boolean.TRUE.equals(ack));
            if (!Boolean.TRUE.equals(ack)) {
                log.warn("Rabbit publish NOT ACKed (confirm failed). correlationId={}, cause={}", id, cause);
            }
        });

        rabbitTemplate.setReturnsCallback(returned -> log.warn(
                "Rabbit publish returned (unroutable). exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()
        ));
    }

    /**
     * 同步 confirm + 失败重试：
     * - confirm 成功：返回
     * - confirm 失败/超时：有限次重试
     *
     * @throws RuntimeException confirm 持续失败时抛出
     */
    public void publishWithConfirmRetry(String exchange, String routingKey, Object message) {
        if (exchange == null || routingKey == null) {
            throw new IllegalArgumentException("exchange and routingKey must not be null");
        }

        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= CONFIRM_MAX_ATTEMPTS; attempt++) {
            String correlationId = UUID.randomUUID().toString();
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            pendingConfirms.put(correlationId, future);

            try {
                rabbitTemplate.convertAndSend(
                        exchange,
                        routingKey,
                        message,
                        new CorrelationData(correlationId)
                );

                Boolean acked = future.get(CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (Boolean.TRUE.equals(acked)) {
                    return;
                }

                lastError = new IllegalStateException("Rabbit publish confirm failed: correlationId=" + correlationId);
            } catch (TimeoutException te) {
                lastError = new IllegalStateException(
                        "Rabbit publish confirm timeout (will retry): correlationId=" + correlationId,
                        te
                );
            } catch (Exception e) {
                lastError = new IllegalStateException(
                        "Rabbit publish confirm wait exception (will retry): correlationId=" + correlationId,
                        e
                );
            } finally {
                pendingConfirms.remove(correlationId);
            }

            // 很小的退避，避免连续失败导致瞬时风暴
            if (attempt < CONFIRM_MAX_ATTEMPTS) {
                try {
                    Thread.sleep(200L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        throw lastError != null ? lastError : new IllegalStateException("Rabbit publish confirm failed");
    }
}

