package com.tree.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author SuohaChan
 * @data 2025/9/14
 */
@Data
public class AppealUpdateDto {
    @NotNull(message = "申诉ID不能为空")
    private Long id;

    private Long handlerId;

    private String replyContent;

    @NotBlank(message = "处理状态不能为空")
    private String status;  // accept/processing/complete/rejected
}

