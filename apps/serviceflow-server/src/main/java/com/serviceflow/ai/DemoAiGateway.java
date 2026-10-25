package com.serviceflow.ai;

import com.serviceflow.agent.Intent;
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
        if (query.contains("订单") || query.contains("物流") || query.contains("退款") || query.contains("取消")) {
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
    public void streamAnswer(String systemPrompt, String userPrompt, Consumer<String> tokenConsumer) {
        String answer = systemPrompt.isBlank() ? "您好，我可以帮助您了解商品、查询订单或处理售后问题。" : systemPrompt;
        for (String part : answer.split("(?<=。|；|！|\\n)")) {
            if (!part.isBlank()) {
                tokenConsumer.accept(part);
            }
        }
    }
}
