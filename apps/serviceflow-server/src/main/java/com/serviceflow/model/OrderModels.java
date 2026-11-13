package com.serviceflow.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class OrderModels {
    private OrderModels() {}

    public record Order(
            long id,
            String orderNo,
            long customerId,
            String status,
            BigDecimal totalAmount,
            int version,
            Instant createdAt) {}

    public record Item(String sku, String productName, int quantity, BigDecimal unitPrice) {}

    public record Search(String keyword, String status, LocalDate from, LocalDate to, String orderNo) {
        public Search(String keyword, String status, LocalDate from, LocalDate to) {
            this(keyword, status, from, to, null);
        }
    }

    public record Summary(String orderNo, String status, BigDecimal totalAmount, Instant createdAt, List<Item> items) {}

    public record Page(List<Summary> items, Long nextCursor) {}

    public record ItemRow(long orderId, String sku, String productName, int quantity, BigDecimal unitPrice) {
        public Item item() {
            return new Item(sku, productName, quantity, unitPrice);
        }
    }

    public record Shipment(String description, String location, Instant occurredAt) {}

    public record Payment(String status, String refundStatus, Instant paidAt, Instant refundRequestedAt) {}

    public record OrderView(
            String orderNo,
            String status,
            BigDecimal totalAmount,
            int version,
            Instant createdAt,
            List<Item> items,
            Payment payment,
            List<Shipment> shipment,
            RefundModels.RefundView refund,
            TicketModels.RefundReview refundReview,
            ReturnModels.View returnRequest) {}

    public record Operation(String requestId, String resultCode, String resultMessage) {}

    public record CancellationResult(Operation operation, OrderView order) {}
}
