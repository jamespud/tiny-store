package com.github.spud.tinystore.auth.infrastructure.outbox;

import com.github.spud.tinystore.auth.application.port.out.OutboxPort;
import com.github.spud.tinystore.auth.domain.event.ConsentChangedEvent;
import com.github.spud.tinystore.auth.domain.event.RefreshTokenRevokedEvent;
import com.github.spud.tinystore.auth.domain.event.UserFrozenEvent;
import com.github.spud.tinystore.auth.domain.event.UserUnfrozenEvent;
import com.github.spud.tinystore.auth.domain.primitives.ClientId;
import com.github.spud.tinystore.auth.domain.primitives.RtVersion;
import com.github.spud.tinystore.auth.domain.primitives.ScopeName;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
      PreparedStatement ps = con.prepareStatement(
          "insert into auth_outbox (event_type, aggregate_id, rt_version, reason, occurred_at, published) values (?, ?, ?, ?, ?, false)");
      ps.setString(1, "RefreshTokenRevokedEvent");
      ps.setString(2, event.userId().toString());
      ps.setLong(3, event.rtVersion().value());
      ps.setString(4, event.reason());
      ps.setObject(5, event.occurredAt());
      return ps;
    });
  }

  @Override
  public void save(ConsentChangedEvent event) {
    jdbcTemplate.update(con -> {
      PreparedStatement ps = con.prepareStatement(
          "insert into auth_outbox (event_type, aggregate_id, client_id, added_scopes, removed_scopes, occurred_at, published) values (?, ?, ?, ?, ?, ?, false)");
      ps.setString(1, "ConsentChangedEvent");
      ps.setString(2, event.userId().value());
      ps.setString(3, event.clientId().value());
      ps.setString(4,
          event.addedScopes().stream().map(ScopeName::value).reduce((a, b) -> a + "," + b)
              .orElse(""));
      ps.setString(5,
          event.removedScopes().stream().map(ScopeName::value).reduce((a, b) -> a + "," + b)
              .orElse(""));
      ps.setObject(6, event.occurredAt());
      return ps;
    });
  }

	@Override
	public void save(UserFrozenEvent event) {
		
	}

	@Override
	public void save(UserUnfrozenEvent event) {

	}

	@Override
  public List<ConsentChangedEvent> fetchConsentChangedUnpublished(int batchSize) {
    return jdbcTemplate.query(
        "select aggregate_id, client_id, added_scopes, removed_scopes, occurred_at from auth_outbox where event_type = 'ConsentChangedEvent' and published = false order by occurred_at asc limit ?",
        (rs, rowNum) -> {
          String userId = rs.getString("aggregate_id");
          String clientId = rs.getString("client_id");
          String added = rs.getString("added_scopes");
          String removed = rs.getString("removed_scopes");
          OffsetDateTime at = rs.getObject("occurred_at", OffsetDateTime.class);
          Set<ScopeName> addedSet = Arrays.stream(
                  (added == null ? "" : added).split(","))
              .filter(s -> !s.isBlank())
              .map(ScopeName::of)
              .collect(Collectors.toCollection(LinkedHashSet::new));
          Set<ScopeName> removedSet = Arrays.stream(
                  (removed == null ? "" : removed).split(","))
              .filter(s -> !s.isBlank())
              .map(ScopeName::of)
              .collect(Collectors.toCollection(LinkedHashSet::new));
          return new ConsentChangedEvent(
              UserId.of(userId),
              ClientId.of(clientId),
              addedSet,
              removedSet,
              at
          );
        },
        batchSize);
  }

  @Override
  public void markConsentChangedPublished(List<ConsentChangedEvent> events) {
    if (events.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(
        "update auth_outbox set published = true, published_at = now() where event_type = 'ConsentChangedEvent' and aggregate_id = ? and client_id = ? and occurred_at = ? and published = false",
        events,
        events.size(),
        (ps, evt) -> {
          ps.setString(1, evt.userId().value());
          ps.setString(2, evt.clientId().value());
          ps.setObject(3, evt.occurredAt());
        });
  }

  @Override
  public List<RefreshTokenRevokedEvent> fetchUnpublished(int batchSize) {
    return jdbcTemplate.query(
        "select aggregate_id, rt_version, reason, occurred_at from auth_outbox where published = false order by occurred_at asc limit ?",
        (rs, rowNum) -> new RefreshTokenRevokedEvent(
            UserId.of((String) rs.getObject("aggregate_id")),
            RtVersion.of(rs.getLong("rt_version")),
            rs.getString("reason"),
            rs.getObject("occurred_at", OffsetDateTime.class)
        ),
        batchSize);
  }

  @Override
  public void markPublished(List<RefreshTokenRevokedEvent> events) {
    if (events.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(
        "update auth_outbox set published = true, published_at = now() where aggregate_id = ? and rt_version = ? and published = false",
        events,
        events.size(),
        (ps, event) -> {
          ps.setObject(1, event.userId().value());
          ps.setLong(2, event.rtVersion().value());
        });
  }
}
