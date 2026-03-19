package com.tree.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生信息展示 DTO，含 collegeId/classId 供前端 WebSocket 订阅用
 */
@Data
public class StudentInfoShowDto {
    private Long id;
    private String name;
    private String gender;
    private String idNumber;
    private String admissionNumber;
    private String school;
    private String face;
    private Long credit;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
    /** 学院ID（WebSocket 订阅 /topic/task/college/{collegeId} 用） */
    private Long collegeId;
    /** 班级ID（WebSocket 订阅 /topic/task/class/{classId} 用） */
    private Long classId;
}
