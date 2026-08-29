package com.serviceflow.agent;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.auth.CurrentPrincipal;
import com.serviceflow.chat.AiGateway;
import com.serviceflow.chat.ChatMemory;
import com.serviceflow.order.OrderModels;
import com.serviceflow.order.OrderService;
import com.serviceflow.product.ProductModels;
import com.serviceflow.product.ProductService;
import com.serviceflow.rag.RagService;
import com.serviceflow.ticket.TicketMapper;
import com.serviceflow.ticket.TicketService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final Pattern PAGE_PRODUCT_REFERENCE =
            Pattern.compile("这款|这个|该机|这台|它|充电|电池|续航|防水|防尘|屏幕|拍照|相机|摄像|存储|内存|重量|尺寸|适配|兼容|支持|功能|使用");
    private static final String GRAPH_ID = "serviceflow-customer";
    private static final Logger log = LoggerFactory.getLogger(CustomerWorkflow.class);
    private static final Pattern ORDER_NO = Pattern.compile("SF\\d{12,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern MODEL = Pattern.compile(
            "(?:[A-Za-z]+\\s+)*[A-Za-z]+\\s*\\d+(?:\\s*(?:Pro\\+?|Ultra|Max|Air))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKU = Pattern.compile(
            "(?<![A-Za-z0-9])[A-Za-z0-9]+(?:-[A-Za-z0-9]+){2,}(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE);
    private final AiGateway ai;
    private final ProductService products;
    private final OrderService orders;
    private final TicketService tickets;
    private final RagService rag;
    private final ChatMemory memory;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final CompiledGraph<WorkflowGraphState> graph;

    public CustomerWorkflow(
            AiGateway ai,
            ProductService products,
            OrderService orders,
            TicketService tickets,
            RagService rag,
            ChatMemory memory,
            ObjectMapper objectMapper,
            RedisProperties redisProperties,
            StringRedisTemplate redis) {
        this.ai = ai;
        this.products = products;
        this.orders = orders;
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
                    .stateSerializer(
                            new JacksonStateSerializer<WorkflowGraphState>(WorkflowGraphState::new, objectMapper) {})
                    .build();
            this.graph = new StateGraph<>(WorkflowGraphState.SCHEMA, WorkflowGraphState::new)
                    .addNode(
                            "router",
                            node_async(s -> Map.of("intent", routeIntent(s).name())))
                    .addNode("product", node_async(s -> branch(s, this::product)))
                    .addNode("knowledge", node_async(s -> branch(s, this::knowledge)))
                    .addNode("order", node_async(s -> branch(s, this::order)))
                    .addNode("complaint", node_async(s -> branch(s, this::complaint)))
                    .addNode(
                            "chat",
                            node_async(
                                    s -> result(new Result("您好，我可以帮助您了解商品、比较型号、查询订单或处理售后问题。", List.of()), List.of())))
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
            input.put("clientRequestId", state.clientRequestId() == null ? "" : state.clientRequestId());
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
            Intent intent = Intent.valueOf(finalState.intent());
            state.intent(intent);
            events.accept(
                    "meta",
                    Map.of(
                            "intent",
                            intent.name(),
                            "degraded",
                            finalState.degraded(),
                            "citations",
                            finalState.citations()));
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
        Result result =
                branch.apply(graphState.runtime(), (name, value) -> events.add(new Event(name, writeJson(value))));
        return result(result, events);
    }

    private Intent routeIntent(WorkflowGraphState state) {
        Intent classified = ai.classify(state.query());
        Intent pageAware = prioritizePageProduct(classified, state.pageProductId(), state.query());
        if (pageAware != classified) {
            return pageAware;
        }
        if (classified == Intent.CHAT
                && (state.pageProductId() > 0
                        || !modelMentions(state.query()).isEmpty()
                        || !products.resolve(state.query()).isEmpty())) {
            return Intent.PRODUCT_QUERY;
        }
        return classified;
    }

    static Intent prioritizePageProduct(Intent classified, long pageProductId, String query) {
        if (pageProductId <= 0 || classified == Intent.ORDER_QUERY || classified == Intent.COMPLAINT) {
            return classified;
        }
        return query != null && PAGE_PRODUCT_REFERENCE.matcher(query).find() ? Intent.PRODUCT_QUERY : classified;
    }

    static List<String> modelMentions(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> mentions = new ArrayList<>();
        Matcher matcher = MODEL.matcher(query);
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

    private Map<String, Object> result(Result result, List<Event> events) {
        return Map.of(
                "answer", result.answer(),
                "citations", result.citations(),
                "degraded", result.degraded(),
                "groundingContext", result.groundingContext() == null ? "" : result.groundingContext(),
                "eventNames", events.stream().map(Event::name).toList(),
                "eventPayloads", events.stream().map(Event::payload).toList());
    }

    private Result product(ServiceFlowState state, BiConsumer<String, Object> events) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (state.pageProductId() != null) {
            ids.add(state.pageProductId());
        }
        skuMentions(state.query())
                .forEach(mention -> products.resolve(mention).forEach(product -> ids.add(product.id())));
        modelMentions(state.query())
                .forEach(mention -> products.resolve(mention).forEach(product -> ids.add(product.id())));
        if (ids.isEmpty()) {
            List<ProductModels.ProductView> candidates = products.resolve(state.query());
            if (candidates.size() == 1) {
                ids.add(candidates.getFirst().id());
            } else {
                events.accept("product_selection_required", candidates);
                return new Result(
                        candidates.isEmpty() ? "没有找到对应商品，请提供 SKU、型号或从商品页发起咨询。" : "找到多个可能的商品，请先选择具体型号。", List.of());
            }
        }
        boolean compare = state.query().contains("对比")
                || state.query().contains("区别")
                || state.query().contains("比较");
        if (compare) {
            if (ids.size() < 2) {
                return new Result("请明确选择至少两个同类别商品后再比较。", List.of());
            }
            if (ids.size() > 3) {
                return new Result("一次最多比较 3 个商品，请缩小比较范围。", List.of());
            }
            ProductModels.Comparison comparison = products.compare(ids.stream().toList());
            events.accept("product_comparison", comparison);
            return new Result("已按结构化规格列出差异。表格仅展示事实，不包含推荐排序。", List.of());
        }
        ProductModels.ProductView product = products.get(ids.getFirst());
        state.productIds().addAll(ids);
        RagService.SearchResult docs = rag.search(state.query(), "PRODUCT_MANUAL", List.of(product.id()));
        String facts = writeJson(Map.of(
                "id", product.id(),
                "sku", product.sku(),
                "name", product.name(),
                "brand", product.brand(),
                "model", product.model(),
                "category", product.category(),
                "specs", product.specs(),
                "listPrice", product.listPrice(),
                "saleStatus", product.saleStatus()));
        String evidence = docs.evidence().stream()
                .map(item -> "[chunkId=" + item.chunkId() + "; title=" + item.title() + "; source=" + item.source()
                        + "]\n" + item.content())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("暂无商品说明文档证据");
        String fallback = product.name() + "（" + product.model() + "），标价 ¥" + product.listPrice()
                + "，状态 " + product.saleStatus() + "，规格：" + product.specs() + "。"
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
                "结构化商品事实：\n" + facts + "\n\n文档证据：\n" + evidence);
    }

    private Result knowledge(ServiceFlowState state, BiConsumer<String, Object> events) {
        RagService.SearchResult result = rag.search(state.query(), "POLICY", List.of());
        if (!result.sufficient()) {
            if (state.principal().guest()) {
                return new Result("暂未找到可靠政策依据；如需人工协助，请先登录客户账号。", List.of());
            }
            String actionId = memory.putAction(new ChatMemory.PendingAction(
                    "CREATE_TICKET",
                    state.principal().subject(),
                    state.principal().requireCustomerId(),
                    state.sessionId(),
                    null,
                    state.query()));
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
        if (state.principal().guest()) {
            return new Result("订单服务需要登录客户账号后使用。", List.of());
        }
        Matcher matcher = ORDER_NO.matcher(state.query());
        String orderNo = matcher.find()
                ? matcher.group().toUpperCase(Locale.ROOT)
                : memory.activeOrder(state.principal().subject(), state.sessionId());
        if (orderNo == null) {
            return new Result("请提供订单号，例如 SF202608280001。", List.of());
        }
        memory.setActiveOrder(state.principal().subject(), state.sessionId(), orderNo);
        if (state.query().contains("取消")) {
            if (!orders.canCancel(orderNo, state.principal().requireCustomerId())) {
                return new Result("该订单已发货或结束，不能直接取消；可以咨询退货政策。", List.of());
            }
            String actionId = memory.putAction(new ChatMemory.PendingAction(
                    "CANCEL_ORDER",
                    state.principal().subject(),
                    state.principal().requireCustomerId(),
                    state.sessionId(),
                    orderNo,
                    state.query()));
            events.accept(
                    "action_required",
                    Map.of(
                            "actionId",
                            actionId,
                            "actionType",
                            "CANCEL_ORDER",
                            "orderNo",
                            orderNo,
                            "message",
                            "确认取消该订单吗？"));
            return new Result("订单当前可以取消，请在确认卡片中二次确认。", List.of());
        }
        OrderModels.OrderView order = orders.get(orderNo, state.principal().requireCustomerId());
        events.accept("order", order);
        String refund = order.payment() == null ? "暂无" : order.payment().refundStatus();
        String shipment =
                order.shipment().isEmpty() ? "暂无" : order.shipment().getFirst().description();
        return new Result(
                "订单 " + order.orderNo() + " 当前状态为 " + order.status() + "，退款状态为 " + refund + "，最新物流：" + shipment + "。",
                List.of());
    }

    private Result complaint(ServiceFlowState state, BiConsumer<String, Object> events) {
        if (state.principal().guest()) {
            return new Result("创建人工工单前请先登录客户账号。", List.of());
        }
        String requestId = state.clientRequestId() == null ? UUID.randomUUID().toString() : state.clientRequestId();
        TicketMapper.TicketView ticket = tickets.createFromAction(
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

    public record Result(String answer, List<String> citations, boolean degraded, String groundingContext) {
        public Result(String answer, List<String> citations) {
            this(answer, citations, false, null);
        }

        public Result(String answer, List<String> citations, boolean degraded) {
            this(answer, citations, degraded, null);
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
                Map.entry("clientRequestId", Channels.base(() -> "")),
                Map.entry("intent", Channels.base(() -> Intent.CHAT.name())),
                Map.entry("answer", Channels.base(() -> "")),
                Map.entry("citations", Channels.base(ArrayList::new)),
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

        String groundingContext() {
            return value("groundingContext", "");
        }

        @SuppressWarnings("unchecked")
        List<String> citations() {
            return value("citations").map(v -> (List<String>) v).orElse(List.of());
        }

        @SuppressWarnings("unchecked")
        List<String> eventNames() {
            return value("eventNames").map(v -> (List<String>) v).orElse(List.of());
        }

        @SuppressWarnings("unchecked")
        List<String> eventPayloads() {
            return value("eventPayloads").map(v -> (List<String>) v).orElse(List.of());
        }

        ServiceFlowState runtime() {
            Number customerId = value("customerId").map(Number.class::cast).orElse(0L);
            Number productId = value("pageProductId").map(Number.class::cast).orElse(0L);
            String requestId = value("clientRequestId", "");
            CurrentPrincipal principal = new CurrentPrincipal(
                    value("subject", ""),
                    value("role", "GUEST"),
                    customerId.longValue() <= 0 ? null : customerId.longValue());
            return new ServiceFlowState(
                    value("sessionId", ""),
                    principal,
                    query(),
                    productId.longValue() <= 0 ? null : productId.longValue(),
                    requestId.isBlank() ? null : requestId);
        }
    }
}
