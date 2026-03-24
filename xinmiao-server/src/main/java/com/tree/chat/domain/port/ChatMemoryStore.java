package com.tree.chat.domain.port;

import com.tree.dto.ChatMessageDto;
import org.springframework.ai.chat.messages.Message;
import org.springframework.lang.NonNull;

import java.util.List;

/**
 * 对话记忆存储抽象（port）。
 *
 * <p>职责边界：
 * <ul>
 *   <li>提供会话维度的历史消息读取能力</li>
 *   <li>提供包含 tokenCount 的 DTO 读取能力（用于预算裁剪）</li>
 *   <li>提供全量覆盖写入能力（由上层维护“当前完整会话窗口”）</li>
 * </ul>
 *
 * <p>约束：
 * <ul>
 *   <li>不约束底层存储介质（可由 Redis / MySQL / KV 等实现）</li>
 *   <li>不承载业务编排逻辑（如 memory 选择策略、prompt 拼接）</li>
 * </ul>
 */
public interface ChatMemoryStore {

    /**
     * 读取会话消息（Spring AI Message 视图）。
     */
    @NonNull
    List<Message> findByConversationId(@NonNull String conversationId);

    /**
     * 读取会话消息（DTO 视图，保留 tokenCount 等元数据）。
     */
    @NonNull
    List<ChatMessageDto> findDtosByConversationId(@NonNull String conversationId);

    /**
     * 保存会话的全量消息列表。
     */
    void saveAll(@NonNull String conversationId, @NonNull List<Message> messages);
}
