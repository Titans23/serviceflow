package com.serviceflow.agent;

import com.serviceflow.ai.AiGateway.ProductReference;
import com.serviceflow.ai.AiGateway.ProductTarget;
import com.serviceflow.model.ProductModels;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class ProductReferenceRules {
    private static final Pattern SWITCH = Pattern.compile("那关于|那说说|改问|换成|换到|改看|这次(?:想|要|请)?(?:比较|对比|问|看)");
    private static final Pattern BACKGROUND = Pattern.compile("^(?:我(?:们)?)?(?:之前|上次|刚才)(?:说|聊|问|比较|提到)");
    private static final Pattern SEPARATOR = Pattern.compile("以及|、|[，,；;\\n]|和|与|(?i:\\s+vs\\.?\\s+)");
    private static final Pattern CURRENT = Pattern.compile("^(?:这款|这台|该款|该机|它|当前商品)(?:手机|耳机|商品)?$");
    private static final Pattern COUNT = Pattern.compile("(\\d+|[一二三四五六七八九十]{1,3})\\s*(?:款|个|台)(?:商品|产品|手机|耳机)?");
    private static final Pattern COMPARE_START =
            Pattern.compile("^(?:(?:请|帮我|我想|我希望|我想要|想|要|一次性|同时|一下|这|以下|下列)\\s*)*(?:比较|对比)");
    private static final Pattern COMPARE_SUBJECT =
            Pattern.compile("^(?:(?:请|帮我|我想|我希望|我想要|想|要|一次性|同时)\\s*)*(?:比较|对比)(?:一下)?" + "(?:(?:以下|这|下列|的)\\s*)*");
    private static final Pattern COMPARE_END =
            Pattern.compile("(?:放在一起|一起)?(?:进行)?(?:有什么区别|有什么差别|有什么不同|有哪些区别|有什么差异|有何区别|的区别(?:是什么)?|的差别"
                    + "|的差异|(?:的)?(?:规格|参数)(?:对比|比较)|(?:对比|比较)(?:一下)?|哪个[^、，,。！？?]*)[。！？?\\s]*$");

    private ProductReferenceRules() {}

    static boolean switchesProduct(String message) {
        return SWITCH.matcher(message).find()
                || BACKGROUND.matcher(message.strip()).find();
    }

    static String targetClause(String message) {
        var switchProduct = SWITCH.matcher(message);
        int start = 0;
        while (switchProduct.find()) start = switchProduct.end();
        if (start > 0) return message.substring(start).strip();
        return BACKGROUND.matcher(message.strip()).find() ? "" : message;
    }

    static String bareTarget(String clause) {
        return clause.replaceFirst("(?:的(?:屏幕|规格|参数|价格|续航)(?:呢|怎么样)?|呢|怎么样)?[。！？?\\s]*$", "")
                .strip();
    }

    static boolean comparesProducts(String message, List<ProductTarget> targets, int expectedCount) {
        if (!CustomerWorkflow.requestsProductComparison(message)) return false;
        if (expectedCount > 1 || COMPARE_START.matcher(message.strip()).find()) return true;
        return targets.stream()
                        .filter(target -> target.reference() == ProductReference.CURRENT
                                || !CustomerWorkflow.modelMentions(target.mention())
                                        .isEmpty()
                                || !CustomerWorkflow.skuMentions(target.mention())
                                        .isEmpty()
                                || ProductReferenceResolver.hasHanModel(target.mention()))
                        .limit(2)
                        .count()
                == 2;
    }

    static int expectedCount(String question) {
        var count = COUNT.matcher(targetClause(question));
        if (!count.find()) return 0;
        String value = count.group(1);
        if (Character.isDigit(value.charAt(0))) {
            return value.length() > 2 ? ProductModels.MAX_CHAT_COMPARISON + 1 : Integer.parseInt(value);
        }
        if (value.equals("十")) return 10;
        if (value.length() > 1) return ProductModels.MAX_CHAT_COMPARISON + 1;
        return "一二三四五六七八九".indexOf(value) + 1;
    }

    static List<ProductTarget> comparisonTargets(String message) {
        String text = comparisonList(message);
        text = text.replaceFirst("^" + COUNT.pattern() + "\\s*[:,]?\\s*(?:分别是|包括)?\\s*", "");
        text = COMPARE_END.matcher(text).replaceFirst("").strip().replaceFirst("[。！？!?：:\\s]+$", "");
        List<ProductTarget> targets = new ArrayList<>();
        for (String part : SEPARATOR.split(text)) {
            String label = part.strip();
            if (label.isBlank() || label.matches("(?:以下|这几款|这些|这几款(?:产品|商品)|第[一二三四五六七八九十\\d]+款)")) continue;
            if (CURRENT.matcher(label).matches()) {
                targets.add(new ProductTarget(ProductReference.CURRENT, null));
            } else {
                targets.add(new ProductTarget(ProductReference.NEW, label));
            }
        }
        return targets;
    }

    static boolean completeExplicitList(String message, int expectedCount, List<ProductTarget> targets) {
        // A count in the list header gives the rule-based split a check independent of model output.
        return expectedCount >= 2
                && expectedCount <= ProductModels.MAX_CHAT_COMPARISON
                && targets.size() == expectedCount
                && CustomerWorkflow.requestsProductComparison(message)
                && COUNT.matcher(comparisonList(message)).lookingAt()
                && targets.stream()
                        .allMatch(target -> target.reference() == ProductReference.CURRENT
                                || target.mention().length() <= 120);
    }

    private static String comparisonList(String message) {
        // Normalize full-width delimiters for both list splitting and header validation.
        String text = Normalizer.normalize(targetClause(message), Normalizer.Form.NFKC)
                .strip();
        return COMPARE_SUBJECT.matcher(text).replaceFirst("");
    }
}
