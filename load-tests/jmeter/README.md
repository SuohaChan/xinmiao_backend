# JMeter 压测总览（按简历模块）

本目录脚本与下表**一一对应**简历中的技术表述。更细的「对照实验、简历填数」见 [docs/简历与压测验证指南.md](../../docs/简历与压测验证指南.md)。

**默认环境**：MySQL、Redis 已启动；`xinmiao-server` 使用 `dev`，端口 **`9090`**（见 `application-dev.yml`），URL 形如 `http://127.0.0.1:9090/...`。

**脚本目录**：

| 目录 | 内容 |
|------|------|
| `auth-module/` | 认证授权合并计划与登录/refresh 专项 |
| `resume-module/` | 资讯缓存与穿透/对照 |
| `ai-chat-module/` | AI 对话与并发 |
| `task-rank-module/` | 排行榜 + 任务列表只读压测 |
| `engineering-module/` | 校验非法参数、4xx 断言 |
| `realtime-module/` | WebSocket 说明（无通用 STOMP `.jmx`） |
| `results/`、`report/` | 压测输出（已 `.gitkeep`，大文件勿提交） |

---

## 环境与通用约定

| 项 | 说明 |
|----|------|
| 账号 | 准备学生账号；业务接口需 Header：`Authorization: Bearer <access_token>` |
| 登录 | `POST /student/login`，Body：`{"username":"学号","password":"密码"}`；`POST /student/refresh` 依赖 **Cookie** 中的 refresh，JMeter 需 **HTTP Cookie Manager** |
| Token 有效期 | 长跑压测前确认 JWT 未在压测中途过期 |
| 报告输出 | 建议在仓库根目录执行 CLI，`-l` 写 `.jtl`，`-e -o` 生成 HTML Dashboard |
| 并发与简历 | 若简历写「200 并发」，请在对应 Thread Group 将线程数改为 **200**（或等价 RPS），并同步 Ramp-up；以下为脚本**默认值**，以 GUI/`.jmx` 为准 |

**结果目录**：

- `load-tests/jmeter/results/`：`.jtl` 原始结果  
- `load-tests/jmeter/report/`：HTML 报告（体积大时可 `.gitignore`）

---

## 1. 认证授权模块

**简历对应**：JWT 无状态登录；Access + Refresh 双令牌；Refresh 存 Redis；拦截器 + ThreadLocal；RBAC；压测无串号、P99 等。

当前仓库内 **`auth-module-all.jmx` 一个文件内包含三个线程组**（与旧文档里拆开的 main / unauthorized / refresh 对应），根节点共用 **Summary Report / Aggregate Report**。

| 线程组（GUI 中名称） | 作用 | 观测与填数 |
|----------------------|------|------------|
| ① `401 (no token)` | `GET /student/info` 不带 Token | 断言 **HTTP 401** |
| ② `refresh (login+refresh)` | `POST /student/login` → `POST /student/refresh`，写入全局 `accessToken` | refresh 延迟、Cookie |
| ③ `main (GET /student/info)` | 带 Bearer 压测受保护接口 | HTML：**P99 / 95th pct、Throughput、Error %**；线程数默认 40，可改为 **200** |

**专项脚本**（只测登录或只测 refresh 时可选）：`login-perf-test.jmx`、`refresh-perf-test.jmx`。

**指标验收（权限 100% / refresh 成功 / 与 JWT·Redis 指标关系）**：见 `auth-module/指标验证说明.md`；对应脚本 **`auth-metrics-validation.jmx`**（单线程、多断言，**Error % = 0** 表示本脚本内权限与状态码与预期一致）。**「JWT 签发/校验 ≤5ms」与「Redis 命中率」不能单靠 JMeter 得出**，说明文档里有服务端与 Redis 侧做法。

**一键合并计划（推荐）**：`auth-module-all.jmx`。

