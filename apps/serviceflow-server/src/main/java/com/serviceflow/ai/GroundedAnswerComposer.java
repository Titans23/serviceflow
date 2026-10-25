package com.serviceflow.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.rag.RagService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** The model selects evidence; only server-owned fact text can reach a product answer. */
public final class GroundedAnswerComposer {
    private static final Map<String, String> SPEC_LABELS = Map.of(
            "screen_size",
            "屏幕",
            "memory",
            "内存",
            "storage",
            "存储",
            "weight",
            "重量",
            "battery",
            "电池",
            "os",
            "系统",
            "region",
            "适用地区");

    private GroundedAnswerComposer() {}

    public static Map<String, Object> context(Map<String, Object> catalog, List<RagService.Evidence> evidence) {
        List<Map<String, Object>> facts = new ArrayList<>();
        catalog.forEach((key, value) -> {
            if (value instanceof Map<?, ?> specs) {
                specs.forEach((field, detail) -> add(
                        facts, SPEC_LABELS.getOrDefault(field.toString(), field.toString()) + "：" + detail, "catalog"));
            } else {
                add(facts, key + "：" + value, "catalog");
            }
        });
        for (var item : evidence) {
            String content = item.content();
            // Keep complete paragraphs, including qualifications, rather than letting the model rewrite claims.
            int section = content.indexOf("\n## ");
            if (section >= 0) {
                content = content.substring(section);
            }
            for (String paragraph : content.split("\\n\\s*\\n")) {
                String text = paragraph.strip();
                if (!text.isEmpty() && !text.startsWith("#") && !text.startsWith("来源") && !text.startsWith("核验日期")) {
                    add(facts, text, item.source());
                }
            }
        }
        return Map.of("product", catalog.get("名称") + "（" + catalog.get("SKU") + "）", "facts", facts);
    }

    private static void add(List<Map<String, Object>> facts, String text, String source) {
        facts.add(Map.of("id", facts.size(), "text", text, "source", source));
    }

    public static String compose(String context, String selection, ObjectMapper mapper) {
        try {
            var root = mapper.readTree(context);
            var selected = mapper.readTree(selection
                    .replaceFirst("(?s)^\\s*```(?:json)?\\s*", "")
                    .replaceFirst("(?s)\\s*```\\s*$", "")
                    .strip());
            if (!selected.path("factIds").isArray()
                    || !selected.path("insufficient").isBoolean()
                    || selected.path("factIds").size() > 5) {
                throw new IllegalArgumentException("Invalid fact selection");
            }
            List<Map<String, Object>> facts = mapper.convertValue(root.path("facts"), new TypeReference<>() {});
            LinkedHashSet<String> answer = new LinkedHashSet<>();
            for (var id : selected.path("factIds")) {
                if (!id.isIntegralNumber()
                        || !id.canConvertToInt()
                        || id.intValue() < 0
                        || id.intValue() >= facts.size()) {
                    throw new IllegalArgumentException("Unknown fact ID");
                }
                answer.add(facts.get(id.intValue()).get("text").toString());
            }
            if (answer.isEmpty() && !selected.path("insufficient").booleanValue()) {
                throw new IllegalArgumentException("Empty supported answer");
            }
            String result = root.path("product").asText() + "：\n" + String.join("\n", answer);
            if (selected.path("insufficient").booleanValue()) {
                result += "\n现有资料不足以确认其余细节，不能据此作出承诺。";
            }
            return result;
        } catch (Exception error) {
            throw new IllegalStateException("Could not validate grounded fact selection", error);
        }
    }
}
