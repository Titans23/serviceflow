package com.serviceflow.agent;

import java.util.Locale;

public enum Intent {
    CHAT,
    PRODUCT_QUERY,
    KNOWLEDGE_QUERY,
    ORDER_QUERY,
    COMPLAINT;

    public static Intent fromKeywords(String message) {
        String query = message.toLowerCase(Locale.ROOT);
        if (query.contains("投诉") || query.contains("人工客服")) {
            return COMPLAINT;
        }
        // Ignore declined lookups while preserving a later request and reports of missing deliveries or refunds.
        query = query.replaceAll("(?:不用|不需要|无需)(?:查询|查|咨询|了解).*?(?=[，。！？；,!?;\\n]|但是|但|不过|而是|$)", "")
                .replaceAll("(?:暂时没有|目前没有|暂无)[^，。！？；,!?;\\n]*?(?:问题|需求)", "");
        if (query.contains("订单")
                || query.contains("物流")
                || query.contains("退款")
                || query.contains("取消")
                || query.contains("买的")
                || query.contains("买过")
                || query.contains("下单")) {
            return ORDER_QUERY;
        }
        if (query.contains("商品")
                || query.contains("型号")
                || query.contains("区别")
                || query.contains("对比")
                || query.contains("这款")) {
            return PRODUCT_QUERY;
        }
        if (query.contains("退货") || query.contains("换货") || query.contains("保修") || query.contains("政策")) {
            return KNOWLEDGE_QUERY;
        }
        return CHAT;
    }
}