```bash
jmeter -n -t load-tests/jmeter/auth-module/auth-module-all.jmx ^
  -Jusername=学号 -Jpassword=密码 -JaccessToken=你的AccessToken ^
  -l load-tests/jmeter/results/auth-all.jtl -e -o load-tests/jmeter/report/auth-module-all
```

- **GUI**：若只想跑主压测，可**禁用**线程组 ①、②，只启用 ③；或只启用 ① 做 401 负例。  
- **③ 的 Token**：若已用 ② 跑过 login+refresh，可用全局属性里的 token；也可在「User Defined Variables」填 `accessToken`，与 `-JaccessToken=` 二选一（以 `.jmx` 内引用为准）。

**「无串号」**：多线程共用同一 `accessToken` 时，校验的是**同一用户上下文**；若需模拟多用户，应准备多账号或多 Token，在 JMeter 用 CSV Data Set Config 分发，并对响应中的 `studentId` 等做断言（需与业务返回字段一致）。

---

## 2. 缓存优化模块

**简历对应**：Cache-Aside、空值缓存、锁、TTL 抖动、Redis 异常回源限流；核心接口耗时、命中率、DB QPS 对比等。

| 验证目标 | 已有脚本 | 观测与填数 |
|----------|----------|------------|
| 热点资讯只读压测（缓存命中、P90/P99） | `resume-module/information-perf-test.jmx` | HTML：**响应时间百分位、吞吐量**；Redis Insight：**key、内存、命中率** |
| 穿透/不存在 ID、与存在 ID 对照 | `resume-module/information-load-test.jmx` | 含 `/student/informations/999999` 等路径，结合日志/DB 观察是否打库 |
| DB QPS 对比 | 同一 `information-perf-test` 跑两轮 | **关缓存**（配置关闭缓存或停 Redis 使走 DB）与 **预热后开缓存** 各一轮；MySQL：`SHOW GLOBAL STATUS LIKE 'Queries';` 前后差分 ÷ 秒数，见 [验证指南 § 缓存](../../docs/简历与压测验证指南.md) |

```bash
jmeter -n -t load-tests/jmeter/resume-module/information-perf-test.jmx ^
  -Jusername=学号 -Jpassword=密码 ^
  -l load-tests/jmeter/results/info-perf.jtl -e -o load-tests/jmeter/report/information-perf
```

**参数**：`HOST`（默认 `127.0.0.1`）、`PORT`（默认 `9090`）、`accessToken`（可选；脚本内可先 login 再压测，与 `.jmx` 内 Step 一致）。

---

## 3. AI 能力集成模块

**简历对应**：Spring AI + DeepSeek、RAG/SSE 线程池、上下文记忆、Redis ZSet + Lua 限流、多路并发等。

| 验证目标 | 已有脚本 | 观测与填数 |
|----------|----------|------------|
| 对话接口（非流式） | `ai-chat-module/chat-load-test.jmx` | `POST /student/chat`，**表单参数 `question`**（与 `BasicChatController` 一致）；已设 **response_timeout=120s**、Cookie 支持 refresh；HTML：**P99、Error %** |
| 外网 LLM | — | 端到端延迟受 DeepSeek 影响大，简历建议区分 **应用侧** 与 **端到端** |
| SSE 流式 `/student/chat/stream` | — | 需插件或长超时；本脚本不覆盖 |

```bash
jmeter -n -t load-tests/jmeter/ai-chat-module/chat-load-test.jmx ^
  -Jusername=学号 -Jpassword=密码 ^
  -l load-tests/jmeter/results/chat-load.jtl -e -o load-tests/jmeter/report/chat-load
```

---

## 4. 积分与排行榜体系模块

**简历对应**：任务防重、CAS 幂等、ZSet 排行、Lua、接口 P99、无重复积分等。

