package com.tree.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_class")
public class Clazz {
    @TableId
    private Long id;
    @NotBlank(message = "班级名称不能为空")
    private String name;
    @NotNull(message = "所属学院不能为空")
    private Long collegeId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}