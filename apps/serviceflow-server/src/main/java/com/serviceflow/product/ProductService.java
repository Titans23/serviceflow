package com.serviceflow.product;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.yaml.snakeyaml.Yaml;

@Service
public class ProductService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_IMPORT_SIZE = 500;
    private static final Set<String> SALE_STATUSES = Set.of("ON_SALE", "OFF_SALE", "DISCONTINUED");

    private final ProductMapper mapper;
    private final ObjectMapper objectMapper;
    private final Map<String, List<String>> comparisonFields;

    public ProductService(ProductMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        try (InputStream input = new ClassPathResource("product-comparison-fields.yml").getInputStream()) {
            this.comparisonFields = Collections.unmodifiableMap(new Yaml().load(input));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load product comparison fields", exception);
        }
    }

    public ProductModels.ProductPage search(String keyword, String category, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<ProductModels.ProductView> items =
                mapper.search(trim(keyword), trim(category), safePage * safeSize, safeSize).stream()
                        .map(this::view)
                        .toList();
        return new ProductModels.ProductPage(items, safePage, safeSize, mapper.count(trim(keyword), trim(category)));
    }

    public ProductModels.ProductView get(long id) {
        ProductModels.Product product = mapper.findById(id);
        if (product == null) {
            throw new ResponseStatusException(NOT_FOUND, "商品不存在");
        }
        return view(product);
    }

    public ProductModels.Comparison compare(List<? extends Number> ids) {
        if (ids == null) {
            throw new ResponseStatusException(BAD_REQUEST, "请选择 2 至 3 个不同商品");
        }
        List<Long> normalizedIds = ids.stream().map(Number::longValue).toList();
        if (normalizedIds.size() < 2
                || normalizedIds.size() > 3
                || new HashSet<>(normalizedIds).size() != normalizedIds.size()) {
            throw new ResponseStatusException(BAD_REQUEST, "请选择 2 至 3 个不同商品");
        }

        List<ProductModels.Product> products = mapper.findByIds(normalizedIds);
        if (products.size() != normalizedIds.size()) {
            throw new ResponseStatusException(NOT_FOUND, "部分商品不存在");
        }
        String category = products.getFirst().category();
        if (products.stream().anyMatch(product -> !category.equals(product.category()))) {
            throw new ResponseStatusException(BAD_REQUEST, "仅支持同类别商品比较");
        }
        List<String> fields = comparisonFields.get(category);
        if (fields == null) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "该商品类别尚未配置比较字段");
        }

        Map<Long, ProductModels.Product> byId = new HashMap<>();
        products.forEach(product -> byId.put(product.id(), product));
        return new ProductModels.Comparison(
                category,
                fields,
                normalizedIds.stream().map(byId::get).map(this::view).toList());
    }

    public List<ProductModels.ProductView> resolve(String term) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        return mapper.resolve(term.trim()).stream().map(this::view).toList();
    }

    @Transactional
    public ProductModels.ImportResponse importProducts(List<ProductModels.ProductImport> imports) {
        if (imports == null || imports.isEmpty() || imports.size() > MAX_IMPORT_SIZE) {
            throw new ResponseStatusException(BAD_REQUEST, "一次最多导入 500 个商品");
        }

        List<String> skus = imports.stream()
                .map(item -> normalizeSku(item == null ? null : item.sku()))
                .toList();
        if (new HashSet<>(skus).size() != skus.size()) {
            throw new ResponseStatusException(BAD_REQUEST, "导入数据包含重复 SKU");
        }

        for (int index = 0; index < imports.size(); index++) {
            ProductModels.ProductImport item = imports.get(index);
            validateImport(item, index);
            String saleStatus = item.saleStatus().trim().toUpperCase(Locale.ROOT);
            if (!SALE_STATUSES.contains(saleStatus)) {
                throw new ResponseStatusException(BAD_REQUEST, "第 " + (index + 1) + " 个商品的销售状态无效");
            }
            try {
                mapper.upsert(
                        skus.get(index),
                        item.name().trim(),
                        item.brand().trim(),
                        item.model().trim(),
                        item.category().trim(),
                        objectMapper.writeValueAsString(item.specs()),
                        item.listPrice(),
                        saleStatus);
            } catch (ResponseStatusException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new ResponseStatusException(BAD_REQUEST, "商品规格不是有效 JSON", exception);
            }
        }

        Map<String, ProductModels.Product> productsBySku = mapper.findBySkus(skus).stream()
                .collect(Collectors.toMap(ProductModels.Product::sku, product -> product));
        List<ProductModels.ProductView> views = skus.stream()
                .map(productsBySku::get)
                .filter(java.util.Objects::nonNull)
                .map(this::view)
                .toList();
        if (views.size() != skus.size()) {
            throw new IllegalStateException("商品导入后无法读取完整结果");
        }
        return new ProductModels.ImportResponse(views.size(), views);
    }

    private ProductModels.ProductView view(ProductModels.Product product) {
        return new ProductModels.ProductView(
                product.id(),
                product.sku(),
                product.name(),
                product.brand(),
                product.model(),
                product.category(),
                product.specs(objectMapper),
                product.listPrice(),
                product.saleStatus());
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeSku(String sku) {
        if (sku == null) {
            return null;
        }
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private void validateImport(ProductModels.ProductImport item, int index) {
        if (item == null
                || blank(item.sku())
                || item.sku().trim().length() > 64
                || blank(item.name())
                || item.name().trim().length() > 200
                || blank(item.brand())
                || item.brand().trim().length() > 100
                || blank(item.model())
                || item.model().trim().length() > 100
                || blank(item.category())
                || item.category().trim().length() > 64
                || item.specs() == null
                || item.specs().isEmpty()
                || item.listPrice() == null
                || item.listPrice().signum() < 0
                || blank(item.saleStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "第 " + (index + 1) + " 个商品字段无效");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