| 验证目标 | 已有脚本 | 观测与填数 |
|----------|----------|------------|
| 今日/周/学院榜 + 任务列表 | `task-rank-module/task-rank-load-test.jmx` | 依次请求 `rankings/today`、`week`、`college`、`GET /student/tasks?type=`；**需学生已关联班级/学院**，否则业务返回错误；HTML 按 Label 看各接口 P99 |
| 完成任务写压测 | 自建或复制 Thread Group | `POST /student/tasks/complete`，JSON `{"taskId":"<真实ID>"}`；幂等需同一 `taskId` 连打两次对照 DB |
| 幂等/无重复 | — | 结合数据库 `tb_credit_flow` 等校验（见集成测试或 SQL） |

**参数**：`-JtaskType=校级`（或 `院级`、`班级`、`未完成`，与 `StudentTaskServiceImpl` 一致）。

```bash
jmeter -n -t load-tests/jmeter/task-rank-module/task-rank-load-test.jmx ^
  -Jusername=学号 -Jpassword=密码 -JtaskType=校级 ^
  -l load-tests/jmeter/results/task-rank.jtl -e -o load-tests/jmeter/report/task-rank
```

---

## 5. 实时消息模块

**简历对应**：WebSocket + STOMP、JWT 鉴权、RabbitMQ、重试、DLQ 等。

详见 **[realtime-module/README.md](./realtime-module/README.md)**（端点 `/ws`、`token` 查询参数、SockJS 说明）。HTTP 压测无法替代长连接指标，请分项写报告。

---

## 6. 工程化治理模块

**简历对应**：全局异常、错误码、Validation、5xx、非法参数拦截等。

| 验证目标 | 已有脚本 | 说明 |
|----------|----------|------|
| 校验失败 → HTTP 400 | `engineering-module/validation-negative-test.jmx` | `POST /student/tasks/complete` 空 body / 空 `taskId`；`GET /student/tasks` 缺 `type`。断言 **400**（与 `GlobalExceptionHandler` 一致） |
| 5xx 比例 | 任意业务压测 HTML | **Error %**、Response codes 分布 |
| 未登录 | `auth-module-all` 线程组 ① | **401** |

若某条断言与本地 Spring 版本不一致（如缺参返回非 400），请在 GUI 中调整 **ResponseAssertion**。

```bash
jmeter -n -t load-tests/jmeter/engineering-module/validation-negative-test.jmx ^
  -Jusername=学号 -Jpassword=密码 ^
  -l load-tests/jmeter/results/validation.jtl -e -o load-tests/jmeter/report/validation-negative
```

---

## 简历句式模板

- 认证模块专用：[auth-module/模块一简历数据模板.md](./auth-module/模块一简历数据模板.md)

---

## 常见问题：每次点 `jmeter.bat` 都像新开、设置没了？

这是**正常现象**，不是坏掉了。

1. **测试计划不会自动保存**  
   每次启动都是空白「新测试计划」。在 GUI 里改的内容若没 **文件 → 保存**（Ctrl+S），关掉就没了。

2. **正确用法（推荐）**  
   启动后立刻 **文件 → 打开**，选本仓库里的 `.jmx`，改完再 **保存到同一文件**。这样配置在 Git 里也有记录。

3. **语言每次要重选**  
   「选项 → 选择语言」只对当前进程生效。要固定中文，任选其一即可长期生效：  
   - **方式 A**：在 JMeter 安装目录的 `bin\user.properties` 末尾增加（没有就新建该文件）：  
     `user.language=zh`  
     `user.region=CN`  
   - **方式 B**：在命令行先 `cd` 到 JMeter 的 `bin` 目录，执行：  
     `jmeter.bat -Duser.language=zh -Duser.region=CN`  
   - **方式 C**：若存在 `bin\setenv.bat`，在其中设置 `set JVM_ARGS=-Duser.language=zh -Duser.region=CN`（部分 JMeter 版本会读取）。

4. **命令行压测（`-n`）**  
   不经过 GUI，直接读仓库里的 `.jmx`，不存在「界面语言」问题，适合正式出 HTML 报告。

