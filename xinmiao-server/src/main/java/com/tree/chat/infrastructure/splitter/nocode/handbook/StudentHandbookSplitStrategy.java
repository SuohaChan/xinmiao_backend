package com.tree.chat.infrastructure.splitter.nocode.handbook;

import com.tree.chat.domain.model.NocodeStructuredSource;
import com.tree.chat.domain.port.NocodeDocumentSplitStrategy;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 《学生手册》切割策略（基础设施侧实现领域端口）。
 */
@Component
@Order(0)
public class StudentHandbookSplitStrategy implements NocodeDocumentSplitStrategy {

    @Override
    public boolean supports(String resourceFileName) {
        return NocodeStructuredSource.STUDENT_HANDBOOK.matches(resourceFileName);
    }

    @Override
    public TextSplitter createSplitter() {
        return new StudentHandbookStructuredSplitter();
    }
}
