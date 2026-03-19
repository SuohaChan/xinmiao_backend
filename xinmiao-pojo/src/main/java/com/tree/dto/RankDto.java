// 1. 创建排行榜DTO类
package com.tree.dto;

import lombok.Data;

@Data
public class RankDto {
    // 学生ID
    private Long studentId;
    // 学生姓名
    private String studentName;
    // 学生昵称
    private String studentNickname;
    // 学院
    private String college;
    // 班级
    private String clazz;
    // 总分数
    private Integer totalScore;
    // 排名
    private Integer rank;
}