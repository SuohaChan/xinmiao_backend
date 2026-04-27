package com.tree.config.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * MVC 异步写出（如 SSE）专用线程池；独立配置类，便于 {@link MvcConfig} 注入同一实例到 {@code configureAsyncSupport}。
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class MvcAsyncTaskExecutorConfig {

    @Bean(name = "mvcTaskExecutor")
    public ThreadPoolTaskExecutor mvcTaskExecutor(TaskDecorator contextPropagatingTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mvc-async-");
        executor.setTaskDecorator(contextPropagatingTaskDecorator);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("[MVC 异步执行器] 初始化完成 | corePoolSize=10, maxPoolSize=50, queueCapacity=200");
        return executor;
    }
}
