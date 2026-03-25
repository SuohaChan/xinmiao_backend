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
 *
 * <p>设计目标：
 * <ul>
 *   <li><b>按推送成本分流</b>：院级推送 fanout 较大（订阅者更多），班级推送 fanout 较小（订阅者更少）</li>
 *   <li><b>优先级隔离</b>：拆分队列后，院级消费变慢不会阻塞班级消费（避免同一 FIFO 队列“前慢后快全被拖住”）</li>
 *   <li><b>更可控的消费策略</b>：院级可配置更保守（如 concurrency=1/prefetch=1），班级可配置更激进（并发更高、prefetch 更大）</li>
 * </ul>
 *
 * <p>拓扑结构：
 * <ul>
 *   <li>{@code xinmiao.task.exchange}：任务发布交换机（Direct）</li>
 *   <li>{@code xinmiao.task.publish.college}：院级任务发布队列（routingKey = {@code task.publish.college}）</li>
 *   <li>{@code xinmiao.task.publish.class}：班级任务发布队列（routingKey = {@code task.publish.class}）</li>
 *   <li>{@code xinmiao.task.dlx}：死信交换机（Direct）</li>
 *   <li>{@code *.dlq}：对应队列的死信队列（消费重试耗尽后进入）</li>
 * </ul>
 *
 * <p>说明：
 * <ul>
 *   <li>主队列通过 {@code x-dead-letter-exchange}/{@code x-dead-letter-routing-key} 绑定到 DLX，实现“失败进 DLQ”兜底</li>
 *   <li>消息体建议保持轻量（本项目仅传 taskId），消费者查库后再推送，减少 MQ 与 WebSocket 压力</li>
 * </ul>
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_TASK = "xinmiao.task.exchange";
    public static final String QUEUE_TASK_PUBLISH_COLLEGE = "xinmiao.task.publish.college";
    public static final String ROUTING_TASK_PUBLISH_COLLEGE = "task.publish.college";
    public static final String QUEUE_TASK_PUBLISH_CLASS = "xinmiao.task.publish.class";
    public static final String ROUTING_TASK_PUBLISH_CLASS = "task.publish.class";

    public static final String EXCHANGE_TASK_DLX = "xinmiao.task.dlx";
    public static final String QUEUE_TASK_PUBLISH_COLLEGE_DLQ = "xinmiao.task.publish.college.dlq";
    public static final String ROUTING_TASK_PUBLISH_COLLEGE_DLQ = "task.publish.college.dlq";
    public static final String QUEUE_TASK_PUBLISH_CLASS_DLQ = "xinmiao.task.publish.class.dlq";
    public static final String ROUTING_TASK_PUBLISH_CLASS_DLQ = "task.publish.class.dlq";

    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(EXCHANGE_TASK, true, false);
    }

    @Bean
    public DirectExchange taskDlxExchange() {
        return new DirectExchange(EXCHANGE_TASK_DLX, true, false);
    }

    @Bean
    public Queue taskPublishCollegeQueue() {
        return QueueBuilder.durable(QUEUE_TASK_PUBLISH_COLLEGE)
                .withArgument("x-dead-letter-exchange", EXCHANGE_TASK_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_TASK_PUBLISH_COLLEGE_DLQ)
                .build();
    }

    @Bean
    public Queue taskPublishClassQueue() {
        return QueueBuilder.durable(QUEUE_TASK_PUBLISH_CLASS)
                .withArgument("x-dead-letter-exchange", EXCHANGE_TASK_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_TASK_PUBLISH_CLASS_DLQ)
                .build();
    }

    @Bean
    public Queue taskPublishCollegeDlq() {
        return QueueBuilder.durable(QUEUE_TASK_PUBLISH_COLLEGE_DLQ).build();
    }

    @Bean
    public Queue taskPublishClassDlq() {
        return QueueBuilder.durable(QUEUE_TASK_PUBLISH_CLASS_DLQ).build();
    }

    @Bean
    public Binding taskPublishCollegeBinding(DirectExchange taskExchange, Queue taskPublishCollegeQueue) {
        return BindingBuilder.bind(taskPublishCollegeQueue).to(taskExchange).with(ROUTING_TASK_PUBLISH_COLLEGE);
    }

    @Bean
    public Binding taskPublishClassBinding(DirectExchange taskExchange, Queue taskPublishClassQueue) {
        return BindingBuilder.bind(taskPublishClassQueue).to(taskExchange).with(ROUTING_TASK_PUBLISH_CLASS);
    }

    @Bean
    public Binding taskPublishCollegeDlqBinding(DirectExchange taskDlxExchange, Queue taskPublishCollegeDlq) {
        return BindingBuilder.bind(taskPublishCollegeDlq).to(taskDlxExchange).with(ROUTING_TASK_PUBLISH_COLLEGE_DLQ);
    }

    @Bean
    public Binding taskPublishClassDlqBinding(DirectExchange taskDlxExchange, Queue taskPublishClassDlq) {
        return BindingBuilder.bind(taskPublishClassDlq).to(taskDlxExchange).with(ROUTING_TASK_PUBLISH_CLASS_DLQ);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

