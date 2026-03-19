package com.tree.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单个任务下各班级完成情况（老师查询自己发布的任务各班级完成情况用）
 */
@Data
public class TaskClassCompletionDto {
    private Long taskId;
    private String taskTitle;
    private String level;
    private String college;
    private String clazz;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime deadline;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /** 该任务下各班级的完成统计 */
    private List<ClassTaskStatsDto> classStats;
}
