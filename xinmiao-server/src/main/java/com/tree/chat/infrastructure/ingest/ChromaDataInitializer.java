package com.tree.chat.infrastructure.ingest;

import com.tree.chat.infrastructure.splitter.nocode.NocodeSplitStrategyRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 向量库数据初始化：启动时将 nocode/ 下的文档分块后写入 Chroma。
 * <p>
 * 防重复机制：对所有源文件内容计算指纹（SHA-256），存入 Redis。
 * 启动时比对指纹——相同则跳过，不同说明文档有更新，需要重新入库。
 * <p>
 * 需要强制重建时：删除 Redis key「chroma:init:fingerprint」后重启（启动时会自动清空与本批 nocode 文档相关的旧向量再写入）。
 * <p>
 * 查看纯切分文本：将本类日志级别设为 DEBUG（例如 logging.level.com.tree.chat.infrastructure.ingest.ChromaDataInitializer=DEBUG），
 * 仅在重新入库（指纹变化）时会在控制台打印每个 chunk 的全文。
 */
@Slf4j
@Component
public class ChromaDataInitializer {

    private static final String INIT_FINGERPRINT_KEY = "chroma:init:fingerprint";

    /**
     * 与本初始化器写入的 chunk 元数据一致：命中即视为历史 nocode 索引，便于在重新入库前整体删除，避免旧切分结果残留。
     */
    private static final Filter.Expression CLEAR_NO_CODE_INDEXED =
            new FilterExpressionTextParser().parse(
                    "where fileName != '' || chunkIndex >= 0 || chunkId != ''");

    private final VectorStore vectorStore;
    private final StringRedisTemplate redisTemplate;
    private final NocodeSplitStrategyRegistry nocodeSplitRegistry;

    public ChromaDataInitializer(VectorStore vectorStore,
                               StringRedisTemplate redisTemplate,
                               NocodeSplitStrategyRegistry nocodeSplitRegistry) {
        this.vectorStore = vectorStore;
        this.redisTemplate = redisTemplate;
        this.nocodeSplitRegistry = nocodeSplitRegistry;
    }

    @PostConstruct
    public void init() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            Resource[] resources = resolver.getResources("classpath:nocode/*.txt");
            if (resources.length == 0) {
                log.warn("未找到classpath:nocode/目录下的txt文件");
                return;
            }

            String fingerprint = computeFingerprint(resources);
            String stored = redisTemplate.opsForValue().get(INIT_FINGERPRINT_KEY);
            if (fingerprint.equals(stored)) {
                log.info("向量库指纹一致（{}），跳过初始化", fingerprint.substring(0, 8));
                return;
            }

            log.info("向量库指纹变化，开始重新入库（旧={} 新={}）",
                    stored != null ? stored.substring(0, 8) : "无",
                    fingerprint.substring(0, 8));

            try {
                vectorStore.delete(CLEAR_NO_CODE_INDEXED);
                log.info("已删除向量库中与本应用 nocode 索引相关的旧数据");
            } catch (Exception e) {
                log.warn("删除旧向量时出现异常（例如集合尚空或后端暂不支持该过滤条件），将继续写入新数据", e);
            }

            int totalChunks = 0;
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                try {
                    TextSplitter textSplitter = nocodeSplitRegistry.resolve(fileName);
                    String content = Files.readString(Path.of(resource.getURI()));
                    Document originalDoc = new Document(content, Map.of("fileName", fileName));
                    List<Document> splitDocs = textSplitter.split(originalDoc);
                    // 为每个 chunk 补充可追溯的证据元数据：chunkIndex/chunkId
                    for (int i = 0; i < splitDocs.size(); i++) {
                        Document d = splitDocs.get(i);
                        if (d == null) {
                            continue;
                        }
                        d.getMetadata().put("chunkIndex", i);
                        d.getMetadata().put("chunkId", fileName + ":" + i);
                        if (log.isDebugEnabled()) {
                            String t = d.getText();
                            log.debug("[切分] {} | chunk {} | {} 字\n{}", fileName, i,
                                    t != null ? t.length() : 0, t);
                        }
                    }
                    vectorStore.add(splitDocs);
                    totalChunks += splitDocs.size();
                    log.info("文档 {} 入库完成（{}个分片）", fileName, splitDocs.size());
                } catch (IOException e) {
                    log.error("加载/拆分文件 {} 失败", fileName, e);
                }
            }

            redisTemplate.opsForValue().set(INIT_FINGERPRINT_KEY, fingerprint);
            log.info("向量库初始化完成，共 {} 个文档、{} 个分片，指纹已记录",
                    resources.length, totalChunks);
        } catch (Exception e) {
            log.error("向量库初始化失败", e);
        }
    }

    /**
     * 对所有源文件内容拼接后取 SHA-256，作为数据指纹。
     * 文件内容或文件数量变化时指纹自动改变，触发重新入库。
     */
    private String computeFingerprint(Resource[] resources) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Arrays.sort(resources, (a, b) -> {
                String na = String.valueOf(a.getFilename());
                String nb = String.valueOf(b.getFilename());
                return na.compareTo(nb);
            });
            for (Resource resource : resources) {
                String content = Files.readString(Path.of(resource.getURI()));
                digest.update(content.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            log.warn("指纹计算失败，将强制重新入库", e);
            return "force-" + System.currentTimeMillis();
        }
    }
}
