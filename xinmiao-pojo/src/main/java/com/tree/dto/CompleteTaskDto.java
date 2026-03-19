package com.tree.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 完成任务请求DTO（studentId 由 token 解析，不传）
 */
@Data
public class CompleteTaskDto {
    @NotBlank(message = "任务ID不能为空")
    private String taskId;
    /** 由 token 解析，前端不传 */
    private String studentId;
}