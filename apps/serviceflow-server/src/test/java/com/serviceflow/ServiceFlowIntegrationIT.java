package com.serviceflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;

import com.serviceflow.mapper.OrderMapper;
import com.serviceflow.model.OrderModels;
import com.serviceflow.service.OrderService;
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

    @Autowired
    ServiceFlowIntegrationIT(
            TestRestTemplate http,
            JdbcTemplate jdbc,
            OrderService orders,
            OrderMapper orderMapper,
            PlatformTransactionManager transactions) {
        this.http = http;
        this.jdbc = jdbc;
        this.orders = orders;
        this.orderMapper = orderMapper;
        this.transactions = transactions;
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

    private record TokenResponse(String accessToken) {}
}
