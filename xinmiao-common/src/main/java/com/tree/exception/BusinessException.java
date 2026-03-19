package com.tree.exception;

import com.tree.result.ErrorCode;

/**
 * 业务异常：Service 层抛出，由全局异常处理器统一转换为 Result。
 * <p>
 * 规范：必须携带 ErrorCode，不再支持仅传字符串消息的构造。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode != null ? errorCode.getDefaultMessage() : null);
        this.errorCode = errorCode != null ? errorCode : ErrorCode.INTERNAL_ERROR;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message != null ? message : (errorCode != null ? errorCode.getDefaultMessage() : null));
        this.errorCode = errorCode != null ? errorCode : ErrorCode.INTERNAL_ERROR;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
