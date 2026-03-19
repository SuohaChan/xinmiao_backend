package com.tree.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * (tb_course)表实体类
 *
 * @author tree
 * @since 2025-04-13 15:26:23
 */
@SuppressWarnings("serial")
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_course")
public class CourseSchedule{
    @TableId
    private Long id;

    private Long studentId;

    private String timePeriod;

    private String monday;

    private String tuesday;

    private String wednesday;

    private String thursday;

    private String friday;

    private Integer sortOrder;

}
