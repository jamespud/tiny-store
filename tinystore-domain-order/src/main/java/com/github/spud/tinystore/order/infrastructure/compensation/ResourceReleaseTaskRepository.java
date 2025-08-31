package com.github.spud.tinystore.order.infrastructure.compensation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceReleaseTaskRepository extends JpaRepository<ResourceReleaseTask, UUID> {

	List<ResourceReleaseTask> findTop100ByCompletedFalseAndNextRetryTimeBeforeOrderByNextRetryTimeAsc(
		OffsetDateTime now);
}