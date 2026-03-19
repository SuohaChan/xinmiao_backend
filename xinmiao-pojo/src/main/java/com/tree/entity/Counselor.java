package com.tree.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.tree.validation.RegisterGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 辅导员
 *
 * @TableName tb_counselor
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Counselor implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空", groups = {RegisterGroup.class})
    @Size(max = 64, groups = {RegisterGroup.class})
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空", groups = {RegisterGroup.class})
    @Size(min = 6, max = 128, message = "密码长度 6~128", groups = {RegisterGroup.class})
    private String password;

    /**
     * 头像路径
     */
    private String avatar;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 所属学院
     */
    private String college;

    /**
     * 是否超级管理员（可管理学院、班级等），0否 1是
     */
    private Integer isAdmin;

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