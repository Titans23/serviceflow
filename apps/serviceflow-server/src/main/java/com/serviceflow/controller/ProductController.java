package com.serviceflow.controller;

import com.serviceflow.model.ProductModels;
import com.serviceflow.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public final class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    ProductModels.ProductPage search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(keyword, category, page, size);
    }

    @GetMapping("/{id}")
    ProductModels.ProductView get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/compare")
    ProductModels.Comparison compare(@Valid @RequestBody ProductModels.CompareRequest request) {
        return service.compare(request.productIds());
    }
}
