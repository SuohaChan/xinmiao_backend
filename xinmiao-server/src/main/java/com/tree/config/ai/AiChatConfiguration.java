package com.tree.config.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 对话配置。
 * <p>
 * LLM 重试：由 spring-ai-autoconfigure-retry 提供 RetryTemplate，注入到 ChatModel 层，
 * 通过 spring.ai.retry.* 配置（见 application.yml）。原 reactor Retry Bean 未绑定到 ChatClient，已移除。
 */
@Configuration
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true", matchIfMissing = true)
public class AiChatConfiguration {

    @Bean
    public ChatClient defaultChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}