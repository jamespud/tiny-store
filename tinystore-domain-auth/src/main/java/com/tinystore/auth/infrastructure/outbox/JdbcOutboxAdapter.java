package com.tinystore.auth.infrastructure.outbox;

import com.tinystore.auth.application.port.out.OutboxPort;
import com.tinystore.auth.domain.event.RefreshTokenRevokedEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

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
			PreparedStatement ps = con.prepareStatement("insert into auth_outbox (event_type, aggregate_id, rt_version, reason, occurred_at, published) values (?, ?, ?, ?, ?, false)");
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
		return jdbcTemplate.query(
			"select aggregate_id, rt_version, reason, occurred_at from auth_outbox where published = false order by occurred_at asc limit ?",
			(rs, rowNum) -> new RefreshTokenRevokedEvent(
				com.tinystore.auth.domain.primitives.UserId.of((UUID) rs.getObject("aggregate_id")),
				com.tinystore.auth.domain.primitives.RtVersion.of(rs.getLong("rt_version")),
				rs.getString("reason"),
				rs.getObject("occurred_at", java.time.OffsetDateTime.class)
			),
			batchSize);
	}

	@Override
	public void markPublished(List<RefreshTokenRevokedEvent> events) {
		if (events.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate("update auth_outbox set published = true, published_at = now() where aggregate_id = ? and rt_version = ? and published = false",
			events,
			events.size(),
			(ps, event) -> {
				ps.setObject(1, event.userId().getValue());
				ps.setLong(2, event.rtVersion().getValue());
			});
	}
}
