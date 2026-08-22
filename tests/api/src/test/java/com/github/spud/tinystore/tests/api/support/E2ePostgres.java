package com.github.spud.tinystore.tests.api.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Direct PostgreSQL client for strong DB-level E2E assertions against the compose-test stack.
 * Connection defaults mirror {@link SeedData}; overridable via {@code POSTGRES_URL}/{@code POSTGRES_USER}/etc.
 */
public final class E2ePostgres implements AutoCloseable {

    private final Connection connection;

    public E2ePostgres() {
        String url = System.getProperty("POSTGRES_URL", "jdbc:postgresql://localhost:5433/tinystore");
        String user = System.getProperty("POSTGRES_USER", "postgres");
        String password = System.getProperty("POSTGRES_PASSWORD", "postgres");
        try {
            this.connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new IllegalStateException("E2E Postgres unavailable at " + url + ": " + e.getMessage(), e);
        }
    }

    public long countReservationsByTradeAndStatus(String tradeId, String status) {
        String sql = "SELECT COUNT(*) FROM tinystore_inventory.inventory_reservation WHERE trade_id = ? AND status = ?";
        return count(sql, tradeId, status);
    }

    public long countShopOrdersByTrade(String tradeId) {
        String sql = "SELECT COUNT(*) FROM tinystore_order.shop_order WHERE trade_id = ?";
        return count(sql, tradeId);
    }

    private long count(String sql, String... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("E2E Postgres query failed: " + sql, e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // ignore on close
        }
    }
}
