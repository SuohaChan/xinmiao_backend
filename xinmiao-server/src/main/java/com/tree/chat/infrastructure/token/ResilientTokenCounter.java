package com.tree.chat.infrastructure.token;

import com.tree.chat.domain.port.TokenCounter;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 统一入口计数器：当前固定使用轻量估算策略。
 */
@Primary
@Component
public class ResilientTokenCounter implements TokenCounter {

    private final EstimatedTokenCounter estimatedTokenCounter;

    public ResilientTokenCounter(EstimatedTokenCounter estimatedTokenCounter) {
        this.estimatedTokenCounter = estimatedTokenCounter;
    }

    @Override
    public int count(String text) {
        return estimatedTokenCounter.count(text);
    }
}
