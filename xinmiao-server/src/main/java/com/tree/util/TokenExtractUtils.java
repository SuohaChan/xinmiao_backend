package com.tree.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求令牌提取工具：统一解析 Authorization 与 Cookie。
 */
public final class TokenExtractUtils {
    private TokenExtractUtils() {
    }

    public static String getBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }

    public static String getCookieValue(HttpServletRequest request, String cookieName) {
        if (cookieName == null || cookieName.isBlank()) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        for (Cookie c : cookies) {
            if (cookieName.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
