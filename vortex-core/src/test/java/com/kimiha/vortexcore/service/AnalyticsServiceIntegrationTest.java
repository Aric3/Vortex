package com.kimiha.vortexcore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import java.util.stream.Collectors;

import com.kimiha.vortexcore.config.AnalyticsProperties;
import com.kimiha.vortexcore.model.AnalyticsMetrics;
import com.kimiha.vortexcore.model.LatencyBucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class AnalyticsServiceIntegrationTest {

    private JdbcTemplate jdbcTemplate;
    private AnalyticsService analyticsService;
    private SingleConnectionDataSource dataSource;

    @BeforeEach
    void setUp() {
        if (dataSource != null) {
            dataSource.destroy();
        }
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        jdbcTemplate = new JdbcTemplate(dataSource);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setEnabled(true);
        properties.setRefreshIntervalMs(1000);
        analyticsService = new AnalyticsService(properties, jdbcTemplate);

        createTables();
        jdbcTemplate.update("DELETE FROM trades");
        jdbcTemplate.update("DELETE FROM order_rejects");
        jdbcTemplate.update("DELETE FROM orders");

        // 3 笔通过订单（orders）
        insertOrder("ATEST_ORDER_000001", 1000L);
        insertOrder("ATEST_ORDER_000002", 1100L);
        insertOrder("ATEST_ORDER_000003", 1200L);

        // 1 笔对敲拒绝（order_rejects, code=4001）
        jdbcTemplate.update(
                "INSERT INTO order_rejects (cl_order_id, market, security_id, side, qty, price, shareholder_id, reject_code, reject_text, create_time) " +
                        "VALUES (?, 'XSHG', '600030', 'B', 100, 10.0, 'SHARE00001', 4001, 'Wash trade rejected', ?)",
                "ATEST_ORDER_000004", 1300L
        );

        // 2 笔成交：首笔延时分别为 5ms、80ms
        jdbcTemplate.update(
                "INSERT INTO trades (exec_id, security_id, market, price, qty, trade_time, taker_cl_order_id, maker_cl_order_id, taker_side, maker_side, taker_shareholder_id, maker_shareholder_id) " +
                        "VALUES (?, '600030', 'XSHG', 10.0, 100, ?, ?, ?, 'B', 'S', 'SHARE00001', 'SHARE00002')",
                "ATEST_EXEC_0001", 1005L, "ATEST_ORDER_000001", "ATEST_ORDER_000099"
        );
        jdbcTemplate.update(
                "INSERT INTO trades (exec_id, security_id, market, price, qty, trade_time, taker_cl_order_id, maker_cl_order_id, taker_side, maker_side, taker_shareholder_id, maker_shareholder_id) " +
                        "VALUES (?, '600030', 'XSHG', 10.0, 100, ?, ?, ?, 'S', 'B', 'SHARE00003', 'SHARE00004')",
                "ATEST_EXEC_0002", 1180L, "ATEST_ORDER_000002", "ATEST_ORDER_000098"
        );
    }

    @Test
    void refresh_shouldComputeMetricsByCurrentSqliteSchema() {
        analyticsService.refresh();
        AnalyticsMetrics metrics = analyticsService.getMetrics();

        assertNotNull(metrics);
        assertEquals(1L, metrics.washRejects());
        assertEquals(4L, metrics.totalOrders());
        assertEquals(0.25d, metrics.washRatio());
        assertNotNull(metrics.timestamp());

        Map<String, Long> bucketCount = metrics.latencyBuckets().stream()
                .collect(Collectors.toMap(LatencyBucket::bucket, LatencyBucket::count));

        assertEquals(1L, bucketCount.getOrDefault("2-5ms", 0L));
        assertEquals(1L, bucketCount.getOrDefault("51-100ms", 0L));
    }

    private void insertOrder(String clOrderId, long createTimeMs) {
        jdbcTemplate.update(
                "INSERT INTO orders (cl_order_id, market, security_id, side, qty, price, shareholder_id, create_time) " +
                        "VALUES (?, 'XSHG', '600030', 'B', 100, 10.0, 'SHARE00001', ?)",
                clOrderId, createTimeMs
        );
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    cl_order_id TEXT,
                    market TEXT,
                    security_id TEXT,
                    side TEXT,
                    qty INTEGER,
                    price REAL,
                    shareholder_id TEXT,
                    create_time INTEGER
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS order_rejects (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    cl_order_id TEXT,
                    market TEXT,
                    security_id TEXT,
                    side TEXT,
                    qty INTEGER,
                    price REAL,
                    shareholder_id TEXT,
                    reject_code INTEGER,
                    reject_text TEXT,
                    create_time INTEGER
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS trades (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    exec_id TEXT,
                    security_id TEXT,
                    market TEXT,
                    price REAL,
                    qty INTEGER,
                    trade_time INTEGER,
                    taker_cl_order_id TEXT,
                    maker_cl_order_id TEXT,
                    taker_side TEXT,
                    maker_side TEXT,
                    taker_shareholder_id TEXT,
                    maker_shareholder_id TEXT
                )
                """);
    }
}
