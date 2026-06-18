package com.dyx.market.middleware.db.router.dynamic;

import com.dyx.market.middleware.db.router.DBContextHolder;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 基于 {@link DBContextHolder} 的动态路由数据源。
 *
 * <p>每次获取连接时，根据当前线程绑定的 dbKey 选择目标物理数据源。</p>
 */
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DBContextHolder.getDBKey();
    }

}
