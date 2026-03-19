package com.tree.listener;

import com.tree.config.mq.RabbitConfig;
import com.tree.dto.TaskPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * DLQ 消费者：任务发布消息重试耗尽后进入死信队列时，记录告警日志。
 * 便于排查消费失败原因，可结合日志聚合（ELK、Loki）或监控（Prometheus + Alertmanager）配置告警。
 */
@Slf4j
@Component
public class TaskPublishDlqConsumer {

    @RabbitListener(queues = RabbitConfig.QUEUE_TASK_PUBLISH_DLQ)
    public void onDeadLetter(TaskPublishMessage message) {
        if (message == null) {
            log.error("[DLQ 告警] 任务发布消息消费失败进入死信队列，消息体为空，请排查主队列消费者异常原因");
            return;
        }
        log.error("[DLQ 告警] 任务发布消息消费失败进入死信队列 taskId={}，请排查主队列消费者异常原因并人工处理（可考虑重新投递或查库补偿）", message.getTaskId());
    }
}
