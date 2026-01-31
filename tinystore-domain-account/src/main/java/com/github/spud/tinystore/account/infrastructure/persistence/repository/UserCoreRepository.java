package com.github.spud.tinystore.account.infrastructure.persistence.repository;

import com.github.spud.tinystore.account.infrastructure.persistence.entity.UserCore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserCoreRepository extends JpaRepository<UserCore, String> {

    Optional<UserCore> findByAccount(String account);
}