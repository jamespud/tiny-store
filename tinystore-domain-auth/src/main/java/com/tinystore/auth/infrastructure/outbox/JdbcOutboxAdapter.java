package com.tinystore.auth.infrastructure.outbox;

import com.tinystore.auth.application.port.out.OutboxPort;
import com.tinystore.auth.domain.event.RefreshTokenRevokedEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Repository
@Primary
public class JdbcOutboxAdapter implements OutboxPort {

	private final JdbcTemplate jdbcTemplate;

	public JdbcOutboxAdapter(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void save(RefreshTokenRevokedEvent event) {
		jdbcTemplate.update(con -> {
			PreparedStatement ps = con.prepareStatement("insert into auth_outbox (event_type, aggregate_id, rt_version, reason, occurred_at) values (?, ?, ?, ?, ?)");
			ps.setString(1, "RefreshTokenRevokedEvent");
			ps.setString(2, event.userId().toString());
			ps.setLong(3, event.rtVersion().getValue());
			ps.setString(4, event.reason());
			ps.setObject(5, event.occurredAt());
			return ps;
		});
	}

	@Override
	public List<RefreshTokenRevokedEvent> fetchUnpublished(int batchSize) {
		return Collections.emptyList();
	}

	@Override
	public void markPublished(List<RefreshTokenRevokedEvent> events) {
		// publication handled in Phase 3
	}
}
