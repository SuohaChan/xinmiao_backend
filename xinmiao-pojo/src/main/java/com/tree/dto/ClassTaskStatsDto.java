package com.tree.dto;

/**
 * @author SuohaChan
 * @data 2025/9/10
 */


import lombok.Data;

/**
 * 班级任务统计数据传输对象
 */
@Data
public class ClassTaskStatsDto {
    private String college;          // 学院
    private String clazz;            // 班级
    private Long taskId;             // 任务ID
    private String taskTitle;        // 任务标题
    private long totalStudents;      // 总人数
    private long completedStudents;  // 已完成人数
    private long uncompletedStudents;// 未完成人数
    private double completionRate;   // 完成率(百分比)
}
