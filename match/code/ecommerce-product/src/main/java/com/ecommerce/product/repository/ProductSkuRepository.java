package com.ecommerce.product.repository;

import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.SkuStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSkuRepository extends JpaRepository<ProductSku, Long>,
        JpaSpecificationExecutor<ProductSku> {

    List<ProductSku> findBySpuId(Long spuId);

    List<ProductSku> findByStatus(SkuStatus status);

    Optional<ProductSku> findBySkuCode(String skuCode);

    List<ProductSku> findByIdIn(Collection<Long> ids);
}
