package com.tree.config.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明：
 * - task.publish：任务发布消息队列（消费者推 WebSocket）
 * - task.publish.dlq：死信队列（重试耗尽后进入）
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_TASK = "xinmiao.task.exchange";
    public static final String QUEUE_TASK_PUBLISH = "xinmiao.task.publish";
    public static final String ROUTING_TASK_PUBLISH = "task.publish";

    public static final String EXCHANGE_TASK_DLX = "xinmiao.task.dlx";
    public static final String QUEUE_TASK_PUBLISH_DLQ = "xinmiao.task.publish.dlq";
    public static final String ROUTING_TASK_PUBLISH_DLQ = "task.publish.dlq";

    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(EXCHANGE_TASK, true, false);
    }

    @Bean
    public DirectExchange taskDlxExchange() {
        return new DirectExchange(EXCHANGE_TASK_DLX, true, false);
    }

    @Bean
    public Queue taskPublishQueue() {
        return QueueBuilder.durable(QUEUE_TASK_PUBLISH)
                .withArgument("x-dead-letter-exchange", EXCHANGE_TASK_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_TASK_PUBLISH_DLQ)
                .build();
    }

    @Bean
    public Queue taskPublishDlq() {
        return QueueBuilder.durable(QUEUE_TASK_PUBLISH_DLQ).build();
    }

    @Bean
    public Binding taskPublishBinding(DirectExchange taskExchange, Queue taskPublishQueue) {
        return BindingBuilder.bind(taskPublishQueue).to(taskExchange).with(ROUTING_TASK_PUBLISH);
    }

    @Bean
    public Binding taskPublishDlqBinding(DirectExchange taskDlxExchange, Queue taskPublishDlq) {
        return BindingBuilder.bind(taskPublishDlq).to(taskDlxExchange).with(ROUTING_TASK_PUBLISH_DLQ);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

