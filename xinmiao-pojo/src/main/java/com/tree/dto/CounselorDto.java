package com.tree.dto;

import lombok.Data;

@Data
public class CounselorDto {
    private Long id;
    private String name;
    private String type = "Counselor";
    /** 是否超级管理员（可访问学院/班级管理接口） */
    private Boolean isAdmin;
}
