package com.serviceflow.agent;

import com.serviceflow.ai.AiGateway;
import com.serviceflow.ai.AiGateway.OrderCommand;
import com.serviceflow.ai.OrderCancellationIntent;
import com.serviceflow.infrastructure.redis.ChatMemory;
import com.serviceflow.model.ChatActionModels;
import com.serviceflow.model.OrderModels;
import com.serviceflow.service.ActionDecisionService;
import com.serviceflow.service.OrderService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.server.ResponseStatusException;

/** Executes a bounded, read-only order plan; cancellation still requires a separate confirmation. */
final class OrderConversation {
    private static final Logger log = LoggerFactory.getLogger(OrderConversation.class);
    private static final Pattern ORDER_NO = Pattern.compile("SF\\d{12,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXACT_LOOKUP =
            Pattern.compile("^(?:查询|查看|查)?(?:订单)?[:：\\s]*SF\\d{12,}[\\s。]*$", Pattern.CASE_INSENSITIVE);
    private static final java.util.Set<String> CURRENT_LOOKUPS = java.util.Set.of(
            "查询订单状态", "订单状态", "查物流", "查询物流", "它的物流呢", "退款进度", "查询退款进度", "它的退款进度呢", "退货进度", "查询退货进度", "工单进度", "查询退款工单",
            "退款工单结果");
    private static final Pattern ORDINAL = Pattern.compile("^(?:选|选择)?第?([1-9一二三四五六七八九十两])(?:笔|个|单)(?:订单)?[。！!\\s]*$");
    private final AiGateway ai;
    private final OrderService orders;
    private final ChatMemory memory;
    private final ActionDecisionService actions;

    OrderConversation(AiGateway ai, OrderService orders, ChatMemory memory, ActionDecisionService actions) {
        this.ai = ai;
        this.orders = orders;
        this.memory = memory;
        this.actions = actions;
    }

    static boolean isSelection(String query) {
        return ORDINAL.matcher(query.strip()).matches();
    }

    static boolean isExactLookup(String query) {
        return EXACT_LOOKUP.matcher(query.strip()).matches();
    }

    static boolean isCurrentLookup(String query) {
        return CURRENT_LOOKUPS.contains(query.replaceAll("[\\s。！？?!]", ""));
    }

    CustomerWorkflow.Result respond(ServiceFlowState state, BiConsumer<String, Object> events) {
        if (state.principal().guest()) return answer("订单服务需要登录客户账号后使用。");
        try {
            return respondToCustomer(state, events);
        } catch (DataAccessException unavailable) {
            return unavailable();
        } catch (ResponseStatusException unavailable) {
            if (!unavailable.getStatusCode().is5xxServerError()) throw unavailable;
            return unavailable();
        }
    }

    private CustomerWorkflow.Result respondToCustomer(ServiceFlowState state, BiConsumer<String, Object> events) {
        String subject = state.principal().subject();
        String session = state.sessionId();
        ChatMemory.OrderCandidates pending = memory.orderCandidates(subject, session);
        if (pending != null && pending.cancellation() && OrderCancellationIntent.readOnly(state.query())) {
            pending = new ChatMemory.OrderCandidates(pending.queryId(), pending.items(), pending.hasMore(), false);
            memory.putOrderCandidates(subject, session, pending);
        }
        if (state.orderSelection() != null) {
            var selection = state.orderSelection();
            if (pending == null
                    || !pending.queryId().equals(selection.queryId())
                    || pending.items().stream().noneMatch(item -> item.orderNo().equals(selection.orderNo()))) {
                return answer("这组订单选项已过期或已被新的查询替换，请重新查询后选择。");
            }
            return show(state, selection.orderNo(), pending.cancellation(), events);
        }
        var number = ORDER_NO.matcher(state.query());
        if (number.find()) {
            String explicit = number.group().toUpperCase(java.util.Locale.ROOT);
            if (number.find()) return answer("这次提到了多个订单号，请明确选择要处理的一笔订单。");
            return show(state, explicit, wantsCancellation(state.query()), events);
        }
        var ordinal = ORDINAL.matcher(state.query().strip());
        if (ordinal.matches()) {
            String value = ordinal.group(1).replace("两", "二");
            int index = Character.isDigit(value.charAt(0)) ? Integer.parseInt(value) : "一二三四五六七八九十".indexOf(value) + 1;
            return choose(state, pending, index, pending != null && pending.cancellation(), events);
        }
        String current = pending == null ? state.activeOrderNo() : null;
        if (current == null && pending == null) current = memory.activeOrder(subject, session);
        if (current != null && isCurrentLookup(state.query())) return show(state, current, false, events);
        if (OrderSearchRules.ambiguousStatus(state.query())) {
            return answer("未发货涉及待付款、已付款和处理中，请明确要查询的状态。");
        }
        // Page context is untrusted input. Check ownership before treating it as usable context.
        if (current != null) orders.get(current, state.principal().requireCustomerId());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        OrderModels.Search fallback = OrderSearchRules.search(state.query(), today);
        OrderCommand command = null;
        try {
            command = ai.requestOrderTool(
                            state.query(), today, current != null, pending == null ? List.of() : pending.items())
                    .orElse(null);
        } catch (RuntimeException failure) {
            if (Thread.currentThread().isInterrupted()) throw failure;
            log.debug("Order tool planning degraded ({})", failure.getClass().getSimpleName());
        }
        if (command == null
                || (command instanceof OrderCommand.Search search
                        && search.filters().equals(new OrderModels.Search(null, null, null, null))
                        && !OrderSearchRules.listsOrders(state.query()))) {
            command = fallback == null ? new OrderCommand.AskUser() : new OrderCommand.Search(fallback);
            log.debug("Order tool source=rules command={}", command.getClass().getSimpleName());
        } else {
            log.debug("Order tool source=model command={}", command.getClass().getSimpleName());
        }
        boolean cancel = wantsCancellation(state.query());
        return switch (command) {
            case OrderCommand.Search search -> search(state, search.filters(), cancel, events);
            case OrderCommand.ReadCurrent ignored -> {
                if (fallback != null) yield search(state, fallback, cancel, events);
                yield current == null ? ask(pending, events) : show(state, current, cancel, events);
            }
            case OrderCommand.SelectCandidate selected -> {
                var latest = memory.orderCandidates(subject, session);
                yield Objects.equals(pending, latest)
                        ? choose(state, pending, selected.index(), false, events)
                        : ask(latest, events);
            }
            case OrderCommand.AskUser ignored -> ask(pending, events);
        };
    }

    private CustomerWorkflow.Result search(
            ServiceFlowState state,
            OrderModels.Search filters,
            boolean cancellation,
            BiConsumer<String, Object> events) {
        OrderModels.Page found = orders.search(state.principal().requireCustomerId(), filters, null, 10);
        if (found.items().size() == 1 && found.nextCursor() == null) {
            return show(state, found.items().getFirst().orderNo(), cancellation, events);
        }
        memory.clearActiveOrder(state.principal().subject(), state.sessionId());
        memory.clearOrderCandidates(state.principal().subject(), state.sessionId());
        var candidates = new ChatMemory.OrderCandidates(
                UUID.randomUUID().toString(), found.items(), found.nextCursor() != null, cancellation);
        if (!found.items().isEmpty())
            memory.putOrderCandidates(state.principal().subject(), state.sessionId(), candidates);
        events.accept("order_selection_required", candidates);
        if (found.items().isEmpty()) return answer("没有找到符合条件的本人订单，请检查商品名称或下单日期，也可以在“我的订单”中查看。");
        return answer(
                found.nextCursor() == null
                        ? "找到多笔符合条件的订单，请点击要处理的订单，或回复“第一笔”“第二笔”。"
                        : "符合条件的订单较多，先展示最近的10笔。请选择其中一笔，或补充商品名称、日期缩小范围。");
    }

    private CustomerWorkflow.Result choose(
            ServiceFlowState state,
            ChatMemory.OrderCandidates pending,
            Integer index,
            boolean cancel,
            BiConsumer<String, Object> events) {
        if (pending == null
                || index == null
                || index < 1
                || index > pending.items().size()) return ask(pending, events);
        return show(state, pending.items().get(index - 1).orderNo(), cancel, events);
    }

    private CustomerWorkflow.Result ask(ChatMemory.OrderCandidates pending, BiConsumer<String, Object> events) {
        if (pending != null) {
            events.accept("order_selection_required", pending);
            return answer("还不能确定您指的是哪一笔，请点击订单卡片或回复对应序号。");
        }
        return answer("请告诉我商品名称或下单日期，也可以从“我的订单”选择一笔后咨询。");
    }

    private CustomerWorkflow.Result show(
            ServiceFlowState state, String orderNo, boolean cancel, BiConsumer<String, Object> events) {
        long customerId = state.principal().requireCustomerId();
        OrderModels.OrderView order = orders.get(orderNo, customerId);
        boolean canCancel = cancel && orders.canCancel(orderNo, customerId);
        String actionId = canCancel
                ? actions.prepare(
                        new ChatActionModels.Pending(
                                "CANCEL_ORDER",
                                state.principal().subject(),
                                customerId,
                                state.sessionId(),
                                orderNo,
                                state.query()),
                        state.clientRequestId())
                : null;
        memory.clearOrderCandidates(state.principal().subject(), state.sessionId());
        memory.setActiveOrder(state.principal().subject(), state.sessionId(), orderNo);
        events.accept("order", order);
        if (cancel) {
            if (!canCancel) return answer("该订单已发货或结束，不能直接取消；可以咨询退货政策。");
            String purchase = order.items().isEmpty()
                    ? "该订单"
                    : order.items().getFirst().productName()
                            + (order.items().size() > 1 ? "等" + order.items().size() + "种商品" : "");
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
                            "确认取消" + purchase + "（订单 " + orderNo + "，¥" + order.totalAmount() + "）吗？"));
            return answer("订单当前可以取消，请在确认卡片中二次确认。");
        }
        String refund = order.payment() == null ? "暂无" : order.payment().refundStatus();
        String shipment =
                order.shipment().isEmpty() ? "暂无" : order.shipment().getFirst().description();
        String progress = order.refund() != null && order.refund().failureReason() != null
                ? "退款处理说明：" + order.refund().failureReason() + "。"
                : "";
        if (order.refundReview() != null) {
            var review = order.refundReview();
            progress += "人工核验工单 " + review.publicId() + "，" + review.progress() + "。";
            if (review.customerMessage() != null) progress += "客服回复：" + review.customerMessage() + "。";
        }
        if (order.returnRequest() != null) {
            var application = order.returnRequest();
            String stage =
                    switch (application.status()) {
                        case "REQUESTED" -> "等待客服审核";
                        case "APPROVED" -> "审核通过，请按指引退回商品";
                        case "REJECTED" -> "审核未通过";
                        case "RECEIVED" -> switch (application.refundStatus() == null
                                ? ""
                                : application.refundStatus()) {
                            case "SUCCEEDED" -> "退货退款已完成";
                            case "FAILED" -> "商家已收到退货，退款失败，等待客服处理";
                            default -> "商家已收到退货，退款处理中";
                        };
                        default -> application.status();
                    };
            progress += "退货单 " + application.publicId() + "，" + stage + "。";
            if (application.reviewNote() != null) progress += "审核说明：" + application.reviewNote() + "。";
        } else if ("DELIVERED".equals(order.status()) && state.query().contains("退货")) {
            progress += "请在订单详情填写退货原因并提交申请，客服审核后会提供退回指引。";
        }
        return answer("订单 " + orderNo + " 当前状态为 " + order.status() + "，退款状态为 " + refund + "，最新物流：" + shipment + "。"
                + progress);
    }

    static boolean wantsCancellation(String query) {
        return OrderCancellationIntent.requested(query);
    }

    private CustomerWorkflow.Result answer(String text) {
        return new CustomerWorkflow.Result(text, List.of());
    }

    private CustomerWorkflow.Result unavailable() {
        return answer("订单服务暂时不可用，请稍后重试。");
    }
}
