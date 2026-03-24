package com.tree.chat.infrastructure.token;

import com.tree.chat.domain.port.TokenCounter;

/**
 * 文本裁剪工具：在给定 token 预算下返回“最长可保留前缀”。
 * <p>
 * 用途：RAG 文档片段裁剪、记忆摘要裁剪等预算内拼装场景。
 */
public final class TokenTextClipper {
    private TokenTextClipper() {
    }

    /**
     * 将单段文本截断到不超过给定 token 预算。
     *
     * <p>实现采用二分查找字符边界：
     * - 目标：在 {@code counter.count(substring)} 约束下找到最长前缀
     * - 好处：比逐字符试探更高效，适合长文本裁剪
     */
    public static String clipToBudget(String text, int tokenBudget, TokenCounter counter) {
        if (text == null || text.isBlank() || tokenBudget <= 0) {
            return "";
        }
        if (counter.count(text) <= tokenBudget) {
            return text;
        }
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            String sub = text.substring(0, mid);
            int tokens = counter.count(sub);
            if (tokens <= tokenBudget) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo == 0 ? "" : text.substring(0, lo);
    }
}
