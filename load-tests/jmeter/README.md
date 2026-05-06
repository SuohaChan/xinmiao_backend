# JMeter 压测总览（按简历模块）

本目录脚本与下表**一一对应**简历中的技术表述。更细的「对照实验、简历填数」见 [docs/简历与压测验证指南.md](../../docs/简历与压测验证指南.md)。

**默认环境**：MySQL、Redis 已启动；`xinmiao-server` 使用 `dev`，端口 **`9090`**（见 `application-dev.yml`），URL 形如 `http://127.0.0.1:9090/...`。

---

## 离线 RAG 评测（非 JMeter，命令行怎么输入）

对应代码：`xinmiao-server` → `com.tree.chat.eval.RagOfflineEvalRunner`。  
这是 **Spring Boot 进程参数**，不是 JMeter；需要先打成可执行 Jar（仓库根目录执行）：

```bash
mvn -pl xinmiao-server -am package -DskipTests
```

**一定要在仓库根目录**打开终端（保证默认路径 `eval/cases/eval.jsonl`、`eval/reports/rag-eval-result.json` 相对仓库根有效）。

本机跑 Jar 时请先准备 **`env/dev.env`**：仓库里通常只有 `env/dev.env.example`，需复制一份并填入真实密钥（`copy env\dev.env.example env\dev.env` 或手动复制）。该文件内含 `SPRING_PROFILES_ACTIVE`、`SPRING_AI_OPENAI_API_KEY`、`CHROMA_HOST=http://localhost` 等，与 IDE / dev profile 一致。

**PowerShell（推荐）：** 在**仓库根目录**执行（路径 `eval/...`、`xinmiao-server/target/...` 均相对当前目录）。

```powershell
# 在仓库根目录，勿写死他人机器上的盘符路径
Set-Location <你的仓库根目录>

Get-Content .\env\dev.env | ForEach-Object {
  $line = $_.Trim()
  if ($line -eq '' -or $line.StartsWith('#')) { return }
  $i = $line.IndexOf('=')
  if ($i -lt 1) { return }
  $k = $line.Substring(0, $i).Trim()
  $v = $line.Substring($i + 1).Trim()
  Set-Item -Path "Env:$k" -Value $v
}

java -jar .\xinmiao-server\target\xinmiao-server-0.0.1-SNAPSHOT.jar --rag.eval.enabled=true
```

说明：`ForEach-Object` 里的 `return` 表示跳过当前行，继续读下一行，**不是**退出整个脚本。  
若 `env\dev.env` 不存在，上面 `Get-Content` 会报错；Jar 未打包则 `java -jar` 会找不到文件——需先在同一目录执行 `mvn -pl xinmiao-server -am package -DskipTests`。  
仅写 `--rag.eval.enabled=true` 时，会使用默认的 `eval/cases/eval.jsonl` 与 `eval/reports/rag-eval-result.json`（见下表）；更稳妥可显式写出路径（见下一节「带可选参数」示例）。

**CMD：** 不支持一键加载 `.env`，请先在 PowerShell 里执行上述 `Get-Content` 段，或在系统环境变量里配置与 `env/dev.env` 相同的键。

**可选参数**（全部接在主类参数后面，**同一个 `java -jar ...` 里用空格分隔**，布尔值用小写 `true`/`false`）：

| 参数 | 含义 | 默认 |
|------|------|------|
| `--rag.eval.enabled=true` | 必须，才会注册评测 Runner | — |
| `--rag.eval.file=...` | 评测集路径（相对**当前工作目录**） | `eval/cases/eval.jsonl` |
| `--rag.eval.k=3` | Top-k，范围会被限制在 1～50 | `3` |
| `--rag.eval.out=...` | 输出报告 JSON | `eval/reports/rag-eval-result.json` |
| `--rag.eval.snippet-max-chars=0` | 报告里每条 `snippet` 最大字符数；`0` 或负数表示**不截断**（与 chunk 全文一致） | `0` |
| `--rag.eval.split=50` | 拆分成多分片输出时的条数；`0` 表示不拆 | `0` |
| `--rag.eval.exit=true` | 跑完是否退出 JVM（`false` 则继续常驻，慎用端口占用） | `true` |

**带可选参数**：在加载 `env/dev.env` 之后，同一 `java -jar` 命令末尾追加参数即可，例如：

```powershell
java -jar .\xinmiao-server\target\xinmiao-server-0.0.1-SNAPSHOT.jar --rag.eval.enabled=true --rag.eval.file=eval/cases/eval.jsonl --rag.eval.k=3 --rag.eval.out=eval/reports/rag-eval-result.json --rag.eval.exit=true
```

**注意：**

- 会拉起**完整** Spring 上下文（向量库 Chroma、Redis、DB、MQ 等按 profile 配置）；依赖服务需可用。
- 默认 **`rag.eval.exit=true`**，评测结束进程退出；若本机 **9090** 已有常驻后端，请先停掉再跑，或评测时用 **`SERVER_PORT=9091`** 等环境变量避免端口冲突（与 Spring Boot 惯例一致）。
- 本机不要用 `env/prod.env` 跑 Jar：`CHROMA_HOST=http://chroma` 仅在 Docker 网络内有效；本地请用 **`env/dev.env`**（`CHROMA_HOST=http://localhost`）。

---

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
| **Redis 榜 vs 纯 DB 榜（对比延迟）** | `task-rank-module/rank-redis-vs-db-compare.jmx` | 每轮：`/rankings/today|week|college` 再 `/rankings/today/db|week/db|college/db`；HTML **Statistics** 按 Label 对比 `[Redis]` 与 `[DB-only]` 的 Average / 95% / 99% Line |
| 今日/周/学院榜 + 任务列表 | `task-rank-module/task-rank-load-test.jmx` | 依次请求 `rankings/today`、`week`、`college`、`GET /student/tasks?type=`；**需学生已关联班级/学院**，否则业务返回错误；HTML 按 Label 看各接口 P99 |
| 完成任务写压测 | 自建或复制 Thread Group | `POST /student/tasks/complete`，JSON `{"taskId":"<真实ID>"}`；幂等需同一 `taskId` 连打两次对照 DB |
| 幂等/无重复 | — | 结合数据库 `tb_credit_flow` 等校验（见集成测试或 SQL） |

**参数**：`-JtaskType=校级`（或 `院级`、`班级`、`未完成`，与 `StudentTaskServiceImpl` 一致）。

```bash
jmeter -n -t load-tests/jmeter/task-rank-module/task-rank-load-test.jmx ^
  -Jusername=学号 -Jpassword=密码 -JtaskType=校级 ^
  -l load-tests/jmeter/results/task-rank.jtl -e -o load-tests/jmeter/report/task-rank
```

**Redis vs DB 排行榜对比**（需后端已提供 `/student/tasks/rankings/*/db` 接口）：

```bash
jmeter -n -t load-tests/jmeter/task-rank-module/rank-redis-vs-db-compare.jmx ^
  -JHOST=127.0.0.1 -JPORT=9090 -Jusername=学号 -Jpassword=密码 ^
  -l load-tests/jmeter/results/rank-redis-vs-db.jtl -e -o load-tests/jmeter/report/rank-redis-vs-db
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

