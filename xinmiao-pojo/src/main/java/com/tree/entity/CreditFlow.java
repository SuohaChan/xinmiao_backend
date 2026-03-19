package com.tree.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分流水表：每次加分写一条，用于排行榜缺 key 时按时间范围聚合，兼做审计查询。
 */
@Data
@TableName("tb_credit_flow")
public class CreditFlow implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 学生ID */
    private Long studentId;
    /** 任务ID */
    private Long taskId;
    /** 本次获得积分 */
    private Integer credit;
    /** 学院名称（冗余，便于按学院+学年聚合） */
    private String college;
    /** 加分发生时间（用于今日/周/学年范围） */
    private LocalDateTime occurredAt;

    private static final long serialVersionUID = 1L;
}
