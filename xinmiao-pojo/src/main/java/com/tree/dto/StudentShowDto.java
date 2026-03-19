package com.tree.dto;

import lombok.Data;

@Data
public class StudentShowDto {
    private Long id;
    private String nickname;
    private String avatar;
    private String type = "Student";
    private Integer level;
    private Integer totalExperience;
    /** 学院ID（WebSocket 订阅 /topic/task/college/{collegeId} 用） */
    private Long collegeId;
    /** 班级ID（WebSocket 订阅 /topic/task/class/{classId} 用） */
    private Long classId;
}
