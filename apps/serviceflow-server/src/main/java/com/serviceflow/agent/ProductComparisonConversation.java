package com.serviceflow.agent;

import com.serviceflow.ai.AiGateway.ProductReference;
import com.serviceflow.ai.AiGateway.ProductTarget;
import com.serviceflow.infrastructure.redis.ChatMemory;
import com.serviceflow.model.ChatModels.ComparisonProduct;
import com.serviceflow.model.ChatModels.ComparisonTarget;
import com.serviceflow.model.ChatModels.PendingComparison;
import com.serviceflow.model.ProductModels;
import com.serviceflow.service.ProductService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import org.springframework.dao.DataAccessException;
import org.springframework.web.server.ResponseStatusException;

final class ProductComparisonConversation {
    private final ProductService products;
    private final ProductReferenceResolver references;
    private final ChatMemory memory;

    ProductComparisonConversation(ProductService products, ProductReferenceResolver references, ChatMemory memory) {
        this.products = products;
        this.references = references;
        this.memory = memory;
    }

    CustomerWorkflow.Result start(
            ServiceFlowState state,
            List<ProductTarget> targets,
            int expected,
            boolean modelTargets,
            BiConsumer<String, Object> events) {
        if (targets.size() > ProductModels.MAX_CHAT_COMPARISON || expected > ProductModels.MAX_CHAT_COMPARISON) {
            // A new comparison invalidates any preceding card, even when the new request exceeds the limit.
            memory.clearPendingComparison(state.principal().subject(), state.sessionId());
            return answer("一次最多比较 10 款商品，请缩小比较范围。");
        }
        List<TargetMatch> resolved;
        if (modelTargets && references.semanticEnabled() && targets.size() > 1) {
            // 各商品可以并行检索。按提交顺序收集结果，候选卡位置和最终表格列仍对应原文顺序。
            try (var lookups = Executors.newVirtualThreadPerTaskExecutor()) {
                var tasks = targets.stream()
                        .map(target -> CompletableFuture.supplyAsync(
                                () -> resolve(target, state.pageProductId(), true), lookups))
                        .toList();
                resolved = tasks.stream().map(CompletableFuture::join).toList();
            }
        } else {
            resolved = targets.stream()
                    .map(target -> resolve(target, state.pageProductId(), modelTargets))
                    .toList();
        }
        boolean degraded = resolved.stream().anyMatch(TargetMatch::degraded);
        var confirmed = new HashSet<Long>();
        List<ComparisonTarget> items = new ArrayList<>();
        for (TargetMatch match : resolved) {
            var item = match.target();
            if (item.confirmed() != null && !confirmed.add(item.confirmed().id())) {
                item = new ComparisonTarget(item.label() + "（与已确认商品重复）", null, List.of());
            }
            items.add(item);
        }
        // 用户要求比较的数量超过提取结果时，为缺少名称的商品保留可单独补充的位置。
        while (items.size() < Math.max(2, expected)) {
            items.add(new ComparisonTarget("第 " + (items.size() + 1) + " 款商品", null, List.of()));
        }
        var comparison = new PendingComparison(UUID.randomUUID().toString(), List.copyOf(items));
        memory.putPendingComparison(state.principal().subject(), state.sessionId(), comparison);
        return finishOrAsk(comparison, false, degraded, events);
    }

    CustomerWorkflow.Result continueComparison(ServiceFlowState state, BiConsumer<String, Object> events) {
        String subject = state.principal().subject();
        String sessionId = state.sessionId();
        var selection = state.comparisonSelection();
        var comparison = memory.pendingComparison(subject, sessionId);
        if (comparison == null || !comparison.comparisonId().equals(selection.comparisonId())) {
            return answer("这个比较已过期或已被新的比较替换，请重新发起。");
        }
        Integer index = selection.targetIndex();
        Long selectedId = selection.productId();
        String term = selection.term() == null ? "" : selection.term().strip();
        boolean skip = selection.skipTarget();
        // 先比较只取当前确认的商品。保留待确认项，用户看完部分结果后还能继续补充。
        if (selection.compareConfirmed() && !skip && index == null && selectedId == null && term.isEmpty()) {
            return finishOrAsk(comparison, true, false, events);
        }
        if (selection.compareConfirmed()
                || index == null
                || index < 0
                || index >= comparison.targets().size()
                || (skip ? selectedId != null || !term.isEmpty() : (selectedId == null) == term.isEmpty())) {
            return ask(comparison, "请为一个待确认项选择商品、补充型号或跳过该项。", false, events);
        }
        var previous = comparison.targets().get(index);
        if (previous.skipped()) {
            if (skip) return finishOrAsk(comparison, false, false, events);
            return ask(comparison, "这一项已跳过，请选择其他待确认的商品。", false, events);
        }
        if (previous.confirmed() != null) {
            // 补充可能已写入 Redis，回复还未提交。同一目标重试时使用已有清单。
            if (!skip
                    && (selectedId != null
                            ? previous.confirmed().id() == selectedId
                            : previous.label().equals(term))) {
                return finishOrAsk(comparison, false, false, events);
            }
            return ask(comparison, "这一项已经确认，请选择其他待确认的商品。", false, events);
        }
        TargetMatch match;
        if (skip) {
            // 保留原位置，使旧卡片和断线重试仍指向同一项。
            match = new TargetMatch(new ComparisonTarget(previous.label(), null, List.of(), true), false);
        } else if (selectedId != null) {
            if (previous.candidates().stream().noneMatch(candidate -> candidate.id() == selectedId)) {
                return ask(comparison, "该商品不在这一项的候选中，请重新选择。", false, events);
            }
            try {
                var current = references.find(selectedId);
                if (current == null) {
                    match = new TargetMatch(new ComparisonTarget(previous.label(), null, List.of()), false);
                } else {
                    match = new TargetMatch(new ComparisonTarget(previous.label(), choice(current), List.of()), false);
                }
            } catch (DataAccessException unavailable) {
                return ask(comparison, "商品目录暂时不可用，请稍后重试。", true, events);
            }
        } else {
            match = resolve(new ProductTarget(ProductReference.NEW, term), null, true);
            if (match.degraded()
                    && match.target().confirmed() == null
                    && match.target().candidates().isEmpty()) {
                return ask(comparison, "暂时无法核对这款商品，请稍后重试或补充 SKU。", true, events);
            }
        }
        ComparisonProduct confirmed = match.target().confirmed();
        if (confirmed != null
                && comparison.targets().stream()
                        .map(ComparisonTarget::confirmed)
                        .filter(Objects::nonNull)
                        .anyMatch(product -> product.id() == confirmed.id())) {
            return ask(comparison, "这款商品已经在比较列表中，请指定另一款或具体规格。", false, events);
        }
        var items = new ArrayList<>(comparison.targets());
        items.set(index, match.target());
        var updated = new PendingComparison(comparison.comparisonId(), List.copyOf(items));
        if (!memory.updatePendingComparison(subject, sessionId, comparison, updated)) {
            // 有并发更新时，以 Redis 里的最新清单为准，避免把旧卡片上的选择套到新状态上。
            var latest = memory.pendingComparison(subject, sessionId);
            return latest != null && latest.comparisonId().equals(comparison.comparisonId())
                    ? ask(latest, "比较选项已更新，请根据最新清单操作。", false, events)
                    : answer("这个比较已过期或已被新的比较替换，请重新发起。");
        }
        return finishOrAsk(updated, false, match.degraded(), events);
    }

