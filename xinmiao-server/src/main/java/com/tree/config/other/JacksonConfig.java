package com.tree.config.other;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tree.jackson.JacksonObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 提供统一的 ObjectMapper Bean，供 HTTP 消息转换器与缓存等使用。
 */
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return new JacksonObjectMapper();
    }
}
