package com.tree.chat.infrastructure.memory;

import com.tree.chat.domain.port.ChatMemoryStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tree.chat.domain.port.TokenCounter;
import com.tree.dto.ChatMessageDto;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import org.springframework.lang.NonNull;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的对话记忆持久化实现。
 * <p>
 * 实现 Spring AI 的 {@link ChatMemoryRepository} 接口（依赖倒置），
 * 上层 {@code ChatApplicationService} 仅依赖接口而非 Redis 实现，后续可替换为 MySQL 等存储而不改业务代码。
 * <p>
 * 存储结构：
 * <ul>
 *   <li>{@code chat:memory:{conversationId}} — String 类型，JSON 序列化的消息列表，带 TTL 自动过期</li>
 *   <li>{@code chat:memory:conversations} — Set 类型，记录所有活跃会话 ID</li>
 * </ul>
 * <p>
 * 特性：
 * <ul>
 *   <li>TTL 过期：默认 7 天（{@code app.chat.memory.ttl-hours}），超时自动清理</li>
 *   <li>条数裁剪：超过上限时只保留最近 N 条（{@code app.chat.memory.max-messages-per-conversation}）</li>
 *   <li>多实例共享：多个 Server 或 Worker 连同一个 Redis 即可共享对话记忆</li>
 * </ul>
 *
 * @author SuohaChan
 * @see ChatMemoryRepository
 * @see ChatMessageDto
 */
@Slf4j
@Repository
public class RedisChatMemoryRepository implements ChatMemoryRepository, ChatMemoryStore {

    private static final String KEY_PREFIX = "chat:memory:";
    private static final String CONVERSATIONS_SET = "chat:memory:conversations";
    private static final TypeReference<List<ChatMessageDto>> LIST_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate template;
    private final ObjectMapper objectMapper;
    private final TokenCounter tokenCounter;

    @Value("${app.chat.memory.ttl-hours:168}") // 默认 7 天
    private long ttlHours;
    @Value("${app.chat.memory.max-messages-per-conversation:50}")
    private int maxMessagesPerConversation;

    public RedisChatMemoryRepository(StringRedisTemplate template, ObjectMapper objectMapper, TokenCounter tokenCounter) {
        this.template = template;
        this.objectMapper = objectMapper;
        this.tokenCounter = tokenCounter;
    }

    /**
     * 查询所有活跃会话 ID（从 Redis Set 中获取）。
     *
     * @return 会话 ID 列表，无会话时返回空列表
     */
    @Override
    @NonNull
    public List<String> findConversationIds() {
        Set<String> members = template.opsForSet().members(CONVERSATIONS_SET);
        return members != null ? new ArrayList<>(members) : List.of();
    }

    /**
     * 按会话 ID 读取历史消息。
     * <p>
     * 从 Redis 读取 JSON 并反序列化为 {@link ChatMessageDto} 列表，再逐条转为 Spring AI 的 {@link Message}。
     *
     * @param conversationId 会话 ID（通常为 userId）
     * @return 该会话的消息列表，会话不存在或解析失败时返回空列表
     */
    @Override
    @NonNull
    public List<Message> findByConversationId(@NonNull String conversationId) {
        List<ChatMessageDto> dtos = findDtosByConversationId(conversationId);
        if (dtos.isEmpty()) return List.of();
        List<Message> messages = new ArrayList<>(dtos.size());
        for (ChatMessageDto dto : dtos) {
            messages.add(toMessage(dto));
        }
        return messages;
    }

    /**
     * 按会话 ID 读取原始 DTO（包含 tokenCount 元数据）。
     */
    @NonNull
    public List<ChatMessageDto> findDtosByConversationId(@NonNull String conversationId) {
        if (conversationId.isBlank()) return List.of();
        String key = KEY_PREFIX + conversationId;
        String json;
        try {
            json = template.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis 对话记忆读取失败，降级为空 conversationId={}: {}", conversationId, e.getMessage(), e);
            return List.of();
        }
        if (json == null || json.isBlank()) return List.of();
        try {
            List<ChatMessageDto> dtos = objectMapper.readValue(json, LIST_TYPE);
            return dtos != null ? dtos : List.of();
        } catch (Exception e) {
            log.warn("Redis 对话记忆反序列化失败，降级为空 conversationId={}: {}", conversationId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 保存会话的全部消息到 Redis。
     * <p>
     * 将 Spring AI 的 {@link Message} 列表转为 {@link ChatMessageDto} 后 JSON 序列化存储。
     * 超过 {@code maxMessagesPerConversation} 条时裁剪，只保留最近的消息（滑动窗口）。
     * 写入时设置 TTL，到期后 Redis 自动删除。
     *
     * @param conversationId 会话 ID
     * @param messages       完整的消息列表（Spring AI 每次传入全量，非增量）
     */
    @Override
    public void saveAll(@NonNull String conversationId, @NonNull List<Message> messages) {
        if (conversationId.isBlank()) return;
        List<Message> list = messages != null ? messages : List.of();
        List<ChatMessageDto> dtos = new ArrayList<>(list.size());
        for (Message m : list) {
            dtos.add(toDto(m));
        }
        if (dtos.size() > maxMessagesPerConversation) {
            dtos = dtos.subList(dtos.size() - maxMessagesPerConversation, dtos.size());
        }
        try {
            String key = KEY_PREFIX + conversationId;
            String json = objectMapper.writeValueAsString(dtos);
            template.opsForValue().set(key, json, ttlHours, TimeUnit.HOURS);
            template.opsForSet().add(CONVERSATIONS_SET, conversationId);
        } catch (Exception e) {
            log.warn("Redis 对话记忆保存失败，conversationId={}: {}", conversationId, e.getMessage(), e);
        }
    }

    /**
     * 删除指定会话的全部记忆。
     * <p>
     * 同时清除消息数据和会话集合中的记录。
     *
     * @param conversationId 要删除的会话 ID
     */
    @Override
    public void deleteByConversationId(@NonNull String conversationId) {
        if (conversationId.isBlank()) return;
        String key = KEY_PREFIX + conversationId;
        try {
            template.delete(key);
            template.opsForSet().remove(CONVERSATIONS_SET, conversationId);
        } catch (Exception e) {
            log.warn("Redis 对话记忆删除失败，降级跳过 conversationId={}: {}", conversationId, e.getMessage(), e);
        }
    }

    /**
     * Spring AI {@link Message} → {@link ChatMessageDto}（序列化用）。
     */
    private ChatMessageDto toDto(Message m) {
        String type = m.getMessageType() != null ? m.getMessageType().name() : "USER";
        String text = m.getText() != null ? m.getText() : "";
        ChatMessageDto dto = new ChatMessageDto();
        dto.setMessageType(type);
        dto.setText(text);
        dto.setTokenCount(tokenCounter.count(text));
        return dto;
    }

    /**
     * {@link ChatMessageDto} → Spring AI {@link Message}（反序列化用）。
     * <p>
     * 根据 {@code messageType} 字段还原为对应子类：
     * {@link AssistantMessage}、{@link SystemMessage} 或 {@link UserMessage}（默认）。
     */
    @NonNull
    private static Message toMessage(ChatMessageDto dto) {
        String type = (dto.getMessageType() != null ? dto.getMessageType() : "USER").toUpperCase();
        String text = dto.getText() != null ? dto.getText() : "";
        return switch (type) {
            case "ASSISTANT" -> new AssistantMessage(text);
            case "SYSTEM" -> new SystemMessage(text);
            default -> new UserMessage(text);
        };
    }
}
