package com.tree.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务发布 MQ 消息体：仅传 taskId，消费者按 id 查库后推送，减少消息体积。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskPublishMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long taskId;
}

