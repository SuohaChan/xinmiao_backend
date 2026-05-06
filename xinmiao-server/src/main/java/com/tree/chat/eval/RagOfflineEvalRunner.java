package com.tree.chat.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tree.chat.infrastructure.rag.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 离线 RAG 评测入口：
 * - 读取评测用例 JSONL（路径见 {@code rag.eval.file}）
 * - 对每条 query 跑线上同款检索（RagService.retrieveDocuments）
 * - 计算 Recall@k / Precision@k / MRR / nDCG（binary relevance）
 *
 * 启动参数：
 * --rag.eval.enabled=true
 * 其余路径、k、exit 等默认见 {@code application.yml} 顶层 {@code rag.eval.*}（可用命令行覆盖）
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.eval.enabled", havingValue = "true")
public class RagOfflineEvalRunner implements CommandLineRunner {

    private final RagService ragService;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final ConfigurableApplicationContext applicationContext;

    public RagOfflineEvalRunner(
            RagService ragService,
            ObjectMapper objectMapper,
            Environment environment,
            ConfigurableApplicationContext applicationContext) {
        this.ragService = ragService;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) throws Exception {
        String file = environment.getRequiredProperty("rag.eval.file");
        int k = Integer.parseInt(environment.getRequiredProperty("rag.eval.k").trim());
        k = Math.max(1, Math.min(50, k));
        String outFile = environment.getRequiredProperty("rag.eval.out");
        int splitSize = Integer.parseInt(environment.getRequiredProperty("rag.eval.split").trim());
        if (splitSize < 0) {
            splitSize = 0;
        }
        boolean exitAfterEval = Boolean.parseBoolean(environment.getRequiredProperty("rag.eval.exit").trim());
        int snippetMaxChars = Integer.parseInt(environment.getRequiredProperty("rag.eval.snippet-max-chars").trim());
        if (snippetMaxChars < 0) {
            snippetMaxChars = 0;
        }

        Path path = Path.of(file);
        if (!Files.exists(path)) {
            log.error(
                    "RAG 离线评测文件不存在：{}（不会写入 {}）。"
                            + "请检查 rag.eval.file 与当前工作目录；用例路径默认在 application.yml 的 rag.eval 下配置。",
                    path.toAbsolutePath(),
                    outFile);
            return;
        }

        List<EvalCase> cases = loadCases(path);
        if (cases.isEmpty()) {
            log.warn("RAG 离线评测用例为空：{}（无有效 query+gold_docs 行，不会写入 {}）",
                    path.toAbsolutePath(), outFile);
            return;
        }

        log.info("RAG 离线评测开始：cases={} k={} snippetMaxChars={} file={}",
                cases.size(), k, snippetMaxChars <= 0 ? "off" : snippetMaxChars, path.toAbsolutePath());

        List<EvalResult> results = new ArrayList<>(cases.size());
        for (EvalCase c : cases) {
            EvalResult r = evalOne(c, k, snippetMaxChars);
            results.add(r);
            log.info("[RAG EVAL] id={} recall@{}={} mrr={} ndcg@{}={} hits={}/{} topK={}",
                    c.id(),
                    k, fmt(r.recallAtK()),
                    fmt(r.mrr()),
                    k, fmt(r.ndcgAtK()),
                    r.hits(), c.goldDocs().size(),
                    r.topK());
        }

        Summary s = summarize(results);
        log.info("RAG 离线评测完成（macro average）：cases={} k={}", results.size(), k);
        log.info("Recall@{}={} Precision@{}={} MRR={} nDCG@{}={}",
                k, fmt(s.avgRecallAtK()),
                k, fmt(s.avgPrecisionAtK()),
                fmt(s.avgMrr()),
                k, fmt(s.avgNdcgAtK()));

        writeReport(path, k, results, s, outFile, splitSize, snippetMaxChars);

        if (exitAfterEval) {
            log.info("RAG 离线评测已完成，准备退出（rag.eval.exit=true）");
            int code = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(code);
        }
    }

