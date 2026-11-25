package com.serviceflow.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.serviceflow.ai.AiGateway.ProductReference;
import com.serviceflow.ai.AiGateway.ProductTarget;
import com.serviceflow.model.ProductModels.ProductPage;
import com.serviceflow.model.ProductModels.ProductView;
import com.serviceflow.rag.RerankerClient;
import com.serviceflow.rag.RerankerClient.ScoredDocument;
import com.serviceflow.service.ProductService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductReferenceResolverTest {
    private final ProductService products = mock(ProductService.class);
    private final RerankerClient reranker = mock(RerankerClient.class);
    private final ProductView iphone = product(29, "APPLE-IP16-US-128", "Apple", "iPhone 16", "128 GB");
    private final ProductView proMax = product(31, "APPLE-IP16PM-US-256", "Apple", "iPhone 16 Pro Max", "256 GB");
    private final ProductView galaxy = product(30, "SAMSUNG-S24-US-128", "Samsung", "Galaxy S24", "128 GB");

    @BeforeEach
    void catalog() {
        when(products.get(29L)).thenReturn(iphone);
        when(products.get(30L)).thenReturn(galaxy);
        when(products.get(31L)).thenReturn(proMax);
        when(products.resolve("iPhone 16")).thenReturn(List.of(iphone, proMax));
        when(products.resolve("Galaxy S24")).thenReturn(List.of(galaxy));
        when(products.search(null, null, 0, 50)).thenReturn(new ProductPage(List.of(iphone, proMax, galaxy), 0, 50, 3));
    }

    @Test
    void correctsOneModelLetterWithoutChangingSeriesOrCallingModel() {
        var resolver = resolver(false, 0);
        var typo = resolver.resolve("GalxyS24 的屏幕呢？", 29L, null, ProductReference.UNCLEAR, null);
        assertThat(typo.resolved()).isTrue();
        assertThat(typo.products()).containsExactly(galaxy);
        var otherSeries = resolver.resolve("GalxyS25 的屏幕呢？", 29L, null, ProductReference.UNCLEAR, null);
        assertThat(otherSeries.resolved()).isFalse();
        assertThat(otherSeries.clearCurrent()).isTrue();
        verifyNoInteractions(reranker);
    }

    @Test
    void explicitSkuKeepsFeatureTermsOutOfProductResolution() {
        when(products.resolve(galaxy.sku())).thenReturn(List.of(galaxy));
        var result = resolver(true, .2)
                .resolve("SAMSUNG-S24-US-128 支持 WiFi 6 吗？", 29L, null, ProductReference.UNCLEAR, null);
        assertThat(result.resolved()).isTrue();
        assertThat(result.products()).containsExactly(galaxy);
        verifyNoInteractions(reranker);
    }

    @Test
    void usesSemanticWinnerOnlyWhenConfiguredAndLeading() {
        when(reranker.rankScored(eq("苹果16promax"), anyList(), eq(3)))
                .thenReturn(List.of(new ScoredDocument("31", .91), new ScoredDocument("29", .45)));
        String query = "苹果16promax 的屏幕呢？";
        for (double gap : List.of(0D, .5D)) {
            var uncertain = resolver(true, gap).resolve(query, 29L, null, ProductReference.NEW, "苹果16promax");
            assertThat(uncertain.resolved()).isFalse();
            assertThat(uncertain.clearCurrent()).isTrue();
            assertThat(uncertain.products()).containsExactly(proMax);
        }
        var confident = resolver(true, .2).resolve(query, 29L, null, ProductReference.NEW, "苹果16promax");
        assertThat(confident.resolved()).isTrue();
        assertThat(confident.products()).containsExactly(proMax);
    }

    @Test
    void comparisonRanksOnlyCompatibleSpecsAndStillRequiresChoice() {
        var otherCapacity = product(32, "APPLE-IP16PM-US-512", "Apple", "iPhone 16 Pro Max", "512 GB");
        when(products.search(null, null, 0, 50))
                .thenReturn(new ProductPage(List.of(iphone, proMax, galaxy, otherCapacity), 0, 50, 4));
        String term = "苹果16promax 256GB";
        when(reranker.rankScored(eq(term), anyList(), eq(3))).thenReturn(List.of(new ScoredDocument("31", .96)));

        var result =
                resolver(true, .2).resolveComparisonTarget(new ProductTarget(ProductReference.NEW, term), null, true);

        assertThat(result.resolved()).isFalse();
        assertThat(result.products()).containsExactly(proMax);
        verify(reranker)
                .rankScored(
                        eq(term),
                        argThat(documents -> documents.stream()
                                .map(RerankerClient.Document::id)
                                .toList()
                                .equals(List.of("31"))),
                        eq(3));
    }

    @Test
    void comparisonReranksTyposAndKeepsLocalSuggestionIfModelFails() {
        String term = "Galxy S24";
        when(reranker.rankScored(eq(term), anyList(), eq(3)))
                .thenThrow(new IllegalStateException("reranker unavailable"));

        var result =
                resolver(true, .2).resolveComparisonTarget(new ProductTarget(ProductReference.NEW, term), null, true);

        assertThat(result.resolved()).isFalse();
        assertThat(result.degraded()).isTrue();
        assertThat(result.products()).containsExactly(galaxy);
    }

    @Test
    void missingVariantAndCompatibleCatalogOverflowRequireClarification() {
        when(products.search(null, null, 0, 50)).thenReturn(new ProductPage(List.of(iphone, galaxy), 0, 50, 2));
        var resolver = resolver(true, .2);
        var missing = resolver.resolve("苹果16promax 的屏幕呢？", 29L, null, ProductReference.NEW, "苹果16promax");
        assertThat(missing.resolved()).isFalse();
        assertThat(missing.products()).isEmpty();
        assertThat(missing.clearCurrent()).isTrue();
        when(products.search(null, null, 0, 50))
                .thenReturn(new ProductPage(List.of(iphone, proMax, galaxy), 0, 50, 51));
        assertThat(resolver.resolve("GalxyS24 屏幕呢？", 29L, null, ProductReference.NEW, "GalxyS24")
                        .resolved())
                .isFalse();
        verifyNoInteractions(reranker);
    }

    @Test
    void sameModelVariantsRequireSelectionEvenWithLargeScoreGap() {
        var alternate = product(32, "APPLE-IP16PM-US-512", "Apple", "iPhone 16 Pro Max", "512 GB");
        when(products.search(null, null, 0, 50))
                .thenReturn(new ProductPage(List.of(iphone, proMax, galaxy, alternate), 0, 50, 4));
        when(reranker.rankScored(eq("苹果16promax"), anyList(), eq(3)))
                .thenReturn(List.of(new ScoredDocument("31", .99), new ScoredDocument("32", .1)));
        var resolver = resolver(true, .2);
        String query = "苹果16promax 的屏幕呢？";
        var ambiguous = resolver.resolve(query, 29L, null, ProductReference.NEW, "苹果16promax");
        assertThat(ambiguous.resolved()).isFalse();
        assertThat(ambiguous.products()).containsExactly(proMax, alternate);
        assertThat(resolver.resolve(query, null, 31L, ProductReference.UNCLEAR, null)
                        .products())
                .containsExactly(proMax);
    }

    @Test
    void preservesCurrentForPronounOrComparisonAndDegradesOnModelFailure() {
        var resolver = resolver(true, .2);
        assertThat(resolver.resolve("这款的屏幕呢？", 29L, null, ProductReference.CURRENT, null)
                        .products())
                .containsExactly(iphone);
        var comparison = resolver.resolve("这款和 Galaxy S24 有什么区别？", 29L, null, ProductReference.NEW, "Galaxy S24");
        assertThat(comparison.comparison()).isTrue();
        assertThat(comparison.products()).containsExactly(iphone, galaxy);
        when(reranker.rankScored(eq("苹果16promax"), anyList(), eq(3))).thenThrow(new IllegalStateException());
        var failure = resolver.resolve("苹果16promax 的屏幕呢？", 29L, null, ProductReference.NEW, "苹果16promax");
        assertThat(failure.resolved()).isFalse();
        assertThat(failure.degraded()).isTrue();
        assertThat(failure.clearCurrent()).isTrue();
    }

    @Test
    void switchDoesNotReadTheBackgroundModelWhenExtractionIsUnavailable() {
        var result =
                resolver(false, 0).resolve("之前说的是 Galaxy S24，那关于苹果16呢？", 30L, null, ProductReference.UNCLEAR, null);
        assertThat(result.resolved()).isFalse();
        assertThat(result.clearCurrent()).isTrue();
        org.mockito.Mockito.verify(products, org.mockito.Mockito.never()).resolve("Galaxy S24");
    }

    @Test
    void comparesCapacityVariantsOnlyAfterAnExplicitChoice() {
        var other = product(32, "APPLE-IP16-US-256", "Apple", "iPhone 16", "256 GB");
        when(products.resolve("iPhone 16")).thenReturn(List.of(iphone, other));
        var resolver = resolver(true, .2);
        assertThat(resolver.resolveComparisonTarget(
                                new com.serviceflow.ai.AiGateway.ProductTarget(ProductReference.NEW, "iPhone 16"),
                                null,
                                false)
                        .resolved())
                .isFalse();
        assertThat(resolver.resolveComparisonTarget(
                                new com.serviceflow.ai.AiGateway.ProductTarget(ProductReference.NEW, "iPhone 16 256GB"),
                                null,
                                false)
                        .products())
                .containsExactly(other);
    }

    private ProductReferenceResolver resolver(boolean semanticEnabled, double gap) {
        return new ProductReferenceResolver(products, reranker, semanticEnabled, "cloud", gap);
    }

    private static ProductView product(long id, String sku, String brand, String model, String capacity) {
        return new ProductView(id, sku, model, brand, model, "phone", Map.of("storage", capacity), null, "OFF_SALE");
    }
}
