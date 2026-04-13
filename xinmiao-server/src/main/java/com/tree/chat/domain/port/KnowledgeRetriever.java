package com.tree.chat.domain.port;

import org.springframework.ai.document.Document;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 知识检索与 RAG Prompt 组装抽象（port）。
 */
public interface KnowledgeRetriever {

    Mono<List<Document>> retrieveDocumentsAsync(String query);

    String buildPromptWithRagWithinBudget(String originalQuestion,
            List<Document> docs,
            int ragBudgetTokens,
            TokenCounter tokenCounter);
}
