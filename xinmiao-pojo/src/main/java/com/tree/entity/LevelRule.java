package com.tree.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 等级规则实体类
 * 对应表: tb_level_rule
 */
@Data
@TableName("tb_level_rule")
public class LevelRule {
    /**
     * 等级
     */
    @TableId(value = "level", type = IdType.NONE)
    private Integer level;

    /**
     * 升级到该等级所需的总经验
     */
    @TableField("need_experience")
    private Integer needExperience;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}