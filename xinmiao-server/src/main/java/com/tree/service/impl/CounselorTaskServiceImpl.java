package com.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.context.CounselorHolder;
import com.tree.exception.BusinessException;
import com.tree.mapper.CounselorMapper;
import com.tree.mapper.StudentTaskMapper;
import com.tree.mapper.TaskMapper;
import com.tree.service.CounselorTaskService;
import com.tree.dto.ClassTaskStatsDto;
import com.tree.dto.CounselorTaskStatusDto;
import com.tree.dto.TaskClassCompletionDto;
import com.tree.entity.Counselor;
import com.tree.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import com.tree.result.ErrorCode;

/**
 * @author SuohaChan
 * @data 2025/9/10
 */

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class CounselorTaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements CounselorTaskService {

    private final CounselorMapper counselorMapper;
    private final StudentTaskMapper studentTaskMapper;
    private final TaskMapper taskMapper;

    public CounselorTaskServiceImpl(CounselorMapper counselorMapper, StudentTaskMapper studentTaskMapper, TaskMapper taskMapper) {
        this.counselorMapper = counselorMapper;
        this.studentTaskMapper = studentTaskMapper;
        this.taskMapper = taskMapper;
    }


    /**
     * 老师查询自己发布的任务、各班级的完成情况（每个任务下按班级统计完成人数与完成率）
     */
    @Override
    public List<TaskClassCompletionDto> getClassTask() {
        if (CounselorHolder.getCounselor() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        Long counselorId = CounselorHolder.getCounselor().getId();
        Counselor counselor = counselorMapper.selectById(counselorId);
        if (counselor == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "辅导员信息不存在");
        }

        LambdaQueryWrapper<Task> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(Task::getTeacherId, counselorId)
                .eq(Task::getIsPublished, 1)
                .orderByDesc(Task::getCreateTime);
        List<Task> tasks = taskMapper.selectList(taskWrapper);
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        List<ClassTaskStatsDto> allStats = studentTaskMapper.selectClassStatsByTaskIds(taskIds);
        Map<Long, List<ClassTaskStatsDto>> statsByTaskId = allStats.stream()
                .collect(Collectors.groupingBy(ClassTaskStatsDto::getTaskId));

        List<TaskClassCompletionDto> result = new ArrayList<>();
        for (Task task : tasks) {
            List<ClassTaskStatsDto> classStats = statsByTaskId.getOrDefault(task.getId(), new ArrayList<>());
            for (ClassTaskStatsDto s : classStats) {
                s.setUncompletedStudents(s.getTotalStudents() - s.getCompletedStudents());
                double rate = s.getTotalStudents() > 0
                        ? (double) s.getCompletedStudents() / s.getTotalStudents() * 100 : 0;
                s.setCompletionRate(Math.round(rate * 100) / 100.0);
            }
            classStats.sort(Comparator.comparing(ClassTaskStatsDto::getCollege).thenComparing(ClassTaskStatsDto::getClazz));

            TaskClassCompletionDto dto = new TaskClassCompletionDto();
            dto.setTaskId(task.getId());
            dto.setTaskTitle(task.getTitle());
            dto.setLevel(task.getLevel());
            dto.setCollege(task.getCollege());
            dto.setClazz(task.getClazz());
            dto.setDeadline(task.getDeadline());
            dto.setCreateTime(task.getCreateTime());
            dto.setClassStats(classStats);
            result.add(dto);
        }
        return result;
    }

    /**
     * 查看辅导员发布的任务状态统计（仅含数量）
     */
    @Override
    public CounselorTaskStatusDto getTaskStatus() {
        if (CounselorHolder.getCounselor() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        Long counselorId = CounselorHolder.getCounselor().getId();
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getTeacherId, counselorId)
                .eq(Task::getIsPublished, 1);

        List<Task> tasks = taskMapper.selectList(queryWrapper);
        if (tasks.isEmpty()) {
            return new CounselorTaskStatusDto(0, 0, 0);
        }

        //计算任务状态
        LocalDateTime now = LocalDateTime.now();
        long normalCount = 0; //正常进行
        long soonExpireCount = 0;//快要过期(24 小时)
        long expiredCount = 0; //已过期

        for (Task task : tasks) {
            LocalDateTime deadline = task.getDeadline();
            if (deadline == null){
                normalCount++;
                continue;
            }
            if (now.isAfter( deadline))
                expiredCount++;
            else if(ChronoUnit.HOURS.between(now,deadline) <= 24)
                soonExpireCount++;
            else
                normalCount++;
        }

        return new CounselorTaskStatusDto(normalCount, soonExpireCount, expiredCount);
    }

    @Override
    public List<Task> getTaskByUserId(Long userId) {
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getTeacherId, userId)
                .eq(Task::getIsPublished, 1);

        return taskMapper.selectList(queryWrapper);
    }
}



