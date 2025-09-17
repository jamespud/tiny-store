package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataReservationRepository extends JpaRepository<ReservationEntity, String> {

}

