package com.tree.chat.infrastructure.splitter.nocode;

import com.tree.chat.domain.port.NocodeDocumentSplitStrategy;
import com.tree.chat.infrastructure.splitter.chinese.ChineseTextSplitter;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code nocode/*.txt} 切块解析：<strong>有约定版式的文档</strong>（如《学生手册》按连续 5 空行切段、{@code university.txt}）
 * 使用各自注册的 {@link NocodeDocumentSplitStrategy}；<strong>其余任意 txt</strong> 一律使用通用
 * {@link ChineseTextSplitter}（句界 + 固定窗口 + overlap），不做结构化假设。
 */
@Component
public class NocodeSplitStrategyRegistry {

    private final List<NocodeDocumentSplitStrategy> strategies;
    private final ChineseTextSplitter defaultSplitter;

    public NocodeSplitStrategyRegistry(List<NocodeDocumentSplitStrategy> strategies) {
        this.strategies = strategies;
        this.defaultSplitter = new ChineseTextSplitter(500, 80, 10);
    }

    public TextSplitter resolve(String resourceFileName) {
        if (resourceFileName == null) {
            return defaultSplitter;
        }
        return strategies.stream()
                .filter(s -> s.supports(resourceFileName))
                .findFirst()
                .map(NocodeDocumentSplitStrategy::createSplitter)
                .orElse(defaultSplitter);
    }
}
