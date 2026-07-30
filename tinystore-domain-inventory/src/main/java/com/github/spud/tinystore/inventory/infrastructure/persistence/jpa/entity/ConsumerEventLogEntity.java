package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "consumer_event_log", schema = "tinystore_inventory")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerEventLogEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "event_id", nullable = false)
    private String eventId;
    @Column(name = "consumer_name", nullable = false)
    private String consumerName;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
