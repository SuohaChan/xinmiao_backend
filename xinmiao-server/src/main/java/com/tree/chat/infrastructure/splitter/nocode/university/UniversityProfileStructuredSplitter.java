package com.tree.chat.infrastructure.splitter.nocode.university;

import com.tree.chat.infrastructure.splitter.chinese.ChineseTextSplitter;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code university.txt}：Markdown 三级标题（###）为一级语义单元；超长小节再按有序列表项 {@code 1. } 切段，
 * 仍超长则退回 {@link ChineseTextSplitter}。
 */
public class UniversityProfileStructuredSplitter extends TextSplitter {

    private static final Pattern MARKDOWN_H3_BOUNDARY = Pattern.compile("\n(?=###\\s)");

    private static final Pattern ORDERED_ITEM_BOUNDARY = Pattern.compile("(?m)(?:^|\\R)(?=\\d+\\.\\s)");

    private final int maxSectionChars;
    private final ChineseTextSplitter oversizeFallback;

    public UniversityProfileStructuredSplitter(int maxSectionChars,
                                               ChineseTextSplitter oversizeFallback) {
        this.maxSectionChars = Math.max(300, maxSectionChars);
        this.oversizeFallback = oversizeFallback;
    }

    @Override
    protected List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> sections = splitByPattern(text, MARKDOWN_H3_BOUNDARY);
        List<String> out = new ArrayList<>();
        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() <= maxSectionChars) {
                out.add(trimmed);
                continue;
            }
            List<String> byItems = splitByPattern(trimmed, ORDERED_ITEM_BOUNDARY);
            for (String part : byItems) {
                String t = part.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (t.length() <= maxSectionChars) {
                    out.add(t);
                } else {
                    out.addAll(oversizeFallback.splitPlainSegments(t));
                }
            }
        }
        return out;
    }

    private static List<String> splitByPattern(String text, Pattern boundary) {
        List<Integer> starts = new ArrayList<>();
        Matcher m = boundary.matcher(text);
        while (m.find()) {
            starts.add(m.start());
        }
        if (starts.isEmpty()) {
            return List.of(text);
        }
        List<String> parts = new ArrayList<>();
        int headEnd = starts.get(0);
        if (headEnd > 0) {
            parts.add(text.substring(0, headEnd));
        }
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = (i + 1 < starts.size()) ? starts.get(i + 1) : text.length();
            parts.add(text.substring(from, to));
        }
        return parts;
    }
}
