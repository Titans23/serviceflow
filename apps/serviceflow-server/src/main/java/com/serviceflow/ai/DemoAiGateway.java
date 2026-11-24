package com.serviceflow.ai;

import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "serviceflow.ai.mode", havingValue = "demo", matchIfMissing = true)
public final class DemoAiGateway implements AiGateway {

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
