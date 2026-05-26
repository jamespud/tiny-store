package com.github.spud.tinystore.tests.performance.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Direct PostgreSQL client for strong assertions
 */
public class PostgresClient implements AutoCloseable {

    private final Connection connection;

    public PostgresClient(String url, String user, String password) {
        try {
            this.connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to Postgres: " + url, e);
        }
    }

    public long countTrades(String tradeId) {
        String sql = "SELECT COUNT(*) FROM tinystore_order.trade WHERE trade_id = ?";
        return executeCount(sql, tradeId);
    }

    public long countShopOrders(String tradeId) {
        String sql = "SELECT COUNT(*) FROM tinystore_order.shop_order WHERE trade_id = ?";
        return executeCount(sql, tradeId);
    }

    public long countReservations(String shopId, String skuId, String status) {
        String sql = "SELECT COUNT(*) FROM tinystore_inventory.inventory_reservation " +
                     "WHERE shop_id = ? AND sku_id = ? AND status = ?";
        return executeCount(sql, shopId, skuId, status);
    }

    public long countReservationsByTradeId(String tradeId, String status) {
        String sql = "SELECT COUNT(*) FROM tinystore_inventory.inventory_reservation " +
                     "WHERE trade_id = ? AND status = ?";
        return executeCount(sql, tradeId, status);
    }

    public long countReservationsByTradePrefix(String tradeIdPrefix, String status) {
        String sql = "SELECT COUNT(*) FROM tinystore_inventory.inventory_reservation " +
                     "WHERE trade_id LIKE ? AND status = ?";
        return executeCount(sql, tradeIdPrefix + "%", status);
    }

    public InventoryStock getInventoryStock(String shopId, String skuId) {
        String sql = "SELECT total_quantity, reserved_quantity FROM tinystore_inventory.inventory_stock " +
                     "WHERE shop_id = ? AND sku_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, shopId);
            ps.setString(2, skuId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new InventoryStock(rs.getLong("total_quantity"), rs.getLong("reserved_quantity"));
                }
                throw new RuntimeException("Stock not found: " + shopId + "/" + skuId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query inventory stock", e);
        }
    }

    public long countSuccessfulTrades(String tradeIdPrefix) {
        String sql = "SELECT COUNT(*) FROM tinystore_order.trade WHERE trade_id LIKE ?";
        return executeCount(sql, tradeIdPrefix + "%");
    }

    private long executeCount(String sql, String... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute count query: " + sql, e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            // Ignore close errors
        }
    }

    public static class InventoryStock {
        public final long totalQuantity;
        public final long reservedQuantity;

        public InventoryStock(long totalQuantity, long reservedQuantity) {
            this.totalQuantity = totalQuantity;
            this.reservedQuantity = reservedQuantity;
        }
    }
}
