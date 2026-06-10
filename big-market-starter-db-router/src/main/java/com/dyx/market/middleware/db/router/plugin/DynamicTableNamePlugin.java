package com.dyx.market.middleware.db.router.plugin;

import com.dyx.market.middleware.db.router.DBContextHolder;
import com.dyx.market.middleware.db.router.annotation.DBRouterStrategy;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.sql.Connection;
import java.util.Properties;
import java.util.regex.Matcher;

/**
 * MyBatis plugin that appends the routed table suffix for sharded mappers.
 *
 * <p>This is intentionally compact so the routing idea is visible while learning.
 * It handles the table names used by this project and leaves SQL untouched when
 * the mapper is not annotated with {@link DBRouterStrategy#splitTable()}.</p>
 */
@Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
public class DynamicTableNamePlugin implements Interceptor {

    private static final String[] SHARDED_TABLES = {
            "raffle_activity_order",
            "user_award_record",
            "user_behavior_rebate_order",
            "credit_award_task",
            "user_credit_order",
            "user_raffle_order",
            "raffle_quota_decrement_ledger"
    };

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        String tableSuffix = DBContextHolder.getTBKey();
        if (null == tableSuffix) {
            return invocation.proceed();
        }

        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        String statementId = String.valueOf(metaObject.getValue("delegate.mappedStatement.id"));
        String mapperClassName = statementId.substring(0, statementId.lastIndexOf('.'));
        Class<?> mapperClass = Class.forName(mapperClassName);
        DBRouterStrategy strategy = mapperClass.getAnnotation(DBRouterStrategy.class);
        if (null == strategy || !strategy.splitTable()) {
            return invocation.proceed();
        }

        BoundSql boundSql = statementHandler.getBoundSql();
        String sql = boundSql.getSql();
        for (String table : SHARDED_TABLES) {
            sql = replaceTable(sql, table, table + "_" + tableSuffix);
        }
        metaObject.setValue("delegate.boundSql.sql", sql);
        return invocation.proceed();
    }

    private String replaceTable(String sql, String logicalName, String physicalName) {
        return sql.replaceAll("(?i)(?<![a-zA-Z0-9_])" + logicalName + "(?![a-zA-Z0-9_])", Matcher.quoteReplacement(physicalName));
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

}
