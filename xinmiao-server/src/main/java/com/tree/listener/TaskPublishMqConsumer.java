package com.tree.listener;

import com.tree.constant.RedisConstants;
import com.tree.config.mq.RabbitConfig;
import com.tree.dto.TaskPublishMessage;
import com.tree.dto.TaskPushDto;
import com.tree.entity.Task;
import com.tree.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * MQ 消费者：消费“任务发布”消息，查库后按校级/院级/班级推送 WebSocket。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPublishMqConsumer {

    private final TaskService taskService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final StringRedisTemplate stringRedisTemplate;

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

        // 幂等：同一个 taskId 的推送只会通过一次占位，避免 MQ 重试/重复投递导致重复 WebSocket 推送
        String dedupKey = RedisConstants.TASK_PUBLISH_DONE_KEY_PREFIX + task.getId();
        Boolean isFirst = stringRedisTemplate.opsForValue().setIfAbsent(
                dedupKey,
                "1",
                RedisConstants.TASK_PUBLISH_DONE_TTL_DAYS,
                TimeUnit.DAYS
        );
        if (!Boolean.TRUE.equals(isFirst)) {
            log.debug("Skip duplicate task push (idempotent hit) taskId={}", task.getId());
            return;
        }

        TaskPushDto payload = toPushDto(task);
        String level = task.getLevel();
        Long collegeId = task.getCollegeId();
        Long classId = task.getClassId();

        if ("校级".equals(level)) {
            // 校级任务：废弃不推送（保留占位逻辑，避免广播风暴与尾延迟尖刺）
            log.debug("Skip school-level task push (disabled). taskId={}", task.getId());
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

