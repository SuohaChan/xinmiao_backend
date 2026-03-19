package com.tree.service.impl;

import com.tree.constant.RedisConstants;
import com.tree.dto.CheckInWeekDto;
import com.tree.mapper.StudentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.when;

/**
 * 针对签到周统计逻辑的纯单元测试示例。
 * 不启动 Spring 容器，使用 Mockito 模拟 Redis 行为，仅验证 Bitmap → 周签到结果 的转换逻辑。
 */
@ExtendWith(MockitoExtension.class)
class CheckInServiceImplTest {

    @Mock
    private StudentMapper studentMapper; // 本测试不会用到，但构造函数需要

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CheckInServiceImpl checkInService;

    private static final DateTimeFormatter SIGN_KEY_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    @Test
    void getWeekCheckStatus_shouldReflectBitmapBits_fromBitmapOnly() {
        Long studentId = 9999L; // 测试用虚拟 ID

        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate sunday = today.with(DayOfWeek.SUNDAY);

        // 设定本周哪些天“打卡”：周一、周三、周五
        Set<LocalDate> checkedDates = new HashSet<>();
        checkedDates.add(monday);
        checkedDates.add(monday.plusDays(2));
        checkedDates.add(monday.plusDays(4));

        // 所有 Redis String 操作都通过同一个 ValueOperations mock
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // monthsInWeek 里只要 hasKey 返回 true，就不会触发 backfillSignMonth（避免访问数据库）
        Set<String> monthKeys = new HashSet<>();
        for (LocalDate d = monday; !d.isAfter(sunday); d = d.plusDays(1)) {
            String key = RedisConstants.SIGN_KEY_PREFIX + studentId + ":" + d.format(SIGN_KEY_MONTH);
            monthKeys.add(key);
        }
        for (String key : monthKeys) {
            when(stringRedisTemplate.hasKey(key)).thenReturn(true);
        }

        // 按照 checkedDates 模拟 Bitmap 中的 bit：打卡日 bit=true，其他为 false
        for (LocalDate d = monday; !d.isAfter(sunday); d = d.plusDays(1)) {
            String key = RedisConstants.SIGN_KEY_PREFIX + studentId + ":" + d.format(SIGN_KEY_MONTH);
            long offset = d.getDayOfMonth() - 1L;
            boolean checked = checkedDates.contains(d);
            when(valueOperations.getBit(eq(key), longThat(l -> l == offset))).thenReturn(checked);
        }

        // 调用待测方法
        CheckInWeekDto dto = checkInService.getWeekCheckStatus(studentId);
        List<Boolean> weekStatus = dto.getWeekCheckStatus();

        // 断言长度为 7 天
        assertEquals(7, weekStatus.size());

        // 逐日对比 Bitmap 与返回结果
        int idx = 0;
        for (LocalDate d = monday; !d.isAfter(sunday); d = d.plusDays(1), idx++) {
            boolean expected = checkedDates.contains(d);
            assertEquals(expected, weekStatus.get(idx), "签到状态不匹配，日期=" + d);
        }

        // todayChecked 应该等于今天对应的那一位
        boolean expectedToday = checkedDates.contains(today);
        assertEquals(expectedToday, dto.isTodayChecked());
    }
}
