package com.tree.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tree.dto.RankDto;
import com.tree.entity.Student;
import com.tree.entity.StudentClass;
import com.tree.exception.BusinessException;
import com.tree.mapper.StudentClassMapper;
import com.tree.mapper.StudentMapper;
import com.tree.mapper.StudentTaskMapper;
import com.tree.result.ErrorCode;
import com.tree.service.RankService;
import com.tree.service.StudentClassService;
import com.tree.util.RankCacheHelper;
import com.tree.util.RankFlowAggregateHelper;
import com.tree.utils.RankKeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import com.tree.context.StudentHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 排行榜服务实现：今日榜、周榜、学院学年榜，以及任务完成后的 Redis 写入。
 */
@Slf4j
@Service
public class RankServiceImpl implements RankService {

    private static final DefaultRedisScript<Long> RANK_UPDATE_SCRIPT;

    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "local member = ARGV[1]\n" +
                "local credit = tonumber(ARGV[2])\n" +
                "local todayTtlDays = tonumber(ARGV[3])\n" +
                "local weekTtlDays = tonumber(ARGV[4])\n" +
                "local collegeTtlDays = tonumber(ARGV[5])\n" +
                "\n" +
                "-- 今日榜\n" +
                "if KEYS[1] ~= '' then\n" +
                "  redis.call('ZINCRBY', KEYS[1], credit, member)\n" +
                "  local ttl = redis.call('TTL', KEYS[1])\n" +
                "  if ttl == -1 then\n" +
                "    redis.call('EXPIRE', KEYS[1], todayTtlDays * 86400)\n" +
                "  end\n" +
                "end\n" +
                "\n" +
                "-- 周榜\n" +
                "if KEYS[2] ~= '' then\n" +
                "  redis.call('ZINCRBY', KEYS[2], credit, member)\n" +
                "  local ttl = redis.call('TTL', KEYS[2])\n" +
                "  if ttl == -1 then\n" +
                "    redis.call('EXPIRE', KEYS[2], weekTtlDays * 86400)\n" +
                "  end\n" +
                "end\n" +
                "\n" +
                "-- 学院学年榜\n" +
                "if KEYS[3] ~= '' then\n" +
                "  redis.call('ZINCRBY', KEYS[3], credit, member)\n" +
                "  local ttl = redis.call('TTL', KEYS[3])\n" +
                "  if ttl == -1 then\n" +
                "    redis.call('EXPIRE', KEYS[3], collegeTtlDays * 86400)\n" +
                "  end\n" +
                "end\n" +
                "return 1\n"
        );
        script.setResultType(Long.class);
        RANK_UPDATE_SCRIPT = script;
    }

    private static final int RANK_TOP_N = 200;

    private final RankCacheHelper rankCacheHelper;
    private final RankFlowAggregateHelper rankFlowAggregateHelper;
    private final StudentTaskMapper studentTaskMapper;
    private final StudentMapper studentMapper;
    private final StudentClassMapper studentClassMapper;
    private final StudentClassService studentClassService;
    private final StringRedisTemplate stringRedisTemplate;

    public RankServiceImpl(RankCacheHelper rankCacheHelper,
                           RankFlowAggregateHelper rankFlowAggregateHelper,
                           StudentTaskMapper studentTaskMapper,
                           StudentMapper studentMapper,
                           StudentClassMapper studentClassMapper,
                           StudentClassService studentClassService,
                           @Qualifier("scriptRedisTemplate") StringRedisTemplate stringRedisTemplate) {
        this.rankCacheHelper = rankCacheHelper;
        this.rankFlowAggregateHelper = rankFlowAggregateHelper;
        this.studentTaskMapper = studentTaskMapper;
        this.studentMapper = studentMapper;
        this.studentClassMapper = studentClassMapper;
        this.studentClassService = studentClassService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<RankDto> getTodayRank() {
        try {
            String key = RankKeyUtils.todayRankKey();
            String lockSuffix = "today:" + key;
            List<RankDto> list = rankCacheHelper.getOrRebuildReal(
                    key,
                    lockSuffix,
                    RANK_TOP_N,
                    RankKeyUtils.todayTtlDays(),
                    this::aggregateTodayRankFromFlow);
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }
            fillRankDtoUserInfo(list);
            return list;
        } catch (Exception e) {
            log.error("获取今日排行榜数据失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取今日排行榜数据失败");
        }
    }

    @Override
    public List<RankDto> getWeekRank() {
        try {
            String key = RankKeyUtils.weekRankKey();
            String lockSuffix = "week:" + key;
            List<RankDto> list = rankCacheHelper.getOrRebuildReal(
                    key,
                    lockSuffix,
                    RANK_TOP_N,
                    RankKeyUtils.weekTtlDays(),
                    this::aggregateWeekRankFromFlow);
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }
            fillRankDtoUserInfo(list);
            return list;
        } catch (Exception e) {
            log.error("获取周排行榜数据失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取周排行榜数据失败");
        }
    }

    @Override
    public List<RankDto> getCollegeRank() {
        try {
            if (StudentHolder.getStudent() == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
            }
            Long studentId = StudentHolder.getStudent().getId();
            StudentClass studentClass = studentClassService.searchClassByStudentId(studentId);
            if (studentClass == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "未找到该学生的班级信息");
            }
            String college = studentClass.getCollege();
            String key = RankKeyUtils.collegeYearRankKey(college);
            String lockSuffix = "college:" + key;
            LocalDateTime yearStart = RankKeyUtils.academicYearStart();
            LocalDateTime endTime = LocalDateTime.now();
            List<RankDto> list = rankCacheHelper.getOrRebuildReal(
                    key,
                    lockSuffix,
                    RANK_TOP_N,
                    RankKeyUtils.collegeYearTtlDays(),
                    () -> studentTaskMapper.getCollegeRankByAcademicYear(college, yearStart, endTime)
            );
            fillRankDtoUserInfo(list);
            return list;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取学院排行榜数据失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取学院排行榜数据失败");
        }
    }

    @Override
    public List<RankDto> getTodayRankFromDatabase() {
        try {
            List<RankDto> list = aggregateTodayRankFromFlow();
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }
            fillRankDtoUserInfo(list);
            return list;
        } catch (Exception e) {
            log.error("获取今日排行榜(DB-only)失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取今日排行榜数据失败");
        }
    }

    @Override
    public List<RankDto> getWeekRankFromDatabase() {
        try {
            List<RankDto> list = aggregateWeekRankFromFlow();
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }
            fillRankDtoUserInfo(list);
            return list;
        } catch (Exception e) {
            log.error("获取周排行榜(DB-only)失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取周排行榜数据失败");
        }
    }

    @Override
    public List<RankDto> getCollegeRankFromDatabase() {
        try {
            if (StudentHolder.getStudent() == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
            }
            Long studentId = StudentHolder.getStudent().getId();
            StudentClass studentClass = studentClassService.searchClassByStudentId(studentId);
            if (studentClass == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "未找到该学生的班级信息");
            }
            String college = studentClass.getCollege();
            LocalDateTime yearStart = RankKeyUtils.academicYearStart();
            LocalDateTime endTime = LocalDateTime.now();
            List<RankDto> list = studentTaskMapper.getCollegeRankByAcademicYear(college, yearStart, endTime);
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }
            fillRankDtoUserInfo(list);
            return list;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取学院排行榜(DB-only)失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取学院排行榜数据失败");
        }
    }

    @Override
    public void applyRankToRedis(String member, int credit, String todayKey, String weekKey, String collegeKey) {
        String safeCollegeKey = (collegeKey != null && !collegeKey.isEmpty()) ? collegeKey : "";
        List<String> keys = List.of(todayKey, weekKey, safeCollegeKey);
        try {
            stringRedisTemplate.execute(
                    RANK_UPDATE_SCRIPT,
                    keys,
                    member,
                    String.valueOf(credit),
                    String.valueOf(RankKeyUtils.todayTtlDays()),
                    String.valueOf(RankKeyUtils.weekTtlDays()),
                    String.valueOf(RankKeyUtils.collegeYearTtlDays())
            );
        } catch (Exception e) {
            // 排行榜属于展示型能力：写 Redis 失败不影响主业务（任务完成/加分等）。
            log.warn("Rank redis update failed, degraded. member={} credit={} todayKey={} weekKey={} collegeKey={}",
                    member, credit, todayKey, weekKey, safeCollegeKey, e);
        }
    }

    private List<RankDto> aggregateTodayRankFromFlow() {
        return rankFlowAggregateHelper.aggregateRankDtoListFromFlow(
                RankKeyUtils.todayStart(), LocalDateTime.now());
    }

    private List<RankDto> aggregateWeekRankFromFlow() {
        return rankFlowAggregateHelper.aggregateRankDtoListFromFlow(
                RankKeyUtils.weekStart(), LocalDateTime.now());
    }

    private void fillRankDtoUserInfo(List<RankDto> list) {
        List<Long> ids = list.stream().map(RankDto::getStudentId).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Student> studentMap = studentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Student::getId, s -> s));
        List<StudentClass> classes = studentClassMapper.selectList(
                Wrappers.<StudentClass>lambdaQuery().in(StudentClass::getStudentId, ids));
        Map<Long, StudentClass> classMap = classes.stream()
                .collect(Collectors.toMap(StudentClass::getStudentId, c -> c));
        for (RankDto dto : list) {
            Student s = studentMap.get(dto.getStudentId());
            if (s != null) {
                dto.setStudentNickname(s.getNickname());
            }
            StudentClass sc = classMap.get(dto.getStudentId());
            if (sc != null) {
                dto.setCollege(sc.getCollege());
                dto.setClazz(sc.getClazz());
            }
        }
    }
}
