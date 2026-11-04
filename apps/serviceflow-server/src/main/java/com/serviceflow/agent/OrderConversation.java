package com.serviceflow.agent;

import com.serviceflow.ai.AiGateway;
import com.serviceflow.infrastructure.redis.ChatMemory;
import com.serviceflow.model.ChatActionModels;
import com.serviceflow.model.OrderModels;
import com.serviceflow.service.ActionDecisionService;
import com.serviceflow.service.OrderService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/** Executes a bounded, read-only order plan; cancellation still requires a separate confirmation. */
final class OrderConversation {
    private static final Pattern ORDER_NO = Pattern.compile("SF\\d{12,}", Pattern.CASE_INSENSITIVE);
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

    CustomerWorkflow.Result respond(ServiceFlowState state, BiConsumer<String, Object> events) {
        if (state.principal().guest()) return answer("订单服务需要登录客户账号后使用。");
        String subject = state.principal().subject();
        String session = state.sessionId();
        ChatMemory.OrderCandidates pending = memory.orderCandidates(subject, session);
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
        // Page context is untrusted input. Check ownership before treating it as usable context.
        if (current != null) orders.get(current, state.principal().requireCustomerId());
        AiGateway.OrderPlan plan = ai.planOrder(
                state.query(),
                LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(),
                current != null,
                pending == null ? List.of() : pending.items(),
                pending != null && pending.cancellation());
        if (plan == null || plan.tool() == null || plan.purpose() == null) {
            return answer("请提供订单号，或告诉我商品名称和大致下单日期。");
        }
        boolean cancel = plan.purpose() == AiGateway.OrderPurpose.CANCEL;
        return switch (plan.tool()) {
            case SEARCH_ORDERS -> search(state, plan, events);
            case READ_CURRENT -> current == null ? ask(pending, events) : show(state, current, cancel, events);
            case SELECT_CANDIDATE -> choose(state, pending, plan.candidateIndex(), cancel, events);
            case ASK_USER -> ask(pending, events);
        };
    }

    private CustomerWorkflow.Result search(
            ServiceFlowState state, AiGateway.OrderPlan plan, BiConsumer<String, Object> events) {
        LocalDate from, to;
        try {
            from = plan.fromDate() == null ? null : LocalDate.parse(plan.fromDate());
            to = plan.toDate() == null ? null : LocalDate.parse(plan.toDate());
        } catch (java.time.DateTimeException error) {
            return answer("没有识别出有效的下单日期，请提供 YYYY-MM-DD 格式的日期。");
        }
        OrderModels.Page found = orders.search(
                state.principal().requireCustomerId(),
                new OrderModels.Search(plan.keyword(), plan.status(), from, to),
                null,
                10);
        memory.clearActiveOrder(state.principal().subject(), state.sessionId());
        memory.clearOrderCandidates(state.principal().subject(), state.sessionId());
        boolean cancellation = plan.purpose() == AiGateway.OrderPurpose.CANCEL;
        if (found.items().size() == 1 && found.nextCursor() == null) {
            return show(state, found.items().getFirst().orderNo(), cancellation, events);
        }
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
        memory.clearOrderCandidates(state.principal().subject(), state.sessionId());
        memory.setActiveOrder(state.principal().subject(), state.sessionId(), orderNo);
        events.accept("order", order);
        if (cancel) {
            if (!orders.canCancel(orderNo, customerId)) return answer("该订单已发货或结束，不能直接取消；可以咨询退货政策。");
            String actionId = actions.prepare(
                    new ChatActionModels.Pending(
                            "CANCEL_ORDER",
                            state.principal().subject(),
                            customerId,
                            state.sessionId(),
                            orderNo,
                            state.query()),
                    state.clientRequestId());
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
                            "确认取消订单 " + orderNo + "（¥" + order.totalAmount() + "）吗？"));
            return answer("订单当前可以取消，请在确认卡片中二次确认。");
        }
        String refund = order.payment() == null ? "暂无" : order.payment().refundStatus();
        String shipment =
                order.shipment().isEmpty() ? "暂无" : order.shipment().getFirst().description();
        String progress = order.refund() != null && order.refund().failureReason() != null
                ? "退款处理说明：" + order.refund().failureReason() + "。"
                : "";
        return answer("订单 " + orderNo + " 当前状态为 " + order.status() + "，退款状态为 " + refund + "，最新物流：" + shipment + "。"
                + progress);
    }

    static boolean wantsCancellation(String query) {
        return query.contains("取消") && !query.matches(".*(?:不取消|不要取消|别取消|不想取消).*");
    }

    private CustomerWorkflow.Result answer(String text) {
        return new CustomerWorkflow.Result(text, List.of());
    }
}
