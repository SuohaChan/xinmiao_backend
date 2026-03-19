package com.tree.result;

/**
 * 统一错误码定义。
 *
 * 约定：
 * - 0：成功
 * - 2xxx：请求/参数相关错误
 * - 3xxx：业务状态/资源相关错误
 * - 4xxx：认证/鉴权相关错误
 * - 5xxx：服务器内部错误
 */
public enum ErrorCode {

    // ============= 通用成功 =============
    SUCCESS(0, "OK"),

    // ============= 参数 / 请求错误（2xxx） =============
    PARAM_INVALID(2001, "请求参数不合法"),

    // ============= 认证 / 鉴权错误（4xxx） =============
    UNAUTHORIZED(4001, "未登录或登录已过期"),
    FORBIDDEN(4003, "无权限访问"),
    LOGIN_FAILED(4004, "用户名或密码错误"),
    REFRESH_TOKEN_INVALID(4005, "refreshToken 无效或已过期"),
    RATE_LIMIT(4029, "请求过于频繁，请稍后再试"),

    // ============= 业务 / 资源状态错误（3xxx） =============
    DUPLICATE(3001, "数据已存在，请勿重复操作"),
    NOT_FOUND(3004, "资源不存在"),
    BUSINESS_CONFLICT(3009, "当前状态不允许该操作"),

    // ============= 服务器内部错误（5xxx） =============
    INTERNAL_ERROR(5000, "服务器内部错误"),
    /** 线程池/队列已满，服务暂时不可用 */
    SERVICE_BUSY(5030, "服务繁忙，请稍后重试");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}

