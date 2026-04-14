package com.tree.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知实体类
 * @TableName tb_notice
 */
@TableName(value = "tb_notice")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Notice implements Serializable {
    /**
     * 主键ID
     */
    @TableId
    private Long id;

    /**
     * 是否必看（0-非必看，1-必看）
     */
    @TableField("is_required")
    @JsonProperty("is_required") // 与前端参数名保持一致
    private Integer isRequired;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知简述（库列 description；JSON 仍用 desc）
     */
    @TableField("description")
    @JsonProperty("desc")
    private String description;

    /**
     * 发布范围（校级、院级、班级）
     */
    private String scope;

    /**
     * 学院
     */
    private String college;

    /**
     * 图片URL（多个图片用逗号分隔,实际只传一个）
     */
    private String imageUrls;  // 新增字段：存储图片URL

    /**
     * 班级
     */
    private String clazz;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * 是否发布（0-未发布，1-已发布）
     */
    @TableField("is_published")
    @JsonProperty("is_published") // 与前端参数名保持一致
    private Integer isPublished;

    @Serial
    private static final long serialVersionUID = 1L;
}