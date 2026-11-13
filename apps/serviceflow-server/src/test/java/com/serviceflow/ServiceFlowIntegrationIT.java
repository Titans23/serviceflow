package com.serviceflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;

import com.serviceflow.infrastructure.redis.ChatMemory;
import com.serviceflow.mapper.OrderMapper;
import com.serviceflow.model.OrderModels;
import com.serviceflow.model.RefundModels;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.ChatSessionService;
import com.serviceflow.service.OrderService;
import com.serviceflow.service.RefundService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ServiceFlowIntegrationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("serviceflow")
            .withUsername("serviceflow")
            .withPassword("serviceflow");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management");

    private final TestRestTemplate http;
    private final JdbcTemplate jdbc;
    private final OrderService orders;
    private final OrderMapper orderMapper;
    private final PlatformTransactionManager transactions;
    private final RefundService refunds;

    @Autowired
    ServiceFlowIntegrationIT(
            TestRestTemplate http,
            JdbcTemplate jdbc,
            OrderService orders,
            OrderMapper orderMapper,
            PlatformTransactionManager transactions,
            RefundService refunds) {
        this.http = http;
        this.jdbc = jdbc;
        this.orders = orders;
        this.orderMapper = orderMapper;
        this.transactions = transactions;
        this.refunds = refunds;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("MYSQL_HOST", MYSQL::getHost);
        registry.add("MYSQL_PORT", MYSQL::getFirstMappedPort);
        registry.add("MYSQL_DATABASE", MYSQL::getDatabaseName);
        registry.add("MYSQL_USER", MYSQL::getUsername);
        registry.add("MYSQL_PASSWORD", MYSQL::getPassword);
        registry.add("REDIS_HOST", REDIS::getHost);
        registry.add("REDIS_PORT", () -> REDIS.getMappedPort(6379));
        registry.add("RABBITMQ_HOST", RABBIT::getHost);
        registry.add("RABBITMQ_PORT", () -> RABBIT.getMappedPort(5672));
        registry.add("RABBITMQ_USER", RABBIT::getAdminUsername);
        registry.add("RABBITMQ_PASSWORD", RABBIT::getAdminPassword);
        registry.add("SERVICEFLOW_AI_MODE", () -> "demo");
        registry.add("SERVICEFLOW_RAG_MODE", () -> "demo");
        registry.add("spring.ai.model.chat", () -> "none");
        registry.add("spring.ai.model.embedding", () -> "none");
        registry.add("JWT_SECRET", () -> "integration-test-secret-with-at-least-32-bytes");
    }

    @Test
    void applicationContextLoadsAgainstRealInfrastructure() {
        assertThat(MYSQL.isRunning()).isTrue();
        assertThat(REDIS.isRunning()).isTrue();
        assertThat(RABBIT.isRunning()).isTrue();
    }

    @Test
    void readinessIsPublicAndUp() {
        ResponseEntity<String> response = http.getForEntity("/actuator/health/readiness", String.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void guestTokenCanReadProductCatalog() {
        TokenResponse token = http.postForObject("/auth/guest", null, TokenResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token.accessToken());

        ResponseEntity<String> response = http.exchange("/products", GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("HUAWEI Pura 80");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sessionDeletionIsScopedToTheOwner(
            boolean guest,
            @Autowired ChatSessionService sessions,
            @Autowired ChatMemory memory,
            @Autowired com.serviceflow.service.ActionDecisionService decisions) {
        CurrentPrincipal owner = chatPrincipal(guest);
        CurrentPrincipal other = chatPrincipal(guest);
        String first = sessions.create(owner, "first").publicId();
        String second = sessions.create(owner, "second").publicId();
        String foreign = sessions.create(other, "other").publicId();
        String pendingAction = guest
                ? null
                : decisions.prepare(
                        new com.serviceflow.model.ChatActionModels.Pending(
                                "CREATE_TICKET", owner.subject(), owner.customerId(), first, null, "help"),
                        UUID.randomUUID().toString());
        if (guest) {
            memory.append(owner.subject(), first, "USER", "history");
        } else {
            jdbc.update(
                    "INSERT INTO chat_message(public_id,session_id,role,content,metadata) VALUES(?,?,'USER','history',JSON_OBJECT())",
                    UUID.randomUUID().toString(),
                    sessions.requireCustomerSession(owner, first));
        }
        assertThat(sessions.messages(owner, first)).hasSize(1);
        sessions.deleteSessions(other, first);
        assertThat(sessions.messages(owner, first)).hasSize(1);
        sessions.deleteSessions(owner, first);
        assertThat(sessions.list(owner)).extracting("publicId").containsExactly(second);
        assertThatThrownBy(() -> sessions.messages(owner, first)).hasMessageContaining("404");
        if (!guest) {
            assertThat(decisions.decide(owner, pendingAction, "CONFIRM").actionStatus())
                    .isEqualTo("INVALIDATED");
        }
        sessions.deleteSessions(owner, null);
        sessions.deleteSessions(owner, null);
        assertThat(sessions.list(owner)).isEmpty();
        assertThatThrownBy(() -> sessions.messages(owner, second)).hasMessageContaining("404");
        assertThat(sessions.list(other)).extracting("publicId").containsExactly(foreign);
    }

    private CurrentPrincipal chatPrincipal(boolean guest) {
        String subject = UUID.randomUUID().toString();
        if (guest) return new CurrentPrincipal(subject, "GUEST", null);
        jdbc.update("INSERT INTO customer(name) VALUES(?)", subject);
        return new CurrentPrincipal(
                subject, "CUSTOMER", jdbc.queryForObject("SELECT id FROM customer WHERE name=?", Long.class, subject));
    }

    @Test
    void orderSearchFiltersAndPagesWithoutLeakingAnotherCustomersOrders(
            @Autowired com.serviceflow.security.JwtService jwt) {
        var owner = chatPrincipal(false);
        var other = chatPrincipal(false);
        long first = searchOrder(owner.customerId(), "PAID", "2026-11-02 16:00:00");
        long second = searchOrder(owner.customerId(), "PAID", "2026-11-02 16:00:00");
        searchOrder(owner.customerId(), "SHIPPED", "2026-11-03 16:00:00");
        long foreign = searchOrder(other.customerId(), "PAID", "2026-11-02 16:00:00");
        var day = java.time.LocalDate.of(2026, 11, 3);
        var query = new OrderModels.Search("手机", "PAID", day, day);
        var page = orders.search(owner.customerId(), query, null, 1);
        assertThat(page.nextCursor()).isEqualTo(second);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().items()).hasSize(2);
        var rest = orders.search(owner.customerId(), query, page.nextCursor(), 1);
        assertThat(rest.items()).hasSize(1);
        assertThat(rest.nextCursor()).isNull();
        assertThat(rest.items().getFirst().orderNo())
                .isNotEqualTo(page.items().getFirst().orderNo());
        assertThat(orders.search(owner.customerId(), new OrderModels.Search("%", null, null, null), null, 10)
                        .items())
                .isEmpty();
        assertThatThrownBy(() -> orders.search(owner.customerId(), query, foreign, 1))
                .hasMessageContaining("400");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.issueUser(new com.serviceflow.model.AuthModels.User(
                        123, "search-test", "", "CUSTOMER", owner.customerId(), true))
                .accessToken());
        var httpPage = http.exchange(
                "/orders?status=PAID&from=2026-11-03&to=2026-11-03&size=1",
                GET,
                new HttpEntity<>(headers),
                OrderModels.Page.class);
        assertThat(httpPage.getStatusCode()).isEqualTo(OK);
        assertThat(httpPage.getBody().nextCursor()).isEqualTo(second);
        var guest = http.postForObject("/auth/guest", null, TokenResponse.class);
        headers.setBearerAuth(guest.accessToken());
        assertThat(http.exchange("/orders", GET, new HttpEntity<>(headers), String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(403);
        assertThat(first).isLessThan(second);
    }

    private long searchOrder(long customerId, String status, String createdAt) {
        String no = "SFSEARCH" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO customer_order(order_no,customer_id,status,total_amount,created_at) VALUES(?,?,?,99,?)",
                no,
                customerId,
                status,
                createdAt);
        long id = jdbc.queryForObject("SELECT id FROM customer_order WHERE order_no=?", Long.class, no);
        jdbc.update(
                "INSERT INTO order_item(order_id,product_id,quantity,unit_price) VALUES(?,1,1,49),(?,2,1,50)", id, id);
        return id;
    }

    @Test
    void fullOrderNumberUsesExactIdentityWhileFiltersAndOwnershipStillApply(
            @Autowired com.serviceflow.security.JwtService jwt) {
        var owner = chatPrincipal(false);
        String number = "SF202611041234567890";
        for (String no : java.util.List.of(number, number + "9"))
            jdbc.update(
                    "INSERT INTO customer_order(order_no,customer_id,status,total_amount,created_at) VALUES(?,?,'PAID',99,'2026-11-03 16:00:00')",
                    no,
                    owner.customerId());
        var exact = new OrderModels.Search(null, null, null, null, number);
        assertThat(orders.search(owner.customerId(), exact, null, 10).items())
                .extracting(OrderModels.Summary::orderNo)
                .containsExactly(number);
        assertThat(orders.search(owner.customerId(), new OrderModels.Search(number, null, null, null), null, 10)
                        .items())
                .extracting(OrderModels.Summary::orderNo)
                .containsExactlyInAnyOrder(number, number + "9");
        var headers = new HttpHeaders();
        headers.setBearerAuth(jwt.issueUser(new com.serviceflow.model.AuthModels.User(
                        124, "lookup-owner", "", "CUSTOMER", owner.customerId(), true))
                .accessToken());
        var response = http.exchange("/orders?orderNo=" + number, GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains(number).doesNotContain(number + "9");
        assertThat(orders.search(
                                owner.customerId(),
                                new OrderModels.Search(number.substring(0, 10), null, null, null),
                                null,
                                10)
                        .items())
                .extracting(OrderModels.Summary::orderNo)
                .containsExactlyInAnyOrder(number, number + "9");
        assertThat(orders.search(chatPrincipal(false).customerId(), exact, null, 10)
                        .items())
                .isEmpty();
        assertThat(orders.search(
                                owner.customerId(),
                                new OrderModels.Search(null, "SHIPPED", null, null, number),
                                null,
                                10)
                        .items())
                .isEmpty();
        assertThat(orders.search(
                                owner.customerId(),
                                new OrderModels.Search(null, null, java.time.LocalDate.of(2026, 11, 5), null, number),
                                null,
                                10)
                        .items())
                .isEmpty();
        assertThat(orders.search(
                                owner.customerId(),
                                new OrderModels.Search(
                                        null,
                                        "PAID",
                                        java.time.LocalDate.of(2026, 11, 4),
                                        java.time.LocalDate.of(2026, 11, 4),
                                        number),
                                null,
                                10)
                        .items())
                .hasSize(1);
    }

    @Test
    void orderConversationPersistsCandidatesAndSelectsWithoutExecutingCancellation(
            @Autowired com.serviceflow.security.JwtService jwt,
            @Autowired com.fasterxml.jackson.databind.ObjectMapper json)
            throws Exception {
        var owner = chatPrincipal(false);
        long first = searchOrder(owner.customerId(), "PAID", "2026-11-02 16:00:00");
        long second = searchOrder(owner.customerId(), "PAID", "2026-11-02 16:00:00");
        String firstNo = jdbc.queryForObject("SELECT order_no FROM customer_order WHERE id=?", String.class, first);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.issueUser(new com.serviceflow.model.AuthModels.User(
                        124, "conversation-test", "", "CUSTOMER", owner.customerId(), true))
                .accessToken());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var session = http.postForObject(
                "/chat/sessions",
                new HttpEntity<>(java.util.Map.of("title", "订单测试"), headers),
                com.serviceflow.model.ChatModels.Session.class);
        String path = "/chat/sessions/" + session.publicId() + "/messages";
        String requestId = UUID.randomUUID().toString();
        var query = new com.serviceflow.model.ChatModels.MessageRequest("我的手机订单帮我取消", requestId, null);
        String response = http.postForObject(path + "/stream", new HttpEntity<>(query, headers), String.class);
        assertThat(response).contains("order_selection_required").doesNotContain("action_required");
        var history = http.exchange(
                        path, GET, new HttpEntity<>(headers), com.serviceflow.model.ChatModels.Message[].class)
                .getBody();
        var candidates = json.readTree(history[1].metadataJson()).path("orderCandidates");
        assertThat(candidates.path("items").size()).isEqualTo(2);
        String queryId = candidates.path("queryId").asText();
        var select = new com.serviceflow.model.ChatModels.MessageRequest(
                "选择订单",
                UUID.randomUUID().toString(),
                null,
                new com.serviceflow.model.ChatModels.OrderSelection(queryId, firstNo));
        String selected = http.postForObject(path + "/stream", new HttpEntity<>(select, headers), String.class);
        assertThat(selected).contains(firstNo).contains("action_required").contains("二次确认");
        assertThat(jdbc.queryForObject("SELECT status FROM customer_order WHERE id=?", String.class, first))
                .isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM order_operation WHERE order_id IN (?,?)", Long.class, first, second))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM refund_request WHERE order_id IN (?,?)", Long.class, first, second))
                .isZero();
        var followup = new com.serviceflow.model.ChatModels.MessageRequest(
                "它的物流呢", UUID.randomUUID().toString(), null);
        assertThat(http.postForObject(path + "/stream", new HttpEntity<>(followup, headers), String.class))
                .contains(firstNo)
                .doesNotContain("action_required");
        // Replaying a completed request must retain its saved candidate cards without re-querying the model.
        assertThat(http.postForObject(path + "/stream", new HttpEntity<>(query, headers), String.class))
                .contains("order_selection_required")
                .contains(queryId)
                .contains("\"replayed\":true");
        var stale = new com.serviceflow.model.ChatModels.MessageRequest(
                "选择订单",
                UUID.randomUUID().toString(),
                null,
                new com.serviceflow.model.ChatModels.OrderSelection(queryId, firstNo));
        assertThat(http.postForObject(path + "/stream", new HttpEntity<>(stale, headers), String.class))
                .contains("过期")
                .doesNotContain("action_required");
    }

    @Test
    void duplicateCancellationReadsCommittedResultDespiteAnOlderSnapshot() {
        String orderNo = "SF-RACE-" + UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO customer_order(order_no,customer_id,status,total_amount) VALUES (?,1,'PROCESSING',99)",
                orderNo);
        Long orderId = jdbc.queryForObject("SELECT id FROM customer_order WHERE order_no=?", Long.class, orderNo);
        jdbc.update(
                "INSERT INTO payment(order_id,status,refund_status,paid_at) VALUES (?,'PAID','NONE',CURRENT_TIMESTAMP(6))",
                orderId);
        TransactionTemplate staleTransaction = new TransactionTemplate(transactions);
        staleTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        TransactionTemplate winner = new TransactionTemplate(transactions);
        winner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        winner.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        OrderModels.Operation replay = staleTransaction.execute(status -> {
            // Open the losing request's snapshot before the other transaction commits.
            assertThat(orderMapper.findOperation(requestId)).isNull();
            OrderModels.Operation completed = winner.execute(ignored -> orders.cancel(orderNo, 1L, requestId));
            assertThat(completed).isNotNull();
            assertThat(completed.resultCode()).isEqualTo("CANCELLED");
            return orders.cancel(orderNo, 1L, requestId);
        });

        assertThat(replay).isNotNull();
        assertThat(replay.resultCode()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT version FROM customer_order WHERE id=?", Integer.class, orderId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM order_operation WHERE request_id=?", Integer.class, requestId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT refund_status FROM payment WHERE order_id=?", String.class, orderId))
                .isEqualTo("PROCESSING");
    }

    @Test
    void cancellationFailureRetryLateCallbackAndSuccessStayConsistent() {
        String orderNo = newOrder("PAID");
        String refundId = UUID.randomUUID().toString();
        orders.cancel(orderNo, 1, refundId);
        assertThat(orders.get(orderNo, 1).refund().status()).isEqualTo("PROCESSING");
        refunds.applySandboxEvent(refundId, refundEvent(1, "FAILED", "模拟通道故障"));
        assertThat(orders.get(orderNo, 1).payment().refundStatus()).isEqualTo("FAILED");
        assertThat(refunds.retry(refundId, 1).attemptNo()).isEqualTo(2);
        assertThat(refunds.retry(refundId, 1).attemptNo()).isEqualTo(2);
        assertThat(refunds.applySandboxEvent(refundId, refundEvent(1, "SUCCEEDED", ""))
                        .disposition())
                .isEqualTo("STALE_ATTEMPT");
        assertThat(orders.get(orderNo, 1).payment().refundStatus()).isEqualTo("PROCESSING");
        var success = refundEvent(2, "SUCCEEDED", "");
        refunds.applySandboxEvent(refundId, success);
        assertThat(refunds.applySandboxEvent(refundId, success).replayed()).isTrue();
        assertThat(orders.get(orderNo, 1).status()).isEqualTo("CANCELLED");
        assertThat(orders.get(orderNo, 1).payment().refundStatus()).isEqualTo("REFUNDED");
        assertThatThrownBy(() -> refunds.applySandboxEvent(refundId, refundEvent(2, "FAILED", "迟到失败")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(eventCount(refundId)).isEqualTo(3);
    }

    @Test
    void unpaidCancellationNeverCreatesARefundRequest() {
        String orderNo = newOrder("PENDING");
        orders.cancel(orderNo, 1, UUID.randomUUID().toString());
        var order = orders.get(orderNo, 1);
        assertThat(order.status()).isEqualTo("CANCELLED");
        assertThat(order.refund()).isNull();
        assertThat(order.payment().refundStatus()).isEqualTo("NONE");
    }

    @Test
    void duplicateCallbacksFromTwoTransactionsProduceOneEvent() throws Exception {
        String orderNo = newOrder("PAID");
        String refundId = UUID.randomUUID().toString();
        orders.cancel(orderNo, 1, refundId);
        var event = refundEvent(1, "SUCCEEDED", "");
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<RefundModels.EventResult> callback = () -> {
                start.await();
                return refunds.applySandboxEvent(refundId, event);
            };
            var first = executor.submit(callback);
            var second = executor.submit(callback);
            start.countDown();
            var a = first.get(20, java.util.concurrent.TimeUnit.SECONDS);
            var b = second.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(a.replayed() ^ b.replayed()).isTrue();
        }
        assertThat(eventCount(refundId)).isEqualTo(1);
        assertThat(orders.get(orderNo, 1).payment().refundStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void failedPaymentUpdateRollsBackRefundAndEventTogether() {
        String orderNo = newOrder("PAID");
        String refundId = UUID.randomUUID().toString();
        orders.cancel(orderNo, 1, refundId);
        jdbc.update(
                "UPDATE payment SET refund_status='FAILED' WHERE order_id=(SELECT id FROM customer_order WHERE order_no=?)",
                orderNo);
        assertThatThrownBy(() -> refunds.applySandboxEvent(refundId, refundEvent(1, "SUCCEEDED", "")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(eventCount(refundId)).isZero();
        assertThat(orders.get(orderNo, 1).refund().status()).isEqualTo("PROCESSING");
    }

    @Test
    void committedOperationIsBoundToItsCustomerAndTargetOrder() {
        String first = newOrder("PAID");
        String second = newOrder("PAID");
        String third = newOrder("PAID");
        String email = UUID.randomUUID() + "@example.invalid";
        jdbc.update("INSERT INTO customer(name,email) VALUES ('Recovery isolation fixture',?)", email);
        long otherCustomer = jdbc.queryForObject("SELECT id FROM customer WHERE email=?", Long.class, email);
        jdbc.update("UPDATE customer_order SET customer_id=? WHERE order_no=?", otherCustomer, third);
        String key = UUID.randomUUID().toString();
        orders.cancel(first, 1, key);
        assertThat(orders.cancellationResult(key, 1).order().orderNo()).isEqualTo(first);
        assertThatThrownBy(() -> orders.cancellationResult(key, otherCustomer))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> orders.cancel(first, otherCustomer, key))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> orders.cancel(second, 1, key))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> orders.cancel(third, otherCustomer, key))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(orders.get(third, otherCustomer).status()).isEqualTo("PAID");
        assertThat(orders.get(third, otherCustomer).refund()).isNull();
        assertThat(orders.get(second, 1).status()).isEqualTo("PAID");
        assertThat(orders.get(second, 1).refund()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_operation WHERE request_id=?", Integer.class, key))
                .isEqualTo(1);
    }

    @Test
    void staleSnapshotCollisionCannotReplayAResultForAnotherOrder() {
        String first = newOrder("PAID");
        String second = newOrder("PAID");
        String key = UUID.randomUUID().toString();
        TransactionTemplate stale = new TransactionTemplate(transactions);
        stale.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        TransactionTemplate winner = new TransactionTemplate(transactions);
        winner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> stale.execute(status -> {
                    assertThat(orderMapper.findOperation(key)).isNull();
                    winner.execute(ignored -> orders.cancel(first, 1, key));
                    return orders.cancel(second, 1, key);
                }))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(orders.get(second, 1).status()).isEqualTo("PAID");
        assertThat(orders.get(second, 1).refund()).isNull();
        assertThat(orders.cancellationResult(key, 1).order().orderNo()).isEqualTo(first);
    }

    private String newOrder(String paymentStatus) {
        String orderNo = "SF"
                + String.format(
                        "%020d",
                        java.util.concurrent.ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
        jdbc.update(
                "INSERT INTO customer_order(order_no,customer_id,status,total_amount) VALUES (?,1,?,10)",
                orderNo,
                "PAID".equals(paymentStatus) ? "PAID" : "CREATED");
        Long id = jdbc.queryForObject("SELECT id FROM customer_order WHERE order_no=?", Long.class, orderNo);
        jdbc.update("INSERT INTO payment(order_id,status,refund_status) VALUES (?,?,'NONE')", id, paymentStatus);
        return orderNo;
    }

    private String deliveredOrder() {
        String no = newOrder("PAID");
        jdbc.update("UPDATE customer_order SET status='DELIVERED' WHERE order_no=?", no);
        jdbc.update(
                "INSERT INTO order_item(order_id,product_id,quantity,unit_price) SELECT id,1,1,10 FROM customer_order WHERE order_no=?",
                no);
        return no;
    }

    @Test
    void singleItemReturnIsAuthorizedReviewedAndRefundedOnlyAfterReceipt(
            @Autowired com.serviceflow.service.ReturnService returns,
            @Autowired com.serviceflow.service.RefundDeliveryService delivery,
            @Autowired com.serviceflow.security.JwtService jwt) {
        String no = deliveredOrder();
        var headers = new HttpHeaders();
        headers.setBearerAuth(
                jwt.issueUser(new com.serviceflow.model.AuthModels.User(1, "customer", "", "CUSTOMER", 1L, true))
                        .accessToken());
        var applied = http.postForEntity(
                "/orders/" + no + "/return",
                new HttpEntity<>(java.util.Map.of("reason", "与需求不符"), headers),
                com.serviceflow.model.ReturnModels.View.class);
        assertThat(applied.getStatusCode()).isEqualTo(OK);
        var request = applied.getBody();
        assertThat(request.status()).isEqualTo("REQUESTED");
        assertThat(request.amount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(orders.get(no, 1).refund()).isNull();
        assertThatThrownBy(() -> returns.apply(no, 999999, "other")).hasMessageContaining("404");
        for (String step : java.util.List.of("review", "receive")) {
            assertThat(http.postForEntity(
                                    "/admin/returns/" + request.publicId() + "/" + step,
                                    new HttpEntity<>(
                                            java.util.Map.of("decision", "APPROVE", "note", "unauthorized"), headers),
                                    String.class)
                            .getStatusCode()
                            .value())
                    .isEqualTo(403);
        }
        assertThatThrownBy(() -> returns.receive(request.publicId(), "premature", "admin"))
                .hasMessageContaining("409");
        returns.review(request.publicId(), "APPROVE", "请交回门店服务台", "admin");
        assertThat(orders.get(no, 1).refund()).isNull();
        assertThat(orders.get(no, 1).returnRequest().reviewNote()).isEqualTo("请交回门店服务台");
        returns.receive(request.publicId(), "商品已收回，验收通过", "admin");
        assertThat(orders.get(no, 1).returnRequest().refundStatus()).isEqualTo("PROCESSING");
        long refundKey =
                jdbc.queryForObject("SELECT id FROM refund_request WHERE public_id=?", Long.class, request.publicId());
        jdbc.update(
                "UPDATE refund_delivery SET deadline_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE refund_id=?",
                refundKey);
        delivery.waitOrEscalate(delivery.claim(refundKey), "渠道待核验");
        assertThat(orders.get(no, 1).refundReview()).isNotNull();
        refunds.applySandboxEvent(request.publicId(), refundEvent(1, "FAILED", "渠道失败"));
        assertThat(orders.get(no, 1).returnRequest().refundStatus()).isEqualTo("FAILED");
        refunds.retry(request.publicId(), 1);
        var event = refundEvent(2, "SUCCEEDED", "");
        refunds.applySandboxEvent(request.publicId(), event);
        refunds.applySandboxEvent(request.publicId(), event);
        assertThat(orders.get(no, 1).returnRequest().refundStatus()).isEqualTo("SUCCEEDED");
        assertThat(orders.get(no, 1).payment().refundStatus()).isEqualTo("REFUNDED");
        assertThat(returns.receive(request.publicId(), "重复收货", "admin").publicId())
                .isEqualTo(request.publicId());
        assertThat(returns.apply(no, 1, "重复申请").publicId()).isEqualTo(request.publicId());
        assertThat(orders.get(no, 1).refund().amount()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void returnEligibilityRejectionAndReceiptRollbackPreserveMoney(
            @Autowired com.serviceflow.service.ReturnService returns) {
        for (String invalid : java.util.List.of("SHIPPED", "UNPAID", "QUANTITY", "MULTIPLE", "EMPTY", "ZERO")) {
            String no = deliveredOrder();
            switch (invalid) {
                case "SHIPPED" -> jdbc.update("UPDATE customer_order SET status='SHIPPED' WHERE order_no=?", no);
                case "UNPAID" -> jdbc.update(
                        "UPDATE payment SET status='PENDING' WHERE order_id=(SELECT id FROM customer_order WHERE order_no=?)",
                        no);
                case "QUANTITY" -> jdbc.update(
                        "UPDATE order_item SET quantity=2 WHERE order_id=(SELECT id FROM customer_order WHERE order_no=?)",
                        no);
                case "MULTIPLE" -> jdbc.update(
                        "INSERT INTO order_item(order_id,product_id,quantity,unit_price) SELECT id,1,1,10 FROM customer_order WHERE order_no=?",
                        no);
                case "EMPTY" -> jdbc.update(
                        "DELETE FROM order_item WHERE order_id=(SELECT id FROM customer_order WHERE order_no=?)", no);
                case "ZERO" -> jdbc.update("UPDATE customer_order SET total_amount=0 WHERE order_no=?", no);
                default -> throw new AssertionError();
            }
            assertThatThrownBy(() -> returns.apply(no, 1, "test")).hasMessageContaining("409");
            assertThat(orders.get(no, 1).returnRequest()).isNull();
            assertThat(orders.get(no, 1).refund()).isNull();
        }
        String rejected = deliveredOrder();
        var request = returns.apply(rejected, 1, "test");
        returns.review(request.publicId(), "REJECT", "请联系门店核实商品情况", "admin");
        assertThatThrownBy(() -> returns.review(request.publicId(), "APPROVE", "地址", "admin"))
                .hasMessageContaining("409");
        assertThatThrownBy(() -> returns.receive(request.publicId(), "收到", "admin"))
                .hasMessageContaining("409");
        assertThat(orders.get(rejected, 1).refund()).isNull();
        String no = deliveredOrder();
        var approved = returns.apply(no, 1, "test");
        returns.review(approved.publicId(), "APPROVE", "门店交回", "admin");
        jdbc.update("UPDATE customer_order SET total_amount=11 WHERE order_no=?", no);
        assertThatThrownBy(() -> returns.receive(approved.publicId(), "收到", "admin"))
                .hasMessageContaining("回滚");
        assertThat(orders.get(no, 1).returnRequest().status()).isEqualTo("APPROVED");
        assertThat(orders.get(no, 1).returnRequest().receivedAt()).isNull();
        assertThat(orders.get(no, 1).payment().refundStatus()).isEqualTo("NONE");
        assertThat(orders.get(no, 1).refund()).isNull();
    }

    @Test
    void concurrentReturnApplicationsAndReceiptsCreateOnlyOneRefund(
            @Autowired com.serviceflow.service.ReturnService returns) throws Exception {
        String no = deliveredOrder();
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var apply = new java.util.ArrayList<java.util.concurrent.Future<com.serviceflow.model.ReturnModels.View>>();
            for (int i = 0; i < 8; i++) apply.add(executor.submit(() -> returns.apply(no, 1, "duplicate")));
            String id = apply.getFirst().get().publicId();
            for (var result : apply) assertThat(result.get().publicId()).isEqualTo(id);
            returns.review(id, "APPROVE", "请交回门店", "admin");
            var receipts =
                    new java.util.ArrayList<java.util.concurrent.Future<com.serviceflow.model.ReturnModels.View>>();
            for (int i = 0; i < 8; i++) receipts.add(executor.submit(() -> returns.receive(id, "验收通过", "admin")));
            for (var result : receipts) assertThat(result.get().status()).isEqualTo("RECEIVED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refund_request WHERE public_id=?", Integer.class, id))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM refund_delivery d JOIN refund_request r ON r.id=d.refund_id WHERE r.public_id=?",
                            Integer.class,
                            id))
                    .isEqualTo(1);
            assertThat(orders.get(no, 1).refund().amount()).isEqualByComparingTo(BigDecimal.TEN);
        }
    }

    @Test
    void refundLeaseFencesCrashedWorkerAndManualReviewIsIdempotent(
            @Autowired com.serviceflow.service.RefundDeliveryService delivery,
            @Autowired com.serviceflow.mapper.RefundDeliveryMapper tasks)
            throws Exception {
        String no = newOrder("PAID"), id = UUID.randomUUID().toString();
        orders.cancel(no, 1, id);
        long refundId = jdbc.queryForObject("SELECT id FROM refund_request WHERE public_id=?", Long.class, id);
        var old = delivery.claim(refundId);
        assertThat(old).isNotNull();
        assertThat(delivery.claim(refundId)).isNull();
        jdbc.update(
                "UPDATE refund_delivery SET lease_until=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE refund_id=?", refundId);
        var current = delivery.claim(refundId);
        assertThat(current.task().leaseToken()).isNotEqualTo(old.task().leaseToken());
        var succeeded =
                new com.serviceflow.model.RefundDeliveryModels.ChannelResult(id, 1, "SUCCEEDED", BigDecimal.TEN, "");
        delivery.accept(old, succeeded);
        assertThat(orders.get(no, 1).refund().status()).isEqualTo("PROCESSING");
        delivery.accept(current, succeeded);
        assertThat(orders.get(no, 1).payment().refundStatus()).isEqualTo("REFUNDED");
        assertThat(delivery.claim(refundId)).isNull();
        assertThat(eventCount(id)).isEqualTo(1);
        String manualNo = newOrder("PAID"), manualId = UUID.randomUUID().toString();
        orders.cancel(manualNo, 1, manualId);
        long manualKey = jdbc.queryForObject("SELECT id FROM refund_request WHERE public_id=?", Long.class, manualId);
        jdbc.update(
                "UPDATE refund_delivery SET deadline_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE refund_id=?",
                manualKey);
        var claim = delivery.claim(manualKey);
        delivery.waitOrEscalate(claim, "渠道不可用");
        delivery.waitOrEscalate(claim, "重复回包");
        String ticket =
                jdbc.queryForObject("SELECT ticket_id FROM refund_delivery WHERE refund_id=?", String.class, manualKey);
        assertThat(orders.get(manualNo, 1).refund().failureReason()).contains(ticket);
        assertThat(jdbc.queryForObject("SELECT description FROM ticket WHERE public_id=?", String.class, ticket))
                .contains(manualNo, manualId, "查询次数");
        assertThat(delivery.claim(manualKey)).isNull();
        delivery.reconcile(manualId);
        var resumed = delivery.claim(manualKey);
        assertThat(resumed).isNotNull();
        delivery.accept(
                resumed,
                new com.serviceflow.model.RefundDeliveryModels.ChannelResult(
                        manualId, 1, "SUCCEEDED", BigDecimal.TEN, ""));
        assertThat(orders.get(manualNo, 1).refund().status()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket WHERE public_id=?", Integer.class, ticket))
                .isEqualTo(1);
        System.out.println("REFUND_LEASE_PASS staleWrites=0 duplicateRefunds=0 manualTickets=1 recovered=1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DUPLICATE", "CONFIRM_REJECT", "DIFFERENT_ACTIONS"})
    void durableActionRacesHaveOneDecisionAndOneRefund(
            String scenario,
            @Autowired com.serviceflow.service.ActionDecisionService decisions,
            @Autowired ChatSessionService sessions)
            throws Exception {
        var owner = new CurrentPrincipal("action-owner", "CUSTOMER", 1L);
        String session = sessions.create(owner, scenario).publicId();
        int rejected = 0;
        for (int round = 0; round < 100; round++) {
            String no = newOrder("PAID");
            var pending = new com.serviceflow.model.ChatActionModels.Pending(
                    "CANCEL_ORDER", owner.subject(), 1, session, no, "取消订单");
            String source = UUID.randomUUID().toString();
            String first = decisions.prepare(pending, source);
            assertThat(decisions.prepare(pending, source)).isEqualTo(first);
            String second = "DIFFERENT_ACTIONS".equals(scenario)
                    ? decisions.prepare(pending, UUID.randomUUID().toString())
                    : first;
            var start = new java.util.concurrent.CountDownLatch(1);
            int count = "DUPLICATE".equals(scenario) ? 20 : 2;
            var results = new java.util.ArrayList<com.serviceflow.model.ChatActionModels.Result>();
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new java.util.ArrayList<
                        java.util.concurrent.Future<com.serviceflow.model.ChatActionModels.Result>>();
                for (int i = 0; i < count; i++) {
                    String id = i == 0 ? first : second;
                    String decision = "CONFIRM_REJECT".equals(scenario) && i == 1 ? "REJECT" : "CONFIRM";
                    futures.add(executor.submit(() -> {
                        start.await();
                        return decisions.decide(owner, id, decision);
                    }));
                }
                start.countDown();
                for (var future : futures) results.add(future.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            if (!"DIFFERENT_ACTIONS".equals(scenario))
                assertThat(results)
                        .extracting(com.serviceflow.model.ChatActionModels.Result::actionStatus)
                        .containsOnly(results.getFirst().actionStatus());
            var result = decisions.result(owner, first);
            assertThat(decisions.decide(owner, first, "REJECT").actionStatus()).isEqualTo(result.actionStatus());
            boolean cancelled = !"REJECTED".equals(result.actionStatus());
            if (!cancelled) rejected++;
            assertThat(orders.get(no, 1).status()).isEqualTo(cancelled ? "CANCELLED" : "PAID");
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM refund_request WHERE order_id=(SELECT id FROM customer_order WHERE order_no=?)",
                            Integer.class,
                            no))
                    .isEqualTo(cancelled ? 1 : 0);
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM order_operation WHERE order_id=(SELECT id FROM customer_order WHERE order_no=?)",
                            Integer.class,
                            no))
                    .isEqualTo(cancelled ? 1 : 0);
        }
        System.out.println("ACTION_RACE_PASS scenario=" + scenario + " rounds=100 rejected=" + rejected);
    }

    @Test
    void durableActionsRejectExpiredForeignAndChangedOrdersAndRollbackTogether(
            @Autowired com.serviceflow.service.ActionDecisionService decisions,
            @Autowired ChatSessionService sessions) {
        var owner = new CurrentPrincipal("action-boundary", "CUSTOMER", 1L);
        String session = sessions.create(owner, "action-boundaries").publicId();
        for (String boundary : java.util.List.of("EXPIRED", "SHIPPED", "VERSION", "REJECT", "ROLLBACK", "FOREIGN")) {
            String no = newOrder("PAID");
            String id = decisions.prepare(
                    new com.serviceflow.model.ChatActionModels.Pending(
                            "CANCEL_ORDER", owner.subject(), 1, session, no, boundary),
                    UUID.randomUUID().toString());
            switch (boundary) {
                case "EXPIRED" -> jdbc.update(
                        "UPDATE chat_action SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE public_id=?", id);
                case "SHIPPED" -> jdbc.update("UPDATE customer_order SET status='SHIPPED' WHERE order_no=?", no);
                case "VERSION" -> jdbc.update("UPDATE customer_order SET version=version+1 WHERE order_no=?", no);
                case "REJECT" -> decisions.decide(owner, id, "REJECT");
                case "ROLLBACK" -> jdbc.update(
                        "UPDATE payment SET refund_status='FAILED' WHERE order_id=(SELECT id FROM customer_order WHERE order_no=?)",
                        no);
                case "FOREIGN" -> {
                    assertThatThrownBy(() -> decisions.decide(chatPrincipal(false), id, "CONFIRM"))
                            .hasMessageContaining("404");
                    assertThat(decisions.result(owner, id).actionStatus()).isEqualTo("PENDING");
                    continue;
                }
                default -> throw new AssertionError();
            }
            if ("ROLLBACK".equals(boundary)) {
                assertThatThrownBy(() -> decisions.decide(owner, id, "CONFIRM")).hasMessageContaining("409");
                assertThat(decisions.result(owner, id).actionStatus()).isEqualTo("PENDING");
                assertThat(orders.get(no, 1).status()).isEqualTo("PAID");
                assertThat(jdbc.queryForObject(
                                "SELECT COUNT(*) FROM order_operation WHERE request_id=?", Integer.class, id))
                        .isZero();
            } else {
                String expected =
                        switch (boundary) {
                            case "EXPIRED" -> "EXPIRED";
                            case "SHIPPED" -> "NOT_CANCELLABLE";
                            case "VERSION" -> "CONFLICT";
                            default -> "REJECTED";
                        };
                assertThat(decisions.decide(owner, id, "CONFIRM").operation().resultCode())
                        .isEqualTo(expected);
                assertThat(decisions.result(owner, id).operation().resultCode()).isEqualTo(expected);
            }
            assertThat(orders.get(no, 1).refund()).isNull();
        }
    }

    private RefundModels.SandboxEvent refundEvent(int attempt, String outcome, String reason) {
        return new RefundModels.SandboxEvent(UUID.randomUUID().toString(), attempt, outcome, BigDecimal.TEN, reason);
    }

    @Test
    void refundReviewSurvivesSessionsAndReconciliationWithoutExposingInternalNotes(
            @Autowired com.serviceflow.service.RefundDeliveryService delivery,
            @Autowired com.serviceflow.service.TicketService tickets,
            @Autowired com.serviceflow.mapper.RefundDeliveryMapper tasks) {
        String no = newOrder("PAID"), refundId = UUID.randomUUID().toString();
        orders.cancel(no, 1, refundId);
        long id = jdbc.queryForObject("SELECT id FROM refund_request WHERE public_id=?", Long.class, refundId);
        jdbc.update("UPDATE refund_delivery SET deadline_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE refund_id=?", id);
        delivery.waitOrEscalate(delivery.claim(id), "unknown");
        var original = orders.get(no, 1).refundReview();
        assertThat(original.progress()).isEqualTo("等待人工核验");
        assertThatThrownBy(() -> orders.get(no, 999999)).hasMessageContaining("404");
        tickets.updateStatus(original.publicId(), "ASSIGNED", "INTERNAL ONLY", "admin");
        tickets.publishCustomerMessage(original.publicId(), "已联系渠道核验，请关注本单进度");
        assertThat(orders.get(no, 1).refundReview().customerMessage()).isEqualTo("已联系渠道核验，请关注本单进度");
        assertThat(orders.get(no, 1).refundReview().toString()).doesNotContain("INTERNAL ONLY");
        delivery.reconcile(refundId);
        assertThat(orders.get(no, 1).refundReview().progress()).isEqualTo("正在重新核验渠道");
        delivery.waitOrEscalate(delivery.claim(id), "still unknown");
        assertThat(tasks.lock(id).ticketId()).isEqualTo(original.publicId());
        // Legacy tasks could lose ticket_id during a retry. The stable source action still identifies the same ticket.
        jdbc.update("UPDATE refund_delivery SET ticket_id=NULL WHERE refund_id=?", id);
        assertThat(orders.get(no, 1).refundReview().publicId()).isEqualTo(original.publicId());
        delivery.callback(refundId, refundEvent(1, "SUCCEEDED", ""));
        assertThat(orders.get(no, 1).refundReview().progress()).isEqualTo("退款已完成");
        assertThat(orders.get(no, 1).refundReview().publicId()).isEqualTo(original.publicId());
        assertThat(eventCount(refundId)).isEqualTo(1);
    }

    private int eventCount(String publicId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM refund_event e JOIN refund_request r ON r.id=e.refund_id WHERE r.public_id=?",
                Integer.class,
                publicId);
    }

    private record TokenResponse(String accessToken) {}
}
