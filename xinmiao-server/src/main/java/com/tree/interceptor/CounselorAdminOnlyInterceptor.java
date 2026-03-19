package com.tree.interceptor;

import com.tree.context.CounselorHolder;
import com.tree.result.ErrorCode;
import com.tree.util.InterceptorResponseHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 辅导员超级权限：仅 isAdmin=true 的辅导员可访问学院/班级管理接口。
 */
@Slf4j
@Component
public class CounselorAdminOnlyInterceptor implements HandlerInterceptor {

    private final InterceptorResponseHelper responseHelper;

    public CounselorAdminOnlyInterceptor(InterceptorResponseHelper responseHelper) {
        this.responseHelper = responseHelper;
    }

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (CounselorHolder.getCounselor() == null || !Boolean.TRUE.equals(CounselorHolder.getCounselor().getIsAdmin())) {
            log.warn("越权访问: 非超级辅导员访问学院/班级管理 uri={}", request.getRequestURI());
            responseHelper.writeResultFail(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getDefaultMessage());
            return false;
        }
        return true;
    }
}
