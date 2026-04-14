# 实时消息（WebSocket + STOMP）— 手工 / 插件验证说明

后端配置见 `xinmiao-server` 中 `WebSocketConfig`：

- **SockJS 端点**：`http://<HOST>:<PORT>/ws`（无 `context-path` 时）
- **JWT**：握手查询参数 **`token=<AccessToken>`**（见 `JwtHandshakeInterceptor`）
- **消息代理**：`/topic` 前缀；应用目标前缀 `/app`

## 为何未提供通用 `.jmx`

STOMP over SockJS 需浏览器式握手与帧协议，**标准 JMeter 不含** WebSocket/STOMP 取样器，需安装 **WebSocket Sampler** 等插件并自行拼 STOMP `CONNECT`/`SUBSCRIBE` 帧。不同 JMeter 版本插件差异大，故仓库只保留 HTTP 压测脚本，本目录以说明为主。

## 建议验证步骤

1. 使用 **Postman**（支持 WebSocket）或 **前端页面**，连接：  
   `ws://127.0.0.1:9090/ws/websocket?token=<你的AccessToken>`（SockJS 会再协商子路径，以浏览器 Network 为准）。
2. **无 Token**：握手应返回 **401**（拦截器拒绝）。
3. **RabbitMQ**：任务推送相关队列在管理台查看堆积、**DLQ** 死信数量；与触发推送的 HTTP 压测频率对照。

## 与简历表述

长连接数、推送延迟、投递成功率等请在**实验室环境**用上述工具实测后填入报告；勿与纯 HTTP 压测报告混为同一指标。
