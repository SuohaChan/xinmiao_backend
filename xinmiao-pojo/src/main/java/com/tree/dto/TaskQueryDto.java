package com.tree.dto;

import lombok.Data;

@Data
public class TaskQueryDto {
    private Long id;
    private String title;
    private Integer isPublished;
    private String createTime; // 接收前端传递的时间字符串（如2025-08-01T23:57:25）
    private String deadline;   // 接收前端传递的时间字符串
}