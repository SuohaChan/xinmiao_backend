package com.tree.chat.infrastructure.token;

import com.tree.chat.domain.port.TokenCounter;
import org.springframework.stereotype.Component;

/**
 * 兜底估算器：不依赖外部 tokenizer，保证服务可用。
 * 1 个中文字 ≈ 1 token
 * 1 个英文单词 ≈ 1~2 token
 */
@Component
public class EstimatedTokenCounter implements TokenCounter {

    @Override
    public int count(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        double tokens = 0d;
        int i = 0;
        final int len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) {
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                tokens += 1.0d;
            } else if (cp <= 0x7F && Character.isLetterOrDigit(cp)) {
                tokens += 0.25d;
            } else {
                tokens += 0.5d;
            }
        }
        // 补一点协议开销，避免估算过于乐观
        return (int) Math.ceil(tokens) + 16;
    }
}
