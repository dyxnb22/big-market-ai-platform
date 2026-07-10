package com.dyx.market.message.job.config;

import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Outbox 表结构校验：当 {@code account.award-credit-outbox.enabled=true} 时，
 * 启动前检测各分片库中 {@code credit_award_task_000..003} 是否存在，缺失则 fail-fast。
 * <p>
 * 回滚：设 {@code OUTBOX_SCHEMA_GUARD_ENABLED=false} 可禁用。
 */
@Slf4j
@Component
public class OutboxSchemaValidator implements CommandLineRunner {

    private static final String[] REQUIRED_TABLES = {
            "credit_award_task_000",
            "credit_award_task_001",
            "credit_award_task_002",
            "credit_award_task_003"
    };

    private static final String DDL_GUIDANCE = "docs/sql/credit-award-task-outbox.sql";

    @Value("${outbox-schema-guard.enabled:true}")
    private boolean guardEnabled;

    @Value("${account.award-credit-outbox.enabled:false}")
    private boolean awardCreditOutboxEnabled;

    @Resource
    private DataSource dataSource;

    @Resource
    private IDBRouterStrategy dbRouter;

    @Override
    public void run(String... args) {
        if (!guardEnabled) {
            log.warn("[OutboxSchemaValidator] DISABLED — missing outbox tables will not be caught at startup");
            return;
        }
        if (!awardCreditOutboxEnabled) {
            return;
        }

        List<String> missing = new ArrayList<>();
        for (int dbIdx = 1; dbIdx <= 2; dbIdx++) {
            dbRouter.setDBKey(dbIdx);
            try (Connection conn = dataSource.getConnection()) {
                String schema = conn.getCatalog();
                for (String table : REQUIRED_TABLES) {
                    if (!tableExists(conn, schema, table)) {
                        missing.add(String.format("db%02d.%s.%s", dbIdx, schema, table));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException(
                        "[OutboxSchemaValidator] failed to inspect shard db" + dbIdx, e);
            } finally {
                dbRouter.clear();
            }
        }

        if (!missing.isEmpty()) {
            String msg = "\n==========================================================\n"
                    + "[OutboxSchemaValidator] REFUSING TO START — "
                    + "credit_award_task outbox tables are missing:\n"
                    + String.join("\n", missing)
                    + "\n\nApply DDL from " + DDL_GUIDANCE
                    + " to big_market_01 and big_market_02 "
                    + "(Docker init: docs/dev-ops/mysql/sql/z-credit-award-task-outbox.sql).\n"
                    + "Set OUTBOX_SCHEMA_GUARD_ENABLED=false only when "
                    + "you are certain this is intentional.\n"
                    + "==========================================================";
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("[OutboxSchemaValidator] credit_award_task_000..003 present on db01 and db02");
    }

    private boolean tableExists(Connection conn, String schema, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_name = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
