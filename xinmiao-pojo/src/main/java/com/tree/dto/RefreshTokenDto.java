package com.tree.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新 Token 请求体：用 refreshToken 换取新的 accessToken（+ 新 refreshToken）
 */
@Data
public class RefreshTokenDto {
    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
