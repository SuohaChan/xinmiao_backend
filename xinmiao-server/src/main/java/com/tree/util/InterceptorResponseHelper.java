package com.tree.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tree.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 拦截器统一写回 JSON：使用统一 Result 结构序列化，避免手拼字符串导致转义错误或与 Result 不一致。
 */
@Slf4j
@Component
public class InterceptorResponseHelper {

    private final ObjectMapper objectMapper;

    public InterceptorResponseHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将 Result.fail(code, message) 序列化为 JSON 并写入 response。
     *
     * @param response 响应对象
     * @param status   HTTP 状态码（如 401、403、429）
     * @param code     业务错误码
     * @param message  提示信息
     */
    public void writeResultFail(HttpServletResponse response, int status, int code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> result = Result.fail(code, message != null ? message : "");
        objectMapper.writeValue(response.getWriter(), result);
    }
}
