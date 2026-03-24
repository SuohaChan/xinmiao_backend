package com.tree.chat.infrastructure.splitter;

import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 中文友好的固定大小分块器，支持重叠（overlap）。
 * <p>
 * 继承 Spring AI 的 {@link TextSplitter}，重写 {@link #splitText(String)} 实现分块逻辑，
 * 父类负责将分块结果包装为 {@link org.springframework.ai.document.Document}（自动复制原文档的元数据）。
 * <p>
 * 与 Spring AI 原生 {@code TokenTextSplitter} 的区别：
 * <ul>
 *   <li>按字符数切割（中文 1 字符 ≈ 1-2 token，避免依赖 GPT tokenizer 导致与 Qwen 等模型不匹配）</li>
 *   <li>识别中英文标点（。？！.?!\n）作为句子边界，避免从句子中间切断</li>
 *   <li>支持 overlap：相邻块之间重叠一段文字，防止切断处丢失上下文</li>
 *   <li>设置 overlapSize 为 0 时退化为普通固定大小分块（无重叠）</li>
 * </ul>
 *
 * @author SuohaChan
 * @see TextSplitter
 */
public class ChineseTextSplitter extends TextSplitter {

    private static final Pattern PUNCTUATION = Pattern.compile("[。？！.?!\n]");

    private final int chunkSize;
    private final int overlapSize;
    private final int minChunkLength;

    /**
     * 创建中文分块器。
     *
     * @param chunkSize      每块最大字符数（中文约等于字数）
     * @param overlapSize    相邻块重叠字符数，建议为 chunkSize 的 10%-20%；设为 0 则无重叠
     * @param minChunkLength 低于此长度的块将被丢弃，避免产生无语义价值的碎片
     * @throws IllegalArgumentException 当 overlapSize &gt;= chunkSize 时抛出（会导致死循环）
     */
    public ChineseTextSplitter(int chunkSize, int overlapSize, int minChunkLength) {
        if (overlapSize >= chunkSize) {
            throw new IllegalArgumentException("overlapSize must be < chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlapSize = overlapSize;
        this.minChunkLength = minChunkLength;
    }

    /**
     * 将文本按固定大小切分为多个块，尽量在句子边界处切断，相邻块之间保留 overlap 重叠。
     * <p>
     * 流程：取 chunkSize 字符 → 回退到最后一个标点截断 → 下一块起点回退 overlapSize 字符。
     * <pre>
     * 示例（chunkSize=500, overlapSize=80）：
     *
     *   块1: [AAAAAA。BBBBBB。]
     *   块2:            [BBB。CCCCCC。DDDDDD。]
     *                   |←80→| 重叠区域
     * </pre>
     *
     * @param text 待分块的原始文本
     * @return 分块结果列表，每个元素为一个文本块；输入为空时返回空列表
     */
    @Override
    protected List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            // 取 [start, start+chunkSize) 范围的文字
            int end = Math.min(start + chunkSize, text.length());
            String raw = text.substring(start, end);

            // 如果不是最后一段，尝试在中文/英文标点处切断，避免切在句子中间
            if (end < text.length()) {
                int boundary = findLastPunctuation(raw);
                if (boundary > 0) {
                    raw = raw.substring(0, boundary + 1);
                }
            }

            String trimmed = raw.trim();
            if (trimmed.length() >= minChunkLength) {
                chunks.add(trimmed);
            }

            // 关键：下一块的起点 = 当前块长度 - overlap
            // 这样下一块的开头会重复当前块末尾的 overlapSize 个字符
            // 最后一段（end 已到文末）不减 overlap，避免多出一个纯重复的尾巴块
            boolean isLast = (start + raw.length()) >= text.length();
            int advance = isLast ? raw.length() : raw.length() - overlapSize;
            if (advance <= 0) {
                advance = raw.length();
            }
            start += advance;
        }

        return chunks;
    }

    /**
     * 在文本中查找最后一个句子结束标点的位置。
     * <p>
     * 支持的标点：中文（。？！）、英文（.?!）、换行符（\n）。
     *
     * @param text 要搜索的文本片段
     * @return 最后一个标点的字符索引；未找到时返回 -1
     */
    private int findLastPunctuation(String text) {
        int last = -1;
        var matcher = PUNCTUATION.matcher(text);
        while (matcher.find()) {
            last = matcher.start();
        }
        return last;
    }
}
