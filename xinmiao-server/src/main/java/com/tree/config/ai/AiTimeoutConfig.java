package com.tree.config.ai;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import com.tree.chat.rag.RagProperties;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * AI HTTP 客户端超时配置：统一按「超时秒数」创建 RestClient / WebClient，
 * LLM 与向量库限时集中在此（含 ChromaApi 使用 chromaRestClientBuilder），避免分散多 config。
 */
@Configuration
public class AiTimeoutConfig {

    @Bean
    @org.springframework.context.annotation.Primary
    public RestClient.Builder restClientBuilder(
            @Value("${app.chat.timeout.seconds:90}") int timeoutSeconds) {
        return restClientBuilderWithTimeout(timeoutSeconds);
    }

    /** 向量库专用：同一工厂传入更短超时，供 ChromaApi 使用 */
    @Bean("chromaRestClientBuilder")
    public RestClient.Builder chromaRestClientBuilder(
            RagProperties ragProperties) {
        return restClientBuilderWithTimeout(ragProperties.getRetrievalTimeoutSeconds());
    }

    /** 向量库 Chroma 使用带限时的 Builder，无 Chroma 配置时不注册 */
    @Bean
    @ConditionalOnClass(ChromaApi.class)
    @ConditionalOnProperty(name = "spring.ai.vectorstore.chroma.client.host")
    public ChromaApi chromaApi(
            @Value("${spring.ai.vectorstore.chroma.client.host:http://localhost}") String host,
            @Value("${spring.ai.vectorstore.chroma.client.port:8000}") int port,
            @Qualifier("chromaRestClientBuilder") RestClient.Builder chromaRestClientBuilder) {
        String baseUrl = (host == null || host.isBlank()) ? "http://localhost:8000"
                : (host.endsWith("/") ? host.substring(0, host.length() - 1) : host) + ":" + port;
        return ChromaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(chromaRestClientBuilder)
                .build();
    }

    @Bean
    public WebClient.Builder webClientBuilder(
            @Value("${app.chat.timeout.seconds:90}") int timeoutSeconds) {
        return webClientBuilderWithTimeout(timeoutSeconds);
    }

    /** 统一工厂：根据超时秒数创建带 connect/read 超时的 RestClient.Builder */
    public static RestClient.Builder restClientBuilderWithTimeout(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .build()
        );
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory);
    }

    /** 统一工厂：根据超时秒数创建带 connect 超时的 WebClient.Builder */
    public static WebClient.Builder webClientBuilderWithTimeout(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        var connector = new JdkClientHttpConnector(httpClient);
        return WebClient.builder().clientConnector(connector);
    }
}
