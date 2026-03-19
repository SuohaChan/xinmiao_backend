package com.tree.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @author SuohaChan
 * @data 2025/9/16
 */
@Data
public class RegisterDto {
    @Size(max = 64)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度 6~128")
    private String password;

    @Size(max = 20)
    private String phone;

    @Size(max = 32)
    private String name;

    @Size(max = 64)
    private String admissionCode;

    @Size(max = 16)
    private String verificationCode;
}
