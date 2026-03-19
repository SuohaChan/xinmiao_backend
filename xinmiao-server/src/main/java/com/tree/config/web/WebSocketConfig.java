package com.tree.config.web;

import com.tree.interceptor.JwtHandshakeInterceptor;
import com.tree.interceptor.TaskSubscribeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${cors.allowed-origins:http://localhost:8000}")
    private String corsAllowedOrigins;

    /**
     * WebSocket 传输层保护：慢客户端会导致发送堆积与尾延迟尖刺。
     * 通过 sendTimeLimit/sendBufferSizeLimit 上限触发框架主动断开慢连接，优先保护系统稳定性。
     */
    @Value("${app.ws.transport.send-time-limit-ms:10000}")
    private int sendTimeLimitMs;
    @Value("${app.ws.transport.send-buffer-size-limit-bytes:1048576}")
    private int sendBufferSizeLimitBytes;
    @Value("${app.ws.transport.message-size-limit-bytes:65536}")
    private int messageSizeLimitBytes;

    /** STOMP 心跳：用于断线检测与僵尸连接清理（单位毫秒） */
    @Value("${app.ws.heartbeat.server-ms:15000}")
    private long heartbeatServerMs;
    @Value("${app.ws.heartbeat.client-ms:15000}")
    private long heartbeatClientMs;

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final TaskSubscribeInterceptor taskSubscribeInterceptor;

    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor, TaskSubscribeInterceptor taskSubscribeInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.taskSubscribeInterceptor = taskSubscribeInterceptor;
    }

    @Bean
    public TaskScheduler wsHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 大批量连接时：broker 对同一 topic 一次广播到所有订阅者，无需应用层再分批；推送体量由 TaskPushDto 控制
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{heartbeatServerMs, heartbeatClientMs})
                .setTaskScheduler(wsHeartbeatScheduler());
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = corsAllowedOrigins.split("\\s*,\\s*");
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(sendTimeLimitMs);
        registration.setSendBufferSizeLimit(sendBufferSizeLimitBytes);
        registration.setMessageSizeLimit(messageSizeLimitBytes);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(taskSubscribeInterceptor);
    }
}