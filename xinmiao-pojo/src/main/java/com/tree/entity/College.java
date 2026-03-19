package com.tree.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_college")
public class College {
    @TableId
    private Long id;
    @NotBlank(message = "学院名称不能为空")
    private String name;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}