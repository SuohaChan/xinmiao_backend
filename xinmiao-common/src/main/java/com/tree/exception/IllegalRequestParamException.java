package com.tree.exception;

import com.tree.result.ErrorCode;

/**
 * 请求参数非法异常：带上参数错误的统一错误码。
 */
public class IllegalRequestParamException extends BusinessException {
    public IllegalRequestParamException() {
        super(ErrorCode.PARAM_INVALID, "参数错误");
    }

    public IllegalRequestParamException(String message) {
        super(ErrorCode.PARAM_INVALID, message);
    }
}
