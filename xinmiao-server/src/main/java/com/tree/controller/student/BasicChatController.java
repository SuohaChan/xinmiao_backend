package com.tree.controller.student;

import com.tree.annotation.mySystemLog;
import com.tree.chat.service.AiChatService;
import com.tree.context.StudentHolder;
import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author SuohaChan
 * @data 2025/9/9
 * 基于 Spring AI 的对话实现，仅做协议层；业务在 AiChatService。
 */
@RestController
@Slf4j
@RequestMapping("student/chat")
public class BasicChatController {

    private final AiChatService aiChatService;
    
    @Resource
    private Scheduler sseScheduler;

    public BasicChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /**
     * 简单问答接口（非阻塞：LLM 等待由 EventLoop 处理，不占用任何线程池）
     * 可选请求头 X-Request-Id：相同 ID 重复请求时返回缓存，不重复调 LLM（幂等）
     * 可选请求头 Use-RAG：true 表示启用 RAG（默认 false）
     */
    @PostMapping
    @mySystemLog(xxbusinessName = "基于 springAI 的无记忆对话（支持 RAG）")
    public Mono<String> chat(@RequestParam String question,
                             @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                             @RequestHeader(value = "Use-RAG", defaultValue = "false") boolean useRag) {
        Long studentId = requireStudentId();
        return aiChatService.chatReactive(studentId, question, requestId, useRag);
    }

    /**
     * 流式有记忆问答接口（响应式版本，支持可选 RAG）
     * 架构说明（MVC + Reactor 混合）：
     * 1. Controller 返回 Flux，Tomcat 线程立即释放（1ms）
     * 2. Spring MVC 的 ReactiveTypeHandler 订阅 Flux（reactor-http-nio 线程）
     * 3. RAG 检索 → async-executor 线程池（阻塞 150ms）
     * 4. LLM 调用 → Reactor NIO（非阻塞 2350ms）
     * 5. SSE 流式推送到客户端
     * 可选请求头：
     * - X-Request-Id：幂等请求 ID，重复请求返回缓存
     * - Use-RAG：true 表示启用 RAG（默认 false）
     */
    @mySystemLog(xxbusinessName = "基于 springAI 的流式有记忆对话（支持 RAG）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequest request,
                                                    @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                    @RequestHeader(value = "Use-RAG", defaultValue = "false") boolean useRag) {

        Long studentId = requireStudentId();
        String userId = String.valueOf(studentId);
        if (request == null || !StringUtils.hasText(request.message())) {
            return Flux.just(
                    ServerSentEvent.builder("message不能为空").event("error").build(),
                    ServerSentEvent.builder("[DONE]").event("done").build()
            );
        }
        log.info("[SSE] 请求 | userId={} | useRag={}", userId, useRag);
        
        return aiChatService.streamChat(userId, request.message(), requestId, useRag)
                .doOnSubscribe(s -> log.debug("[SSE] 订阅 | userId={}", userId))
                .doFinally(signal -> log.debug("[SSE] 完成 | userId={} | signal={}", userId, signal))
                .publishOn(sseScheduler)
                .map(content -> ServerSentEvent.builder(content).event("message").build())
                .concatWithValues(ServerSentEvent.builder("[DONE]").event("done").build())
                .timeout(
                        Duration.ofSeconds(60),
                        Flux.just(
                                ServerSentEvent.builder("响应超时，请检查网络连接").event("timeout").build(),
                                ServerSentEvent.builder("[DONE]").event("done").build()
                        )
                )
                .doOnComplete(() ->
                    log.info("[SSE] 流式完成 | userId={}", userId)
                )
                .onErrorResume(e -> {
                    log.error("[HTTP] 流式失败 | userId={}", userId, e);
                    String msg;
                    if (e instanceof BusinessException be) {
                        msg = (be.getMessage() != null && !be.getMessage().isBlank())
                                ? be.getMessage()
                                : "服务繁忙，请稍后重试";
                    } else if (e instanceof RejectedExecutionException) {
                        msg = "服务繁忙，请稍后重试";
                    } else {
                        msg = "AI 回复失败，请稍后重试";
                    }
                    return Flux.just(ServerSentEvent.builder(msg).event("error").build());
                });
    }

    private Long requireStudentId() {
        if (StudentHolder.getStudent() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return StudentHolder.getStudent().getId();
    }

    public record ChatRequest(String message) {}
   
}
