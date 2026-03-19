package com.tree.config.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * RabbitMQ 发布可靠性配置：
 * - ConfirmCallback：确认消息是否到达 broker（exchange）
 * - ReturnsCallback：确认消息是否成功路由到队列（mandatory=true 时生效）
 *
 * 注意：这里只做“可感知 + 记录日志”，如需真正做到不丢消息，建议引入 Outbox 落库重试。
 */
@Slf4j
@Configuration
public class RabbitPublisherConfirmConfig {

    private final RabbitTemplate rabbitTemplate;

    public RabbitPublisherConfirmConfig(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostConstruct
    public void initCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (Boolean.TRUE.equals(ack)) {
                return;
            }
            String id = correlationData != null ? correlationData.getId() : null;
            log.warn("Rabbit publish NOT ACKed (confirm failed). correlationId={}, cause={}", id, cause);
        });

        rabbitTemplate.setReturnsCallback(returned -> log.warn(
                "Rabbit publish returned (unroutable). exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()
        ));
    }
}

