package com.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.constant.RedisConstants;
import com.tree.exception.BusinessException;
import com.tree.mapper.CheckInMapper;
import com.tree.mapper.StudentMapper;
import com.tree.service.CheckInService;
import com.tree.dto.CheckInStatusDto;
import com.tree.dto.CheckInWeekDto;
import com.tree.entity.CheckIn;
import com.tree.entity.Student;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.tree.result.ErrorCode;

@Service
public class CheckInServiceImpl extends ServiceImpl<CheckInMapper, CheckIn> implements CheckInService {

    private static final DateTimeFormatter SIGN_KEY_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final StudentMapper studentMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public CheckInServiceImpl(StudentMapper studentMapper, StringRedisTemplate stringRedisTemplate) {
        this.studentMapper = studentMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 签到 Bitmap key：sign:studentId:yyyyMM */
    private static String signKey(Long studentId, LocalDate date) {
        return RedisConstants.SIGN_KEY_PREFIX + studentId + ":" + date.format(SIGN_KEY_MONTH);
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public CheckInStatusDto checkInToday(Long studentId) {
        //验证学生是否存在
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        LocalDate today = LocalDate.now();
        //检验是否重复签到
        LambdaQueryWrapper<CheckIn> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CheckIn::getStudentId, studentId)
                .eq(CheckIn::getCheckInDate, today)
                .eq(CheckIn::getIsValid, 1);

        if (baseMapper.selectCount(queryWrapper) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "今日已签到");
        }

        //签到
        CheckIn checkIn = new CheckIn();
        checkIn.setStudentId(studentId);
        checkIn.setCheckInDate(today);
        checkIn.setExperience(5); // 固定5点经验
        checkIn.setCreateTime(LocalDateTime.now());
        checkIn.setIsValid(1);
        baseMapper.insert(checkIn);

        // 更新学生总经验和等级（每100经验升一级，升级后经验清零）
        int currentExp = student.getTotalExperience() == null ? 0 : student.getTotalExperience();
        int newExp = currentExp + 5;
        int currentLevel = student.getLevel() == null ? 1 : student.getLevel();
        
        // 计算新等级和剩余经验
        int newLevel = currentLevel + (newExp / 100);
        int remainingExp = newExp % 100;
        
        student.setTotalExperience(remainingExp);
        student.setLevel(newLevel);
        student.setUpdateTime(LocalDateTime.now());
        studentMapper.updateById(student);

        // Redis Bitmap：按月记录签到，offset = 日-1，便于本周/本月统计
        String signKey = signKey(studentId, today);
        stringRedisTemplate.opsForValue().setBit(signKey, today.getDayOfMonth() - 1, true);
        stringRedisTemplate.expire(signKey, RedisConstants.SIGN_KEY_TTL_DAYS, TimeUnit.DAYS);

        CheckInStatusDto checkInStatusVO = new CheckInStatusDto();
        checkInStatusVO.setCheckedIn(true);
        checkInStatusVO.setCheckInDate(today);
        checkInStatusVO.setExperienceAdded(5);
        checkInStatusVO.setCurrentExperience(remainingExp);
        checkInStatusVO.setCurrentLevel(newLevel);

        return checkInStatusVO;
    }

    @Override
    public CheckInWeekDto getWeekCheckStatus(Long studentId) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate sunday = today.with(DayOfWeek.SUNDAY);

        // 本周可能跨两个月，对缺失的月份从 DB 回填 Redis Bitmap（每个月份只回填一次）
        Set<YearMonth> monthsInWeek = new LinkedHashSet<>();
        for (LocalDate d = monday; !d.isAfter(sunday); d = d.plusDays(1)) {
            monthsInWeek.add(YearMonth.from(d));
        }
        for (YearMonth ym : monthsInWeek) {
            String key = signKey(studentId, ym.atDay(1));
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(key))) {
                backfillSignMonth(studentId, ym.getYear(), ym.getMonthValue());
            }
        }

        // 从 Redis Bitmap 读本周 7 天签到状态
        List<Boolean> weekStatus = new ArrayList<>();
        for (LocalDate d = monday; !d.isAfter(sunday); d = d.plusDays(1)) {
            String key = signKey(studentId, d);
            weekStatus.add(Boolean.TRUE.equals(stringRedisTemplate.opsForValue().getBit(key, d.getDayOfMonth() - 1)));
        }

        CheckInWeekDto checkInWeekDto = new CheckInWeekDto();
        checkInWeekDto.setTodayChecked(weekStatus.get((int) (today.toEpochDay() - monday.toEpochDay())));
        checkInWeekDto.setWeekCheckStatus(weekStatus);
        return checkInWeekDto;
    }

    /** 从 DB 拉取该月签到记录并写入 Redis Bitmap（用于历史数据或 Redis 未命中时回填） */
    private void backfillSignMonth(Long studentId, int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
        LambdaQueryWrapper<CheckIn> q = new LambdaQueryWrapper<>();
        q.eq(CheckIn::getStudentId, studentId)
                .ge(CheckIn::getCheckInDate, first)
                .le(CheckIn::getCheckInDate, last)
                .eq(CheckIn::getIsValid, 1);
        List<CheckIn> list = list(q);
        String key = signKey(studentId, first);
        for (CheckIn c : list) {
            if (c.getCheckInDate() != null) {
                stringRedisTemplate.opsForValue().setBit(key, c.getCheckInDate().getDayOfMonth() - 1, true);
            }
        }
        if (!list.isEmpty()) {
            stringRedisTemplate.expire(key, RedisConstants.SIGN_KEY_TTL_DAYS, TimeUnit.DAYS);
        }
    }
}