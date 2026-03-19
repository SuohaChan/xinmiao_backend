package com.tree.interceptor;

import com.tree.result.ErrorCode;
import com.tree.util.InterceptorResponseHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import static com.tree.constant.LoginContextConstants.LOGIN_USER_TYPE;

/**
 * 学生专属接口鉴权：仅允许 Student 访问，身份由 JwtAuthInterceptor 写入的 request 属性提供。
 * 辅导员访问这些路径将返回 403。
 */
@Slf4j
@Component
public class StudentOnlyInterceptor implements HandlerInterceptor {

    private final InterceptorResponseHelper responseHelper;

    public StudentOnlyInterceptor(InterceptorResponseHelper responseHelper) {
        this.responseHelper = responseHelper;
    }

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Object typeObj = request.getAttribute(LOGIN_USER_TYPE);
        String type = typeObj != null ? typeObj.toString().trim() : "";
        if (type.isEmpty()) {
            responseHelper.writeResultFail(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getCode(), "未登录或Token无效");
            return false;
        }
        if ("Counselor".equalsIgnoreCase(type)) {
            log.warn("越权访问: 辅导员Token访问学生接口 uri={}", request.getRequestURI());
            responseHelper.writeResultFail(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getDefaultMessage());
            return false;
        }
        if (!"Student".equalsIgnoreCase(type)) {
            responseHelper.writeResultFail(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getDefaultMessage());
            return false;
        }
        return true;
    }
}
