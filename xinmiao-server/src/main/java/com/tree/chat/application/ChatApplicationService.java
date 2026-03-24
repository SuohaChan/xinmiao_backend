package com.tree.chat.application;

import com.tree.chat.domain.port.KnowledgeRetriever;
import com.tree.chat.domain.model.BudgetPlan;
import com.tree.chat.application.orchestrator.TokenBudgetPlanner;
import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import com.tree.util.CacheClient;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.tree.constant.RedisConstants.CACHE_AI_REPLY_KEY;
import static com.tree.constant.RedisConstants.CACHE_AI_REPLY_TTL_MINUTES;

@Slf4j
@Service
public class ChatApplicationService {
    /** 命中缓存时按此长度拆成 chunk 流式返回，避免一次大块写占满 Tomcat 线程 */
    private static final int CACHE_HIT_CHUNK_SIZE = 128;

    /** 预创建的系统提示词模板（单例复用） */
    private static final PromptTemplate SYSTEM_TEMPLATE = PromptTemplate.builder()
            .template(loadSystemPromptTemplate())
            .build();

    private final ChatClient chatClient;
    private final MemoryConversationService memoryConversationService;
    private final TokenBudgetPlanner tokenBudgetPlanner;
    /**
     * AI 对话并发控制：所有对话请求（流式 + 非流式）共用同一个信号量，防止同时调 LLM 的并发数击穿下游。
     */
    private final Semaphore conversationSemaphore;
    /** Flux 相邻两次产出（token/chunk）的最大间隔；{@link Flux#timeout(Duration)} 语义，非整条流总时长 */
    private final int streamTokenGapSeconds;
    /** 模型单次最大输出 token 上限（通过 ChatOptions 下发给模型） */
    private final int maxOutputTokens;
    /** 预估输入 token 观测指标 */
    private final DistributionSummary estimatedInputTokensSummary;

    @Resource
    private KnowledgeRetriever knowledgeRetriever;

    @Resource
    private CacheClient cacheClient;

    public ChatApplicationService(ChatClient chatClient,
                                  MemoryConversationService memoryConversationService,
                                  TokenBudgetPlanner tokenBudgetPlanner,
                                  @Value("${app.chat.stream.max-concurrent:30}") int streamMaxConcurrent,
                                  @Value("${app.chat.limit.max-output-tokens:1500}") int maxOutputTokens,
                                  @Value("${app.chat.timeout.stream-token-gap-seconds:${app.chat.timeout.reactor-seconds:45}}") int streamTokenGapSeconds,
                                  MeterRegistry meterRegistry) {
        this.chatClient = chatClient;
        this.memoryConversationService = memoryConversationService;
        this.tokenBudgetPlanner = tokenBudgetPlanner;
        this.conversationSemaphore = new Semaphore(streamMaxConcurrent);
        this.maxOutputTokens = maxOutputTokens;
        this.streamTokenGapSeconds = streamTokenGapSeconds;
        this.estimatedInputTokensSummary = DistributionSummary.builder("chat_estimated_input_tokens")
                .baseUnit("tokens")
                .description("Estimated input tokens before model call")
                .register(meterRegistry);
    }

