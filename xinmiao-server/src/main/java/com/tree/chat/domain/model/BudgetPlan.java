package com.tree.chat.domain.model;

/**
 * token 预算计划模型。
 *
 * @param remainingBudget 当前阶段总可用预算
 * @param ragBudget 分配给 RAG 的预算
 * @param memoryBudget 分配给 memory 的预算
 */
public record BudgetPlan(int remainingBudget, int ragBudget, int memoryBudget) {
}
