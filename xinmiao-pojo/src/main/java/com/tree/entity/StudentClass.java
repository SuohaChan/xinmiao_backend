package com.tree.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tb_student_class")
public class StudentClass {
    @TableId
    private Long id;
    private Long studentId;
    private String college;
    private String clazz;
    private Long collegeId;
    private Long classId;
    private Long counselorId;
}