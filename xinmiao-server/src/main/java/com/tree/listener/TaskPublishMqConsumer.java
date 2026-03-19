package com.tree.listener;

import com.tree.config.mq.RabbitConfig;
import com.tree.dto.TaskPublishMessage;
import com.tree.dto.TaskPushDto;
import com.tree.entity.Task;
import com.tree.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * MQ 消费者：消费“任务发布”消息，查库后按校级/院级/班级推送 WebSocket。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPublishMqConsumer {

    private final TaskService taskService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @RabbitListener(queues = RabbitConfig.QUEUE_TASK_PUBLISH)
    public void onTaskPublished(TaskPublishMessage message) {
        if (message == null || message.getTaskId() == null) {
            log.warn("task publish message invalid: {}", message);
            return;
        }
        Task task = taskService.getById(message.getTaskId());
        if (task == null) {
            log.warn("task not found, skip push. taskId={}", message.getTaskId());
            return;
        }
        if (task.getIsPublished() == null || task.getIsPublished() != 1) {
            return;
        }

        TaskPushDto payload = toPushDto(task);
        String level = task.getLevel();
        Long collegeId = task.getCollegeId();
        Long classId = task.getClassId();

        if ("校级".equals(level)) {
            // 禁用“校级”广播推送：校级订阅者数量可能很大，容易造成广播风暴与尾延迟尖刺。
            // 如需启用，建议配合更严格的限流/分片/外置 Broker 或按灰度范围推送。
            log.debug("Skip school-level task push to avoid broadcast storm. taskId={}", task.getId());
        } else if ("院级".equals(level) && collegeId != null) {
            simpMessagingTemplate.convertAndSend("/topic/task/college/" + collegeId, payload);
        } else if ("班级".equals(level) && classId != null) {
            simpMessagingTemplate.convertAndSend("/topic/task/class/" + classId, payload);
        }
    }

    private static TaskPushDto toPushDto(Task task) {
        return TaskPushDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .level(task.getLevel())
                .score(task.getScore())
                .deadline(task.getDeadline())
                .build();
    }
}

