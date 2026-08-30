package com.serviceflow.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ProductServiceTest {
    private ProductMapper mapper;
    private ProductService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductMapper.class);
        service = new ProductService(mapper, new ObjectMapper());
    }

    @Test
    void comparesTwoPhonesInRequestedOrderAndKeepsMissingValuesMissing() {
        var first = product(1, "A100", "phone", "{\"screen_size\":\"6.1 inch\",\"storage\":\"128 GB\"}");
        var second = product(2, "A100 Pro", "phone", "{\"screen_size\":\"6.7 inch\"}");
        when(mapper.findByIds(List.of(2L, 1L))).thenReturn(List.of(first, second));

        var comparison = service.compare(List.of(2L, 1L));

        assertThat(comparison.category()).isEqualTo("phone");
        assertThat(comparison.products())
                .extracting(ProductModels.ProductView::id)
                .containsExactly(2L, 1L);
        assertThat(comparison.products().getFirst().specs()).doesNotContainKey("storage");
    }

    @Test
    void rejectsCrossCategoryComparison() {
        when(mapper.findByIds(List.of(1L, 3L)))
                .thenReturn(List.of(product(1, "Phone", "phone", "{}"), product(3, "Laptop", "laptop", "{}")));

        assertThatThrownBy(() -> service.compare(List.of(1L, 3L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("仅支持同类别商品比较");
    }

    @Test
    void rejectsDuplicateAndMoreThanThreeProducts() {
        assertThatThrownBy(() -> service.compare(List.of(1L, 1L))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.compare(List.of(1L, 2L, 3L, 4L))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void importsProductsWithNormalizedSkuAndReturnsPersistedViews() {
        var request = new ProductModels.ProductImport(
                " sf-phone-x1 ",
                "旗舰手机 X1",
                "ServiceFlow",
                "X1",
                "phone",
                Map.of("screen_size", "6.7 英寸", "storage", "512 GB"),
                BigDecimal.valueOf(4999),
                "ON_SALE");
        var persisted =
                product(7, "SF-PHONE-X1", "旗舰手机 X1", "phone", "{\"screen_size\":\"6.7 英寸\",\"storage\":\"512 GB\"}");
        when(mapper.findBySkus(List.of("SF-PHONE-X1"))).thenReturn(List.of(persisted));

        var response = service.importProducts(List.of(request));

        assertThat(response.imported()).isEqualTo(1);
        assertThat(response.products())
                .extracting(ProductModels.ProductView::sku)
                .containsExactly("SF-PHONE-X1");
        verify(mapper)
                .upsert(
                        eq("SF-PHONE-X1"),
                        eq("旗舰手机 X1"),
                        eq("ServiceFlow"),
                        eq("X1"),
                        eq("phone"),
                        contains("screen_size"),
                        eq(BigDecimal.valueOf(4999)),
                        eq("ON_SALE"));
    }

    @Test
    void rejectsDuplicateSkuAndInvalidSaleStatusBeforeWriting() {
        var first = new ProductModels.ProductImport(
                "x1", "X1", "B", "X1", "phone", Map.of("storage", "128 GB"), BigDecimal.ONE, "ON_SALE");
        var duplicate = new ProductModels.ProductImport(
                "X1", "X1 copy", "B", "X1", "phone", Map.of("storage", "256 GB"), BigDecimal.TEN, "ON_SALE");
        assertThatThrownBy(() -> service.importProducts(List.of(first, duplicate)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("重复 SKU");

        var invalid = new ProductModels.ProductImport(
                "x2", "X2", "B", "X2", "phone", Map.of("storage", "128 GB"), BigDecimal.ONE, "AVAILABLE");
        assertThatThrownBy(() -> service.importProducts(List.of(invalid)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("销售状态无效");
        verifyNoInteractions(mapper);
    }

    @Test
    void searchesWithTrimmedFiltersAndClampedPaging() {
        when(mapper.search("phone", "mobile", 0, 50)).thenReturn(List.of(product(1, "Phone", "phone", "{}")));
        when(mapper.count("phone", "mobile")).thenReturn(1L);

        var page = service.search("  phone  ", " mobile ", -1, 1000);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(50);
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).hasSize(1);
    }

    @Test
    void returnsProductAndResolvesTerms() {
        var persisted = product(5, "P5", "Phone", "phone", "{\"storage\":\"256 GB\"}");
        when(mapper.findById(5L)).thenReturn(persisted);
        when(mapper.resolve("P5")).thenReturn(List.of(persisted));

        assertThat(service.get(5L).sku()).isEqualTo("P5");
        assertThat(service.resolve(" P5 "))
                .extracting(ProductModels.ProductView::sku)
                .containsExactly("P5");
        assertThat(service.resolve(" ")).isEmpty();
    }

    @Test
    void rejectsMissingProductAndUnknownComparisonCategory() {
        when(mapper.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("商品不存在");

        when(mapper.findByIds(List.of(8L, 9L)))
                .thenReturn(List.of(product(8, "P8", "P8", "camera", "{}"), product(9, "P9", "P9", "camera", "{}")));
        assertThatThrownBy(() -> service.compare(List.of(8L, 9L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("尚未配置");
    }

    @Test
    void rejectsInvalidImportPayloadsBeforeMapperCall() {
        assertThatThrownBy(() -> service.importProducts(List.of())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.importProducts(List.of(new ProductModels.ProductImport(
                        "x", "X", "B", "M", "phone", Map.of(), BigDecimal.ONE, "ON_SALE"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("字段无效");
        assertThatThrownBy(() -> service.importProducts(List.of(new ProductModels.ProductImport(
                        "x", "X", "B", "M", "phone", Map.of("storage", "1"), BigDecimal.valueOf(-1), "ON_SALE"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("字段无效");
        verifyNoInteractions(mapper);
    }

    @Test
    void failsIfImportedProductsCannotBeReadBack() {
        var request = new ProductModels.ProductImport(
                "x1", "X1", "B", "M", "phone", Map.of("storage", "128 GB"), BigDecimal.ONE, "ON_SALE");
        when(mapper.findBySkus(List.of("X1"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.importProducts(List.of(request)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("完整结果");
    }

    private ProductModels.Product product(long id, String name, String category, String specs) {
        return new ProductModels.Product(
                id, "SKU-" + id, name, "ServiceFlow", name, category, specs, BigDecimal.valueOf(3999), "ON_SALE");
    }

    private ProductModels.Product product(long id, String sku, String name, String category, String specs) {
        return new ProductModels.Product(
                id, sku, name, "ServiceFlow", name, category, specs, BigDecimal.valueOf(4999), "ON_SALE");
    }
}