    private void writeReport(Path evalFile,
            int k,
            List<EvalResult> results,
            Summary summary,
            String outFile,
            int splitSize,
            int snippetMaxChars) {
        try {
            Path outPath = Path.of(outFile);
            Path parent = outPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (splitSize <= 0 || results.size() <= splitSize) {
                Report report = new Report(
                        System.currentTimeMillis(),
                        evalFile.toAbsolutePath().toString(),
                        k,
                        results.size(),
                        results,
                        summary,
                        snippetMaxChars);
                byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
                Files.write(outPath, bytes);
                log.info("RAG 离线评测结果已写入：{}", outPath.toAbsolutePath());
                return;
            }

            // 分片输出：每份 splitSize 条 results，summary 也按分片单独计算，便于对比
            String fileName = outPath.getFileName() != null ? outPath.getFileName().toString() : "rag-eval-result.json";
            String baseName = fileName;
            String ext = "";
            int dot = fileName.lastIndexOf('.');
            if (dot > 0) {
                baseName = fileName.substring(0, dot);
                ext = fileName.substring(dot);
            }

            int total = results.size();
            int parts = (total + splitSize - 1) / splitSize;
            for (int part = 0; part < parts; part++) {
                int from = part * splitSize;
                int to = Math.min(total, from + splitSize);
                List<EvalResult> slice = results.subList(from, to);
                Summary sliceSummary = summarize(slice);
                Report report = new Report(
                        System.currentTimeMillis(),
                        evalFile.toAbsolutePath().toString(),
                        k,
                        slice.size(),
                        slice,
                        sliceSummary,
                        snippetMaxChars);

                String partName = String.format("%s.part-%02d%s", baseName, part + 1, ext.isEmpty() ? ".json" : ext);
                Path partPath = (parent != null ? parent.resolve(partName) : Path.of(partName));
                byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
                Files.write(partPath, bytes);
                log.info("RAG 离线评测分片结果已写入：{}（{}-{} / {}）",
                        partPath.toAbsolutePath(), from + 1, to, total);
            }
        } catch (Exception e) {
            log.warn("写入 RAG 离线评测结果失败 outFile={}", outFile, e);
        }
    }

