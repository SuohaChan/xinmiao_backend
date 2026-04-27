package com.tree.config.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tree.interceptor.ChatRateLimitInterceptor;
import com.tree.interceptor.CounselorAdminOnlyInterceptor;
import com.tree.interceptor.CounselorOnlyInterceptor;
import com.tree.interceptor.JwtAuthInterceptor;
import com.tree.interceptor.StudentOnlyInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
@Slf4j
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    private final ObjectMapper objectMapper;
    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final CounselorOnlyInterceptor counselorOnlyInterceptor;
    private final StudentOnlyInterceptor studentOnlyInterceptor;
    private final CounselorAdminOnlyInterceptor counselorAdminOnlyInterceptor;
    private final ChatRateLimitInterceptor chatRateLimitInterceptor;
    private final ThreadPoolTaskExecutor mvcTaskExecutor;

    public MvcConfig(
            ObjectMapper objectMapper,
            JwtAuthInterceptor jwtAuthInterceptor,
            CounselorOnlyInterceptor counselorOnlyInterceptor,
            StudentOnlyInterceptor studentOnlyInterceptor,
            CounselorAdminOnlyInterceptor counselorAdminOnlyInterceptor,
            ChatRateLimitInterceptor chatRateLimitInterceptor,
            @Qualifier("mvcTaskExecutor") ThreadPoolTaskExecutor mvcTaskExecutor) {
        this.objectMapper = objectMapper;
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.counselorOnlyInterceptor = counselorOnlyInterceptor;
        this.studentOnlyInterceptor = studentOnlyInterceptor;
        this.counselorAdminOnlyInterceptor = counselorAdminOnlyInterceptor;
        this.chatRateLimitInterceptor = chatRateLimitInterceptor;
        this.mvcTaskExecutor = mvcTaskExecutor;
    }

    @Value("${cors.allowed-origins:http://localhost:8000}")
    private String corsAllowedOrigins;

    /** 与 spring.mvc.async.request-timeout 一致，避免此处写死与全局配置不一致 */
    @Value("${spring.mvc.async.request-timeout:180s}")
    private Duration asyncRequestTimeout;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = corsAllowedOrigins.split("\\s*,\\s*");
        log.info("[CORS 配置] 允许的源：{}", String.join(", ", origins));
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 0: JWT 认证（校验 Access Token，有效则写入 request 属性；默认拦截全部，仅排除下列路径）
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // Springdoc OpenAPI
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/index.html",
                        // 学生端免认证
                        "/student/login",
                        "/student/register",
                        "/student/refresh",
                        "/student/validate",
                        "/student/face",
                        // 辅导员端免认证
                        "/counselor/login",
                        "/counselor/register",
                        "/counselor/refresh",
                        "/counselor/refreshToken"
                ).order(0);

        // 1: 学生端专属（规范路径 /student/**，除登录注册等已排除项）
        registry.addInterceptor(studentOnlyInterceptor)
                .addPathPatterns("/student/**")
                .excludePathPatterns(
                        "/student/login",
                        "/student/register",
                        "/student/refresh",
                        "/student/validate",
                        "/student/face"
                )
                .order(1);

        // 辅导员端专属（规范路径 /counselor/** 下的业务接口）
        registry.addInterceptor(counselorOnlyInterceptor)
                .addPathPatterns(
                        "/counselor/my/**",
                        "/counselor/notices/**",
                        "/counselor/information/**",
                        "/counselor/appeals/**",
                        "/counselor/tasks/**",
                        "/counselorTask/**",
                        "/counselor/college/**",
                        "/counselor/class/**"
                )
                .order(1);

        registry.addInterceptor(counselorAdminOnlyInterceptor)
                .addPathPatterns("/counselor/college/**", "/counselor/class/**", "/counselor/students/**")
                .order(1);

        // 2: AI 对话限流（/student/chat 下的接口）
        registry.addInterceptor(chatRateLimitInterceptor)
                .addPathPatterns("/student/chat/**")
                .order(2);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcTaskExecutor);
        configurer.setDefaultTimeout(asyncRequestTimeout.toMillis());
        log.info("[MVC 异步支持] 配置完成 | defaultTimeout={} (spring.mvc.async.request-timeout)",
                asyncRequestTimeout);
    }


    // ===================== 消息转换器（项目约定：HTTP JSON 使用 Jackson） =====================

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // ByteArray 放首位，避免 /v3/api-docs 被错误处理成 Base64（SpringDoc 已知问题）
        converters.add(0, new ByteArrayHttpMessageConverter());
        MappingJackson2HttpMessageConverter jackson = new MappingJackson2HttpMessageConverter();
        jackson.setObjectMapper(objectMapper);
        converters.add(1, jackson);
    }
    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setDateTimeFormatter(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        registrar.setDateTimeFormatter(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        registrar.registerFormatters(registry);
    }
}