package com.tree.chat.domain.policy;

import com.tree.chat.domain.port.TokenCounter;
import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 单次对话 token 预算守卫（估算版）。
 * <p>
 * 说明：
 * - 当前先做“单次硬上限”，采用字符近似估算 token
 * - 预留 maxOutputTokens，避免输入占满上下文导致输出无空间。
 */
@Component
public class InputBudgetPolicy {
    @Getter
    private final TokenCounter tokenCounter;
    private final Counter rejectedOverBudgetCounter;

    @Getter
    @Value("${app.chat.limit.max-input-budget-tokens:70000}")
    private int maxInputBudgetTokens;

    @Getter
    @Value("${app.chat.limit.max-output-tokens:1500}")
    private int maxOutputTokens;

    public InputBudgetPolicy(TokenCounter tokenCounter, MeterRegistry meterRegistry) {
        this.tokenCounter = tokenCounter;
        this.rejectedOverBudgetCounter = Counter.builder("chat_rejected_over_budget_total")
                .description("Requests rejected because estimated input exceeds budget")
                .register(meterRegistry);
    }

    /**
     * 校验本次请求的输入预算（system + user）。
     */
    public void assertInputBudget(String systemPrompt, String userPrompt) {
        int systemTokens = tokenCounter.count(systemPrompt);
        int userTokens = tokenCounter.count(userPrompt);
        int totalInput = systemTokens + userTokens;
        if (totalInput > maxInputBudgetTokens) {
            rejectedOverBudgetCounter.increment();
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "输入内容过长，超过单次预算上限，请精简问题后重试");
        }
    }

    /**
     * 计算 system + user 已占用 token。
     */
    public int usedInputTokens(String systemPrompt, String userPrompt) {
        return tokenCounter.count(systemPrompt) + tokenCounter.count(userPrompt);
    }

    /**
     * 计算剩余输入预算（扣除安全余量）。
     */
    public int remainingInputBudget(String systemPrompt, String userPrompt, int safetyReserveTokens) {
        int used = usedInputTokens(systemPrompt, userPrompt);
        return Math.max(0, maxInputBudgetTokens - used - Math.max(0, safetyReserveTokens));
    }
}
