package com.tree.utils;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * 日期时间解析工具。前端可能传入多种格式（如 yyyy-MM-dd'T'HH:mm:ss、yyyy-MM-dd 等），
 * 依次尝试解析，失败返回 null。
 */
@Slf4j
public final class DateTimeUtils {

    /** 常用格式：日期时间、日期时间（无秒）、仅日期 */
    private static final List<DateTimeFormatter> DEFAULT_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    private DateTimeUtils() {
    }

    /**
     * 使用默认格式列表解析时间字符串。
     *
     * @param timeStr 时间字符串，null 或空白返回 null
     * @return 解析结果，无法解析返回 null
     */
    public static LocalDateTime parse(String timeStr) {
        return parse(timeStr, DEFAULT_FORMATTERS);
    }

    /**
     * 使用指定格式列表依次尝试解析。
     *
     * @param timeStr    时间字符串
     * @param formatters 格式列表
     * @return 解析结果，无法解析返回 null
     */
    public static LocalDateTime parse(String timeStr, List<DateTimeFormatter> formatters) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        String trimmed = timeStr.trim();
        if (formatters == null || formatters.isEmpty()) {
            return parse(trimmed);
        }
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException e) {
                log.debug("时间解析格式不匹配: timeStr={}, formatter={}", trimmed, formatter);
            }
        }
        log.warn("无法解析时间格式: {}", trimmed);
        return null;
    }
}
