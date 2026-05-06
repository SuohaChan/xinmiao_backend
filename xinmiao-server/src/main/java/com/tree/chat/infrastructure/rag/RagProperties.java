package com.tree.chat.infrastructure.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 配置属性（绑定 {@code app.chat.rag.*}）。
 * <p>数值默认值只维护在 {@code application.yml}，避免与 YAML 分叉；改参改 YAML 即可。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.chat.rag")
public class RagProperties {

    /** RAG 检索返回的文档数量（topK），见 {@code app.chat.rag.top-k} */
    private int topK;

    /** 相似度阈值（0～1），见 {@code app.chat.rag.similarity-threshold} */
    private double similarityThreshold;

    /**
     * RAG 检索的响应式超时（秒）：retrieveDocumentsAsync 的 timeout。
     * 超时/异常会降级为无 RAG（返回空文档列表）。见 {@code app.chat.rag.timeout-seconds}。
     */
    private int timeoutSeconds;

    /** 向量库 HTTP 读取超时（秒），见 {@code app.chat.rag.retrieval-timeout-seconds} */
    private int retrievalTimeoutSeconds;
}
