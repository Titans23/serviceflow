package com.serviceflow.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.serviceflow.model.ProductModels;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomerProductFactsTest {
    @Test
    void missingPriceIsExplicitWithoutExposingImportMetadataOrMutatingCatalog() {
        var product = product(null);
        var facts = CustomerWorkflow.customerFacts(product);
        assertThat(facts).containsEntry("报价", "暂无报价");
        assertThat(facts.get("规格")).isEqualTo(Map.of("battery", "5600 mAh"));
        assertThat(product.specs()).containsKeys("source_id", "source_url", "price_note", "sale_status_note");
    }

    @Test
    void verifiedPriceKeepsItsActualValueAndProductIdentity() {
        var facts = CustomerWorkflow.customerFacts(product(new BigDecimal("5999.00")));
        assertThat(facts).containsEntry("报价", "¥5999.00").containsEntry("SKU", "PHONE-001");
    }

    private ProductModels.ProductView product(BigDecimal price) {
        return new ProductModels.ProductView(
                1,
                "PHONE-001",
                "测试手机",
                "品牌",
                "型号",
                "phone",
                Map.of(
                        "battery",
                        "5600 mAh",
                        "source_id",
                        "public-source",
                        "source_url",
                        "https://example.com",
                        "price_note",
                        "null means unknown",
                        "sale_status_note",
                        "demo merchant configuration"),
                price,
                "OFF_SALE");
    }
}
