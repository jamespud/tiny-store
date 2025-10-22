package com.github.spud.tinystore.auth.infrastructure.persistence.repository;

import com.github.spud.tinystore.auth.infrastructure.persistence.entity.Client;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends JpaRepository<Client, String> {

  Optional<Client> findByClientId(String clientId);
}