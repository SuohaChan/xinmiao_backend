package com.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.exception.BusinessException;
import com.tree.mapper.TaskMapper;
import com.tree.service.StudentTaskService;
import com.tree.service.TaskService;
import com.tree.dto.TaskQueryDto;
import com.tree.entity.Clazz;
import com.tree.entity.College;
import com.tree.entity.Task;
import com.tree.config.mq.RabbitConfig;
import com.tree.config.mq.RabbitPublisherConfirmConfig;
import com.tree.dto.TaskPublishMessage;
import com.tree.service.ClassService;
import com.tree.service.CollegeService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


import com.tree.result.ErrorCode;
import com.tree.utils.DateTimeUtils;

import java.time.LocalDateTime;

/**
 * @author ljx
 * @description 针对表【tb_task(任务)】的数据库操作Service实现
 * @createDate 2024-02-17 14:26:55
 */
@Slf4j
@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements TaskService {
    // 每页固定10条数据
    private static final Integer PAGE_SIZE = 10;

    private final StudentTaskService studentTaskService;
    private final CollegeService collegeService;
    private final ClassService classService;
    private final RabbitPublisherConfirmConfig rabbitPublisherConfirmConfig;

    public TaskServiceImpl(StudentTaskService studentTaskService,
                            CollegeService collegeService,
                            ClassService classService,
                            RabbitPublisherConfirmConfig rabbitPublisherConfirmConfig) {
        this.studentTaskService = studentTaskService;
        this.collegeService = collegeService;
        this.classService = classService;
        this.rabbitPublisherConfirmConfig = rabbitPublisherConfirmConfig;
    }

    /** 根据学院/班级名称填充 collegeId、classId（未传 ID 时用） */
    private void fillTaskCollegeAndClassIds(Task task) {
        if (task.getCollegeId() == null && task.getCollege() != null && !task.getCollege().isBlank()) {
            College co = collegeService.getOne(Wrappers.<College>lambdaQuery().eq(College::getName, task.getCollege().trim()));
            if (co != null) task.setCollegeId(co.getId());
        }
        if (task.getClassId() == null && task.getClazz() != null && !task.getClazz().isBlank() && task.getCollegeId() != null) {
            Clazz cz = classService.getOne(Wrappers.<Clazz>lambdaQuery()
                    .eq(Clazz::getName, task.getClazz().trim())
                    .eq(Clazz::getCollegeId, task.getCollegeId()));
            if (cz != null) task.setClassId(cz.getId());
        }
    }


    /**
     * 新增任务
     * @param task 封装任务数据的实体对象
     * @return 操作结果的Result对象（包含新增任务的ID）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTask(Task task) {
        fillTaskCollegeAndClassIds(task);
        boolean saved = save(task);
        if (!saved) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "任务创建失败");
        if (task.getIsPublished() == 1) {
            studentTaskService.linkStudentTask(task);
            publishTaskMessageAfterCommit(task);
        }
    }

    /**
     * 根据ID删除任务（
     * @param id 任务ID
     * @return 操作结果的Result对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTaskById(Long id) {
        studentTaskService.removeByTaskId(id);
        boolean removed = removeById(id);
        if (!removed) throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在或已删除");
    }

    /**
     * 条件查询任务（支持多字段匹配）
     * @param queryDTO 封装查询条件的实体对象
     * @return 包含任务列表的Result对象
     */
    @Override
    public IPage<Task> page(TaskQueryDto queryDTO, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getId() != null) queryWrapper.eq(Task::getId, queryDTO.getId());
        if (queryDTO.getTitle() != null && !queryDTO.getTitle().trim().isEmpty()) queryWrapper.like(Task::getTitle, queryDTO.getTitle().trim());
        if (queryDTO.getIsPublished() != null) queryWrapper.eq(Task::getIsPublished, queryDTO.getIsPublished());

        LocalDateTime queryStart = DateTimeUtils.parse(queryDTO.getCreateTime());
        LocalDateTime queryEnd = DateTimeUtils.parse(queryDTO.getDeadline());
        if (queryStart != null) queryWrapper.ge(Task::getCreateTime, queryStart);
        if (queryEnd != null) queryWrapper.le(Task::getDeadline, queryEnd);

        Page<Task> page = new Page<>(pageNum, pageSize);
        baseMapper.selectPage(page, queryWrapper);
        return page;
    }

    @Override
    public Task getTaskById(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "任务ID不能为空");
        Task task = getById(id);
        if (task == null) throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        return task;
    }

    /**
     * 更新任务
     * @param task 封装更新数据的实体对象（必须包含ID）
     * @return 操作结果的Result对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(Task task) {
        fillTaskCollegeAndClassIds(task);
        boolean updated = updateById(task);
        if (!updated) throw new BusinessException(ErrorCode.NOT_FOUND, "任务id不存在");
        if (task.getIsPublished() != null && task.getIsPublished() == 1) {
            publishTaskMessageAfterCommit(task);
        }
    }

    /**
     * 事务提交后投递 MQ：避免回滚导致“消息已发但 DB 未提交”。
     */
    private void publishTaskMessageAfterCommit(Task task) {
        if (task == null || task.getId() == null) return;
        Long taskId = task.getId();
        String level = task.getLevel();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 校级任务不走推送，避免全局广播风暴
                if ("校级".equals(level)) {
                    return;
                }
                String routingKey;
                if ("院级".equals(level)) {
                    routingKey = RabbitConfig.ROUTING_TASK_PUBLISH_COLLEGE;
                } else if ("班级".equals(level)) {
                    routingKey = RabbitConfig.ROUTING_TASK_PUBLISH_CLASS;
                } else {
                    // 未知 level：不推送，避免错误广播
                    return;
                }
                rabbitPublisherConfirmConfig.publishWithConfirmRetry(
                        RabbitConfig.EXCHANGE_TASK,
                        routingKey,
                        new TaskPublishMessage(taskId)
                );
            }
        });
    }

    /**
     * 分页查询所有任务（每页10条）
     * @param pageNum 页码（默认第1页）
     * @return 包含分页数据的Result对象
     */
    @Override
    public IPage<Task> pageAllTasks(Integer pageNum) {
        int currentPage = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        Page<Task> page = new Page<>(currentPage, PAGE_SIZE);
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(Task::getCreateTime);
        return baseMapper.selectPage(page, queryWrapper);
    }
}