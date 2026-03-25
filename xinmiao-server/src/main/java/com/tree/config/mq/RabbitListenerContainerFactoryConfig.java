package com.tree.config.mq;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为不同队列提供不同消费参数：
 * - 院级：concurrency=1、prefetch=1（更稳，防 fanout 突刺）
 * - 班级：concurrency=1~N、prefetch 较大（更快，降低等待）
 */
@Configuration
public class RabbitListenerContainerFactoryConfig {

    @Bean(name = "collegeRabbitListenerContainerFactory")
    public RabbitListenerContainerFactory<?> collegeRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        return factory;
    }

    @Bean(name = "classRabbitListenerContainerFactory")
    public RabbitListenerContainerFactory<?> classRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            @Value("${app.task-push.class.concurrency:2}") int concurrency,
            @Value("${app.task-push.class.prefetch:10}") int prefetch) {
        int c = Math.max(1, concurrency);
        int p = Math.max(1, prefetch);
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(c);
        factory.setMaxConcurrentConsumers(c);
        factory.setPrefetchCount(p);
        return factory;
    }
}

