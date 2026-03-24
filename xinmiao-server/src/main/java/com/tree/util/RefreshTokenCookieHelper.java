package com.tree.util;

import com.tree.constant.RedisConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 统一处理 refreshToken Cookie 的读写与清理。
 */
@Component
public class RefreshTokenCookieHelper {
    @Value("${app.auth.refresh-cookie.name:refreshToken}")
    private String refreshCookieName;
    @Value("${app.auth.refresh-cookie.path:/}")
    private String refreshCookiePath;
    @Value("${app.auth.refresh-cookie.secure:false}")
    private boolean refreshCookieSecure;
    @Value("${app.auth.refresh-cookie.same-site:Lax}")
    private String refreshCookieSameSite;

    public void write(HttpServletResponse response, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(refreshCookieName, refreshToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .path(refreshCookiePath)
                .maxAge(RedisConstants.REFRESH_TOKEN_TTL)
                .sameSite(refreshCookieSameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .path(refreshCookiePath)
                .maxAge(0)
                .sameSite(refreshCookieSameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String read(HttpServletRequest request) {
        return TokenExtractUtils.getCookieValue(request, refreshCookieName);
    }
}
