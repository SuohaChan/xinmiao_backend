package com.tree.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tree.dto.CompleteTaskDto;
import com.tree.dto.TaskStatusDto;
import com.tree.entity.StudentTask;
import com.tree.entity.Task;

import java.util.List;

/**
 * 学生任务：按类型查任务、完成任务。排行榜逻辑见 {@link RankService}。
 */
public interface StudentTaskService extends IService<StudentTask> {

    List<TaskStatusDto> getTasksByType(String type);

    void linkStudentTask(Task task);

    List<Long> getTargetStudentIds(Task task);

    void removeByTaskId(Long id);

    /** 返回本次获得的积分 */
    int completeTask(CompleteTaskDto completeTaskDto);
}
