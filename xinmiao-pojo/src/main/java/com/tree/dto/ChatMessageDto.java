package com.tree.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息 DTO，用于 Redis 序列化（与 Spring AI Message 互转）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private String messageType; // USER, ASSISTANT, SYSTEM
    private String text;
    /** 该条消息的 token 估算值（写入时计算，用于后续预算裁剪快速累加） */
    private Integer tokenCount;
}
