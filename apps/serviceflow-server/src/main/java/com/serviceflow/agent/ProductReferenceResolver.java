package com.serviceflow.agent;

import com.serviceflow.ai.AiGateway.ProductReference;
import com.serviceflow.ai.AiGateway.ProductTarget;
import com.serviceflow.model.ProductModels.ProductView;
import com.serviceflow.rag.RerankerClient;
import com.serviceflow.rag.RerankerClient.ScoredDocument;
import com.serviceflow.service.ProductService;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 将本轮点名、商品页和会话记忆解析为商品目录中的具体商品。 */
@Component
public final class ProductReferenceResolver {
    private static final int CATALOG_LIMIT = 50;
    private static final int MAX_CANDIDATES = 3;
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern VARIANT = Pattern.compile(
            "(?<![a-z])(?:pro\\s*max|pro\\+?|ultra|plus|max|air|mini)(?![a-z])", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAPACITY = Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:gb|tb)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADJACENT_CAPACITY =
            Pattern.compile("^\\s*\\d+(?:\\.\\d+)?\\s*(?:gb|tb)(?![a-z])", Pattern.CASE_INSENSITIVE);
    private static final Pattern HAN_MODEL = Pattern.compile(
            "\\p{IsHan}+\\s*\\d+\\s*(?:pro\\s*max|pro\\+?|ultra|plus|max|air|mini)(?![a-z])", Pattern.CASE_INSENSITIVE);
    private static final Pattern REFERENCE = Pattern.compile("这款|这台|该款|该机|它");
    private static final Map<String, String> REGIONS =
            Map.of("美版|美国规格", "unitedstates", "国行|大陆版|中国大陆", "china", "港版|香港版", "hongkong", "日版|日本版", "japan");
    private static final Pattern REGION = Pattern.compile(String.join("|", REGIONS.keySet()));

    private final ProductService products;
    private final RerankerClient reranker;
    private final boolean semanticEnabled;
    private final double minimumScoreGap;

    public ProductReferenceResolver(
            ProductService products,
            RerankerClient reranker,
            @Value("${serviceflow.product-resolution.semantic-enabled:true}") boolean semanticEnabled,
            @Value("${serviceflow.ai.mode:demo}") String aiMode,
            @Value("${serviceflow.product-resolution.minimum-score-gap:0}") double minimumScoreGap) {
        if (!Double.isFinite(minimumScoreGap) || minimumScoreGap < 0 || minimumScoreGap > 1) {
            throw new IllegalArgumentException("Product resolution score gap must be between 0 and 1");
        }
        this.products = products;
        this.reranker = reranker;
        this.semanticEnabled = semanticEnabled && "cloud".equals(aiMode);
        this.minimumScoreGap = minimumScoreGap;
    }

    boolean semanticEnabled() {
        return semanticEnabled;
    }

    static boolean hasHanModel(String query) {
        return HAN_MODEL
                .matcher(Normalizer.normalize(query, Normalizer.Form.NFKC))
                .find();
    }

    Resolution resolve(String query, Long currentId, Long selectedId, ProductReference reference, String mention) {
        // 用户点选具体规格后，按 ID 取商品，原问题里的简称或拼写错误无需重新解析。
        if (selectedId != null) {
            ProductView selected = find(selectedId);
            return selected == null ? unresolved(List.of(), true, false) : resolved(List.of(selected), false);
        }

        if (ProductReferenceRules.switchesProduct(query)) {
            query = ProductReferenceRules.targetClause(query);
            if (query.isBlank() && mention == null) return unresolved(List.of(), true, false);
            if (mention != null && !query.isBlank() && !query.contains(mention)) mention = null;
            if (query.isBlank()) query = mention;
            currentId = null;
            reference = ProductReference.NEW;
        }
        String normalizedQuery = Normalizer.normalize(query, Normalizer.Form.NFKC);
        List<String> skus = CustomerWorkflow.skuMentions(normalizedQuery);
        List<String> models = CustomerWorkflow.modelMentions(normalizedQuery);
        // A grounded target from routing takes precedence over mentions used as background.
        if (mention != null && query.contains(mention) && !comparisonWithCurrent(query)) {
            skus = CustomerWorkflow.skuMentions(mention);
            models = List.of(mention);
        } else if (models.isEmpty() && skus.isEmpty()) {
            Matcher hanModel = HAN_MODEL.matcher(normalizedQuery);
            if (hanModel.find()) models = List.of(hanModel.group());
        }

        ProductView current = find(currentId);
        if (skus.isEmpty() && models.isEmpty()) {
            // 尝试换商品但无法确定对象时，请用户澄清，防止旧商品的说明书进入本轮。
            if (reference == ProductReference.NEW) {
                List<ProductView> exact = exactModel(ProductReferenceRules.bareTarget(query));
                return exact.size() == 1 ? resolved(exact, false) : unresolved(exact, true, false);
            }
            if (current != null && (reference == ProductReference.CURRENT || !semanticEnabled)) {
                return resolved(List.of(current), false);
            }
            List<ProductView> candidates = products.resolve(query);
            return candidates.size() == 1 ? resolved(candidates, false) : unresolved(candidates, false, false);
        }

        // 对比前逐个确认点名的商品，避免只解析到其中一款就回答整道对比题。
        var named = new LinkedHashMap<Long, ProductView>();
        for (String sku : skus) {
            List<ProductView> matches = products.resolve(sku).stream()
                    .filter(p -> p.sku().equalsIgnoreCase(sku))
                    .toList();
            if (matches.isEmpty()) return unresolved(List.of(), true, false);
            matches.forEach(p -> named.put(p.id(), p));
        }
        if (named.size() == 1 && !CustomerWorkflow.requestsProductComparison(query)) {
            return resolved(List.copyOf(named.values()), false);
        }
        if (skus.isEmpty() && models.size() == 1) {
            return named(query, models.getFirst(), current, mention != null, false);
        }
        for (String model : models) {
            List<ProductView> matches = exactModel(model);
            if (matches.isEmpty()) return unresolved(List.of(), true, false);
            matches.forEach(p -> named.put(p.id(), p));
        }
        List<ProductView> candidates = List.copyOf(named.values());
        if (comparisonWithCurrent(query) && candidates.size() == 1) return singleMatch(query, candidates, current);
        if (CustomerWorkflow.isProductComparison(query, candidates)) return resolved(candidates, true);
        return candidates.size() == 1 ? resolved(candidates, false) : unresolved(candidates, true, false);
    }

