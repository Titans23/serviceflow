package com.serviceflow.mapper;

import com.serviceflow.model.ProductModels;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductMapper {
    List<ProductModels.Product> search(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("offset") int offset,
            @Param("size") int size);

    long count(@Param("keyword") String keyword, @Param("category") String category);

    ProductModels.Product findById(@Param("id") long id);

    List<ProductModels.Product> findByIds(@Param("ids") List<Long> ids);

    List<ProductModels.Product> resolve(@Param("term") String term);

    void upsert(
            @Param("sku") String sku,
            @Param("name") String name,
            @Param("brand") String brand,
            @Param("model") String model,
            @Param("category") String category,
            @Param("specsJson") String specsJson,
            @Param("listPrice") java.math.BigDecimal listPrice,
            @Param("saleStatus") String saleStatus);

    List<ProductModels.Product> findBySkus(@Param("skus") List<String> skus);
}
