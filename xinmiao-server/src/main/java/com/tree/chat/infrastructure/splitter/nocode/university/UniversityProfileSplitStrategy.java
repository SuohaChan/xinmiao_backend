package com.tree.chat.infrastructure.splitter.nocode.university;

import com.tree.chat.domain.model.NocodeStructuredSource;
import com.tree.chat.domain.port.NocodeDocumentSplitStrategy;
import com.tree.chat.infrastructure.splitter.chinese.ChineseTextSplitter;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@code university.txt} 切割策略。
 */
@Component
@Order(1)
public class UniversityProfileSplitStrategy implements NocodeDocumentSplitStrategy {

    private static final int MAX_SECTION_CHARS = 1200;
    private static final int FALLBACK_CHUNK = 500;
    private static final int FALLBACK_OVERLAP = 80;
    private static final int FALLBACK_MIN = 10;

    @Override
    public boolean supports(String resourceFileName) {
        return NocodeStructuredSource.UNIVERSITY_PROFILE.matches(resourceFileName);
    }

    @Override
    public TextSplitter createSplitter() {
        ChineseTextSplitter fallback = new ChineseTextSplitter(FALLBACK_CHUNK, FALLBACK_OVERLAP, FALLBACK_MIN);
        return new UniversityProfileStructuredSplitter(MAX_SECTION_CHARS, fallback);
    }
}
