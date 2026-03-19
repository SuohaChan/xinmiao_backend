package com.tree.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 广告
 *
 * @TableName tb_ad
 */
@Data
public class Ad implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 广告标题
     */
    private String title;

    /**
     * 广告内容
     */
    private String content;

    /**
     * 广告图片，存储图片的URL
     */
    private String image;

    /**
     * 广告费用，精确到小数点后两位
     */
    private BigDecimal fee;

    /**
     * 广告关键词，用逗号分隔
     */
    private String keywords;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}