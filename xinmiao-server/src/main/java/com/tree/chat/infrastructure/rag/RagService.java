package com.tree.chat.infrastructure.rag;

import com.tree.chat.domain.port.KnowledgeRetriever;
import com.tree.chat.domain.port.TokenCounter;
import com.tree.chat.infrastructure.token.TokenTextClipper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * RAG 服务组件：负责向量检索与 RAG Prompt 组装。
 * <p>
 * 职责边界：
 * <ul>
 *   <li>检索：从向量库召回候选文档（阻塞/响应式两套入口）</li>
 *   <li>组装：将候选文档拼接为可喂给大模型的上下文</li>
 *   <li>预算裁剪：在给定 token 预算内做去重、截断与总量兜底</li>
 * </ul>
 */
@Slf4j
@Service
public class RagService implements KnowledgeRetriever {

    private final VectorStore vectorStore;
    private final Scheduler ragScheduler;
    private final RagProperties ragProperties;
    private final int timeoutSeconds;
    private final Counter ragTruncatedCounter;

    public RagService(VectorStore vectorStore,
                      Scheduler ragScheduler,
                      RagProperties ragProperties,
                      MeterRegistry meterRegistry) {
        this.vectorStore = vectorStore;
        this.ragScheduler = ragScheduler;
        this.ragProperties = ragProperties;
        this.timeoutSeconds = Math.max(1, ragProperties.getTimeoutSeconds());
        this.ragTruncatedCounter = Counter.builder("chat_rag_truncated_total")
                .description("Total number of RAG document chunks truncated by token budget")
                .register(meterRegistry);
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
    @Override
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

    /**
     * 在预算内构建 RAG Prompt（budget-aware）。
     *
     * <p>裁剪策略（按顺序执行）：
     * <ol>
     *   <li>按检索顺序读取候选文档（默认已是高相关优先）</li>
     *   <li>使用文本去重（相同片段只保留一次）</li>
     *   <li>按每段预算 {@code perDocBudget} 截断长片段</li>
     *   <li>若累计超出 {@code ragBudgetTokens}，对当前片段做二次截断以吃满剩余预算</li>
     *   <li>累计达到预算后立即停止，保证总上下文不越界</li>
     * </ol>
     *
     * @param originalQuestion 原始用户问题
     * @param docs 召回的候选文档
     * @param ragBudgetTokens 本次可分配给 RAG 上下文的 token 预算
     * @param tokenCounter token 计数器（用于估算与截断）
     * @return 预算内的 RAG Prompt；若预算不足或无有效文档则返回原问题
     */
    @Override
    public String buildPromptWithRagWithinBudget(String originalQuestion,
                                                 List<Document> docs,
                                                 int ragBudgetTokens,
                                                 TokenCounter tokenCounter) {
        if (docs == null || docs.isEmpty() || ragBudgetTokens <= 0) {
            return originalQuestion;
        }
        // 片段上限：不超过 topK，同时至少保留 1 条（有预算且有文档时）
        int maxDocs = Math.max(1, Math.min(ragProperties.getTopK(), docs.size()));
        // 每条文档初始预算，设置下限避免预算均分后过小导致上下文失真
        int perDocBudget = Math.max(120, ragBudgetTokens / maxDocs);
        int consumed = 0;
        int kept = 0;
        int truncated = 0;
        Set<String> dedup = new LinkedHashSet<>();
        StringBuilder context = new StringBuilder();

        for (Document doc : docs) {
            if (kept >= maxDocs) {
                break;
            }
            String text = doc != null ? doc.getText() : null;
            if (text == null || text.isBlank()) {
                continue;
            }
            String normalized = text.strip();
            // 完全文本去重：避免同一片段重复占预算
            if (!dedup.add(normalized)) {
                continue;
            }
            String clipped = TokenTextClipper.clipToBudget(normalized, perDocBudget, tokenCounter);
            if (!clipped.equals(normalized)) {
                truncated++;
                ragTruncatedCounter.increment();
            }
            int t = tokenCounter.count(clipped);
            // 总预算兜底：当前片段会超预算时，按剩余额度做二次截断
            if (consumed + t > ragBudgetTokens) {
                int remain = ragBudgetTokens - consumed;
                if (remain <= 0) {
                    break;
                }
                clipped = TokenTextClipper.clipToBudget(clipped, remain, tokenCounter);
                t = tokenCounter.count(clipped);
                if (clipped.isBlank() || t <= 0) {
                    break;
                }
            }
            context.append(clipped).append("\n");
            consumed += t;
            kept++;
            if (consumed >= ragBudgetTokens) {
                break;
            }
        }

        if (kept == 0) {
            return originalQuestion;
        }
        log.debug("RAG budget clip done: budget={}, kept={}, truncated={}", ragBudgetTokens, kept, truncated);
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
