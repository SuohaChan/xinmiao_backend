# 新苗 · 高校智慧服务系统

面向高校的智慧服务后端系统，集成 **AI 智能问答、RAG 检索增强、实时通讯、任务积分体系** 等核心功能。采用 **Reactor响应式编程、线程池隔离、Redis Lua 防重** 等技术，支撑高并发场景。

## 技术栈

- **JDK 17** · **Spring Boot 3.5.3** · **MyBatis-Plus 3.5.6** · **MySQL**
- **Project Reactor** · **WebSocket** · **JWT** · **Spring AI 1.0.0**
- **Redis**  · **RabbitMQ** · **Chroma 向量库** · 
- **Druid** · **SpringDoc OpenAPI 3** · **阿里云 OSS** · **Docker Compose**

## 核心特性

### AI 智能问答与 RAG 检索增强
基于 **Spring AI + Reactor** 实现流式 SSE 输出，Tomcat 线程立即释放；集成 **Chroma 向量库** 支持语义检索；**Redis String**(JSON 数组) 持久化上下文记忆，TTL 自动过期 + 滑动窗口裁剪；`X-Request-Id` 幂等控制，相同请求 10 分钟内返回缓存。

### 高并发与线程池隔离
按场景拆分 **RAG 检索线程池** (20/100)、**SSE 发送线程池** (50/300)；Semaphore 限流 (30 并发) 防止 LLM 调用压垮下游；**Redis ZSET + Lua 脚本** 滑动窗口限流 (单用户 10 次/分钟)；信号量控制缓存回源 DB 并发数 (20)，超出返回 503。

### 分布式缓存与一致性保障
**Cache Aside + Redis 分布式锁** 解决缓存击穿；数据库 **CAS 乐观更新** 保证任务提交幂等；**Redis Set 防重** 减轻 DB 压力；**Lua 脚本原子操作** 实时更新排行榜；定时任务重建榜单兜底最终一致性。

### 实时消息推送
基于 **Spring WebSocket + STOMP** 实现通知/聊天实时推送，心跳检测清理僵尸连接；**RabbitMQ** 开启 Publisher Confirm/Return，消费失败重试 (3 次) + 死信队列 (DLQ)；配置发送超时 (10s)、缓冲上限 (1MB) 断开慢客户端。

### 权限认证与安全
**JWT 无状态 + Refresh Token 存 Redis** 双令牌机制；**HandlerInterceptor** 动态鉴权实现学生/辅导员/管理员三级权限；**BCrypt** 密码加密支持自定义 salt 强度。

### 工程化与可观测性
全局 `@RestControllerAdvice` 统一异常处理；**Logback + AOP 切面** 记录接口耗时/参数/用户 ID；**SpringDoc** 自动生成 Swagger UI 接口文档；**Druid** SQL 统计与慢查询日志。

## 项目结构

| 模块 | 说明 |
|------|------|
| xinmiao-common | 通用工具、Result、异常、常量等 |
| xinmiao-pojo | 实体 / DTO / VO / 枚举等数据结构 |
| xinmiao-server | 后端服务：Web + 业务逻辑 + AI 集成 |

## 运行说明

- **环境**: JDK 17+ (必须), Maven 3.x, MySQL 8.0+, Redis 6.x+, Docker & Docker Compose
- **启动**: 根目录执行 `mvn clean install`,进入 xinmiao-server 运行主类或 `mvn spring-boot:run`
- **配置**: 编辑 `application-dev.yml` 配置 MySQL/Redis/AI 密钥 (硅基流动 API Key)/阿里云 OSS
- **接口文档**: 启动后访问 http://localhost:8080/swagger-ui.html


## 架构设计与性能指

| 线程池名称 | 核心线程数 | 最大线程数 | 队列容量 | 用途 |
|-----------|----------|----------|---------|------|
| Tomcat HTTP | - | 200(可配) | - | 普通 MVC 接口同步请求 |
| MVC 异步 (SSE 写出) | 10 | 50 | 200 | SSE 流式响应写出，避免阻塞 Tomcat 主线程 |
| RAG 检索 | 20 | 100 | 256 | 向量检索 + Prompt 组装等阻塞操作 |
| SSE 发送 | 50 | 300 | 512 | Reactor Scheduler 切换写出线程 |
| AI 对话并发 | Semaphore 30 | - | - | 所有对话请求统一限流 |

**关键性能指标**: SSE 流式响应 Tomcat 线程释放 < 1ms；RAG 超时 (15s)/异常自动降级；缓存命中率 > 90%；相同 `X-Request-Id` 请求 10 分钟内返回缓存。

**典型调用链路 **(流式问答): HTTP 请求 → Tomcat 线程 (1ms) → MVC 异步线程池 (mvcTaskExecutor) → RAG 检索 (150ms, ragExecutor) → WebClient NIO (非阻塞 2350ms) → LLM 流式响应 → publishOn(sseScheduler) 切换线程 → SSE 发送 (sseExecutor) → 客户端接收 [DONE] 事件流结束标记。# xinmiao_backend

