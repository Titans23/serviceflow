package com.serviceflow.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.serviceflow.ai.AiGateway;
import com.serviceflow.config.ServiceFlowProperties;
import com.serviceflow.mapper.KnowledgeMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MilvusRagService implements RagService {

    private static final Logger log = LoggerFactory.getLogger(MilvusRagService.class);
    private static final int RETRIEVAL_LIMIT = 20;
    private static final int RERANK_LIMIT = 5;

    private final ServiceFlowProperties properties;
    private final EmbeddingClient embeddingClient;
    private final RerankerClient rerankerClient;
    private final RestClient milvusClient;
    private final KnowledgeMapper knowledgeMapper;
    private final AiGateway aiGateway;
    private final MeterRegistry metrics;
    private final AtomicBoolean collectionReady = new AtomicBoolean();

    public MilvusRagService(
            ServiceFlowProperties properties,
            EmbeddingClient embeddingClient,
            RerankerClient rerankerClient,
            KnowledgeMapper knowledgeMapper,
            AiGateway aiGateway,
            MeterRegistry metrics) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embeddingClient must not be null");
        this.rerankerClient = Objects.requireNonNull(rerankerClient, "rerankerClient must not be null");
        this.knowledgeMapper = Objects.requireNonNull(knowledgeMapper, "knowledgeMapper must not be null");
        this.aiGateway = Objects.requireNonNull(aiGateway, "aiGateway must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.rag().connectTimeout());
        requestFactory.setReadTimeout(properties.rag().readTimeout());
        this.milvusClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.rag().milvusRestUrl())
                .build();
    }

    @Override
    @CircuitBreaker(name = "milvus")
    @Bulkhead(name = "milvus", type = Bulkhead.Type.SEMAPHORE)
    public SearchResult search(String query, String documentType, List<Long> productIds) {
        long started = System.nanoTime();
        if (isDemoMode()) {
            return demo(query, documentType, productIds);
        }

        ensureCloudReady();
        try {
            List<Long> activeVersions = knowledgeMapper.activeVersionIds(documentType, productIds);
            if (activeVersions.isEmpty()) {
                return new SearchResult(List.of(), false, false);
            }

            RankedEvidence ranked = rerank(query, hybrid(query, documentType, productIds, activeVersions));
            EvaluationResult evaluated = evaluate(query, ranked.evidence());
            // 首轮证据不足时只进行一次查询改写与重试，避免无限检索循环。
            if (!evaluated.sufficient()) {
                evaluated = evaluateRewrittenQuery(query, documentType, productIds, activeVersions, evaluated);
            }

            SearchResult result = new SearchResult(
                    evaluated.evidence(), evaluated.sufficient(), ranked.degraded() || evaluated.degraded());
            metrics.counter("serviceflow.rag.search", "status", "success").increment();
            if (result.degraded()) {
                metrics.counter("serviceflow.rag.degraded").increment();
            }
            metrics.timer("serviceflow.rag.search.duration")
                    .record(java.time.Duration.ofNanos(System.nanoTime() - started));
            return result;
        } catch (Exception exception) {
            log.warn("Milvus hybrid search unavailable; returning no evidence", exception);
            metrics.counter("serviceflow.rag.search", "status", "failed").increment();
            metrics.counter("serviceflow.rag.degraded").increment();
            metrics.timer("serviceflow.rag.search.duration")
                    .record(java.time.Duration.ofNanos(System.nanoTime() - started));
            return new SearchResult(List.of(), false, true);
        }
    }

    private EvaluationResult evaluateRewrittenQuery(
            String query,
            String documentType,
            List<Long> productIds,
            List<Long> activeVersions,
            EvaluationResult current) {
        try {
            // 让模型把口语或模糊问题改写成更适合检索、且保留关键实体的一句话。
            String rewrittenQuery = aiGateway.rewriteQuery(query);
            // 改写结果没有变化时，重复检索没有意义，直接保留首轮结果。
            if (rewrittenQuery.equalsIgnoreCase(query)) {
                return current;
            }

            // 使用改写后的问题完整重跑混合召回和重排，而不是只重新执行 Grader。
            RankedEvidence rewrittenRanked =
                    rerank(rewrittenQuery, hybrid(rewrittenQuery, documentType, productIds, activeVersions));
            EvaluationResult rewrittenEvaluation = evaluate(rewrittenQuery, rewrittenRanked.evidence());
            return new EvaluationResult(
                    rewrittenEvaluation.evidence(),
                    rewrittenEvaluation.sufficient(),
                    rewrittenRanked.degraded() || rewrittenEvaluation.degraded());
        } catch (Exception exception) {
            // 改写或第二轮检索失败时保留首轮证据，同时标记为不足且已降级。
            log.warn("Query rewrite failed; keeping original evidence", exception);
            return new EvaluationResult(current.evidence(), false, true);
        }
    }

    private List<Evidence> hybrid(String query, String documentType, List<Long> productIds, List<Long> activeVersions) {
        // 稠密检索：先把用户问题 Embedding 成向量，用于匹配语义相近的切片。
        float[] vector = embed(query);
        String filter = "documentType == \"" + documentType + "\" and documentVersionId in " + activeVersions;
        if (productIds != null && !productIds.isEmpty()) {
            filter += " and productId in " + productIds;
        }

        Map<String, Object> denseSearch = Map.of(
                "data",
                List.of(vector),
                "annsField",
                "denseVector",
                "limit",
                RETRIEVAL_LIMIT,
                "filter",
                filter,
                "searchParams",
                Map.of("metricType", "COSINE", "params", Map.of("ef", 64)));
        // 稀疏检索：把原始问题交给 Milvus 的 BM25 分词和关键词权重计算，匹配精确词语。
        Map<String, Object> sparseSearch =
                Map.of("data", List.of(query), "annsField", "sparseVector", "limit", RETRIEVAL_LIMIT, "filter", filter);
        // 一次 hybrid_search 同时执行两路检索，再通过 RRF 融合两份候选排名。
        Map<String, Object> request = Map.of(
                "collectionName", properties.rag().collectionName(),
                "search", List.of(denseSearch, sparseSearch),
                "rerank", Map.of("strategy", "rrf", "params", Map.of("k", 60)),
                "limit", RETRIEVAL_LIMIT,
                "outputFields", List.of("chunkId", "title", "source", "content"));

        JsonNode response = milvusClient
                .post()
                .uri("/v2/vectordb/entities/hybrid_search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        requireMilvusSuccess(response, "hybrid search");

        List<Evidence> evidence = new ArrayList<>();
        if (response != null && response.path("data").isArray()) {
            for (JsonNode row : response.path("data")) {
                evidence.add(new Evidence(
                        row.path("chunkId").asText(),
                        row.path("title").asText(),
                        row.path("source").asText(),
                        row.path("content").asText(),
                        row.path("distance").asDouble()));
            }
        }
        return evidence;
    }

    private RankedEvidence rerank(String query, List<Evidence> candidates) {
        // 没有召回候选时无需调用外部重排模型。
        if (candidates.isEmpty()) {
            return new RankedEvidence(List.of(), false);
        }

        try {
            // 重排模型直接比较“用户问题”和每个候选分块的原文，比只依赖召回排名判断得更细。
            List<RerankerClient.Document> documents = candidates.stream()
                    .map(item -> new RerankerClient.Document(item.chunkId(), item.content()))
                    .toList();
            List<String> ids = rerankerClient.rank(query, documents, RERANK_LIMIT);
            // Reranker 只返回有序分块 ID；这里再映射回包含标题、来源和原文的完整证据对象。
            Map<String, Evidence> byId = new LinkedHashMap<>();
            candidates.forEach(item -> byId.put(item.chunkId(), item));
            List<Evidence> ranked = ids.stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .limit(RERANK_LIMIT)
                    .toList();
            if (ranked.isEmpty()) {
                throw new IllegalStateException("Reranker returned no matching evidence");
            }
            return new RankedEvidence(ranked, false);
        } catch (Exception exception) {
            // 重排是提升相关性的优化步骤；外部服务失败时退回 RRF 前五，避免整个问答不可用。
            log.warn("Reranker unavailable; using RRF top {}", RERANK_LIMIT, exception);
            metrics.counter("serviceflow.rag.reranker.degraded").increment();
            return new RankedEvidence(candidates.stream().limit(RERANK_LIMIT).toList(), true);
        }
    }

    private EvaluationResult evaluate(String query, List<Evidence> candidates) {
        if (candidates.isEmpty()) {
            return new EvaluationResult(List.of(), false, false);
        }

        try {
            // Grader 再检查重排后的分块能否直接支持答案，并可过滤掉仍然无关的证据。
            AiGateway.EvidenceEvaluation evaluation = aiGateway.evaluateEvidence(
                    query,
                    candidates.stream()
                            .map(item -> new AiGateway.EvidenceCandidate(item.chunkId(), item.title(), item.content()))
                            .toList());
            Map<String, Evidence> byId = new LinkedHashMap<>();
            candidates.forEach(item -> byId.put(item.chunkId(), item));
            List<Evidence> ranked = evaluation.rankedChunkIds().stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .limit(RERANK_LIMIT)
                    .toList();
            return new EvaluationResult(ranked, evaluation.sufficient(), false);
        } catch (Exception exception) {
            // 判定器故障时保留已有证据，但将 sufficient=false，交给上层尝试改写查询后再次检索。
            log.warn("Evidence grader unavailable; using reranked evidence", exception);
            metrics.counter("serviceflow.rag.grader.degraded").increment();
            return new EvaluationResult(candidates.stream().limit(RERANK_LIMIT).toList(), false, true);
        }
    }

    @Override
    @CircuitBreaker(name = "milvus")
    @Bulkhead(name = "milvus", type = Bulkhead.Type.SEMAPHORE)
    public void ingest(Chunk chunk) {
        // demo 模式使用内置示例数据，不连接真实的 Embedding 服务和 Milvus。
        if (isDemoMode()) {
            return;
        }

        ensureCloudReady();
        // 一条 Milvus 实体同时保存可追溯的原文/元数据，以及用于语义检索的稠密向量。
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("chunkId", chunk.chunkId());
        entity.put("documentId", chunk.documentId());
        entity.put("documentVersionId", chunk.documentVersionId());
        entity.put("documentType", chunk.documentType());
        entity.put("productId", chunk.productId() == null ? -1 : chunk.productId());
        entity.put("title", chunk.title());
        entity.put("category", chunk.category());
        entity.put("source", chunk.source());
        entity.put("content", chunk.content());
        // 入库阶段把切片转换为向量；查询阶段会用同一套 Embedding 逻辑转换用户问题。
        entity.put("denseVector", embed(chunk.content()));

        // 调用 Milvus REST API 后才产生真正的向量库写入副作用。
        JsonNode response = milvusClient
                .post()
                .uri("/v2/vectordb/entities/insert")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("collectionName", properties.rag().collectionName(), "data", List.of(entity)))
                .retrieve()
                .body(JsonNode.class);
        requireMilvusSuccess(response, "insert");
    }

    @Override
    @CircuitBreaker(name = "milvus")
    @Bulkhead(name = "milvus", type = Bulkhead.Type.SEMAPHORE)
    public void deleteVersion(long documentVersionId) {
        if (isDemoMode()) {
            return;
        }

        JsonNode response = milvusClient
                .post()
                .uri("/v2/vectordb/entities/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "collectionName",
                        properties.rag().collectionName(),
                        "filter",
                        "documentVersionId == " + documentVersionId))
                .retrieve()
                .body(JsonNode.class);
        requireMilvusSuccess(response, "delete version");
    }

    private boolean isDemoMode() {
        return "demo".equalsIgnoreCase(properties.rag().mode());
    }

    private float[] embed(String text) {
        float[] vector = embeddingClient.embed(text);
        int expectedDimension = properties.rag().embeddingDimension();
        // Milvus 的 FloatVector 字段维度固定；配置或模型不匹配时应在写入/检索前明确失败。
        if (vector.length != expectedDimension) {
            throw new IllegalStateException(
                    "Embedding dimension mismatch: expected " + expectedDimension + ", got " + vector.length);
        }
        return vector;
    }

    private void ensureCloudReady() {
        if (collectionReady.compareAndSet(false, true)) {
            try {
                if (!collectionExists()) {
                    createCollection();
                }
            } catch (RuntimeException exception) {
                collectionReady.set(false);
                throw exception;
            }
        }
    }

    private boolean collectionExists() {
        JsonNode response = milvusClient
                .post()
                .uri("/v2/vectordb/collections/has")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("collectionName", properties.rag().collectionName()))
                .retrieve()
                .body(JsonNode.class);
        requireMilvusSuccess(response, "check collection");
        return response.path("data").path("has").asBoolean(false);
    }

    private void createCollection() {
        try {
            List<Map<String, Object>> fields = List.of(
                    field("chunkId", "VarChar", true, Map.of("max_length", 64)),
                    field("documentId", "Int64", false, Map.of()),
                    field("documentVersionId", "Int64", false, Map.of()),
                    field("documentType", "VarChar", false, Map.of("max_length", 32)),
                    field("productId", "Int64", false, Map.of()),
                    field("title", "VarChar", false, Map.of("max_length", 512)),
                    field("category", "VarChar", false, Map.of("max_length", 128)),
                    field("source", "VarChar", false, Map.of("max_length", 512)),
                    field("content", "VarChar", false, Map.of("max_length", 8192, "enable_analyzer", true)),
                    field(
                            "denseVector",
                            "FloatVector",
                            false,
                            Map.of("dim", properties.rag().embeddingDimension())),
                    field("sparseVector", "SparseFloatVector", false, Map.of()));
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("autoId", false);
            schema.put("enableDynamicField", false);
            schema.put("fields", fields);
            // Milvus 在写入 content 时自动运行 BM25，将关键词及其权重写入 sparseVector；
            // 因此 ingest() 不需要像 denseVector 一样在 Java 中显式计算稀疏向量。
            schema.put(
                    "functions",
                    List.of(Map.of(
                            "name",
                            "content_bm25",
                            "type",
                            "BM25",
                            "inputFieldNames",
                            List.of("content"),
                            "outputFieldNames",
                            List.of("sparseVector"))));
            List<Map<String, Object>> indexes = List.of(
                    Map.of(
                            "fieldName",
                            "denseVector",
                            "indexName",
                            "dense_hnsw",
                            "metricType",
                            "COSINE",
                            "indexType",
                            "HNSW",
                            "params",
                            Map.of("M", 16, "efConstruction", 200)),
                    Map.of(
                            "fieldName",
                            "sparseVector",
                            "indexName",
                            "sparse_bm25",
                            "metricType",
                            "BM25",
                            "indexType",
                            "SPARSE_INVERTED_INDEX"));

            JsonNode response = milvusClient
                    .post()
                    .uri("/v2/vectordb/collections/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "collectionName", properties.rag().collectionName(),
                            "schema", schema,
                            "indexParams", indexes))
                    .retrieve()
                    .body(JsonNode.class);
            requireMilvusSuccess(response, "create collection");
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Cannot create Milvus collection", exception);
        }
    }

    private void requireMilvusSuccess(JsonNode response, String operation) {
        int code = response == null ? -1 : response.path("code").asInt(-1);
        if (code != 0) {
            String message = response == null
                    ? "empty response"
                    : response.path("message").asText("unknown error");
            throw new IllegalStateException("Milvus " + operation + " failed (code " + code + "): " + message);
        }
    }

    private Map<String, Object> field(String name, String dataType, boolean primary, Map<String, Object> params) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldName", name);
        field.put("dataType", dataType);
        field.put("isPrimary", primary);
        field.put("elementTypeParams", params);
        return field;
    }

    private SearchResult demo(String query, String documentType, List<Long> productIds) {
        if ("PRODUCT_MANUAL".equals(documentType) && productIds != null && !productIds.isEmpty()) {
            return new SearchResult(
                    List.of(new Evidence(
                            "demo-product", "商品使用说明", "demo", "商品应使用原装配件，并在说明书规定的温度与湿度范围内使用；标准保修期为一年。", 1)),
                    true,
                    false);
        }
        if (query.contains("退") || query.contains("换") || query.contains("保修")) {
            return new SearchResult(
                    List.of(new Evidence(
                            "demo-policy",
                            "售后政策",
                            "demo",
                            "符合完好条件的商品可在签收后七天内申请退货；质量问题按保修政策处理。退款在审核完成后通常 3 至 5 个工作日原路退回。",
                            1)),
                    true,
                    false);
        }
        return new SearchResult(List.of(), false, false);
    }

    private record RankedEvidence(List<Evidence> evidence, boolean degraded) {}

    private record EvaluationResult(List<Evidence> evidence, boolean sufficient, boolean degraded) {}
}
