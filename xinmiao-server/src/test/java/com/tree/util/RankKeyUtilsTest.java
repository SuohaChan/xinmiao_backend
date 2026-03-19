package com.tree.util;

import com.tree.utils.RankKeyUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 针对 {@link RankKeyUtils} 的基础单元测试示例。
 * 主要验证日期计算逻辑是否符合预期，方便你熟悉 JUnit 的单方法测试写法。
 */
class RankKeyUtilsTest {

    @Test
    void todayStart_shouldBeMidnightOfToday() {
        LocalDateTime start = RankKeyUtils.todayStart();
        assertEquals(LocalDate.now(), start.toLocalDate());
        assertEquals(LocalTime.MIDNIGHT, start.toLocalTime());
    }

    @Test
    void weekStart_shouldBeMondayMidnight() {
        LocalDateTime weekStart = RankKeyUtils.weekStart();
        assertEquals(LocalTime.MIDNIGHT, weekStart.toLocalTime());
        // ISO 标准下 Monday 的值是 1
        assertEquals(1, weekStart.getDayOfWeek().getValue());
    }

    @Test
    void todayRankKey_shouldContainTodayDate() {
        String key = RankKeyUtils.todayRankKey();
        String today = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertTrue(key.endsWith(today));
    }

    @Test
    void academicYearStart_shouldBeSeptemberFirst() {
        LocalDateTime start = RankKeyUtils.academicYearStart();
        // 不关心年份，只关心是 9 月 1 日的 0 点
        assertEquals(Month.SEPTEMBER, start.getMonth());
        assertEquals(1, start.getDayOfMonth());
        assertEquals(LocalTime.MIDNIGHT, start.toLocalTime());
    }
}

