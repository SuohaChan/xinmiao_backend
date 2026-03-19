package com.tree.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回结果结构（规范版）：仅 code、message、data。
 * 成功与否以 code === 0 为准。
 *
 * @param <T> 业务数据类型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {

    /** 业务错误码：0 成功，非 0 失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    // ============ 工厂方法 ============

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = ErrorCode.SUCCESS.getCode();
        r.message = ErrorCode.SUCCESS.getDefaultMessage();
        r.data = data;
        return r;
    }

    public static Result<Void> fail(int code, String message) {
        Result<Void> r = new Result<>();
        r.code = code;
        r.message = message != null ? message : "";
        return r;
    }
}

