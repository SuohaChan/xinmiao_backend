package com.tree.interceptor;

import com.tree.util.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手时校验 JWT，将 userId、userType 写入 session attributes，供后续订阅校验使用。
 * 前端连接时需带 token，例如：/ws?token=AccessToken
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtils jwtUtils;

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USER_TYPE = "userType";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        String token = getTokenFromRequest(request);
        if (token == null || token.isBlank()) {
            log.warn("WebSocket handshake: missing token");
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        Claims claims = jwtUtils.verifyAndGetClaims(token);
        if (claims == null) {
            log.warn("WebSocket handshake: invalid or expired token");
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        String sub = claims.getSubject();
        String type = claims.get("type", String.class);
        if (sub == null || sub.isBlank() || type == null || type.isBlank()) {
            log.warn("WebSocket handshake: token missing sub or type");
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        long userId = Long.parseLong(sub.replace("\"", "").trim());
        attributes.put(ATTR_USER_ID, userId);
        attributes.put(ATTR_USER_TYPE, type);
        return true;
    }

    @SuppressWarnings("null")
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
    }

    private String getTokenFromRequest(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query == null || query.isBlank()) return null;
        return UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
    }
}
