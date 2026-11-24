package com.serviceflow.agent;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.ai.AiGateway;
import com.serviceflow.ai.AiGateway.ProductReference;
import com.serviceflow.ai.AiGateway.ProductRouting;
import com.serviceflow.ai.AiGateway.ProductTarget;
import com.serviceflow.infrastructure.redis.ChatMemory;
import com.serviceflow.model.ChatActionModels;
import com.serviceflow.model.ChatModels;
import com.serviceflow.model.ProductModels;
import com.serviceflow.model.TicketModels;
import com.serviceflow.rag.RagService;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.ActionDecisionService;
import com.serviceflow.service.OrderService;
import com.serviceflow.service.ProductService;
import com.serviceflow.service.TicketService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.RedisSaver;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public final class CustomerWorkflow {

    private static final Duration CHECKPOINT_TTL = Duration.ofMinutes(30);
    private static final Pattern PAGE_PRODUCT_REFERENCE = Pattern.compile(
            "这款|这个|该机|这台|它|充电|电池|续航|防水|防尘|屏幕|拍照|相机|摄像|存储|内存|重量|尺寸|适配|兼容|支持|功能|使用|配对|连接|重置|恢复出厂|重启|按键|手势|触控|音量");
    private static final String GRAPH_ID = "serviceflow-customer";
    private static final Logger log = LoggerFactory.getLogger(CustomerWorkflow.class);
    private static final Pattern ORDER_NO = Pattern.compile("SF\\d{12,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern MODEL = Pattern.compile(
            "(?:[A-Za-z]+\\s+)*[A-Za-z]+\\s*\\d+(?:\\s*(?:Pro\\s*Max|Pro\\+?|Ultra|Plus|Max|Air|Mini))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SKU = Pattern.compile(
            "(?<![A-Za-z0-9])[A-Za-z0-9]+(?:-[A-Za-z0-9]+){2,}(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE);
    private final AiGateway ai;
    private final ProductService products;
    private final ProductReferenceResolver productReferences;
    private final ProductComparisonConversation comparison;
    private final OrderConversation orderConversation;
    private final ActionDecisionService actions;
    private final TicketService tickets;
    private final RagService rag;
    private final ChatMemory memory;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final CompiledGraph<WorkflowGraphState> graph;

    public CustomerWorkflow(
            AiGateway ai,
            ProductService products,
            ProductReferenceResolver productReferences,
            OrderService orders,
            ActionDecisionService actions,
            TicketService tickets,
            RagService rag,
            ChatMemory memory,
            ObjectMapper objectMapper,
            RedisProperties redisProperties,
            StringRedisTemplate redis) {
        this.ai = ai;
        this.products = products;
        this.productReferences = productReferences;
        this.comparison = new ProductComparisonConversation(products, productReferences, memory);
        this.orderConversation = new OrderConversation(ai, orders, memory, actions);
        this.actions = actions;
        this.tickets = tickets;
        this.rag = rag;
        this.memory = memory;
        this.objectMapper = objectMapper;
        this.redis = redis;
        try {
            RedisSaver saver = RedisSaver.builder()
                    .host(redisProperties.getHost())
                    .port(redisProperties.getPort())
                    .database(redisProperties.getDatabase())
                    .ttl(30, TimeUnit.MINUTES)
                    // LangGraph registers raw List/Map deserializers; isolate them from business DTOs.
                    .stateSerializer(
                            new JacksonStateSerializer<WorkflowGraphState>(
                                    WorkflowGraphState::new, objectMapper.copy()) {})
                    .build();
            this.graph = new StateGraph<>(WorkflowGraphState.SCHEMA, WorkflowGraphState::new)
                    // 所有请求先经过 router：识别意图并把结果写入图状态的 intent 字段。
                    .addNode("router", node_async(this::route))
                    .addNode("product", node_async(s -> branch(s, this::product)))
                    .addNode("knowledge", node_async(s -> branch(s, this::knowledge)))
                    .addNode("order", node_async(s -> branch(s, this::order)))
                    .addNode("complaint", node_async(s -> branch(s, this::complaint)))
                    .addNode(
                            "chat",
                            node_async(s ->
                                    result(new Result("您好，我可以帮助您了解商品、比较型号、查询订单或处理售后问题。", List.of()), List.of(), false)))
                    .addEdge(START, "router")
                    .addConditionalEdges(
                            "router",
                            edge_async(WorkflowGraphState::intent),
                            Map.of(
                                    Intent.PRODUCT_QUERY.name(), "product",
                                    Intent.KNOWLEDGE_QUERY.name(), "knowledge",
                                    Intent.ORDER_QUERY.name(), "order",
                                    Intent.COMPLAINT.name(), "complaint",
                                    Intent.CHAT.name(), "chat"))
                    .addEdge("product", END)
                    .addEdge("knowledge", END)
                    .addEdge("order", END)
                    .addEdge("complaint", END)
                    .addEdge("chat", END)
                    .compile(CompileConfig.builder()
                            .checkpointSaver(saver)
                            .graphId(GRAPH_ID)
                            .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot compile customer workflow", exception);
        }
    }

    public Result execute(ServiceFlowState state, BiConsumer<String, Object> events) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("sessionId", state.sessionId());
            input.put("subject", state.principal().subject());
            input.put("role", state.principal().role());
            input.put(
                    "customerId",
                    state.principal().customerId() == null
                            ? 0L
                            : state.principal().customerId());
            input.put("query", state.query());
            input.put("pageProductId", state.pageProductId() == null ? 0L : state.pageProductId());
            input.put("selectedProductId", state.selectedProductId() == null ? 0L : state.selectedProductId());
            // 商品片段属于本轮输入，进入工作流时清空上一轮检查点里的摘录。
            input.put("productRouting", "");
            input.put(
                    "comparisonSelection",
                    state.comparisonSelection() == null ? "" : writeJson(state.comparisonSelection()));
            input.put("clientRequestId", state.clientRequestId() == null ? "" : state.clientRequestId());
            input.put("activeOrderNo", state.activeOrderNo() == null ? "" : state.activeOrderNo());
            input.put(
                    "orderSelectionQuery",
                    state.orderSelection() == null ? "" : state.orderSelection().queryId());
            input.put(
                    "orderSelectionNo",
                    state.orderSelection() == null ? "" : state.orderSelection().orderNo());
            WorkflowGraphState finalState;
            try {
                finalState = graph.invoke(
                                input,
                                RunnableConfig.builder()
                                        .threadId(state.sessionId())
                                        .build())
                        .orElseThrow();
            } finally {
                expireCheckpointThread(state.sessionId());
            }
            // 工作流结束后，将图状态中的意图复制回业务状态，供后续回答生成、审计和持久化使用。
            Intent intent = Intent.valueOf(finalState.intent());
            state.intent(intent);
            state.productIds().addAll(finalState.productIds());
            state.clearProductContext(finalState.clearProductContext());
            events.accept(
                    "meta",
                    Map.of(
                            "intent",
                            intent.name(),
                            "degraded",
                            finalState.degraded(),
                            "citations",
                            finalState.citations(),
                            "productIds",
                            state.productIds()));
            List<String> names = finalState.eventNames();
            List<String> payloads = finalState.eventPayloads();
            for (int i = 0; i < Math.min(names.size(), payloads.size()); i++) {
                events.accept(names.get(i), objectMapper.readValue(payloads.get(i), Object.class));
            }
            return new Result(
                    finalState.answer(), finalState.citations(), finalState.degraded(), finalState.groundingContext());
        } catch (Exception exception) {
            throw new IllegalStateException("Customer workflow failed", exception);
        }
    }

    private void expireCheckpointThread(String sessionId) {
        try {
            String activeKey = "langgraph4j:thread:name:" + sessionId + ":active";
            String threadId = redis.opsForValue().get(activeKey);
            redis.expire(activeKey, CHECKPOINT_TTL);
            if (threadId != null && !threadId.isBlank()) {
                redis.expire("langgraph4j:thread:" + threadId, CHECKPOINT_TTL);
                redis.expire("langgraph4j:thread:" + threadId + ":checkpoints", CHECKPOINT_TTL);
            }
        } catch (RuntimeException exception) {
            log.warn("Could not refresh checkpoint TTL for session {}", sessionId, exception);
        }
    }

    private Map<String, Object> branch(WorkflowGraphState graphState, Branch branch) {
        List<Event> events = new ArrayList<>();
        ServiceFlowState runtime = graphState.runtime(objectMapper);
        Result result = branch.apply(runtime, (name, value) -> events.add(new Event(name, writeJson(value))));
        return result(result, events, runtime.clearProductContext());
    }

    private Map<String, Object> route(WorkflowGraphState state) {
        ServiceFlowState runtime = state.runtime(objectMapper);
        Intent intent = routeIntent(state, runtime);
        return Map.of(
                "intent",
                intent.name(),
                "productRouting",
                runtime.productRouting() == null ? "" : writeJson(runtime.productRouting()));
    }

    /**
     * 先由 AI 判断用户问题的基础意图，再结合当前商品页面和型号信息进行项目规则修正。
     */
    private Intent routeIntent(WorkflowGraphState state, ServiceFlowState runtime) {
        if (runtime.comparisonSelection() != null) return Intent.PRODUCT_QUERY;
        if (runtime.orderSelection() != null || OrderConversation.isSelection(state.query())) return Intent.ORDER_QUERY;
        boolean pendingOrders = !runtime.principal().guest()
                && memory.orderCandidates(runtime.principal().subject(), runtime.sessionId()) != null;
        boolean currentOrder = runtime.activeOrderNo() != null
                || (!runtime.principal().guest()
                        && memory.activeOrder(runtime.principal().subject(), runtime.sessionId()) != null);
        if (OrderConversation.isExactLookup(state.query())
                || (!pendingOrders && currentOrder && OrderConversation.isCurrentLookup(state.query())))
            return Intent.ORDER_QUERY;
        if (runtime.selectedProductId() != null) return Intent.PRODUCT_QUERY;
        // 云模式调用大模型分类；演示模式使用关键词规则分类。
        String classificationContext = "";
        try {
            var contextProduct = productReferences.find(runtime.pageProductId());
            if (contextProduct != null) classificationContext = "当前商品：" + contextProduct.name() + "。\n";
        } catch (org.springframework.dao.DataAccessException unavailable) {
            log.debug("Product context unavailable for intent routing");
        }
        if (pendingOrders) classificationContext = "用户正在从候选订单中选择，可继续查询或更换话题。\n" + classificationContext;
        else if (currentOrder) classificationContext = "当前会话关联本人订单，可指代它查询，也可切换到商品等其他话题。\n" + classificationContext;
        var routing = classifyIntent(ai, state.query(), classificationContext);
        Intent classified = routing.intent();
        if (productReferences.semanticEnabled()) {
            runtime.productRouting(routing);
        }
        if (state.query().contains("退货")
                && currentOrder
                && !state.query().contains("政策")
                && !state.query().contains("投诉")) return Intent.ORDER_QUERY;
        if (state.query().contains("工单")
                && (currentOrder || state.query().contains("退款"))
                && !state.query().contains("投诉")) return Intent.ORDER_QUERY;
        if (classified != Intent.COMPLAINT && ORDER_NO.matcher(state.query()).find()) {
            return Intent.ORDER_QUERY;
        }
        Intent pageAware = prioritizePageProduct(classified, state.pageProductId(), state.query());
        if (pageAware != classified) {
            return pageAware;
        }
        if (classified == Intent.CHAT
                && (state.pageProductId() > 0
                        || !modelMentions(state.query()).isEmpty()
                        || !skuMentions(state.query()).isEmpty()
                        || ProductReferenceResolver.hasHanModel(state.query())
                        || !routing.targets().isEmpty()
                        || !products.resolve(state.query()).isEmpty())) {
            return Intent.PRODUCT_QUERY;
        }
        return classified;
    }

    static ProductRouting classifyIntent(AiGateway ai, String query, String context) {
        try {
            var routing = ai.classifyProductReference(query, context);
            if (routing.isPresent()) return routing.get();
        } catch (RuntimeException failure) {
            if (Thread.currentThread().isInterrupted()) throw failure;
            log.debug(
                    "Intent classification unavailable ({})", failure.getClass().getSimpleName());
        }
        return new ProductRouting(Intent.fromKeywords(query), false, List.of());
    }

    static Intent prioritizePageProduct(Intent classified, long pageProductId, String query) {
        if (pageProductId <= 0 || classified == Intent.ORDER_QUERY || classified == Intent.COMPLAINT) {
            return classified;
        }
        if (query == null || query.isBlank()) {
            return classified;
        }
        if (query.contains("政策")) return Intent.KNOWLEDGE_QUERY;
        return PAGE_PRODUCT_REFERENCE.matcher(query).find()
                        || !modelMentions(query).isEmpty()
                ? Intent.PRODUCT_QUERY
                : classified;
    }

    static List<String> modelMentions(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> mentions = new ArrayList<>();
        Matcher matcher = MODEL.matcher(
                SKU.matcher(ORDER_NO.matcher(query).replaceAll("")).replaceAll(""));
        while (matcher.find()) {
            mentions.add(matcher.group().trim());
        }
        return mentions;
    }

    static List<String> skuMentions(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> mentions = new ArrayList<>();
        Matcher matcher = SKU.matcher(query);
        while (matcher.find()) {
            mentions.add(matcher.group().trim());
        }
        return mentions;
    }

    private Map<String, Object> result(Result result, List<Event> events, boolean clearProductContext) {
        return Map.of(
                "answer", result.answer(),
                "citations", result.citations(),
                "degraded", result.degraded(),
                "groundingContext", result.groundingContext() == null ? "" : result.groundingContext(),
                "productIds", result.productIds(),
                "clearProductContext", clearProductContext,
                "eventNames", events.stream().map(Event::name).toList(),
                "eventPayloads", events.stream().map(Event::payload).toList());
    }

    private Result product(ServiceFlowState state, BiConsumer<String, Object> events) {
        // 比较卡提交的是已有目标位置，直接续接 Redis 中的目标列表，避免重新解析原问题。
        if (state.comparisonSelection() != null) return comparison.continueComparison(state, events);
        ProductRouting routing = state.productRouting();
        List<ProductTarget> targets = routing == null ? List.of() : routing.targets();
        String targetClause = ProductReferenceRules.targetClause(state.query());
        // 句子明确转向新目标时，再核对模型摘录是否落在本轮目标范围内。
        if (!targetClause.isBlank() && !targetClause.equals(state.query())) {
            targets = targets.stream()
                    .filter(target ->
                            target.reference() == ProductReference.CURRENT || targetClause.contains(target.mention()))
                    .toList();
        }
        if (state.selectedProductId() == null
                && (requestsProductComparison(state.query()) || (routing != null && routing.comparison()))) {
            List<ProductTarget> rules = ProductReferenceRules.comparisonTargets(state.query());
            int expected = ProductReferenceRules.expectedCount(state.query());
            if ((routing != null && routing.comparison())
                    || ProductReferenceRules.comparesProducts(state.query(), rules, expected)) {
                // 原文已列足用户要求的数量时，可用规则补回模型漏掉的位置，保留原来的商品顺序。
                boolean completeSourceList = expected >= 2 && rules.size() == expected && rules.size() > targets.size();
                boolean modelTargets = !targets.isEmpty() && !completeSourceList;
                return comparison.start(state, modelTargets ? targets : rules, expected, modelTargets, events);
            }
        }
        // 先确定商品范围。需要澄清时直接返回，等用户选择后再查说明书。
        ProductTarget target = targets.size() == 1 ? targets.getFirst() : null;
        var resolution = productReferences.resolve(
                state.query(),
                state.pageProductId(),
                state.selectedProductId(),
                target == null ? ProductReference.UNCLEAR : target.reference(),
                target == null ? null : target.mention());
        state.clearProductContext(resolution.clearCurrent());
        if (!resolution.resolved()) {
            events.accept("product_selection_required", resolution.products());
            String reply = "请确认你指的是哪一款商品。";
            if (resolution.products().isEmpty()) {
                reply = resolution.degraded() ? "暂时无法确认对应商品，请提供 SKU 或完整型号。" : "没有找到对应商品，请提供 SKU、型号或从商品页发起咨询。";
            }
            return new Result(reply, List.of(), resolution.degraded(), null);
        }
        List<Long> ids = resolution.products().stream()
                .map(ProductModels.ProductView::id)
                .toList();
        state.productIds().addAll(ids);
        if (resolution.comparison()) {
            if (ids.size() < 2) {
                return new Result("请明确选择至少两个同类别商品后再比较。", List.of(), ids.stream().toList());
            }
            if (ids.size() > ProductModels.MAX_CHAT_COMPARISON) {
                return new Result(
                        "一次最多比较 10 个商品，请缩小比较范围。", List.of(), ids.stream().toList());
            }
            events.accept("product_comparison", products.compareForChat(ids));
            return new Result(
                    "已按结构化规格列出差异。表格仅展示事实，不包含推荐排序。", List.of(), ids.stream().toList());
        }
        ProductModels.ProductView product = resolution.products().getFirst();
        // 只检索当前商品的有效说明书分块，避免其他型号的文档混入回答依据。
        RagService.SearchResult docs = rag.search(state.query(), "PRODUCT_MANUAL", List.of(product.id()));
        events.accept("rag_evidence", evidenceTrace(docs));
        if (docs.diagnosticId() != null) events.accept("rag_diagnostic", Map.of("traceId", docs.diagnosticId()));
        // 数据库中的价格、型号和规格属于结构化事实，回答时优先级高于说明书文本。
        Map<String, Object> customerFacts = customerFacts(product);
        // 云端大模型不可用或不支持可信生成时，使用这段确定性的后端答案。
        String fallback = product.name() + "（" + product.model() + "），"
                + (product.listPrice() == null ? "暂无报价" : "标价 ¥" + product.listPrice())
                + "，状态 " + product.saleStatus() + "，规格：" + customerFacts.get("规格") + "。"
                + (docs.evidence().isEmpty()
                        ? "当前知识库没有该商品的说明文档，无法确认使用、适配或保修细节。"
                        : "\n说明文档摘要："
                                + docs.evidence().stream()
                                        .map(RagService.Evidence::content)
                                        .reduce((a, b) -> a + "\n" + b)
                                        .orElse(""));
        return new Result(
                fallback,
                docs.evidence().stream()
                        .map(RagService.Evidence::source)
                        .distinct()
                        .toList(),
                docs.degraded(),
                // groundingContext 是交给大模型的可信依据，不是预先生成的最终答案。
                writeJson(com.serviceflow.ai.GroundedAnswerComposer.context(customerFacts, docs.evidence())),
                ids.stream().toList());
    }

    static boolean isProductComparison(String query, List<ProductModels.ProductView> candidates) {
        // Multiple capacity SKUs inferred from one model name do not imply a cross-product comparison.
        // A question about a device's inner/outer screen or left/right earbud still needs its manual.
        return requestsProductComparison(query)
                && (candidates.stream()
                                        .map(ProductModels.ProductView::model)
                                        .distinct()
                                        .count()
                                > 1
                        || skuMentions(query).stream()
                                        .map(s -> s.toUpperCase(Locale.ROOT))
                                        .distinct()
                                        .count()
                                > 1);
    }

    static boolean requestsProductComparison(String query) {
        return query.contains("对比") || query.contains("区别") || query.contains("比较");
    }

    static List<Map<String, String>> evidenceTrace(RagService.SearchResult result) {
        // Trace immutable chunk identifiers without exposing the retrieved document body in SSE metadata.
        return result.evidence().stream()
                .map(item -> Map.of("chunkId", item.chunkId(), "source", item.source()))
                .toList();
    }

    static Map<String, Object> customerFacts(ProductModels.ProductView product) {
        Map<String, Object> specs = new LinkedHashMap<>(product.specs());
        // Keep provenance in the catalog, without turning import/audit metadata into customer answers.
        specs.keySet()
                .removeIf(
                        key -> key.startsWith("source_") || key.equals("price_note") || key.equals("sale_status_note"));
        return Map.of(
                "名称",
                product.name(),
                "型号",
                product.model(),
                "SKU",
                product.sku(),
                "品牌",
                product.brand(),
                "类别",
                product.category(),
                "报价",
                product.listPrice() == null ? "暂无报价" : "¥" + product.listPrice().toPlainString(),
                "店铺销售状态",
                product.saleStatus(),
                "规格",
                specs);
    }

    private Result knowledge(ServiceFlowState state, BiConsumer<String, Object> events) {
        if (state.query().contains("上传")
                && (state.query().contains("知识") || state.query().contains("文档"))
                && !"ADMIN".equals(state.principal().role())) {
            return new Result("知识库上传与版本管理仅允许管理员操作，请联系管理员处理。", List.of());
        }
        RagService.SearchResult result = rag.search(state.query(), "POLICY", List.of());
        events.accept("rag_evidence", evidenceTrace(result));
        if (result.diagnosticId() != null) events.accept("rag_diagnostic", Map.of("traceId", result.diagnosticId()));
        if (!result.sufficient()) {
            if (state.principal().guest()) {
                return new Result("暂未找到可靠政策依据；如需人工协助，请先登录客户账号。", List.of());
            }
            String actionId = actions.prepare(
                    new ChatActionModels.Pending(
                            "CREATE_TICKET",
                            state.principal().subject(),
                            state.principal().requireCustomerId(),
                            state.sessionId(),
                            null,
                            state.query()),
                    state.clientRequestId());
            events.accept(
                    "action_required",
                    Map.of(
                            "actionId", actionId,
                            "actionType", "CREATE_TICKET",
                            "message", "未找到充分依据，是否转人工？"));
            return new Result("暂未找到充分依据，您可以确认转人工处理。", List.of(), result.degraded());
        }
        return new Result(
                result.evidence().stream()
                        .map(RagService.Evidence::content)
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse(""),
                result.evidence().stream()
                        .map(RagService.Evidence::source)
                        .distinct()
                        .toList(),
                result.degraded());
    }

    private Result order(ServiceFlowState state, BiConsumer<String, Object> events) {
        return orderConversation.respond(state, events);
    }

    private Result complaint(ServiceFlowState state, BiConsumer<String, Object> events) {
        if (state.principal().guest()) {
            return new Result("创建人工工单前请先登录客户账号。", List.of());
        }
        String requestId = state.clientRequestId() == null ? UUID.randomUUID().toString() : state.clientRequestId();
        TicketModels.TicketView ticket = tickets.createFromAction(
                requestId, state.principal().requireCustomerId(), state.sessionId(), "客户投诉", state.query());
        events.accept("ticket", ticket);
        return new Result("已创建人工工单 " + ticket.publicId() + "，客服将继续处理。", List.of());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize graph event", exception);
        }
    }

    public record Result(
            String answer, List<String> citations, boolean degraded, String groundingContext, List<Long> productIds) {
        public Result(String answer, List<String> citations) {
            this(answer, citations, false, null, List.of());
        }

        public Result(String answer, List<String> citations, boolean degraded) {
            this(answer, citations, degraded, null, List.of());
        }

        public Result(String answer, List<String> citations, List<Long> productIds) {
            this(answer, citations, false, null, productIds);
        }

        public Result(String answer, List<String> citations, boolean degraded, String groundingContext) {
            this(answer, citations, degraded, groundingContext, List.of());
        }
    }

    private record Event(String name, String payload) {}

    @FunctionalInterface
    private interface Branch {
        Result apply(ServiceFlowState state, BiConsumer<String, Object> events);
    }

    static final class WorkflowGraphState extends AgentState {
        static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
                Map.entry("sessionId", Channels.base(() -> "")),
                Map.entry("subject", Channels.base(() -> "")),
                Map.entry("role", Channels.base(() -> "GUEST")),
                Map.entry("customerId", Channels.base(() -> 0L)),
                Map.entry("query", Channels.base(() -> "")),
                Map.entry("pageProductId", Channels.base(() -> 0L)),
                Map.entry("selectedProductId", Channels.base(() -> 0L)),
                Map.entry("productRouting", Channels.base(() -> "")),
                Map.entry("comparisonSelection", Channels.base(() -> "")),
                Map.entry("clearProductContext", Channels.base(() -> false)),
                Map.entry("clientRequestId", Channels.base(() -> "")),
                Map.entry("activeOrderNo", Channels.base(() -> "")),
                Map.entry("orderSelectionQuery", Channels.base(() -> "")),
                Map.entry("orderSelectionNo", Channels.base(() -> "")),
                Map.entry("intent", Channels.base(() -> Intent.CHAT.name())),
                Map.entry("answer", Channels.base(() -> "")),
                Map.entry("citations", Channels.base(ArrayList::new)),
                Map.entry("productIds", Channels.base(ArrayList::new)),
                Map.entry("degraded", Channels.base(() -> false)),
                Map.entry("groundingContext", Channels.base(() -> "")),
                Map.entry("eventNames", Channels.base(ArrayList::new)),
                Map.entry("eventPayloads", Channels.base(ArrayList::new)));

        WorkflowGraphState(Map<String, Object> initData) {
            super(initData);
        }

        String query() {
            return value("query", "");
        }

        long pageProductId() {
            return value("pageProductId")
                    .map(Number.class::cast)
                    .map(Number::longValue)
                    .orElse(0L);
        }

        String intent() {
            return value("intent", Intent.CHAT.name());
        }

        String answer() {
            return value("answer", "");
        }

        boolean degraded() {
            return value("degraded", false);
        }

        boolean clearProductContext() {
            return value("clearProductContext", false);
        }

        String groundingContext() {
            return value("groundingContext", "");
        }

        @SuppressWarnings("unchecked")
        List<String> citations() {
            return value("citations").map(v -> (List<String>) v).orElse(List.of());
        }

        List<Long> productIds() {
            return value("productIds")
                    .map(value -> ((List<?>) value)
                            .stream()
                                    .map(Number.class::cast)
                                    .map(Number::longValue)
                                    .toList())
                    .orElse(List.of());
        }

        @SuppressWarnings("unchecked")
        List<String> eventNames() {
            return value("eventNames").map(v -> (List<String>) v).orElse(List.of());
        }

        @SuppressWarnings("unchecked")
        List<String> eventPayloads() {
            return value("eventPayloads").map(v -> (List<String>) v).orElse(List.of());
        }

        ServiceFlowState runtime(ObjectMapper mapper) {
            Number customerId = value("customerId").map(Number.class::cast).orElse(0L);
            Number productId = value("pageProductId").map(Number.class::cast).orElse(0L);
            String requestId = value("clientRequestId", "");
            CurrentPrincipal principal = new CurrentPrincipal(
                    value("subject", ""),
                    value("role", "GUEST"),
                    customerId.longValue() <= 0 ? null : customerId.longValue());
            ServiceFlowState runtime = new ServiceFlowState(
                    value("sessionId", ""),
                    principal,
                    query(),
                    productId.longValue() <= 0 ? null : productId.longValue(),
                    requestId.isBlank() ? null : requestId);
            String orderNo = value("activeOrderNo", "");
            runtime.activeOrderNo(orderNo.isBlank() ? null : orderNo);
            Number selectedId =
                    value("selectedProductId").map(Number.class::cast).orElse(0L);
            runtime.selectedProductId(selectedId.longValue() > 0 ? selectedId.longValue() : null);
            runtime.productRouting(readInput(mapper, value("productRouting", ""), ProductRouting.class));
            runtime.comparisonSelection(
                    readInput(mapper, value("comparisonSelection", ""), ChatModels.ComparisonSelection.class));
            String selection = value("orderSelectionQuery", "");
            if (!selection.isBlank())
                runtime.orderSelection(
                        new com.serviceflow.model.ChatModels.OrderSelection(selection, value("orderSelectionNo", "")));
            return runtime;
        }

        private static <T> T readInput(ObjectMapper mapper, String value, Class<T> type) {
            if (value.isBlank()) return null;
            try {
                return mapper.readValue(value, type);
            } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                throw new IllegalStateException("Cannot read workflow state", error);
            }
        }
    }
}
