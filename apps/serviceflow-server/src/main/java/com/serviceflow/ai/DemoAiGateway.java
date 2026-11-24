package com.serviceflow.ai;

import com.serviceflow.model.OrderModels;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "serviceflow.ai.mode", havingValue = "demo", matchIfMissing = true)
public final class DemoAiGateway implements AiGateway {

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
