package com.dyx.market.infrastructure.config;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * 共享 MySQL MyBatis 配置：挂载分库路由插件 {@code dbRouterDynamicMybatisPlugin}。
 * <p>
 * 由扫描 {@code com.dyx.market.infrastructure} 的微服务自动加载，替代各服务内重复的 DataSourceConfig。
 */
@Configuration
@MapperScan(basePackages = "com.dyx.market.infrastructure.dao", sqlSessionFactoryRef = "mysqlSqlSessionFactory")
public class MysqlMybatisConfiguration {

    @Bean("mysqlSqlSessionFactory")
    public SqlSessionFactory mysqlSqlSessionFactory(DataSource mysqlDataSource,
                                                    Interceptor dbRouterDynamicMybatisPlugin) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(mysqlDataSource);
        factoryBean.setPlugins(dbRouterDynamicMybatisPlugin);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath:/mybatis/mapper/mysql/*.xml"));
        return factoryBean.getObject();
    }
}
