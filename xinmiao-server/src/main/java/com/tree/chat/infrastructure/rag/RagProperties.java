package com.tree.chat.infrastructure.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.chat.rag")
public class RagProperties {

    /**
     * RAG 检索返回的文档数量（topK）
     */
    private int topK = 6;

    /**
     * 相似度阈值（0-1 之间）
     */
    private double similarityThreshold = 0.3;

    /**
     * RAG 检索的响应式超时（秒）：retrieveDocumentsAsync 的 timeout。
     * 超时/异常会降级为无 RAG（返回空文档列表）。
     */
    private int timeoutSeconds = 15;

    /**
     * 向量库 HTTP 读取超时（秒）：用于 ChromaApi 的 RestClient。
     */
    private int retrievalTimeoutSeconds = 10;
}
