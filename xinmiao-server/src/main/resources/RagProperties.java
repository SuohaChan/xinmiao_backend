package com.tree.rag;

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
}
