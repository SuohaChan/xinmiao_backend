package com.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.constant.RedisConstants;
import com.tree.dto.CompleteTaskDto;
import com.tree.dto.TaskStatusDto;
import com.tree.entity.CreditFlow;
import com.tree.entity.StudentClass;
import com.tree.entity.StudentTask;
import com.tree.entity.Task;
import com.tree.entity.StudentInfo;
import com.tree.mapper.CreditFlowMapper;
import com.tree.mapper.StudentClassMapper;
import com.tree.mapper.StudentInfoMapper;
import com.tree.exception.BusinessException;
import com.tree.mapper.StudentTaskMapper;
import com.tree.mapper.TaskMapper;
import com.tree.service.RankService;
import com.tree.service.StudentTaskService;
import com.tree.utils.RankKeyUtils;
import com.tree.context.StudentHolder;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.bean.BeanUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

import com.tree.dto.TaskStatusItem;
import com.tree.result.ErrorCode;


/**
 * @author ljx
 * @description 针对表【tb_student_task(学生和任务关系)】的数据库操作Service实现
 * @createDate 2024-02-28 17:59:39
 */
@Slf4j
@Service
public class StudentTaskServiceImpl extends ServiceImpl<StudentTaskMapper, StudentTask> implements StudentTaskService {

    private final StudentClassMapper studentClassMapper;
    private final CreditFlowMapper creditFlowMapper;
    private final TaskMapper taskMapper;
    private final StudentTaskMapper studentTaskMapper;
    private final StudentInfoMapper studentInfoMapper;
    private final RankService rankService;
    private final StringRedisTemplate stringRedisTemplate;

