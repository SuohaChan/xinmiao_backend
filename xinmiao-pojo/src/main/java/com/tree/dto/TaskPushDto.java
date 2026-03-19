package com.tree.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务推送轻量 DTO：仅含通知展示与拉取详情所需字段，控制 WebSocket 单条消息体积。
 * 上千连接广播时减小 payload，客户端可据此展示“新任务”提示，再按 id 请求详情。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskPushDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String level;
    private Integer score;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime deadline;
}
