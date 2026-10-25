package com.serviceflow.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.agent.CustomerWorkflow;
import com.serviceflow.agent.Intent;
import com.serviceflow.agent.ServiceFlowState;
import com.serviceflow.ai.AiGateway;
import com.serviceflow.infrastructure.redis.ChatMemory;
import com.serviceflow.model.ChatModels;
import com.serviceflow.model.ProductModels;
import com.serviceflow.rag.RagService;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.AiAuditService;
import com.serviceflow.service.ChatSessionService;
import com.serviceflow.service.ProductService;
import com.serviceflow.service.impl.support.ChatRequestStore;
import com.serviceflow.web.sse.SseEventWriter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Real context resolution, product branch and grounded composition; deterministic models/storage only. */
class ProductContextFlowTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProductService products = mock(ProductService.class);
    private final RagService rag = mock(RagService.class);
    private final ChatMemory memory = mock(ChatMemory.class);
    private final CustomerWorkflow workflow = mock(CustomerWorkflow.class, CALLS_REAL_METHODS);
    private final AiGateway ai = mock(AiGateway.class);
    private final AtomicReference<Long> remembered = new AtomicReference<>();
    private final AtomicReference<CustomerWorkflow.Result> result = new AtomicReference<>();
    private final AtomicReference<String> selectionPrompt = new AtomicReference<>();
    private ChatServiceImpl service;

    @BeforeEach
    void setup() {
        var requests = mock(ChatRequestStore.class);
        var sessions = mock(ChatSessionService.class);
        ReflectionTestUtils.setField(workflow, "products", products);
        ReflectionTestUtils.setField(workflow, "rag", rag);
        ReflectionTestUtils.setField(workflow, "objectMapper", mapper);
        var apple = product(29, "APPLE-IP16-US-128", "iPhone 16");
        var samsung = product(30, "SAMSUNG-S24-US-128", "Galaxy S24");
        when(products.get(29)).thenReturn(apple);
        when(products.get(30)).thenReturn(samsung);
        when(products.resolve(anyString())).thenReturn(List.of());
        when(products.resolve("APPLE-IP16-US-128")).thenReturn(List.of(apple));
        when(products.resolve("SAMSUNG-S24-US-128")).thenReturn(List.of(samsung));
        when(memory.activeProduct(anyString(), anyString())).thenAnswer(call -> remembered.get());
        doAnswer(call -> {
                    remembered.set(null);
                    return null;
                })
                .when(memory)
                .clearActiveProduct(anyString(), anyString());
        doAnswer(call -> {
                    remembered.set(call.getArgument(2));
                    return null;
                })
                .when(memory)
                .setActiveProduct(anyString(), anyString(), anyLong());
        when(memory.history(anyString(), anyString()))
                .thenReturn(List.of(Map.of("role", "ASSISTANT", "content", "上轮旧商品：iPhone 16。")));
        when(sessions.requireCustomerSession(any(), anyString())).thenReturn(1L);
        when(requests.begin(anyLong(), anyString(), anyString()))
                .thenReturn(new ChatRequestStore.BeginResult(true, null));
        when(rag.search(anyString(), eq("PRODUCT_MANUAL"), anyList())).thenAnswer(call -> {
            long id = ((List<Long>) call.getArgument(2)).getFirst();
            return new RagService.SearchResult(
                    List.of(new RagService.Evidence(
                            "chunk-" + id,
                            "current manual",
                            "manual-" + id + ".md",
                            id == 29 ? "标准矩形6.12英寸，实际可视区域较小。" : "实际可视区域因圆角和摄像头开孔而较小。",
                            1)),
                    true,
                    false);
        });
        doAnswer(call -> {
                    ServiceFlowState state = call.getArgument(0);
                    state.intent(Intent.PRODUCT_QUERY);
                    CustomerWorkflow.Result current =
                            ReflectionTestUtils.invokeMethod(workflow, "product", state, call.getArgument(1));
                    result.set(current);
                    return current;
                })
                .when(workflow)
                .execute(any(), any());
        when(ai.supportsGroundedGeneration()).thenReturn(true);
        doAnswer(call -> {
                    selectionPrompt.set(call.getArgument(1));
                    var facts = mapper.readTree(result.get().groundingContext()).path("facts");
                    int id = facts.size() - 1;
                    java.util.function.Consumer<String> consumer = call.getArgument(2);
                    consumer.accept("{\"factIds\":[" + id + "],\"insufficient\":false}");
                    return null;
                })
                .when(ai)
                .streamAnswer(anyString(), anyString(), any());
        service = new ChatServiceImpl(
                memory,
                sessions,
                requests,
                workflow,
                ai,
                mock(StringRedisTemplate.class),
                mapper,
                mock(AiAuditService.class),
                mock(SseEventWriter.class));
    }

    @Test
    void explicitSkuOverridesOldPageAndNextPronounUsesNewProduct() {
        String query = "改问 SAMSUNG-S24-US-128，圆角也都算可视区吗？";
        send(query, 29L);
        verify(rag).search(query, "PRODUCT_MANUAL", List.of(30L));
        assertThat(remembered.get()).isEqualTo(30L);
        assertThat(result.get().groundingContext())
                .contains("SAMSUNG-S24-US-128", "manual-30.md")
                .doesNotContain("manual-29.md");
        assertThat(selectionPrompt.get()).contains("当前用户问题：\n" + query);
        send("它的圆角限制还适用吗？", null);
        verify(rag).search("它的圆角限制还适用吗？", "PRODUCT_MANUAL", List.of(30L));
    }

    @Test
    void unknownExplicitSkuMustNotFallBackToPageManual() {
        remembered.set(29L);
        send("改问 UNKNOWN-PHONE-999 的可视面积。", 29L);
        verifyNoInteractions(rag);
        assertThat(result.get().groundingContext()).isNull();
        assertThat(result.get().answer()).contains("没有找到");
        assertThat(remembered.get()).isNull();
        send("它的屏幕呢？", null);
        verifyNoInteractions(rag);
    }

    @Test
    void pronounWithoutExplicitSwitchStillUsesSuppliedPage() {
        send("它的圆角算可视区吗？", 29L);
        verify(rag).search("它的圆角算可视区吗？", "PRODUCT_MANUAL", List.of(29L));
    }

    private void send(String query, Long page) {
        service.stream(
                new CurrentPrincipal("owner", "CUSTOMER", 1L),
                "session",
                new ChatModels.MessageRequest(
                        query, UUID.randomUUID().toString(), page == null ? null : new ChatModels.PageContext(page)),
                new SseEmitter());
    }

    private ProductModels.ProductView product(long id, String sku, String model) {
        return new ProductModels.ProductView(id, sku, model, "brand", model, "phone", Map.of(), null, "OFF_SALE");
    }
}
