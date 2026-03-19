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
 * 辅导员专属接口鉴权：仅允许 Counselor 访问，身份由 JwtAuthInterceptor 写入的 request 属性提供。
 */
@Slf4j
@Component
public class CounselorOnlyInterceptor implements HandlerInterceptor {

    private final InterceptorResponseHelper responseHelper;

    public CounselorOnlyInterceptor(InterceptorResponseHelper responseHelper) {
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
        if ("Student".equalsIgnoreCase(type)) {
            log.warn("越权访问: 学生Token访问辅导员接口 uri={}", request.getRequestURI());
            responseHelper.writeResultFail(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getDefaultMessage());
            return false;
        }
        if (!"Counselor".equalsIgnoreCase(type)) {
            responseHelper.writeResultFail(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getDefaultMessage());
            return false;
        }
        return true;
    }
}
