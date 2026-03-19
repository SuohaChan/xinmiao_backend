package com.tree.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 接收前端资讯文本参数的DTO
 */
@Data
public class AddInformationDto {
    private Integer isRequired;  // 对应前端is_required
    @NotBlank(message = "资讯标题不能为空")
    private String title;        // 资讯标题
    private String desc;         // 资讯简述
    private String scope;        // 发布范围
    private String college;      // 学院
    private String clazz;        // 班级
    private Integer isPublished; // 对应前端is_published
}