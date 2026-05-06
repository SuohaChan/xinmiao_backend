package com.tree.chat.domain.port;

import org.springframework.ai.transformer.splitter.TextSplitter;

/**
 * 领域端口：仅对<strong>版式固定、已有专门切段规则</strong>的 {@code nocode} 文档提供策略；
 * 其余文件一律走基础设施中的<strong>通用中文切块</strong>（按句界 + 固定窗口 + overlap，无结构化假设），
 * 由 {@code NocodeSplitStrategyRegistry} 在未命中任何实现时选用。
 */
public interface NocodeDocumentSplitStrategy {

    /**
     * @param resourceFileName {@link org.springframework.core.io.Resource#getFilename()}，非空
     */
    boolean supports(String resourceFileName);

    TextSplitter createSplitter();
}
