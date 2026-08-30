package com.serviceflow.service;

import com.serviceflow.model.ProductModels;
import java.util.List;

public interface ProductService {
    ProductModels.ProductPage search(String keyword, String category, int page, int size);

    ProductModels.ProductView get(long id);

    ProductModels.Comparison compare(List<? extends Number> ids);

    List<ProductModels.ProductView> resolve(String term);

    ProductModels.ImportResponse importProducts(List<ProductModels.ProductImport> imports);
}
