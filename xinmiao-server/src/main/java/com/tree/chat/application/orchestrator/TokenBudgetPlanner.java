package com.tree.chat.application.orchestrator;

import com.tree.chat.domain.model.BudgetPlan;
import com.tree.chat.domain.policy.InputBudgetPolicy;
import com.tree.chat.domain.port.TokenCounter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * token 预算编排器：
 * - 统一计算 RAG / memory 预算分配
 * - 提供最终输入预算校验入口，避免预算逻辑分散在业务服务中
 *
 * <p>设计目标：
 * <ul>
 *   <li>让 {@code ChatApplicationService} 只负责流程编排，不承载预算细节</li>
 *   <li>将“预分配 + 最终兜底校验”收口到单点，降低遗漏风险</li>
 *   <li>后续若按模型/租户做动态预算策略，只需改本类</li>
 * </ul>
 */
@Component
public class TokenBudgetPlanner {

    private final InputBudgetPolicy tokenBudgetGuard;
    private final int inputSafetyReserveTokens;
    private final double ragBudgetRatio;

    public TokenBudgetPlanner(
            InputBudgetPolicy tokenBudgetGuard,
            @Value("${app.chat.limit.input-safety-reserve-tokens:2000}") int inputSafetyReserveTokens,
            @Value("${app.chat.limit.rag-budget-ratio:0.6}") double ragBudgetRatio) {
        this.tokenBudgetGuard = tokenBudgetGuard;
        this.inputSafetyReserveTokens = Math.max(0, inputSafetyReserveTokens);
        this.ragBudgetRatio = Math.min(0.9d, Math.max(0.1d, ragBudgetRatio));
    }

    /**
     * 预算预分配（RAG 阶段）：
     * 先基于「system + 原始问题」计算可用输入预算（扣安全余量），
     * 再按比例切分为 RAG 预算与 memory 预算。
     */
    public BudgetPlan planForRag(String systemPrompt, String baseUserPrompt) {
        int remain = tokenBudgetGuard.remainingInputBudget(systemPrompt, baseUserPrompt, inputSafetyReserveTokens);
        int ragBudget = Math.max(0, (int) (remain * ragBudgetRatio));
        int memoryBudget = Math.max(0, remain - ragBudget);
        return new BudgetPlan(remain, ragBudget, memoryBudget);
    }

    /**
     * 预算预分配（memory 阶段）：
     * 在 RAG 增强后的 user prompt 基础上重新计算剩余预算，
     * 该值即 memory 可用预算（不再扣安全余量，避免双重扣减）。
     */
    public BudgetPlan planForMemory(String systemPrompt, String ragEnhancedUserPrompt) {
        int memoryBudget = tokenBudgetGuard.remainingInputBudget(systemPrompt, ragEnhancedUserPrompt, 0);
        return new BudgetPlan(memoryBudget, 0, memoryBudget);
    }

    /**
     * 最终输入预算校验：
     * 在调用模型前做最后一次硬校验，防止 memory 最小保留等策略导致边界超量。
     */
    public void assertFinalInputBudget(String systemPrompt, String finalUserPrompt) {
        tokenBudgetGuard.assertInputBudget(systemPrompt, finalUserPrompt);
    }

    /**
     * 统一的“已占用输入 token”计算入口，用于监控与日志。
     */
    public int usedInputTokens(String systemPrompt, String finalUserPrompt) {
        return tokenBudgetGuard.usedInputTokens(systemPrompt, finalUserPrompt);
    }

    /**
     * 暴露 tokenCounter 给下游预算相关组件（如 RAG 裁剪器）复用。
     */
    public TokenCounter getTokenCounter() {
        return tokenBudgetGuard.getTokenCounter();
    }

}
