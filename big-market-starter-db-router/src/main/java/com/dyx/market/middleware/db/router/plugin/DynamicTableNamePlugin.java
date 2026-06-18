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
 * MyBatis 插件：为分片 Mapper 追加路由后的物理表后缀。
 *
 * <p>实现刻意保持精简，便于学习路由原理。
 * 仅处理本项目涉及的分片表名；若 Mapper 未标注
 * {@link DBRouterStrategy#splitTable()}，则原样放行 SQL。</p>
 */
@Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
public class DynamicTableNamePlugin implements Interceptor {

    /** 需要分片的逻辑表名列表。 */
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
