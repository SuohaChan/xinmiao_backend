package com.tree.config.other;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author SuohaChan
 * @data 2026/3/18
 */

/**
 * 线程池配置：为 AI 聊天场景提供独立的 RAG 和 SSE 线程池，实现资源隔离。
 */
@Configuration
public class ThreadPoolConfig {

    @Value("${app.chat.rag.core-pool-size:20}")
    private int ragCorePoolSize;
    @Value("${app.chat.rag.max-pool-size:100}")
    private int ragMaxPoolSize;
    @Value("${app.chat.rag.queue-capacity:1000}")
    private int ragQueueCapacity;

    @Value("${app.chat.sse.core-pool-size:50}")
    private int sseCorePoolSize;
    @Value("${app.chat.sse.max-pool-size:300}")
    private int sseMaxPoolSize;
    @Value("${app.chat.sse.queue-capacity:3000}")
    private int sseQueueCapacity;

    /**
     * RAG 检索专用线程池（阻塞操作隔离）
     */
    @Bean(name = "ragExecutor")
    public ThreadPoolTaskExecutor ragExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(ragCorePoolSize);
        executor.setMaxPoolSize(ragMaxPoolSize);
        executor.setQueueCapacity(ragQueueCapacity);
        executor.setThreadNamePrefix("rag-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        // 快速失败：队列满/线程满时直接拒绝，避免阻塞任务回灌到提交线程导致隔离失效与尾延迟尖刺
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * SSE 发送专用线程池（阻塞 IO 隔离）
     */
    @Bean(name = "sseExecutor")
    public ThreadPoolTaskExecutor sseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(sseCorePoolSize);
        executor.setMaxPoolSize(sseMaxPoolSize);
        executor.setQueueCapacity(sseQueueCapacity);
        executor.setThreadNamePrefix("sse-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        // 快速失败：慢客户端/大量连接导致堆积时直接拒绝，优先保护系统稳定性
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 将传统线程池适配成 Reactor Scheduler
     */
    @Bean
    public Scheduler ragScheduler(@Qualifier("ragExecutor") ThreadPoolTaskExecutor ragExecutor) {
        return Schedulers.fromExecutor(ragExecutor);
    }

    @Bean
    public Scheduler sseScheduler(@Qualifier("sseExecutor") ThreadPoolTaskExecutor sseExecutor) {
        return Schedulers.fromExecutor(sseExecutor);
    }
}
