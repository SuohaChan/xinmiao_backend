package com.tree.chat.domain.port;

import java.util.List;

/**
 * Token 计数抽象。
 */
public interface TokenCounter {

    /**
     * 统计单段文本 token 数。
     */
    int count(String text);

    /**
     * 统计多段消息 token 总数。
     */
    default int countMessages(List<String> msgs) {
        if (msgs == null || msgs.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (String msg : msgs) {
            total += count(msg);
        }
        return total;
    }
}