    private CustomerWorkflow.Result finishOrAsk(
            PendingComparison comparison,
            boolean compareConfirmed,
            boolean degraded,
            BiConsumer<String, Object> events) {
        List<Long> ids = comparison.targets().stream()
                .map(ComparisonTarget::confirmed)
                .filter(Objects::nonNull)
                .map(ComparisonProduct::id)
                .toList();
        int total = (int) comparison.targets().stream()
                .filter(target -> !target.skipped())
                .count();
        if (total < 2) {
            events.accept("comparison_selection_required", comparison);
            return answer("跳过后剩余商品不足 2 款，请重新发起比较。");
        }
        // 有待确认项时先发候选卡。用户主动选择部分比较时，仍需至少两款才能生成表格。
        if (ids.size() < 2 || (!compareConfirmed && ids.size() < total)) {
            return ask(
                    comparison, countMessage(ids.size(), total) + (degraded ? " 部分商品暂时无法核对。" : ""), degraded, events);
        }
        try {
            var table = products.compareForChat(ids);
            events.accept("product_comparison", table);
            if (ids.size() < total) {
                events.accept("comparison_selection_required", comparison);
                return new CustomerWorkflow.Result(
                        "本次比较已确认的 " + ids.size() + " 款商品。还有 " + (total - ids.size()) + " 款待确认，可以继续补充。", List.of(), ids);
            }
            // 保存跳过状态，恢复历史时也能停用该轮旧候选卡。
            if (total < comparison.targets().size()) events.accept("comparison_selection_required", comparison);
            return new CustomerWorkflow.Result("已按所选商品的顺序列出规格差异。", List.of(), ids);
        } catch (DataAccessException unavailable) {
            return ask(comparison, "商品目录暂时不可用，请稍后重试。", true, events);
        } catch (ResponseStatusException invalid) {
            if (invalid.getStatusCode().is5xxServerError()) {
                return ask(comparison, "商品目录暂时不可用，请稍后重试。", true, events);
            }
            return answer(Objects.requireNonNullElse(invalid.getReason(), "无法比较这些商品，请重新检查选择。"));
        }
    }

    private TargetMatch resolve(ProductTarget target, Long currentId, boolean allowRerank) {
        String label = target.reference() == ProductReference.CURRENT ? "当前商品" : target.mention();
        try {
            var resolution = references.resolveComparisonTarget(target, currentId, allowRerank);
            if (resolution.resolved()) {
                return new TargetMatch(
                        new ComparisonTarget(label, choice(resolution.products().getFirst()), List.of()), false);
            }
            return new TargetMatch(
                    new ComparisonTarget(
                            label,
                            null,
                            resolution.products().stream()
                                    .map(ProductComparisonConversation::choice)
                                    .toList()),
                    resolution.degraded());
        } catch (DataAccessException unavailable) {
            return new TargetMatch(new ComparisonTarget(label, null, List.of()), true);
        }
    }

    private static String countMessage(int confirmed, int total) {
        return "已确认 " + confirmed + " 款商品，还有 " + (total - confirmed) + " 款待确认。"
                + (confirmed < 2 ? "请至少确认 2 款再比较。" : "可以补充待确认项，也可以先比较已确认的商品。");
    }

    private CustomerWorkflow.Result ask(
            PendingComparison comparison, String message, boolean degraded, BiConsumer<String, Object> events) {
        events.accept("comparison_selection_required", comparison);
        return new CustomerWorkflow.Result(message, List.of(), degraded);
    }

    private CustomerWorkflow.Result answer(String message) {
        return new CustomerWorkflow.Result(message, List.of());
    }

    private static ComparisonProduct choice(ProductModels.ProductView product) {
        return new ComparisonProduct(product.id(), product.name(), product.sku());
    }

    private record TargetMatch(ComparisonTarget target, boolean degraded) {}
}
