package com.tree.interceptor;

import cn.hutool.core.util.StrUtil;
import com.tree.context.CounselorHolder;
import com.tree.context.StudentHolder;
import com.tree.dto.CounselorDto;
import com.tree.dto.StudentDto;
import com.tree.result.ErrorCode;
import com.tree.util.InterceptorResponseHelper;
import com.tree.util.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import static com.tree.constant.LoginContextConstants.LOGIN_USER_DTO;
import static com.tree.constant.LoginContextConstants.LOGIN_USER_TYPE;

/**
 * JWT 认证拦截器：校验 Access Token（验签 + 过期 + 黑名单），有效则写入 request 属性并设置 Holder，供后续拦截器与 Service 使用。
 */
@Slf4j
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final InterceptorResponseHelper responseHelper;

    public JwtAuthInterceptor(@Autowired JwtUtils jwtUtils, @Autowired InterceptorResponseHelper responseHelper) {
        this.jwtUtils = jwtUtils;
        this.responseHelper = responseHelper;
    }

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        String token = parseToken(authHeader);
        if (StrUtil.isBlank(token)) {
            responseHelper.writeResultFail(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getCode(), "未登录或Token缺失");
            return false;
        }

        Claims claims = jwtUtils.verifyAndGetClaims(token);
        if (claims == null) {
            responseHelper.writeResultFail(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getCode(), "Token无效或已过期");
            return false;
        }

        String sub = claims.getSubject();
        String type = claims.get("type", String.class);
        if (sub == null || sub.isBlank() || type == null || type.isBlank()) {
            responseHelper.writeResultFail(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getCode(), "Token格式异常");
            return false;
        }
        long userId = Long.parseLong(sub.replace("\"", "").trim());

        Object userDto;
        if ("Student".equalsIgnoreCase(type)) {
            StudentDto dto = new StudentDto();
            dto.setId(userId);
            dto.setType("Student");
            userDto = dto;
        } else if ("Counselor".equalsIgnoreCase(type)) {
            CounselorDto dto = new CounselorDto();
            dto.setId(userId);
            dto.setType("Counselor");
            dto.setIsAdmin(Boolean.TRUE.equals(claims.get("isAdmin", Boolean.class)));
            userDto = dto;
        } else {
            responseHelper.writeResultFail(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getCode(), "Token身份类型异常");
            return false;
        }
        request.setAttribute(LOGIN_USER_DTO, userDto);
        request.setAttribute(LOGIN_USER_TYPE, type);
        if (userDto instanceof StudentDto studentDto) {
            StudentHolder.setStudent(studentDto);
        } else {
            CounselorHolder.setCounselor((CounselorDto) userDto);
        }
        return true;
    }

    @SuppressWarnings("null")
    @Override
    public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler, Exception ex) {
        StudentHolder.removeStudent();
        CounselorHolder.removeCounselor();
    }

    private static String parseToken(String authorization) {
        if (authorization == null || authorization.isEmpty()) return null;
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }
}