    private List<EvalCase> loadCases(Path path) throws Exception {
        List<EvalCase> out = new ArrayList<>();
        int lineNo = 0;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            lineNo++;
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = objectMapper.readValue(trimmed, Map.class);
                String id = Objects.toString(m.getOrDefault("id", "line-" + lineNo));
                String query = Objects.toString(m.get("query"), "").trim();
                if (query.isEmpty()) {
                    continue;
                }
                Object gold = m.get("gold_docs");
                List<String> goldDocs = toStringList(gold);
                if (goldDocs.isEmpty()) {
                    continue;
                }
                out.add(new EvalCase(id, query, goldDocs));
            } catch (Exception e) {
                log.warn("解析评测 JSONL 行失败，跳过该行 lineNo={} content={}", lineNo, trimmed, e);
            }
        }
        return out;
    }

    private static List<String> toStringList(Object v) {
        if (v == null) {
            return List.of();
        }
        if (v instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return List.of();
        }
        return List.of(s);
    }

    private EvalResult evalOne(EvalCase c, int k, int snippetMaxChars) {
        // 线上同款检索（阻塞）
        var docs = ragService.retrieveDocuments(c.query());

        // 文档 id 约定：metadata.fileName（入库时写入）
        List<String> topK = docs.stream()
                .map(d -> d.getMetadata().get("fileName"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        // 去重保序（避免同一 fileName 多个 chunk 影响指标）
        Set<String> dedup = new LinkedHashSet<>(topK);
        topK = dedup.stream().limit(k).toList();

        Set<String> gold = new LinkedHashSet<>(c.goldDocs());

        int hits = 0;
        for (String docId : topK) {
            if (gold.contains(docId)) {
                hits++;
            }
        }

        // 金标与检索结果均在「metadata.fileName」字符串集合上比较；无语义判断。
        double recall = gold.isEmpty() ? 0.0 : (double) hits / (double) gold.size();
        // 去重后的 fileName 短名单长度常 < k；分母用实际短名单长度，避免 hits/k 被低估。
        double precision = topK.isEmpty() ? 0.0 : (double) hits / (double) topK.size();
        double mrr = calcMrr(topK, gold);
        double ndcg = calcNdcg(topK, gold);

        List<EvalEvidence> evidences = toTopKEvidences(docs, k, snippetMaxChars);
        return new EvalResult(
                c.id(), c.query(), c.goldDocs(), topK, evidences, hits, recall, precision, mrr, ndcg);
    }

    private static List<EvalEvidence> toTopKEvidences(
            List<org.springframework.ai.document.Document> docs, int k, int snippetMaxChars) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(k, docs.size());
        List<EvalEvidence> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            var d = docs.get(i);
            if (d == null) {
                continue;
            }
            Map<String, Object> md = d.getMetadata() != null ? d.getMetadata() : Map.of();
            String fileName = md.get("fileName") != null ? md.get("fileName").toString() : null;
            String chunkId = md.get("chunkId") != null ? md.get("chunkId").toString() : null;
            Integer chunkIndex = null;
            Object ci = md.get("chunkIndex");
            if (ci instanceof Number n) {
                chunkIndex = n.intValue();
            } else if (ci != null) {
                try {
                    chunkIndex = Integer.parseInt(ci.toString());
                } catch (Exception ignored) {
                    chunkIndex = null;
                }
            }
            // snippet：写入评测报告；默认不截断（与向量库 chunk 一致）。rag.eval.snippet-max-chars>0 时按字符数上限截断以缩小
            // JSON。
            String text = d.getText();
            String snippet = text == null ? "" : text.strip();
            if (snippetMaxChars > 0 && snippet.length() > snippetMaxChars) {
                snippet = snippet.substring(0, snippetMaxChars);
            }
            // 只保留少量 metadata 方便对比，不把整份 metadata 塞进报告
            Map<String, Object> kept = new LinkedHashMap<>();
            if (fileName != null)
                kept.put("fileName", fileName);
            if (chunkId != null)
                kept.put("chunkId", chunkId);
            if (chunkIndex != null)
                kept.put("chunkIndex", chunkIndex);
            out.add(new EvalEvidence(i + 1, kept, snippet));
        }
        return out;
    }

    private static double calcMrr(List<String> ranked, Set<String> gold) {
        for (int i = 0; i < ranked.size(); i++) {
            if (gold.contains(ranked.get(i))) {
                return 1.0 / (double) (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * 二元相关 nDCG：ranked 为去重后的 fileName 顺序列表（长度 ≤ k）。
     * IDCG 按「当前列表长度」内可排下的相关项数计算，避免 ranked 短于 k 时 IDCG 虚高。
     */
    private static double calcNdcg(List<String> ranked, Set<String> gold) {
        int len = ranked.size();
        if (len == 0) {
            return 0.0;
        }
        double dcg = 0.0;
        for (int i = 0; i < len; i++) {
            if (!gold.contains(ranked.get(i))) {
                continue;
            }
            dcg += 1.0 / log2(i + 2);
        }
        int idealRel = Math.min(gold.size(), len);
        double idcg = 0.0;
        for (int i = 0; i < idealRel; i++) {
            idcg += 1.0 / log2(i + 2);
        }
        return idcg == 0.0 ? 0.0 : (dcg / idcg);
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2.0);
    }

    private static Summary summarize(List<EvalResult> results) {
        double r = 0, p = 0, m = 0, n = 0;
        for (EvalResult e : results) {
            r += e.recallAtK();
            p += e.precisionAtK();
            m += e.mrr();
            n += e.ndcgAtK();
        }
        int size = Math.max(1, results.size());
        return new Summary(r / size, p / size, m / size, n / size);
    }

    private static String fmt(double v) {
        return String.format("%.4f", v);
    }

    private record EvalCase(String id, String query, List<String> goldDocs) {
    }

    private record EvalEvidence(int rank, Map<String, Object> metadata, String snippet) {
    }

    private record EvalResult(
            String id,
            String query,
            List<String> goldDocs,
            List<String> topK,
            List<EvalEvidence> evidences,
            int hits,
            double recallAtK,
            double precisionAtK,
            double mrr,
            double ndcgAtK) {
    }

    private record Summary(double avgRecallAtK, double avgPrecisionAtK, double avgMrr, double avgNdcgAtK) {
    }

    private record Report(
            long finishedAtEpochMillis,
            String evalFile,
            int k,
            int cases,
            List<EvalResult> results,
            Summary summary,
            int snippetMaxChars // 写入每条 evidence.snippet 的最大字符数；0 表示不截断
    ) {
    }
}
