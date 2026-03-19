package com.tree.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

import static com.tree.constant.RedisConstants.RANK_COLLEGE_YEAR_TTL_DAYS;
import static com.tree.constant.RedisConstants.RANK_COLLEGE_YEAR_KEY_PREFIX;
import static com.tree.constant.RedisConstants.RANK_TODAY_KEY_PREFIX;
import static com.tree.constant.RedisConstants.RANK_TODAY_TTL_DAYS;
import static com.tree.constant.RedisConstants.RANK_WEEK_KEY_PREFIX;
import static com.tree.constant.RedisConstants.RANK_WEEK_TTL_DAYS;

/**
 * 排行榜 Redis key 生成工具：按自然日 / 周 / 学年组成 key。
 * - 今日榜：xinmiao:rank:today:yyyyMMdd
 * - 周榜：  xinmiao:rank:week:yyyyWww   （ISO 周，周次两位）
 * - 学院榜：xinmiao:rank:college:yyyy-yyyy:学院名称 （学年）
 */
public final class RankKeyUtils {

    private static final DateTimeFormatter DATE_KEY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private RankKeyUtils() {
    }

    /** 今日榜 key，如 xinmiao:rank:today:20250302 */
    public static String todayRankKey() {
        LocalDate now = LocalDate.now();
        return RANK_TODAY_KEY_PREFIX + now.format(DATE_KEY_FMT);
    }

    /** 本周榜 key，如 xinmiao:rank:week:2025W10（ISO 周） */
    public static String weekRankKey() {
        LocalDate now = LocalDate.now();
        WeekFields wf = WeekFields.of(Locale.getDefault());
        int week = now.get(wf.weekOfWeekBasedYear());
        int year = now.get(wf.weekBasedYear());
        return RANK_WEEK_KEY_PREFIX + year + "W" + String.format("%02d", week);
    }

    /**
     * 当前学年的学院榜 key。
     * 学年划分规则（可按需要调整）：<br/>
     * - 9 月及以后：当年-下一年，例如 2024-09-01 起为 2024-2025 学年<br/>
     * - 否则：上一年-当年，例如 2025-03-01 属于 2024-2025 学年
     */
    public static String collegeYearRankKey(String college) {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int startYear;
        int endYear;
        if (now.getMonthValue() >= 9) {
            startYear = year;
            endYear = year + 1;
        } else {
            startYear = year - 1;
            endYear = year;
        }
        String academicYear = startYear + "-" + endYear;
        return RANK_COLLEGE_YEAR_KEY_PREFIX + academicYear + ":" + college;
    }

    /**
     * 当前学年起始时间（用于 DB 按时间范围汇总学院榜）。
     * 规则与 collegeYearRankKey 一致：9 月及以后为当年 9 月 1 日 0 点，否则为上年 9 月 1 日 0 点。
     */
    public static LocalDateTime academicYearStart() {
        LocalDate now = LocalDate.now();
        int startYear = now.getMonthValue() >= 9 ? now.getYear() : now.getYear() - 1;
        return LocalDate.of(startYear, 9, 1).atStartOfDay();
    }

    /** 今日 0 点（用于流水表时间范围聚合） */
    public static LocalDateTime todayStart() {
        return LocalDate.now().atStartOfDay();
    }

    /** 本周一 0 点（用于流水表时间范围聚合） */
    public static LocalDateTime weekStart() {
        return LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
    }

    /** 今日 0 点的时间戳（毫秒），用于今日榜聚合时间范围，与定时任务一致 */
    public static long todayStartEpochMs() {
        return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 本周一 0 点的时间戳（毫秒），用于周榜聚合时间范围，与定时任务一致 */
    public static long weekStartEpochMs() {
        return LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 今日榜 key 的 TTL（天） */
    public static long todayTtlDays() {
        return RANK_TODAY_TTL_DAYS;
    }

    /** 周榜 key 的 TTL（天） */
    public static long weekTtlDays() {
        return RANK_WEEK_TTL_DAYS;
    }

    /** 学院榜（按学年） key 的 TTL（天） */
    public static long collegeYearTtlDays() {
        return RANK_COLLEGE_YEAR_TTL_DAYS;
    }
}

