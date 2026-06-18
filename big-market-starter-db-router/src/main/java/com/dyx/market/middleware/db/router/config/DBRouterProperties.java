package com.dyx.market.middleware.db.router.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据库分库分表路由配置属性。
 *
 * <p>绑定前缀：{@code mini-db-router.jdbc.datasource}</p>
 */
@ConfigurationProperties(prefix = "mini-db-router.jdbc.datasource", ignoreInvalidFields = true)
public class DBRouterProperties {

    /** 分库数量，默认 1。 */
    private int dbCount = 1;
    /** 每个库的分表数量，默认 1。 */
    private int tbCount = 1;
    /** 默认数据源键，如 db00。 */
    private String defaultDataSource = "db00";
    /** 路由键字段名，默认 userId。 */
    private String routerKey = "userId";
    /** 逗号分隔的数据源键列表，如 db01,db02。 */
    private String list = "";

    public int getDbCount() {
        return dbCount;
    }

    public void setDbCount(int dbCount) {
        this.dbCount = dbCount;
    }

    public int getTbCount() {
        return tbCount;
    }

    public void setTbCount(int tbCount) {
        this.tbCount = tbCount;
    }

    public String getDefaultDataSource() {
        return defaultDataSource;
    }

    public void setDefaultDataSource(String defaultDataSource) {
        this.defaultDataSource = defaultDataSource;
    }

    public String getRouterKey() {
        return routerKey;
    }

    public void setRouterKey(String routerKey) {
        this.routerKey = routerKey;
    }

    public String getList() {
        return list;
    }

    public void setList(String list) {
        this.list = list;
    }

}
