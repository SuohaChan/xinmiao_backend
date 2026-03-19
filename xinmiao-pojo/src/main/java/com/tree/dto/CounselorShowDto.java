package com.tree.dto;

import lombok.Data;

/**
 * @author SuohaChan
 * @data 2025/9/23
 */
@Data
public class CounselorShowDto {
    private Long id;
    private String nickname;
    private String avatar;
    private final String type = "Counselor";
    /** 是否超级管理员（可管理学院/班级） */
    private Boolean isAdmin;
}
