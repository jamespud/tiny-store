package com.github.spud.tinystore.auth.infrastructure.audit;

import com.github.spud.tinystore.auth.application.dto.AuditQuery;
import com.github.spud.tinystore.auth.application.dto.AuditRecordView;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.domain.audit.AuditEvent;
import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class JdbcAuditLogAdapter implements AuditLogPort {

  private static final String BASE_QUERY = "select user_id, client_id, action, scopes, ip, user_agent, occurred_at from auth_audit";

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcAuditLogAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void append(AuditEvent event) {
    jdbcTemplate.getJdbcTemplate().update(con -> {
      var ps = con.prepareStatement(
          "insert into auth_audit (user_id, subject, client_id, action, scopes, success, ip, user_agent, detail, occurred_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      ps.setObject(1, event.userId());
      ps.setString(2, event.subject());
      ps.setString(3, event.clientId());
      ps.setString(4, event.action());
      ps.setArray(5, createTextArray(con, event.scopes()));
      ps.setBoolean(6, event.success());
      ps.setString(7, event.ip());
      ps.setString(8, event.userAgent());
      ps.setString(9, event.detail());
      ps.setObject(10, event.occurredAt());
      return ps;
    });
  }

  @Override
  public List<AuditRecordView> query(AuditQuery query) {
    StringBuilder sql = new StringBuilder(BASE_QUERY);
    List<String> conditions = new ArrayList<>();
    MapSqlParameterSource params = new MapSqlParameterSource();

    if (query.userId() != null && !query.userId().isBlank()) {
      conditions.add("user_id = :userId");
      params.addValue("userId", UUID.fromString(query.userId()));
    }
    if (query.clientId() != null && !query.clientId().isBlank()) {
      conditions.add("client_id = :clientId");
      params.addValue("clientId", query.clientId());
    }
    if (query.from() != null) {
      conditions.add("occurred_at >= :from");
      params.addValue("from", query.from());
    }
    if (query.to() != null) {
      conditions.add("occurred_at <= :to");
      params.addValue("to", query.to());
    }

    if (!conditions.isEmpty()) {
      sql.append(" where ").append(String.join(" and ", conditions));
    }

    sql.append(" order by occurred_at desc");
    int limit = query.limit() > 0 ? query.limit() : 50;
    sql.append(" limit ").append(limit);

    return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new AuditRecordView(
        Objects.toString(rs.getObject("user_id"), null),
        rs.getString("client_id"),
        rs.getString("action"),
        arrayToScopeString(rs.getArray("scopes")),
        rs.getString("ip"),
        rs.getString("user_agent"),
        rs.getObject("occurred_at", OffsetDateTime.class)
    ));
  }

  private Array createTextArray(Connection connection, Set<String> scopes) throws SQLException {
    if (scopes == null || scopes.isEmpty()) {
      return connection.createArrayOf("text", new String[0]);
    }
    return connection.createArrayOf("text", scopes.toArray(String[]::new));
  }

  private String arrayToScopeString(Array array) throws SQLException {
    if (array == null) {
      return null;
    }
    Object[] values = (Object[]) array.getArray();
    List<String> scopes = new ArrayList<>();
    for (Object value : values) {
      scopes.add(Objects.toString(value));
    }
    return String.join(" ", scopes);
  }
}
