package com.tree.dto;

/**
 * @author SuohaChan
 * @data 2025/9/10
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务状态统计数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CounselorTaskStatusDto {
    private long normalCount;             // 正常进行的任务数
    private long soonExpireCount;         // 即将超时的任务数(24小时内)
    private long expiredCount;            // 已超时的任务数
}
