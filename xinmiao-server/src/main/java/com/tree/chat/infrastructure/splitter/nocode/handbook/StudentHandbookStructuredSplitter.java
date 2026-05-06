package com.tree.chat.infrastructure.splitter.nocode.handbook;

import com.tree.chat.infrastructure.splitter.chinese.ChineseTextSplitter;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.List;

/**
 * 《学生手册》版式切割：以「连续不少于 5 个空行」为段界（与正文中人工预留的五行空白一致）。
 * 若某段超过 {@link #MAX_STRUCTURAL_SEGMENT_CHARS} 字，再按 {@link ChineseTextSplitter}
 * 做标点优先、带重叠的二次切分（约 600～800 字一块）。
 */
public class StudentHandbookStructuredSplitter extends TextSplitter {

    /** 与 nocode 正文中「五行换行」约定一致 */
    private static final int CONSECUTIVE_BLANK_LINES_TO_SPLIT = 5;

    /** 超过该字数（字符数）的段再走重叠分块，避免单段过长 */
    private static final int MAX_STRUCTURAL_SEGMENT_CHARS = 800;

    /**
     * 二次切分：目标块约 750 字、重叠 100 字，尽量在句号等处断开；与「600～800 字左右」一致。
     */
    private static final ChineseTextSplitter OVERSIZED_SEGMENT_SPLITTER = new ChineseTextSplitter(750, 100, 1);

    @Override
    protected List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> structural = splitByConsecutiveBlankLines(text);
        if (structural.isEmpty()) {
            String one = text.trim();
            return one.isEmpty() ? List.of() : splitOversizedIfNeeded(one);
        }

        List<String> out = new ArrayList<>();
        for (String segment : structural) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            out.addAll(splitOversizedIfNeeded(trimmed));
        }
        return out;
    }

    private static List<String> splitOversizedIfNeeded(String trimmed) {
        if (trimmed.length() <= MAX_STRUCTURAL_SEGMENT_CHARS) {
            return List.of(trimmed);
        }
        return OVERSIZED_SEGMENT_SPLITTER.splitPlainSegments(trimmed);
    }

    /**
     * 按行扫描：遇连续 5 条及以上 {@link String#isBlank()} 行则切段，
     * 该空白带本身不写入任一段；不足 5 行的空行保留在段内。
     */
    private static List<String> splitByConsecutiveBlankLines(String text) {
        String[] raw = text.split("\\R", -1);
        List<String> chunks = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        int consecutiveBlanks = 0;
        boolean discardingBlanks = false;

        for (String line : raw) {
            if (line.isBlank()) {
                if (discardingBlanks) {
                    continue;
                }
                consecutiveBlanks++;
                if (consecutiveBlanks >= CONSECUTIVE_BLANK_LINES_TO_SPLIT) {
                    flushBuffer(buffer, chunks);
                    discardingBlanks = true;
                    consecutiveBlanks = 0;
                }
            } else {
                if (discardingBlanks) {
                    discardingBlanks = false;
                } else if (consecutiveBlanks > 0) {
                    for (int k = 0; k < consecutiveBlanks; k++) {
                        buffer.add("");
                    }
                    consecutiveBlanks = 0;
                }
                buffer.add(line);
            }
        }
        flushBuffer(buffer, chunks);
        return chunks;
    }

    private static void flushBuffer(List<String> buffer, List<String> chunks) {
        if (buffer.isEmpty()) {
            return;
        }
        String joined = String.join("\n", buffer).trim();
        buffer.clear();
        if (!joined.isEmpty()) {
            chunks.add(joined);
        }
    }
}
