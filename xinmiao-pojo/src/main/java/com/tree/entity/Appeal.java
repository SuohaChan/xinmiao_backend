package com.tree.entity;

/**
 * @author SuohaChan
 * @data 2025/9/14
 */


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生申诉实体类，对应数据库表 tb_appeal
 */
@Data
public class Appeal {
    /**
     * 申诉ID（自增主键）
     */
    private Long id;

    /**
     * 申诉人ID（关联学生表 tb_student 的 id）
     */
    private Long userId;

    /**
     * 申诉类型（account:账号问题, content:内容问题, reward:奖励问题, other:其他）
     */
    private String appealType;

    /**
     * 申诉标题
     */
    private String appealTitle;

    /**
     * 申诉详细内容
     */
    private String appealDescription;

    /**
     * 联系方式（邮箱、电话等）
     */
    private String contactInfo;

    /**
     * 提交时间
     */
    private LocalDateTime submitTime;

    /**
     * 受理时间
     */
    private LocalDateTime acceptTime;

    /**
     * 处理中时间
     */
    private LocalDateTime processingTime;

    /**
     * 完成时间
     */
    private LocalDateTime completeTime;

    /**
     * 处理人ID（关联辅导员表 tb_counselor 的 id）
     */
    private Long handlerId;

    /**
     * 处理回复内容
     */
    private String replyContent;

    /**
     * 当前状态（submit:已提交, accept:已受理, processing:处理中, complete:已完成）
     */
    private String status;

    /**
     * 记录创建时间（自动生成）
     */
    private LocalDateTime createTime;

    /**
     * 记录更新时间（自动更新）
     */
    private LocalDateTime updateTime;
}
