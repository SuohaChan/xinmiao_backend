package com.tree.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tree.annotation.mySystemLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Map;

@Component
@Aspect
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.log.aop", name = "enabled", havingValue = "true")
public class MyLogAspect {

    private final ObjectMapper objectMapper;

    // 定义切点，匹配被 @mySystemLog 注解标注的方法
    @Pointcut("@annotation(com.tree.annotation.mySystemLog)")
    public void xxpt() {
    }

    // 环绕通知，在切点方法执行前后记录日志
    @Around("xxpt()")
    public Object xxprintLog(ProceedingJoinPoint joinPoint) throws Throwable {
        Object ret;
        try {
            handleBefore(joinPoint);
            ret = joinPoint.proceed();
            handleAfter(ret);
        } finally {
            log.info("=======================end======================={}", System.lineSeparator());
        }
        return ret;
    }

    private void handleBefore(ProceedingJoinPoint joinPoint) {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return;
        }
        HttpServletRequest request = requestAttributes.getRequest();
        mySystemLog systemlog = getSystemLog(joinPoint);

        log.info("======================Start======================");
        log.info("请求URL   : {}", request.getRequestURL());
        log.info("接口描述   : {}", systemlog.xxbusinessName());
        log.info("请求方式   : {}", request.getMethod());
        log.info("请求类名   : {}.{}", joinPoint.getSignature().getDeclaringTypeName(), ((MethodSignature) joinPoint.getSignature()).getName());
        log.info("访问IP    : {}", request.getRemoteHost());

        // 过滤掉复杂对象（如 HttpServletRequest 和 MultipartFile）
        Object[] args = joinPoint.getArgs();
        Object[] filteredArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof HttpServletRequest) {
                filteredArgs[i] = "HttpServletRequest";
            } else if (args[i] instanceof MultipartFile file) {
                filteredArgs[i] = Map.of(
                        "fileName", file.getOriginalFilename(),
                        "size", file.getSize(),
                        "contentType", file.getContentType()
                );
            } else {
                filteredArgs[i] = args[i];
            }
        }
        log.info("传入参数   : {}", toJSONStringSafe(filteredArgs));
    }

    // 安全的JSON序列化方法（使用 Jackson，与项目约定一致）
    private String toJSONStringSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "Serialization failed: " + e.getMessage();
        }
    }

    // 处理方法执行后的日志记录
    private void handleAfter(Object ret) {
        try {
            log.info("返回参数   : {}", objectMapper.writeValueAsString(ret));
        } catch (JsonProcessingException e) {
            log.info("返回参数   : [Serialization failed: {}]", e.getMessage());
        }
    }

    private mySystemLog getSystemLog(ProceedingJoinPoint joinPoint) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        return methodSignature.getMethod().getAnnotation(mySystemLog.class);
    }
}

