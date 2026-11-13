package com.serviceflow.ai;

import com.serviceflow.agent.Intent;
import com.serviceflow.model.OrderModels;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "serviceflow.ai.mode", havingValue = "demo", matchIfMissing = true)
public final class DemoAiGateway implements AiGateway {

    @Override
    public Intent classify(String message) {
        String query = message.toLowerCase(Locale.ROOT);
        if (query.contains("投诉") || query.contains("人工客服")) {
            return Intent.COMPLAINT;
        }
        // Ignore explicitly declined lookups, but preserve a request after a contrast or punctuation.
        // Do not strip missing deliveries/refunds such as “没有收到订单”.
        query = query.replaceAll("(?:不用|不需要|无需)(?:查询|查|咨询|了解).*?(?=[，。！？；,!?;\\n]|但是|但|不过|而是|$)", "")
                .replaceAll("(?:暂时没有|目前没有|暂无)[^，。！？；,!?;\\n]*?(?:问题|需求)", "");
        if (query.contains("订单")
                || query.contains("物流")
                || query.contains("退款")
                || query.contains("取消")
                || query.contains("买的")
                || query.contains("买过")
                || query.contains("下单")) {
            return Intent.ORDER_QUERY;
        }
        if (query.contains("商品")
                || query.contains("型号")
                || query.contains("区别")
                || query.contains("对比")
                || query.contains("这款")) {
            return Intent.PRODUCT_QUERY;
        }
        if (query.contains("退货") || query.contains("换货") || query.contains("保修") || query.contains("政策")) {
            return Intent.KNOWLEDGE_QUERY;
        }
        return Intent.CHAT;
    }

    @Override
    public OrderPlan planOrder(
            String message,
            String today,
            boolean hasCurrentOrder,
            List<OrderModels.Summary> candidates,
            boolean pendingCancellation) {
        boolean cancel = OrderCancellationIntent.requested(message);
        String keyword = List.of("耳机", "手机", "笔记本", "电脑").stream()
                .filter(message::contains)
                .findFirst()
                .orElse(null);
        LocalDate day = LocalDate.parse(today);
        String from = null, to = null;
        if (message.contains("昨天")) from = to = day.minusDays(1).toString();
        else if (message.contains("前天")) from = to = day.minusDays(2).toString();
        else if (message.contains("今天")) from = to = today;
        else if (message.contains("最近一周")) {
            from = day.minusDays(6).toString();
            to = today;
        }
        boolean search = keyword != null || from != null || message.matches(".*(?:所有|其他|最近|我的订单|买过).*");
        return new OrderPlan(
                search || !hasCurrentOrder ? OrderTool.SEARCH_ORDERS : OrderTool.READ_CURRENT,
                cancel ? OrderPurpose.CANCEL : OrderPurpose.QUERY,
                keyword,
                null,
                from,
                to,
                null);
    }

    @Override
    public void streamAnswer(String systemPrompt, String userPrompt, Consumer<String> tokenConsumer) {
        String answer = systemPrompt.isBlank() ? "您好，我可以帮助您了解商品、查询订单或处理售后问题。" : systemPrompt;
        for (String part : answer.split("(?<=。|；|！|\\n)")) {
            if (!part.isBlank()) {
                tokenConsumer.accept(part);
            }
        }
    }
}