    public StudentTaskServiceImpl(StudentClassMapper studentClassMapper,
                                  CreditFlowMapper creditFlowMapper,
                                  TaskMapper taskMapper,
                                  StudentTaskMapper studentTaskMapper,
                                  StudentInfoMapper studentInfoMapper,
                                  RankService rankService,
                                  StringRedisTemplate stringRedisTemplate) {
        this.studentClassMapper = studentClassMapper;
        this.creditFlowMapper = creditFlowMapper;
        this.taskMapper = taskMapper;
        this.studentTaskMapper = studentTaskMapper;
        this.studentInfoMapper = studentInfoMapper;
        this.rankService = rankService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 学生任务完成 Set key：防重，member=taskId */
    private static String stuTaskDoneKey(Long studentId) {
        return RedisConstants.STU_TASK_DONE_KEY_PREFIX + studentId + ":done";
    }

    /** 将完成任务写入 Redis Set 并设置 TTL，便于后续防重 */
    private void putTaskDoneToRedis(String key, String taskId) {
        stringRedisTemplate.opsForSet().add(key, taskId);
        if (RedisConstants.STU_TASK_DONE_TTL_DAYS > 0) {
            stringRedisTemplate.expire(key, RedisConstants.STU_TASK_DONE_TTL_DAYS, TimeUnit.DAYS);
        }
    }
    /**
     * 根据任务类型获取任务列表
     * @param type 任务类型（"校级"、"院级"、"班级"）
     * @return 包含任务列表的Result对象
     */
    @Override
    public List<TaskStatusDto> getTasksByType(String type) {
        if (StudentHolder.getStudent() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        Long studentId = StudentHolder.getStudent().getId();
        log.info("学生ID: {}", studentId);

        StudentClass studentClass = studentClassMapper.selectOne(
                Wrappers.<StudentClass>lambdaQuery()
                        .eq(StudentClass::getStudentId, studentId)
        );
        if (studentClass == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生班级学院信息不存在");
        }

        Long studentCollegeId = studentClass.getCollegeId();
        Long studentClassId = studentClass.getClassId();

        List<Task> tasks;
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getIsPublished, 1);

        tasks = switch (type) {
            case "校级" -> {
                queryWrapper.eq(Task::getLevel, "校级");
                yield taskMapper.selectList(queryWrapper);
            }
            case "院级" -> {
                queryWrapper.eq(Task::getLevel, "院级").eq(Task::getCollegeId, studentCollegeId);
                yield taskMapper.selectList(queryWrapper);
            }
            case "班级" -> {
                queryWrapper.eq(Task::getLevel, "班级").eq(Task::getClassId, studentClassId);
                yield taskMapper.selectList(queryWrapper);
            }
            case "未完成" -> baseMapper.selectUnfinishedTasks(studentId, studentCollegeId, studentClassId);
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的任务类型");
        };

        if (tasks.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        List<TaskStatusItem> statusList = baseMapper.selectTaskStatusBatch(studentId, taskIds);
        Map<Long, Integer> statusMap = statusList.stream()
                .collect(Collectors.toMap(TaskStatusItem::getTaskId, TaskStatusItem::getStatus, (a, b) -> b));

        List<TaskStatusDto> taskDTOs = new ArrayList<>(tasks.stream().map(task -> {
                    TaskStatusDto dto = new TaskStatusDto();
                    BeanUtil.copyProperties(task, dto);
                    Integer status = statusMap.getOrDefault(task.getId(), 0);
                    dto.setCompleted(status != null && status == 1);
                    return dto;
                })
                .toList());

        if (!"未完成".equals(type)) {
            taskDTOs.sort((t1, t2) -> t2.getDeadline().compareTo(t1.getDeadline()));
        }

        return taskDTOs;
    }

    // 关联学生任务
    @Override
    public void linkStudentTask(Task task) {
        List<Long> targetStudentIds = getTargetStudentIds(task);
        if (targetStudentIds.isEmpty()) {
            log.warn("任务【{}】未找到匹配的学生", task.getId());
            return;
        }

        List<StudentTask> studentTasks = targetStudentIds.stream().map(studentId -> {
            StudentTask st = new StudentTask();
            st.setStudentId(studentId);
            st.setTaskId(task.getId());
            st.setStatus(0); // 0-未完成
            st.setCreateTime(LocalDateTime.now());
            st.setUpdateTime(LocalDateTime.now());
            return st;
        }).collect(Collectors.toList());

        studentTaskMapper.batchInsert(studentTasks);
        log.info("任务【{}】已关联 {} 名学生", task.getId(), studentTasks.size());
    }

    /**
     * 根据任务级别查询目标范围学生ID列表
     * 校级任务查询校级学生
     * 院级任务查询院级学生
     * 班级任务查询班级学生
     */
    @Override
    public List<Long> getTargetStudentIds(Task task) {
        String level = task.getLevel();
        LambdaQueryWrapper<StudentClass> queryWrapper = new LambdaQueryWrapper<>();

        switch (level) {
            case "校级":
                return studentClassMapper.selectList(queryWrapper).stream()
                        .map(StudentClass::getStudentId)
                        .collect(Collectors.toList());
            case "院级":
                if (task.getCollegeId() == null) return Collections.emptyList();
                queryWrapper.eq(StudentClass::getCollegeId, task.getCollegeId());
                break;
            case "班级":
                if (task.getClassId() == null) return Collections.emptyList();
                queryWrapper.eq(StudentClass::getClassId, task.getClassId());
                break;
            default:
                log.error("无效的任务级别: {}", level);
                return Collections.emptyList();
        }
        return studentClassMapper.selectList(queryWrapper).stream()
                .map(StudentClass::getStudentId)
                .collect(Collectors.toList());
    }

    @Override
    public void removeByTaskId(Long id) {
        LambdaQueryWrapper<StudentTask> stQuery = new LambdaQueryWrapper<>();
        stQuery.eq(StudentTask::getTaskId, id);
        studentTaskMapper.delete(stQuery);
    }

    /**
     * 完成任务
     * @param dto 完成任务请求DTO
     * @return Result对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int completeTask(CompleteTaskDto dto) {
        if (StudentHolder.getStudent() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        Long studentId = StudentHolder.getStudent().getId();
        log.info("学生ID: {}, 任务ID: {}", studentId, dto.getTaskId());

        // 0. 先查 Redis 防重：若该任务已在学生完成 Set 中则视为已完成，直接返回，不动 DB
        String stuTaskKey = stuTaskDoneKey(studentId);
        if (Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(stuTaskKey, dto.getTaskId()))) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "任务已经完成");
        }

        // 1. 基础校验：任务是否存在、是否已完成（DB 为准）
        Integer status = baseMapper.getTaskStatus(studentId, Long.valueOf(dto.getTaskId()));
        if (status == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }

        if (status == 1) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "任务已经完成");
        }

        // 2. 幂等更新：仅允许从“未完成”变为“已完成”（并发下只会成功一次）
        LambdaUpdateWrapper<StudentTask> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(StudentTask::getStudentId, studentId)
                .eq(StudentTask::getTaskId, dto.getTaskId())
                .eq(StudentTask::getStatus, 0)
                .set(StudentTask::getStatus, 1)
                .set(StudentTask::getSubmitTime, LocalDateTime.now());

        int rows = studentTaskMapper.update(null, updateWrapper);
        if (rows == 0) {
            // 并发场景下可能被其他请求抢先完成，视为已完成：回写 Redis 便于下次先走 Redis
            putTaskDoneToRedis(stuTaskKey, dto.getTaskId());
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "任务已经完成");
        }

        // 3. 查询任务积分并增加学生积分（DB 为真相）
        Task task = taskMapper.selectById(dto.getTaskId());
        int credit = (task != null && task.getScore() != null) ? task.getScore() : 0;
        String college = null;
        if (credit > 0) {
            LambdaUpdateWrapper<StudentInfo> creditUpdate = new LambdaUpdateWrapper<>();
            creditUpdate.eq(StudentInfo::getId, studentId)
                    .setSql("credit = IFNULL(credit, 0) + " + credit);
            studentInfoMapper.update(null, creditUpdate);

            // 4. 写流水表（审计 + 缺 key 时从流水聚榜）
            LocalDateTime now = LocalDateTime.now();
            StudentClass sc = studentClassMapper.selectOne(
                    Wrappers.<StudentClass>lambdaQuery().eq(StudentClass::getStudentId, studentId));
            if (sc != null) {
                college = sc.getCollege();
            }
            CreditFlow flow = new CreditFlow();
            flow.setStudentId(studentId);
            flow.setTaskId(Long.valueOf(dto.getTaskId()));
            flow.setCredit(credit);
            flow.setCollege(college);
            flow.setOccurredAt(now);
            creditFlowMapper.insert(flow);
        }

        // 5. 事务提交后写 Redis：学生任务完成防重 Set（始终写）；有积分时再写排行榜
        final String taskDoneKey = stuTaskKey;
        final String taskIdVal = dto.getTaskId();
        final int rankCredit = credit;
        final String collegeForRank = college;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    putTaskDoneToRedis(taskDoneKey, taskIdVal);
                } catch (Exception e) {
                    log.warn("任务完成防重 Set 写入失败 studentId={} taskId={}", studentId, taskIdVal, e);
                }
                if (rankCredit > 0) {
                    try {
                        String member = String.valueOf(studentId);
                        String todayKey = RankKeyUtils.todayRankKey();
                        String weekKey = RankKeyUtils.weekRankKey();
                        String collegeKey = (collegeForRank != null && !collegeForRank.isEmpty()) ? RankKeyUtils.collegeYearRankKey(collegeForRank) : null;
                        rankService.applyRankToRedis(member, rankCredit, todayKey, weekKey, collegeKey);
                    } catch (Exception e) {
                        log.warn("排行榜 Redis 写入失败，将由定时从流水补偿 studentId={} credit={}", studentId, rankCredit, e);
                    }
                }
            }
        });

        return credit;
    }
}