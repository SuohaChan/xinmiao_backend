package com.tree.handler;

import com.tree.exception.BusinessException;
import com.tree.exception.DeleteNotAllowedException;
import com.tree.exception.IllegalRequestParamException;
import com.tree.result.ErrorCode;
import com.tree.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * 全局异常处理：统一返回 Result，避免将异常堆栈或内部信息直接暴露给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.error("Duplicate key exception", e);
        return Result.fail(ErrorCode.DUPLICATE.getCode(), ErrorCode.DUPLICATE.getDefaultMessage());
    }

    @ExceptionHandler(DeleteNotAllowedException.class)
    public Result<Void> handleDeleteNotAllowed(DeleteNotAllowedException e) {
        log.error("Delete not allowed", e);
        String msg = e.getMessage() != null ? e.getMessage() : ErrorCode.BUSINESS_CONFLICT.getDefaultMessage();
        return Result.fail(ErrorCode.BUSINESS_CONFLICT.getCode(), msg);
    }

    @ExceptionHandler(IllegalRequestParamException.class)
    public Result<Void> handleIllegalRequestParam(IllegalRequestParamException e) {
        log.error("Illegal request param", e);
        String msg = e.getMessage() != null ? e.getMessage() : ErrorCode.PARAM_INVALID.getDefaultMessage();
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), msg);
    }

    /** @Valid / @Validated 校验失败（如 LoginDto、RegisterDto） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (msg.isEmpty()) {
            msg = ErrorCode.PARAM_INVALID.getDefaultMessage();
        }
        log.warn("Request validation failed: {}", msg);
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), msg);
    }

    /**
     * Service 层抛出的业务异常，统一映射为带错误码的 Result。
     * SERVICE_BUSY（如回源限流、队列满）返回 HTTP 503。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        String msg = e.getMessage();
        if (code == null) {
            log.warn("Business exception without ErrorCode: {}", msg, e);
            return ResponseEntity.ok(Result.fail(ErrorCode.BUSINESS_CONFLICT.getCode(),
                    msg != null ? msg : ErrorCode.BUSINESS_CONFLICT.getDefaultMessage()));
        }
        log.warn("Business exception [{}]: {}", code.name(), msg);
        Result<Void> body = Result.fail(code.getCode(), msg != null ? msg : code.getDefaultMessage());
        if (code == ErrorCode.SERVICE_BUSY) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        return ResponseEntity.ok(body);
    }

    /** 请求体 JSON 格式错误（如非法 JSON、类型不匹配） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleJsonParse(HttpMessageNotReadableException ex) {
        log.error("JSON parse error", ex);
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), "JSON数据格式错误");
    }

    /** AI 异步线程池队列已满时抛出，统一返回友好提示，HTTP 503 */
    @ExceptionHandler(RejectedExecutionException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleRejectedExecution(RejectedExecutionException e) {
        log.warn("AI 请求队列已满，拒绝执行", e);
        return Result.fail(ErrorCode.SERVICE_BUSY.getCode(), ErrorCode.SERVICE_BUSY.getDefaultMessage());
    }

    /**
     * 兜底异常处理，防止漏网的异常直接返回 500 堆栈。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("Unhandled exception", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }
}

