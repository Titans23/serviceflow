package com.serviceflow.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ProductModels {
    private ProductModels() {}

    public record Product(
            long id,
            String sku,
            String name,
            String brand,
            String model,
            String category,
            String specsJson,
            BigDecimal listPrice,
            String saleStatus) {
        public Map<String, Object> specs(ObjectMapper mapper) {
            try {
                return mapper.readValue(specsJson, new TypeReference<>() {});
            } catch (Exception exception) {
                throw new IllegalStateException("Invalid product specs for " + sku, exception);
            }
        }
    }

    public record ProductView(
            long id,
            String sku,
            String name,
            String brand,
            String model,
            String category,
            Map<String, Object> specs,
            BigDecimal listPrice,
            String saleStatus) {}

    public record ProductPage(List<ProductView> items, int page, int size, long total) {}

    public record CompareRequest(List<Number> productIds) {}

    public record Comparison(String category, List<String> fields, List<ProductView> products) {}

    public record ProductImport(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String brand,
            @NotBlank @Size(max = 100) String model,
            @NotBlank @Size(max = 64) String category,
            @NotEmpty Map<String, Object> specs,
            @NotNull @DecimalMin(value = "0.00") BigDecimal listPrice,
            @NotBlank String saleStatus) {}

    public record ImportResponse(int imported, List<ProductView> products) {}
}
