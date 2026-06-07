package com.dyx.market.middleware.db.router.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mini-db-router.jdbc.datasource", ignoreInvalidFields = true)
public class DBRouterProperties {

    private int dbCount = 1;
    private int tbCount = 1;
    private String defaultDataSource = "db00";
    private String routerKey = "userId";
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
