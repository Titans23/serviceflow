package com.serviceflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;

import com.serviceflow.mapper.OrderMapper;
import com.serviceflow.model.OrderModels;
import com.serviceflow.model.RefundModels;
import com.serviceflow.service.OrderService;
import com.serviceflow.service.RefundService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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

    private RefundModels.SandboxEvent refundEvent(int attempt, String outcome, String reason) {
        return new RefundModels.SandboxEvent(UUID.randomUUID().toString(), attempt, outcome, BigDecimal.TEN, reason);
    }

    private int eventCount(String publicId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM refund_event e JOIN refund_request r ON r.id=e.refund_id WHERE r.public_id=?",
                Integer.class,
                publicId);
    }

    private record TokenResponse(String accessToken) {}
}