    /**
     * 简单问答（无记忆）—— 非阻塞版本，用于 HTTP 接口。
     * 系统提示词与流式接口一致（新苗助手 {@link #SYSTEM_TEMPLATE}），仅无对话记忆 Advisor。
     * 内部通过 stream + collect 实现，LLM 等待期间不占用任何线程（EventLoop 非阻塞等待）。
     * 若提供 requestId 且缓存命中，直接返回缓存，不调 LLM（幂等防重复）。
     * LLM 侧 {@code .content().timeout} 为相邻 chunk 最大间隔（与流式 token 间隔语义一致），非整段总时长。
     * 
     * @param studentId 学生 ID（从上下文获取，用于记忆存储）
     * @param question  问题
     * @param requestId 幂等请求 ID（可选）
     * @param useRag    是否启用 RAG（默认 false）
     */
    public Mono<String> chatReactive(Long studentId, @NonNull String question, String requestId, boolean useRag) {
        // 命中缓存直接返回，不占用并发额度
        if (requestId != null && !requestId.isBlank()) {
            String cacheKeySuffix = studentId + ":" + requestId;
            String cached = cacheClient.get(CACHE_AI_REPLY_KEY, cacheKeySuffix, String.class);
            if (cached != null && !cached.isEmpty()) {
                log.debug("AI chatReactive idempotent hit requestId={}", requestId);
                return Mono.just(cached);
            }
        }
        // 并发控制：所有非流式对话也占用一份对话并发额度
        if (!conversationSemaphore.tryAcquire()) {
            log.warn("ai-dialog-concurrency-exceeded non-stream questionLength={}", question.length());
            return Mono.error(new BusinessException(ErrorCode.SERVICE_BUSY));
        }

        String userIdStr = String.valueOf(studentId);
        String systemPrompt = SYSTEM_TEMPLATE.render(Map.of("userId", userIdStr));
        // 构建用户问题（如果需要 RAG，则增强 Prompt，并在预算内裁剪）
        Mono<String> userPromptMono = useRag
                ? knowledgeRetriever.retrieveDocumentsAsync(question)
                        .map(docs -> {
                            BudgetPlan budgetPlan =
                                    tokenBudgetPlanner.planForRag(systemPrompt, question);
                            return knowledgeRetriever.buildPromptWithRagWithinBudget(
                                    question, docs, budgetPlan.ragBudget(), tokenBudgetPlanner.getTokenCounter());
                        })
                : Mono.just(question);

        return userPromptMono
                .flatMap(userPrompt -> {
                    tokenBudgetPlanner.assertFinalInputBudget(systemPrompt, userPrompt);
                    //埋点指标
                    estimatedInputTokensSummary.record(tokenBudgetPlanner.usedInputTokens(systemPrompt, userPrompt));
                    return chatClient.prompt()
                            .system(systemPrompt)
                            .user(userPrompt)
                            .options(OpenAiChatOptions.builder().maxTokens(maxOutputTokens).build())
                            .stream()
                            .content()
                            .timeout(Duration.ofSeconds(streamTokenGapSeconds))
                            .collectList()
                            .map(chunks -> String.join("", chunks));
                })
                .doOnNext(result -> {
                    if (requestId != null && !requestId.isBlank()) {
                        String cacheKeySuffix = studentId + ":" + requestId;
                        cacheClient.set(CACHE_AI_REPLY_KEY, cacheKeySuffix, result,
                                CACHE_AI_REPLY_TTL_MINUTES, TimeUnit.MINUTES);
                    }
                })
                .doOnError(e -> log.warn("ai-dialog-timeout-or-error non-stream {}rag questionLength={}",
                        useRag ? "" : "no-", question.length(), e))
                .doFinally(signal -> conversationSemaphore.release());
    }

