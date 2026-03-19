package com.tree.exception;

import com.tree.result.ErrorCode;

/**
 * 删除不被允许的业务场景（例如有外键约束、状态不允许删除）。
 */
public class DeleteNotAllowedException extends BusinessException {
    public DeleteNotAllowedException() {
        super(ErrorCode.BUSINESS_CONFLICT, "不允许执行该删除操作");
    }

    public DeleteNotAllowedException(String message) {
        super(ErrorCode.BUSINESS_CONFLICT, message);
    }
}
