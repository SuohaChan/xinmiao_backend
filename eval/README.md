# Eval：RAG 离线评测与 LLM 裁判

本目录包含两条流水线：**Java 侧**用与线上一致的检索器，在 **`metadata.fileName` 字符串与 `gold_docs` 完全匹配** 的前提下算客观指标（无语义模型）；**Python 侧**用 OpenAI 兼容 API 对检索证据做主观裁判。二者共用「用例 → 报告 → 裁判」的数据约定。

---

## 目录

| 路径 | 说明 |
|------|------|
| `cases/` | 评测用例，JSONL，默认 `eval.jsonl` |
| `reports/` | Spring 离线报告，默认 `rag-eval-result.json` |
| `runs/` | `dp.py` 输出（`eval/runs/*` 默认被 `.gitignore` 忽略，仅保留 `.gitkeep`） |
| `tools/` | 语料预处理脚本（手册空行、章节等） |
| `dp.py` | LLM-as-a-judge 入口 |
| `.env` / `.env.example` | API 密钥等（`.env` 勿提交；从 example 复制） |

---

## 一、用例格式（`cases/*.jsonl`）

每行一个 JSON 对象，至少包含：

| 字段 | 类型 | 含义 |
|------|------|------|
| `id` | string | 可选；缺省时按行号生成 |
| `query` | string | 检索问题 |
| `gold_docs` | string[] | 期望命中的文档 id；与向量库 `metadata.fileName` 一致 |

---

## 二、Spring 离线评测（`RagOfflineEvalRunner`）

在**仓库根目录**启动，保证 `eval/cases/...` 等相对路径正确。

### 2.1 编译方式（不必每次 `package`）

- **日常改 Java**：用 IDE 直接运行 `com.tree.XinmiaoApplication`（或本模块 `spring-boot:run`），保存后增量编译即可，进程读的是 `target/classes`，**不用**每次打 jar。
- **只有**你要用 **`java -jar xinmiao-server/target/*.jar ...`** 跑离线评测时，才需要在改代码后执行一次打包（因为 jar 里仍是旧 class）：

```bash
mvn -pl xinmiao-server -am package -DskipTests
```

命令行不打 jar、与线上一致起服务时（示例，参数按需改）：

```bash
mvn -pl xinmiao-server -am compile -DskipTests
mvn -pl xinmiao-server spring-boot:run -DskipTests -Dspring-boot.run.arguments="--spring.profiles.active=dev,--rag.eval.enabled=true,--rag.eval.exit=true"
```

`compile` 比 `package` 快得多；评测跑完若 `rag.eval.exit=true` 进程会退出，下次改代码再 `compile` 后重跑即可。

**只调 YAML、少碰编译**：线上 RAG 的 `top-k`、相似度等在 `application.yml` 的 `app.chat.rag`；离线评测路径、`k`、`exit` 等在**同文件**顶层 `rag.eval`。改这两处即可，Java 里不再写一套默认值。用 `java -jar` 又不想为改配置重打 jar 时，可用 `--spring.config.additional-location=file:./config/` 指向 jar 外的覆盖 yml。

### 2.2 启动参数（常用）

| 参数 | 默认值来源 | 说明 |
|------|-------------|------|
| `--rag.eval.enabled` | — | 必须为 `true` 才会跑评测 |
| `--rag.eval.file` / `out` / `k` / `split` / `exit` / `snippet-max-chars` | `application.yml` → `rag.eval` | 命令行可逐项覆盖；不在代码里写默认 |
| （线上检索）`app.chat.rag.top-k` 等 | `application.yml` → `app.chat.rag` | 与对话里 RAG 检索一致 |

PowerShell 加载 `env/dev.env` 的写法与完整示例见 **`load-tests/jmeter/README.md`** 中的「离线 RAG 评测」一节。

若本机 `env/dev.env` 里配置了 **`RAG_EVAL_FILE`**，须指向 `eval/cases/eval.jsonl`（或删除该项，交给 `rag.eval.file`）。

### 2.3 报告里有什么

- 顶层含 `results[]`：每条含 `query`、`goldDocs`、`topK`、`evidences`（`snippet` + `metadata.fileName` 等）及下列数值字段。
- 控制台会打每条与 macro 平均；**数值含义以本节为准**（与 `RagOfflineEvalRunner` 实现一致）。

#### `rag-eval-result.json` 客观指标（硬性定义）

以下均在 **fileName 级**：先把检索返回的文档按顺序取 `metadata.fileName`，**去重保序**后最多保留 `k` 个，得到列表 `topK`（长度 ≤ `k`）。金标集合 `G` 来自用例 `gold_docs`。某条 `fileName` 算「相关」当且仅当其字符串在 `G` 中。

| 字段 | 定义 |
|------|------|
| `hits` | `topK` 中属于 `G` 的 fileName 个数（每个金标文件在 `topK` 中最多计 1 次）。 |
| `recallAtK` | 若 `G` 为空则 0；否则 `hits / \|G\|`（被短名单覆盖到的金标比例）。 |
| `precisionAtK` | 若 `topK` 为空则 0；否则 `hits / \|topK\|`（短名单里相关 fileName 占比）。**不是** `hits/k`：去重后短名单长度常小于配置里的 `k`。 |
| `mrr` | 在 `topK` 顺序下，第一个相关项的位置为 `r`（1 起算），则 `mrr = 1/r`；无相关项则为 0。 |
| `ndcgAtK` | 对 `topK` 做二元相关 DCG，位置贡献为 `1/log2(i+2)`（`i` 为 0 起算的下标，等价于名次 `rank=i+1` 时的 `1/log2(rank+1)`）；IDCG 假定在**当前 `topK` 长度**内把至多 `min(\|G\|, \|topK\|)` 个相关项排在最前。 |

