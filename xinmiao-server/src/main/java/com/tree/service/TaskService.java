package com.tree.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tree.dto.TaskQueryDto;
import com.tree.entity.Task;

public interface TaskService extends IService<Task> {

    void deleteTaskById(Long id);

    void updateTask(Task task);

    void addTask(Task task);

    IPage<Task> pageAllTasks(Integer pageNum);

    IPage<Task> page(TaskQueryDto queryDTO, Integer pageNum, Integer pageSize);

    Task getTaskById(Long id);
}
