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
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException e) {
        log.error("Duplicate key exception", e);
        Result<Void> body = Result.fail(ErrorCode.DUPLICATE.getCode(), ErrorCode.DUPLICATE.getDefaultMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DeleteNotAllowedException.class)
    public ResponseEntity<Result<Void>> handleDeleteNotAllowed(DeleteNotAllowedException e) {
        log.error("Delete not allowed", e);
        String msg = e.getMessage() != null ? e.getMessage() : ErrorCode.BUSINESS_CONFLICT.getDefaultMessage();
        Result<Void> body = Result.fail(ErrorCode.BUSINESS_CONFLICT.getCode(), msg);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IllegalRequestParamException.class)
    public ResponseEntity<Result<Void>> handleIllegalRequestParam(IllegalRequestParamException e) {
        log.error("Illegal request param", e);
        String msg = e.getMessage() != null ? e.getMessage() : ErrorCode.PARAM_INVALID.getDefaultMessage();
        Result<Void> body = Result.fail(ErrorCode.PARAM_INVALID.getCode(), msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** @Valid / @Validated 校验失败（如 LoginDto 空账号/空密码、RegisterDto）→ HTTP 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (msg.isEmpty()) {
            msg = ErrorCode.PARAM_INVALID.getDefaultMessage();
        }
        log.warn("Request validation failed: {}", msg);
        Result<Void> body = Result.fail(ErrorCode.PARAM_INVALID.getCode(), msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Service 层抛出的业务异常：HTTP 状态与 ErrorCode 对齐（登录失败/密码错误 → 401，等），
     * 不再对业务错误统一返回 HTTP 200。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        String msg = e.getMessage();
        if (code == null) {
            log.warn("Business exception without ErrorCode: {}", msg, e);
            Result<Void> body = Result.fail(ErrorCode.BUSINESS_CONFLICT.getCode(),
                    msg != null ? msg : ErrorCode.BUSINESS_CONFLICT.getDefaultMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        log.warn("Business exception [{}]: {}", code.name(), msg);
        Result<Void> body = Result.fail(code.getCode(), msg != null ? msg : code.getDefaultMessage());
        return ResponseEntity.status(httpStatusForBusiness(code)).body(body);
    }

    /** 业务错误码与 HTTP 状态（RFC/常见 REST 实践）：登录失败、凭证错误 → 401；参数类 → 400；等 */
    private static HttpStatus httpStatusForBusiness(ErrorCode code) {
        return switch (code) {
            case PARAM_INVALID -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED, LOGIN_FAILED, REFRESH_TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            case DUPLICATE, BUSINESS_CONFLICT -> HttpStatus.CONFLICT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case SERVICE_BUSY -> HttpStatus.SERVICE_UNAVAILABLE;
            case SUCCESS -> HttpStatus.OK;
        };
    }

    /** 请求体 JSON 格式错误（如非法 JSON、类型不匹配）→ HTTP 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleJsonParse(HttpMessageNotReadableException ex) {
        log.error("JSON parse error", ex);
        Result<Void> body = Result.fail(ErrorCode.PARAM_INVALID.getCode(), "JSON数据格式错误");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** AI 异步线程池队列已满时抛出，统一返回友好提示，HTTP 503 */
    @ExceptionHandler(RejectedExecutionException.class)
    public ResponseEntity<Result<Void>> handleRejectedExecution(RejectedExecutionException e) {
        log.warn("AI 请求队列已满，拒绝执行", e);
        Result<Void> body = Result.fail(ErrorCode.SERVICE_BUSY.getCode(), ErrorCode.SERVICE_BUSY.getDefaultMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * 兜底异常处理，防止漏网的异常直接返回 500 堆栈；HTTP 500。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("Unhandled exception", e);
        Result<Void> body = Result.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getDefaultMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