    /**
     * 流式有记忆问答接口（支持可选 RAG）。
     * 若提供 requestId 且缓存命中，按 chunk 流式返回（与未命中一致，避免一次大块写占线程）；否则流式返回，完成后写入缓存。
     * LLM 流使用 {@code stream-token-gap-seconds}：相邻两次 token
     * 的最大间隔（{@link Flux#timeout(Duration)}），整段总时长由 MVC 异步超时等兜底。
     * 
     * @param userId      用户 ID
     * @param userMessage 用户消息
     * @param requestId   幂等请求 ID（可选）
     * @param useRag      是否启用 RAG（默认 false）
     */
    public Flux<String> streamChat(String userId,
            @NonNull String userMessage,
            String requestId,
            boolean useRag) {

        log.info("[AI] 流式请求开始 | userId={} | useRag={} | requestId={}",
                userId, useRag, requestId);

        // ========== 阶段 0：检查缓存 ==========
        // 幂等缓存：与 useRag 无关，只要 requestId 存在就尝试命中
        if (requestId != null && !requestId.isBlank()) {
            String cacheKeySuffix = userId + ":" + requestId;
            String cached = cacheClient.get(CACHE_AI_REPLY_KEY, cacheKeySuffix, String.class);
            if (cached != null && !cached.isEmpty()) {
                log.info("[AI] 缓存命中 | userId={} | 长度={}", userId, cached.length());
                // Controller 层的 publishOn(sseScheduler) 会处理线程切换
                return Flux.fromIterable(splitIntoChunks(cached));
            } else {
                log.debug("[AI] 缓存未命中 | userId={}", userId);
            }
        }

        // ========== 并发控制 ==========
        if (!conversationSemaphore.tryAcquire()) {
            log.warn("[AI] 并发超限 | userId={}, useRag={}", userId, useRag);
            return Flux.error(new BusinessException(ErrorCode.SERVICE_BUSY));
        }

        // ========== 阶段 1：RAG 检索（在线程池中） ==========
        Mono<List<Document>> ragSearch = useRag
                ? knowledgeRetriever.retrieveDocumentsAsync(userMessage)
                        .doOnSubscribe(sub -> log.info("[AI] RAG 检索开始 | userId={}", userId))
                        .doOnSuccess(docs -> log.info("[AI] RAG 完成 | userId={} | 文档数={}", userId, docs.size()))
                        .doOnError(e -> {
                            log.error("[AI] RAG 异常 | userId={}", userId, e);
                        })
                : Mono.just(List.of());

        // ========== 阶段 2：LLM 流式调用（Reactor NIO） ==========
        return ragSearch.flatMapMany(docs -> {
            log.info("[AI] LLM 调用开始 | userId={} | useRag={}", userId, useRag);

            // 使用预创建的模板，渲染系统提示词
            String systemPrompt = SYSTEM_TEMPLATE.render(Map.of("userId", userId));

            // 如果有 RAG 结果，增强 Prompt
            String finalUserMessage = userMessage;
            if (useRag && !docs.isEmpty()) {
                BudgetPlan budgetPlan =
                        tokenBudgetPlanner.planForRag(systemPrompt, userMessage);
                finalUserMessage = knowledgeRetriever.buildPromptWithRagWithinBudget(
                        userMessage, docs, budgetPlan.ragBudget(), tokenBudgetPlanner.getTokenCounter());
                log.debug("[AI] RAG 增强 | userId={} | prompt 长度={}", userId, finalUserMessage.length());
            }
            tokenBudgetPlanner.assertFinalInputBudget(systemPrompt, finalUserMessage);

            BudgetPlan memoryPlan =
                    tokenBudgetPlanner.planForMemory(systemPrompt, finalUserMessage);
            List<Message> history = memoryConversationService.loadHistory(userId);
            String userPromptWithMemory = memoryConversationService.buildUserPromptWithHistory(
                    finalUserMessage, userId, memoryPlan.memoryBudget());

            // 终检不能省：memory 有最小保留条数策略，边界场景可能略超预算
            tokenBudgetPlanner.assertFinalInputBudget(systemPrompt, userPromptWithMemory);
            estimatedInputTokensSummary.record(tokenBudgetPlanner.usedInputTokens(systemPrompt, userPromptWithMemory));

            List<String> chunks = new ArrayList<>();
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPromptWithMemory)
                    .options(OpenAiChatOptions.builder().maxTokens(maxOutputTokens).build())
                    .stream()
                    .content()
                    .doOnNext(chunks::add)
                    .timeout(Duration.ofSeconds(streamTokenGapSeconds))
                    .doOnError(e -> log.error("[AI] 流式异常 | userId={}", userId, e))
                    .doOnComplete(() -> {
                        log.info("[AI] LLM 完成 | userId={} | 总字符数={}",
                                userId, chunks.stream().mapToInt(String::length).sum());

                        // 缓存完整结果
                        if (requestId != null && !requestId.isBlank() && !chunks.isEmpty()) {
                            String full = String.join("", chunks);
                            String cacheKeySuffix = userId + ":" + requestId;
                            cacheClient.set(CACHE_AI_REPLY_KEY, cacheKeySuffix, full,
                                    CACHE_AI_REPLY_TTL_MINUTES, TimeUnit.MINUTES);
                            log.debug("[AI] 缓存写入 | requestId={}", requestId);
                        }
                        if (!chunks.isEmpty()) {
                            String full = String.join("", chunks);
                            // 只保存原始问题与回答，不把 RAG 增强后的 user prompt 写入记忆
                            memoryConversationService.appendOriginalTurn(userId, history, userMessage, full);
                        }
                    });
        }).doFinally(signal -> {
            // 整条链（RAG Mono + LLM Flux）结束即释放，避免 RAG 失败时内层未订阅导致漏 release
            log.debug("[AI] 对话并发许可释放 | userId={} | signal={}", userId, signal);
            conversationSemaphore.release();
        });
    }

    /**
     * 将字符串按固定长度拆成列表，用于命中缓存时模拟流式返回。
     */
    private static List<String> splitIntoChunks(String s) {
        if (s == null || s.isEmpty()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (int i = 0; i < s.length(); i += ChatApplicationService.CACHE_HIT_CHUNK_SIZE) {
            list.add(s.substring(i, Math.min(i + ChatApplicationService.CACHE_HIT_CHUNK_SIZE, s.length())));
        }
        return list;
    }

    private static String loadSystemPromptTemplate() {
        ClassPathResource resource = new ClassPathResource("prompts/ai-system-prompt.st");
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt template: prompts/ai-system-prompt.st", e);
        }
    }
}
