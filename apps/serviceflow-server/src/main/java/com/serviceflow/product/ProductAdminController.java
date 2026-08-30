package com.serviceflow.product;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/products")
public final class ProductAdminController {

    private final ProductService service;
    private final ObjectMapper objectMapper;

    public ProductAdminController(ProductService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/import")
    public ProductModels.ImportResponse importProducts(@RequestBody JsonNode payload) {
        JsonNode productsNode = payload == null ? null : payload.get("products");
        if (productsNode == null || !productsNode.isArray()) {
            throw new ResponseStatusException(BAD_REQUEST, "products 必须是数组");
        }
        List<ProductModels.ProductImport> imports;
        try {
            imports = new ArrayList<>();
            for (JsonNode productNode : productsNode) {
                imports.add(objectMapper.treeToValue(productNode, ProductModels.ProductImport.class));
            }
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "商品字段格式无效", exception);
        }
        return service.importProducts(imports);
    }
}
