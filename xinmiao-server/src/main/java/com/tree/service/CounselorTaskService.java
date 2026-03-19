package com.tree.service;

import com.tree.dto.CounselorTaskStatusDto;
import com.tree.dto.TaskClassCompletionDto;
import com.tree.entity.Task;

import java.util.List;

/**
 * 辅导员任务相关：班级完成情况、任务状态统计、按用户查任务。
 */
public interface CounselorTaskService {

    List<TaskClassCompletionDto> getClassTask();

    CounselorTaskStatusDto getTaskStatus();

    List<Task> getTaskByUserId(Long userId);
}
