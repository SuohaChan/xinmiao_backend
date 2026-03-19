package com.tree.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 登录用户工具类：从当前请求获取 token（鉴权由 JWT 拦截器完成）。
 */
@Slf4j
public class LoginUserUtils {

    /**
     * 从当前请求头获取 Access Token（Authorization: Bearer xxx 或 header token）。
     */
    public static String getCurrentToken() {
        // 获取当前请求的Servlet请求属性
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }
        HttpServletRequest request = requestAttributes.getRequest();

        // 优先从Authorization头获取Bearer token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7); // 去掉 "Bearer " 前缀
        } else {
            token = request.getHeader("token");
        }

        if (token == null || token.isEmpty()) {
            log.info("Token is null or empty.");
            return null;
        }
        return token;
    }
}

