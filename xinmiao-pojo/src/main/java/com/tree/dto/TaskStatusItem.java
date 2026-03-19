package com.tree.dto;

import lombok.Data;

/**
 * 批量查询时：任务ID与完成状态，用于避免 N+1。
 */
@Data
public class TaskStatusItem {
    private Long taskId;
    private Integer status;
}
