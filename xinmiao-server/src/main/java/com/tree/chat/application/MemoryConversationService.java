package com.tree.chat.application;

import com.tree.chat.domain.port.ChatMemoryStore;
import com.tree.chat.domain.port.TokenCounter;
import com.tree.dto.ChatMessageDto;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话记忆编排服务：
 * - 读取最近历史并拼成可用于模型的上下文文本
 * - 以“原始问题 + 回答”形式写回记忆（避免写入 RAG 增强文本）
 */
@Component
public class MemoryConversationService {

    private final ChatMemoryStore chatMemoryStore;
    private final TokenCounter tokenCounter;
    private final int memoryMinMessages;
    private final int memoryMaxMessages;

    public MemoryConversationService(
            ChatMemoryStore chatMemoryStore,
            TokenCounter tokenCounter,
            @Value("${app.chat.limit.memory-min-messages:2}") int memoryMinMessages,
            @Value("${app.chat.limit.memory-max-messages:10}") int memoryMaxMessages) {
        this.chatMemoryStore = chatMemoryStore;
        this.tokenCounter = tokenCounter;
        this.memoryMinMessages = Math.max(2, memoryMinMessages);
        this.memoryMaxMessages = Math.max(this.memoryMinMessages, memoryMaxMessages);
    }

    public List<Message> loadHistory(String conversationId) {
        return chatMemoryStore.findByConversationId(conversationId);
    }

    public String buildUserPromptWithHistory(String currentUserPrompt, String conversationId, int memoryTokenBudget) {
        if (memoryTokenBudget <= 0) {
            return currentUserPrompt;
        }
        List<ChatMessageDto> history = chatMemoryStore.findDtosByConversationId(conversationId);
        if (history.isEmpty()) {
            return currentUserPrompt;
        }
        List<ChatMessageDto> selected = pickRecentHistoryWithinBudget(history, memoryTokenBudget);
        if (selected.isEmpty()) {
            return currentUserPrompt;
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append("历史对话（仅供参考）：\n");
        for (ChatMessageDto m : selected) {
            sb.append(resolveRole(m.getMessageType())).append("：").append(safeText(m.getText())).append("\n");
        }
        sb.append("\n当前问题：\n").append(currentUserPrompt);
        return sb.toString();
    }

    public void appendOriginalTurn(String conversationId, List<Message> history, String originalUserQuestion, String answer) {
        List<Message> updated = new ArrayList<>(history != null ? history : List.of());
        updated.add(new UserMessage(originalUserQuestion));
        updated.add(new AssistantMessage(answer));
        chatMemoryStore.saveAll(conversationId, updated);
    }

    private List<ChatMessageDto> pickRecentHistoryWithinBudget(List<ChatMessageDto> history, int memoryTokenBudget) {
        int budget = Math.max(0, memoryTokenBudget);
        int used = 0;
        List<ChatMessageDto> reversed = new ArrayList<>(Math.min(history.size(), memoryMaxMessages));
        for (int i = history.size() - 1; i >= 0; i--) {
            if (reversed.size() >= memoryMaxMessages) {
                break;
            }
            ChatMessageDto dto = history.get(i);
            int tokenCount = safeTokenCount(dto);
            if (!reversed.isEmpty() && used + tokenCount > budget && reversed.size() >= memoryMinMessages) {
                break;
            }
            reversed.add(dto);
            used += tokenCount;
        }
        List<ChatMessageDto> selected = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            selected.add(reversed.get(i));
        }
        return selected;
    }

    private int safeTokenCount(ChatMessageDto dto) {
        if (dto == null) {
            return 0;
        }
        Integer tokenCount = dto.getTokenCount();
        if (tokenCount != null && tokenCount > 0) {
            return tokenCount;
        }
        return tokenCounter.count(safeText(dto.getText()));
    }

    private static String safeText(String text) {
        return text != null ? text : "";
    }

    private static String resolveRole(String messageType) {
        String type = messageType != null ? messageType.toUpperCase() : "USER";
        return switch (type) {
            case "ASSISTANT" -> "助手";
            case "SYSTEM" -> "系统";
            default -> "用户";
        };
    }
}