`evidences` 仍为检索器返回的**前 `k` 条 chunk**（未按 fileName 去重），便于人读；**上述五个指标只依赖去重后的 `topK` 与 `goldDocs`**。语义是否「答对了题」请看 `dp.py` 的 LLM 汇总，不在本 JSON 里推断。

---

## 三、LLM 裁判（`dp.py`）

依赖：

```bash
pip install "openai>=1.0"
```

配置：复制 `eval/.env.example` 为 `eval/.env`，填写 **`DEEPSEEK_API_KEY`**；可选用 `DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL`。已存在的环境变量**不会被** `.env` 覆盖。

在仓库根目录：

```bash
python eval/dp.py
```

更全参数：

```bash
python eval/dp.py --help
```

### 3.1 与代码一致的默认值

| 项 | 默认值 |
|----|--------|
| 输入报告 | `./eval/reports/rag-eval-result.json` |
| 输出目录 | `./eval/runs/` |
| API | `https://api.deepseek.com`，模型 `deepseek-chat`（可用环境变量或 `--model` 覆盖，如 `deepseek-v4-flash`） |
| `--batch-size` | `20`：每批合并一次 `chat.completions`；整批解析失败时**仅该批**回退为逐条请求 |
| `--delay` | `0.5`：每批（或 `--batch-size 1` 时每条）结束后的额外等待（秒），可设 `0` |
| 重试 | 单次请求失败最多 3 次，指数退避 |

### 3.2 输出文件

| 文件 | 说明 |
|------|------|
| `eval_llm_verdicts_<时间>.jsonl` | 每行一条：含 `id`、`query`、裁判字段（`sufficient` / `score` / `useful_ranks` / `noise_ranks` / `missing`）、`evidence_count`、按条汇总的 `useful_evidence_count` / `noise_evidence_count` 等 |
| `eval_llm_aggregate_<时间>.json` | `run`（含 `report_top_k`、`batch_size`、`report_source`、`slice` 等）+ `metrics`（`llm_relevance_hit_rate_at_k`、命中用例内 **有效/噪音/未标注** 条数与占比等） |

`--write-full-detail` 会额外生成 `eval_detail_<时间>.json`（含 `retrieved_evidence` 与系统侧字段），体积大。

### 3.3 批量请求与「像卡住」

首批 **20 条 × 全量 snippet** 时，一次 HTTP 可能持续 **数分钟**；期间若已将 `httpx` 日志压到 WARNING，控制台可能长时间只有「批次 x/y」类日志，属正常。可调小 `--batch-size` 以换更短单次请求；试跑可用 `--end 5`。

行与 `PS` 提示符粘连时，可使用 `python -u eval/dp.py` 减少缓冲。

### 3.4 Windows 下控制台中文乱码（IDE 正常、命令行乱码）

**原因**：IDE 里跑 Spring Boot 时，内置终端和 JVM 输出往往按 **UTF-8** 显示；你在 **系统 PowerShell / CMD** 里跑 `python eval/dp.py` 或 `java …` 时，默认代码页常见为 **GBK（936）**，而日志/中文是 **UTF-8** 编码的字节流，控制台按 GBK 解码就会乱码。**写到磁盘的 JSON/JSONL 仍是 UTF-8**，一般只有「屏幕显示」有问题。

**可先在同一窗口执行再跑命令**（PowerShell 示例）：

```powershell
chcp 65001
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONUTF8 = "1"
# Java / Maven 离线评测（JDK 18+ 可显式指定控制台编码）
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
```

然后再执行 `python eval/dp.py` 或 `mvn … spring-boot:run` / `java -jar …`。长期可在 **Windows 终端** 里把该 profile 设为默认 UTF-8，或继续用 **IDE 终端** 跑脚本。

---

## 四、如何读分

**Spring 报告（客观）**

- **Recall@k / precisionAtK / MRR / nDCG@k**：均在 **去重后的 fileName 短名单** 上按「二、2.3」表格计算；**不**表示 chunk 级或语义正确率。  
- **Recall@k**：金标集合中有多少比例在短名单里出现（多金标时为分数，单金标时为 0 或 1）。  
- **MRR**：第一个金标 fileName 在短名单中的位置倒数。  

**`dp.py` 汇总（主观）**

- **`llm_relevance_hit_rate` / `llm_relevance_hit_rate_at_k`**：`score ≥ 1` 的用例占比；`run.report_top_k` 与 Spring 报告顶层 `k` 一致（检索条数上限）。  
- **`within_hit_cases_*`**：仅在 `score ≥ 1` 的用例上，把每条检索片段的 Rank 归入 **有效**（`useful_ranks`）或 **噪音**（`noise_ranks`）；`useful_ratio_among_hit_evidence_slots` 与 `noise_ratio_among_hit_evidence_slots` 的分母为这些用例的检索槽位总数。若模型未输出 `noise_ranks`（旧版），则对「非 useful 的 Rank」**推断为噪音**（见 `hit_cases_with_legacy_inferred_noise`）。  
- **`sufficiency_rate`**：`sufficient == "yes"` 的比例。  
- **`avg_score`**：0～3 支持度均值。  

二者互补：Spring 看「金标文件名是否进榜」，`dp.py` 看「片段是否支撑回答」及「命中用例里有多少检索噪音」。

---

## 五、`tools/`

手册等文本的预处理脚本（空行、章节、附件等），与评测流水线无强耦合；按需单独执行。
