package com.dyx.market.middleware.db.router.config;

import com.dyx.market.middleware.db.router.dynamic.DynamicRoutingDataSource;
import com.dyx.market.middleware.db.router.plugin.DynamicTableNamePlugin;
import com.dyx.market.middleware.db.router.strategy.HashDBRouterStrategy;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(DBRouterProperties.class)
public class DBRouterAutoConfiguration {

    @Bean("mysqlDataSource")
    public DataSource mysqlDataSource(DBRouterProperties properties, Environment environment) {
        DynamicRoutingDataSource routingDataSource = new DynamicRoutingDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>();
        String defaultKey = read(environment, "mini-db-router.jdbc.datasource.default", properties.getDefaultDataSource());
        targetDataSources.put(defaultKey, createDataSource(environment, defaultKey));
        for (String dbKey : properties.getList().split(",")) {
            String trimmed = dbKey.trim();
            if (!trimmed.isEmpty()) {
                targetDataSources.put(trimmed, createDataSource(environment, trimmed));
            }
        }
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(targetDataSources.get(defaultKey));
        return routingDataSource;
    }

    @Bean
    public IDBRouterStrategy dbRouterStrategy(DBRouterProperties properties) {
        return new HashDBRouterStrategy(properties);
    }

    @Bean("dbRouterDynamicMybatisPlugin")
    public Interceptor dbRouterDynamicMybatisPlugin() {
        return new DynamicTableNamePlugin();
    }

    @Bean("transactionManager")
    public DataSourceTransactionManager transactionManager(DataSource mysqlDataSource) {
        return new DataSourceTransactionManager(mysqlDataSource);
    }

    @Bean
    public TransactionTemplate transactionTemplate(DataSourceTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    private HikariDataSource createDataSource(Environment environment, String key) {
        String prefix = "mini-db-router.jdbc.datasource." + key + ".";
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(read(environment, prefix + "driver-class-name", "com.mysql.cj.jdbc.Driver"));
        dataSource.setJdbcUrl(read(environment, prefix + "url", ""));
        dataSource.setUsername(read(environment, prefix + "username", "root"));
        dataSource.setPassword(read(environment, prefix + "password", ""));
        dataSource.setPoolName(read(environment, prefix + "pool.pool-name", "BigMarketHikariCP-" + key));
        return dataSource;
    }

    private String read(Environment environment, String key, String defaultValue) {
        String value = environment.getProperty(key);
        return null == value ? defaultValue : value;
    }

}
