package com.serviceflow.ai;

/** Conservative gate for showing an action card, including deterministic order-number lookups. */
public final class OrderCancellationIntent {
    private OrderCancellationIntent() {}

    public static boolean readOnly(String message) {
        String text = message.replaceAll("\\s+", "");
        return text.matches(".*(?:不|别|不要|不用|不想|不需要|无需|暂不|先不)(?:再|先|帮我|给我|替我)?取消.*")
                || text.matches(
                        ".*(?:查询|查看|查一下|只查|查物流|查状态|进度|状态|结果|工单|是否|有没有|能否|能不能|可不可以|怎么|如何|为什么|如果|假如|取消了吗|取消了没|取消成功|取消失败).*")
                || text.matches(".*(?:吗|么|[？?]).*");
    }

    public static boolean requested(String message) {
        return !readOnly(message) && message.matches("(?s).*(?:取消|不要了|不想要了).*");
    }
}
