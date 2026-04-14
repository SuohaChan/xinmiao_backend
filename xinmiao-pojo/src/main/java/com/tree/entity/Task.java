package com.tree.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务实体类
 * @TableName tb_task
 */
@TableName(value = "tb_task")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Task implements Serializable {
    /**
     * 主键
     */
    @TableId
    private Long id;

    /**
     * 标签
     */
    private String label;

    /**
     * 分数
     */
    private Integer score;

    /**
     * 是否置顶
     */
    private Boolean top;

    /**
     * 是否选中
     */
    private Boolean selected;

    /**
     * 标题
     */
    @NotBlank(message = "任务标题不能为空")
    private String title;

    /**
     * 描述（库列 description；JSON 仍用 desc）
     */
    @TableField("description")
    @JsonProperty("desc")
    private String description;

    /**
     * 发布级别（校级、院级、班级）
     */
    private String level;

    /**
     * 学院
     */
    private String college;

    /**
     * 班级名称（展示用，与 class_id 对应）
     */
    private String clazz;

    /**
     * 学院ID（关联 tb_college.id，规范用 ID）
     */
    @TableField("college_id")
    private Long collegeId;

    /**
     * 班级ID（关联 tb_class.id，规范用 ID）
     */
    @TableField("class_id")
    private Long classId;

    @TableField("is_published")
    @JsonProperty("is_published") // 与前端传递的参数名保持一致
    private Integer isPublished; // 0-未发布，1-已发布
    /**
     * 截止时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime deadline;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8") // 新增此行
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 发布教师ID（新增属性）
     */
    @TableField("teacher_id") // 映射数据库字段
    private Long teacherId;


    @Serial
    private static final long serialVersionUID = 1L;
}