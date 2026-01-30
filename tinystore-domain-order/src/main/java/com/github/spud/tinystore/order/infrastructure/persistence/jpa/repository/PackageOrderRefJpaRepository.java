package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.PackageOrderRefEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * PackageOrderRef JPA Repository
 */
@Repository
public interface PackageOrderRefJpaRepository extends JpaRepository<PackageOrderRefEntity, Long> {

    List<PackageOrderRefEntity> findByPackageId(String packageId);

    List<PackageOrderRefEntity> findByOrderId(String orderId);
}
