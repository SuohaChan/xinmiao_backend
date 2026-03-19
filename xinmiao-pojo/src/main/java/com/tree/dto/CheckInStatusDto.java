package com.tree.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 签到状态 用于返回签到后的结果 返回当前经验相关数据 供前端显示
 */
@Data
public class CheckInStatusDto {
    private boolean isCheckedIn; // 是否签到成功
    private LocalDate checkInDate; // 签到日期
    private Integer experienceAdded; // 本次获得经验
    private Integer currentExperience; // 当前总经验
    private Integer currentLevel; // 当前等级
}
