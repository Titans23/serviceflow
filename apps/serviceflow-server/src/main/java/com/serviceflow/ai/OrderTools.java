package com.serviceflow.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.ai.AiGateway.OrderCommand;
import com.serviceflow.model.OrderModels;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.openai.api.OpenAiApi.FunctionTool;

final class OrderTools {
    private static final Set<String> SEARCH_FIELDS = Set.of("keyword", "status", "fromDate", "toDate");
    static final List<FunctionTool> DEFINITIONS = List.of(
            tool(
                    "search_orders",
                    "用户要求列出订单或给了新的商品、下单日期、订单状态筛选条件。",
                    """
                    {"type":"object","additionalProperties":false,"properties":{
                      "keyword":{"type":"string","maxLength":80,"description":"从用户原文摘录一个商品名、型号、SKU或类别，不含修饰词"},
                      "status":{"type":"string","enum":[%s]},
                      "fromDate":{"type":"string","format":"date","description":"按传入的北京时间 today 解析起始日，包含该日"},
                      "toDate":{"type":"string","format":"date","description":"结束日，包含该日"}
                    }}
                    """
                            .formatted(String.join(
                                    ",",
                                    OrderModels.STATUSES.stream()
                                            .sorted()
                                            .map(status -> "\"" + status + "\"")
                                            .toList()))),
            tool(
                    "read_current_order",
                    "仅当明确指代当前订单、存在当前订单且没有新筛选条件时读取当前订单。",
                    """
                    {"type":"object","additionalProperties":false,"properties":{}}
                    """),
            tool(
                    "select_order_candidate",
                    "仅当用户明确指向当前候选中的唯一一笔时选择。",
                    """
                    {"type":"object","additionalProperties":false,"properties":{"index":{"type":"integer","minimum":1}},
                     "required":["index"]}
                    """),
            tool(
                    "ask_order_clarification",
                    "无法确定订单、条件不完整或状态可能对应多个枚举时，请用户澄清。",
                    """
                    {"type":"object","additionalProperties":false,"properties":{}}
                    """));

    private OrderTools() {}

    static OrderCommand parse(ObjectMapper mapper, ToolCall call, String message, List<OrderModels.Summary> candidates)
            throws JsonProcessingException {
        JsonNode arguments = mapper.reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .readTree(call.arguments());
        if (!arguments.isObject() || !"function".equals(call.type())) throw invalid();
        return switch (call.name()) {
            case "search_orders" -> search(arguments, message);
            case "read_current_order" -> {
                if (!arguments.isEmpty()) throw invalid();
                yield new OrderCommand.ReadCurrent();
            }
            case "select_order_candidate" -> {
                var index = arguments.path("index");
                if (arguments.size() != 1
                        || !index.isIntegralNumber()
                        || !index.canConvertToInt()
                        || index.intValue() < 1
                        || index.intValue() > candidates.size()) throw invalid();
                yield new OrderCommand.SelectCandidate(index.intValue());
            }
            case "ask_order_clarification" -> {
                if (!arguments.isEmpty()) throw invalid();
                yield new OrderCommand.AskUser();
            }
            default -> throw invalid();
        };
    }

    private static OrderCommand search(JsonNode arguments, String message) {
        var names = arguments.fieldNames();
        while (names.hasNext()) {
            if (!SEARCH_FIELDS.contains(names.next())) throw invalid();
        }
        String keyword = text(arguments, "keyword");
        String status = text(arguments, "status");
        if ((keyword != null && (keyword.length() > 80 || !message.contains(keyword)))
                || (status != null && !OrderModels.STATUSES.contains(status))) throw invalid();
        LocalDate from = date(arguments, "fromDate");
        LocalDate to = date(arguments, "toDate");
        if (from != null && to != null && from.isAfter(to)) throw invalid();
        return new OrderCommand.Search(new OrderModels.Search(keyword, status, from, to));
    }

    private static String text(JsonNode arguments, String name) {
        JsonNode value = arguments.path(name);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isTextual() || value.textValue().isBlank()) throw invalid();
        return value.textValue().strip();
    }

    private static LocalDate date(JsonNode arguments, String name) {
        String value = text(arguments, name);
        if (value == null) return null;
        var date = LocalDate.parse(value);
        if (date.getYear() < 2000 || date.getYear() > 2100) throw invalid();
        return date;
    }

    private static FunctionTool tool(String name, String description, String schema) {
        return new FunctionTool(new FunctionTool.Function(description, name, schema));
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid order tool arguments");
    }
}
