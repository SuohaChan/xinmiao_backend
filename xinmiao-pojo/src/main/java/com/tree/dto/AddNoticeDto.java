package com.tree.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 接收前端通知文本参数的DTO
 */
@Data
public class AddNoticeDto {
    private Integer isRequired;  // 对应前端is_required
    @NotBlank(message = "通知标题不能为空")
    private String title;        // 通知标题
    @JsonProperty("desc")
    private String description;   // 通知简述（接口字段名仍为 desc）
    private String scope;        // 发布范围
    private String college;      // 学院
    private String clazz;        // 班级
    private Integer isPublished; // 对应前端is_published
}