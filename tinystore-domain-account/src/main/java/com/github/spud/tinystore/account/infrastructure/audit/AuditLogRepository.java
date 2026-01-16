package com.github.spud.tinystore.account.infrastructure.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByClientIp(String clientIp);

    List<AuditLog> findBySuccess(Boolean success);

    List<AuditLog> findByCreateTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    List<AuditLog> findByRequestUriContaining(String uri);
}