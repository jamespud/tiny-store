package com.github.spud.tinystore.order.infrastructure.compensation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ResourceReleaseTaskRepository extends JpaRepository<ResourceReleaseTask, UUID> {

	List<ResourceReleaseTask> findTop100ByCompletedFalseAndNextRetryTimeBeforeOrderByNextRetryTimeAsc(
		OffsetDateTime now);
}