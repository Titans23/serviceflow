package com.serviceflow.agent;

import com.serviceflow.model.OrderModels;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

final class OrderSearchRules {
    private static final Pattern LIST =
            Pattern.compile("(?:请|帮我|请帮我)?(?:查询|查看|看一下|查|看|列出)?(?:我(?:的|买过的)|所有|全部|其他|最近的)?订单(?:列表)?");
    private static final Pattern MULTIPLE_STATUSES = Pattern.compile("还没发货|尚未发货|未发货|没发货");
    private static final Pattern FILTERS =
            Pattern.compile("(?<date>最近一周|前天|昨天|今天)?(?:下单|买|购买)?(?:的)?(?<keyword>耳机|手机|笔记本|电脑)?"
                    + "(?:的)?(?<status>待付款|已付款|处理中|已发货|已送达|已取消|退款中|已退款)?(?:的)?"
                    + "(?:订单)?(?:不要了|不想要了|状态|物流)?");

    private OrderSearchRules() {}

    static boolean listsOrders(String message) {
        return LIST.matcher(normalize(message)).matches();
    }

    static boolean ambiguousStatus(String message) {
        return MULTIPLE_STATUSES.matcher(message).find();
    }

    static OrderModels.Search search(String message, LocalDate today) {
        if (listsOrders(message)) return new OrderModels.Search(null, null, null, null);
        String filter = normalize(message)
                .replaceFirst("^(?:请帮我|帮我|请)?(?:查询|查看|查一下|查|取消)?(?:我的)?", "")
                .replaceFirst("(?:请帮我|帮我|请)?取消$", "");
        // 完整匹配简短的查询表达。若只认得部分条件，例如商品类别却认不出日期，就交给澄清分支。
        var matched = FILTERS.matcher(filter);
        if (!matched.matches()) return null;
        String date = matched.group("date");
        String keyword = matched.group("keyword");
        String status =
                switch (Objects.toString(matched.group("status"), "")) {
                    case "待付款" -> "CREATED";
                    case "已付款" -> "PAID";
                    case "处理中" -> "PROCESSING";
                    case "已发货" -> "SHIPPED";
                    case "已送达" -> "DELIVERED";
                    case "已取消" -> "CANCELLED";
                    case "退款中" -> "REFUNDING";
                    case "已退款" -> "REFUNDED";
                    default -> null;
                };
        if (date == null && keyword == null && status == null) return null;
        LocalDate from =
                switch (Objects.toString(date, "")) {
                    case "最近一周" -> today.minusDays(6);
                    case "昨天" -> today.minusDays(1);
                    case "前天" -> today.minusDays(2);
                    case "今天" -> today;
                    default -> null;
                };
        return new OrderModels.Search(keyword, status, from, "最近一周".equals(date) ? today : from);
    }

    private static String normalize(String message) {
        return message.replaceAll("[\\s。！？?!]", "");
    }
}
