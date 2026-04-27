package com.tree.config.concurrent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/**
 * 线程池任务装饰：与切换线程池/工作线程配合的隐式上下文传递。
 * <ul>
 *   <li>{@link ContextPropagatingTaskDecorator}（Spring 6.2+）：基于 Micrometer {@code ContextSnapshot}，
 *       传播已注册的 {@code ThreadLocal}（含 Web 请求上下文、Tracing 等，取决于 classpath 上的注册器）</li>
 *   <li>{@link MdcContextTaskDecorator}：显式复制 SLF4J {@link org.slf4j.MDC}，保证 logback 中 traceId/spanId 等与线程池日志对齐</li>
 * </ul>
 * Reactor 侧（{@code publishOn}/{@code subscribeOn}）需配合 {@code spring.reactor.context-propagation=auto}
 * 与 {@code io.micrometer:context-propagation}，在调度到上述线程池前把上下文挂到 Reactor Context。
 */
@Configuration(proxyBeanMethods = false)
public class ContextPropagationTaskDecoratorConfig {

    @Bean
    public TaskDecorator contextPropagatingTaskDecorator() {
        TaskDecorator mdc = new MdcContextTaskDecorator();
        TaskDecorator micrometer = new ContextPropagatingTaskDecorator();
        return runnable -> micrometer.decorate(mdc.decorate(runnable));
    }
}
