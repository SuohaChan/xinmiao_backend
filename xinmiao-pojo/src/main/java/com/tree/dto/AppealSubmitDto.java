package com.tree.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author SuohaChan
 * @data 2025/9/14
 */
@Data
public class AppealSubmitDto {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "申诉类型不能为空")
    private String appealType;  // account/content/reward/other

    @NotBlank(message = "申诉标题不能为空")
    private String appealTitle;

    private String appealDescription;

    private String contactInfo;
}