    Resolution resolveComparisonTarget(ProductTarget target, Long currentId, boolean allowRerank) {
        if (target.reference() == ProductReference.CURRENT) {
            ProductView current = find(currentId);
            return current == null ? unresolved(List.of(), false, false) : resolved(List.of(current), false);
        }
        String term = target.mention().strip();
        return named(term, term, null, allowRerank, true);
    }

    private Resolution named(
            String query, String mention, ProductView current, boolean allowRerank, boolean comparison) {
        // 紧跟型号的容量用于选择规格，问题里其他位置的容量可能是用户想核实的参数。
        String normalized = Normalizer.normalize(mention, Normalizer.Form.NFKC);
        String identity = REGION.matcher(CAPACITY.matcher(normalized).replaceAll(""))
                .replaceAll("")
                .trim();
        String capacity = first(CAPACITY, mention);
        int mentionStart = query.indexOf(mention);
        if (capacity.isEmpty() && mentionStart >= 0) {
            capacity = first(ADJACENT_CAPACITY, query.substring(mentionStart + mention.length()));
        }
        String requestedCapacity = capacity;
        List<ProductView> exact = exactModel(identity).stream()
                .filter(p -> compatible(identity, normalized, requestedCapacity, p))
                .toList();
        if (!exact.isEmpty()) return singleMatch(query, exact, current);
        if (CustomerWorkflow.skuMentions(identity).stream().anyMatch(identity::equalsIgnoreCase)) {
            return unresolved(List.of(), true, false);
        }
        if (comparison && !allowRerank) return unresolved(List.of(), true, false);

        var page = products.search(null, null, 0, CATALOG_LIMIT);
        // 目录过大时请用户补充型号，防止只看前一页就选错商品。
        if (page == null || page.total() > CATALOG_LIMIT) return unresolved(List.of(), true, false);
        List<ProductView> compatible = page.items().stream()
                .filter(p -> compatible(identity, normalized, requestedCapacity, p))
                .toList();
        List<ProductView> near =
                compatible.stream().filter(p -> similarModel(identity, p)).toList();
        if (!comparison && !near.isEmpty()) return singleMatch(query, near, current);
        List<ProductView> nearCandidates = near.stream().limit(MAX_CANDIDATES).toList();
        if (!allowRerank || !semanticEnabled || compatible.isEmpty()) {
            return unresolved(nearCandidates, nearCandidates.isEmpty(), false);
        }

        // 对比时先过滤规格，避免错误规格占掉重排的前三个名额。
        List<ProductView> rankingPool = comparison ? compatible : page.items();
        List<ScoredDocument> ranked;
        try {
            ranked = reranker.rankScored(
                    mention,
                    rankingPool.stream()
                            .map(p -> new RerankerClient.Document(Long.toString(p.id()), descriptor(p)))
                            .toList(),
                    MAX_CANDIDATES);
        } catch (RuntimeException exception) {
            if (Thread.currentThread().isInterrupted()) throw exception;
            return unresolved(nearCandidates, nearCandidates.isEmpty(), true);
        }
        Map<String, ProductView> byId = new LinkedHashMap<>();
        compatible.forEach(p -> byId.put(Long.toString(p.id()), p));
        List<ProductView> candidates = ranked.stream()
                .map(row -> byId.get(row.id()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (candidates.isEmpty()) return unresolved(List.of(), true, false);
        ProductView first = candidates.getFirst();
        // 设置校准门槛后才允许自动选款。分差取自同一次请求，并检查同型号是否还有其他规格。
        if (!comparison
                && minimumScoreGap > 0
                && ranked.size() >= 2
                && ranked.getFirst().id().equals(Long.toString(first.id()))
                && !ranked.getFirst().id().equals(ranked.get(1).id())
                && page.items().stream().anyMatch(p -> Long.toString(p.id())
                        .equals(ranked.get(1).id()))
                && validScore(ranked.getFirst().score())
                && validScore(ranked.get(1).score())
                && ranked.getFirst().score() - ranked.get(1).score() >= minimumScoreGap
                && compatible.stream().filter(p -> sameModel(p, first)).count() == 1
                && !comparisonWithCurrent(query)) {
            return resolved(List.of(first), false);
        }
        return unresolved(candidates, true, false);
    }

    private static Resolution singleMatch(String query, List<ProductView> candidates, ProductView current) {
        if (comparisonWithCurrent(query)) {
            if (current != null
                    && candidates.size() == 1
                    && candidates.getFirst().id() != current.id()) {
                return resolved(List.of(current, candidates.getFirst()), true);
            }
            return unresolved(candidates, false, false);
        }
        if (candidates.size() == 1) return resolved(candidates, false);
        if (current != null && candidates.stream().anyMatch(p -> p.id() == current.id())) {
            return resolved(List.of(current), false);
        }
        return unresolved(candidates, true, false);
    }

    private List<ProductView> exactModel(String term) {
        return products.resolve(term).stream()
                .filter(p -> p.sku().equalsIgnoreCase(term) || modelNames(p).contains(normalize(term)))
                .toList();
    }

    ProductView find(Long id) {
        if (id == null) return null;
        try {
            return products.get(id);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) return null;
            throw exception;
        }
    }

    private static boolean similarModel(String mention, ProductView candidate) {
        // 型号数字及版本后缀一致后，才用一次字符编辑修正 Galxy 这样的错拼。
        String modelNumber = first(NUMBER, candidate.model());
        String modelVariant = first(VARIANT, candidate.model());
        return !modelNumber.isEmpty()
                && modelNumber.equals(first(NUMBER, mention))
                && modelVariant.equals(first(VARIANT, mention))
                && modelNames(candidate).stream().anyMatch(name -> withinOneEdit(normalize(mention), name));
    }

    private static boolean compatible(String identity, String mention, String capacity, ProductView product) {
        if (product.sku().equalsIgnoreCase(identity)) return true;
        String variant = first(VARIANT, identity);
        String series = first(NUMBER, identity);
        String region = normalize(Objects.toString(product.specs().get("region"), ""));
        return (variant.isEmpty() || variant.equals(first(VARIANT, product.model())))
                && (series.isEmpty() || series.equals(first(NUMBER, product.model())))
                && (capacity.isEmpty()
                        || List.of("storage", "memory").stream()
                                .anyMatch(key -> capacity.equals(normalize(
                                        Objects.toString(product.specs().get(key), "")))))
                && REGIONS.entrySet().stream()
                        .noneMatch(entry ->
                                Pattern.compile(entry.getKey()).matcher(mention).find()
                                        && !region.contains(entry.getValue()));
    }

    private static boolean comparisonWithCurrent(String query) {
        return REFERENCE.matcher(query).find()
                && (query.contains("对比") || query.contains("比较") || (query.contains("和") && query.contains("区别")));
    }

    private static boolean sameModel(ProductView a, ProductView b) {
        return normalize(a.brand()).equals(normalize(b.brand()))
                && normalize(a.model()).equals(normalize(b.model()));
    }

    private static List<String> modelNames(ProductView product) {
        return List.of(
                normalize(product.model()), normalize(product.name()), normalize(product.brand() + product.model()));
    }

    private static String descriptor(ProductView product) {
        return "%s | %s | %s | %s | %s | %s"
                .formatted(
                        product.brand(),
                        product.model(),
                        product.name(),
                        product.category(),
                        Objects.toString(product.specs().get("storage"), ""),
                        Objects.toString(product.specs().get("region"), ""));
    }

    private static String first(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC));
        return matcher.find() ? normalize(matcher.group()) : "";
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private static boolean withinOneEdit(String left, String right) {
        if (left.length() > right.length()) return withinOneEdit(right, left);
        if (right.length() - left.length() > 1) return false;
        int index = 0;
        while (index < left.length() && left.charAt(index) == right.charAt(index)) index++;
        if (index == left.length()) return true;
        int nextLeft = left.length() == right.length() ? index + 1 : index;
        return left.substring(nextLeft).equals(right.substring(index + 1));
    }

    private static boolean validScore(double score) {
        return Double.isFinite(score) && score >= 0 && score <= 1;
    }

    private static Resolution resolved(List<ProductView> products, boolean comparison) {
        return new Resolution(products, true, comparison, false, false);
    }

    private static Resolution unresolved(List<ProductView> products, boolean clearCurrent, boolean degraded) {
        return new Resolution(products, false, false, clearCurrent, degraded);
    }

    record Resolution(
            List<ProductView> products, boolean resolved, boolean comparison, boolean clearCurrent, boolean degraded) {}
}
