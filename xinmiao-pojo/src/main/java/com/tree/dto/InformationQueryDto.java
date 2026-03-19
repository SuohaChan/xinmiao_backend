package com.tree.dto;

import lombok.Data;

@Data
public class InformationQueryDto {
    private Long id;
    private String title;
    private Integer isPublished;
    private String createTime;
}