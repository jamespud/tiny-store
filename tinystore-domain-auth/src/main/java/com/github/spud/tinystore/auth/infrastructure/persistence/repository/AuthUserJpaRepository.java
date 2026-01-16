package com.github.spud.tinystore.auth.infrastructure.persistence.repository;

import com.github.spud.tinystore.auth.infrastructure.persistence.entity.AuthUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AuthUserJpaRepository extends JpaRepository<AuthUserEntity, Long> {

  Optional<AuthUserEntity> findByPhone(String phone);
}
