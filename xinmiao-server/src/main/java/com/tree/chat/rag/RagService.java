package com.tree.chat.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.List;

/**
 * RAG 服务组件
 * 提供向量库检索和响应式处理能力
 */
@Slf4j
@Service
public class RagService {

    private final VectorStore vectorStore;
    private final Scheduler ragScheduler;
    private final RagProperties ragProperties;
    private final int timeoutSeconds;

    public RagService(VectorStore vectorStore,
                      Scheduler ragScheduler,
                      RagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.ragScheduler = ragScheduler;
        this.ragProperties = ragProperties;
        this.timeoutSeconds = Math.max(1, ragProperties.getTimeoutSeconds());
    }

    /**
     * 向量库检索（阻塞版本）
     */
    public List<Document> retrieveDocuments(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        long start = System.currentTimeMillis();
        try {
            DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                    .vectorStore(vectorStore)
                    .similarityThreshold(ragProperties.getSimilarityThreshold())
                    .topK(ragProperties.getTopK())
                    .build();

            List<Document> docs = retriever.retrieve(new Query(query));
            long cost = System.currentTimeMillis() - start;

            if (log.isDebugEnabled()) {
                log.debug("RAG 检索 | 耗时={}ms | 找到{}个文档 | topK={}",
                        cost, docs.size(), ragProperties.getTopK());
            }

            return docs;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("RAG 检索失败 | 耗时={}ms | queryLength={}", cost, query.length(), e);
            return List.of();
        }
    }

    /**
     * 向量库检索（响应式版本）
     * 在线程池中执行阻塞检索，返回 Mono 包装的结果
     */
    public Mono<List<Document>> retrieveDocumentsAsync(String query) {
        if (query == null || query.isBlank()) {
            return Mono.just(List.of());
        }

        return Mono.fromCallable(() -> {
                    log.debug("[RAG] 检索开始 | queryLength={} | 当前线程={}",
                            query.length(), Thread.currentThread().getName());
                    long start = System.currentTimeMillis();

                    List<Document> docs = retrieveDocuments(query);

                    log.debug("[RAG] 检索完成 | 耗时={}ms | 找到{}个文档 | 当前线程={}",
                            System.currentTimeMillis() - start, docs.size(),
                            Thread.currentThread().getName());
                    return docs;
                })
                .subscribeOn(ragScheduler)  // 使用 RAG 专用调度器
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .onErrorResume(e -> {
                    log.warn("[RAG] 检索异常，返回空结果 | queryLength={}", query.length(), e);
                    return Mono.just(List.of());
                });
    }

    /**
     * 构建带 RAG 上下文的 Prompt
     */
    public String buildPromptWithRag(String originalQuestion, List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return originalQuestion;
        }

        StringBuilder context = new StringBuilder();
        for (Document doc : docs) {
            context.append(doc.getText()).append("\n");
        }

        return String.format("""
            知识库上下文：
            ---------------------
            %s
            ---------------------
            结合上述知识库回答以下问题：
            %s
            """, context.toString(), originalQuestion);
    }
}
