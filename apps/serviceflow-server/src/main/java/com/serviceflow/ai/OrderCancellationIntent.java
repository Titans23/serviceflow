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
        if (readOnly(message)) return false;
        String text = message.replaceAll("\\s+", "").replaceFirst("[。！!]+$", "");
        if (text.matches("^(?:我|你|客服)?(?:之前|上次|刚才).+")) return false;
        return text.matches("^(?:请|麻烦|麻烦你|麻烦帮我|帮我|请帮我|我想|我要|我希望|现在|给我|那就)?" + "取消(?!了|过|的|后|记录|流程|政策|规则|完成|原因).*")
                || text.matches("(?i).*(?:帮我取消|请取消|不要了|不想要了)(?:订单)?(?:SF\\d{12,})?$")
                || text.matches("(?i)^(?:订单)?SF\\d{12,}取消$");
    }
}
