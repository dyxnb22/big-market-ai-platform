package com.dyx.market.account.config;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.elasticsearch.xpack.sql.jdbc.EsDataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * 多数据源 MyBatis 配置：Elasticsearch SQL JDBC 与 MySQL 各一套 SqlSessionFactory。
 * <p>
 * ES 数据源绑定 {@code spring.elasticsearch.datasource}；MySQL 使用 Spring Boot
 * 默认 {@link DataSource}，并挂载分库路由插件 {@code dbRouterDynamicMybatisPlugin}。
 */
@Configuration
public class DataSourceConfig {

    @Configuration
    @MapperScan(basePackages = "com.dyx.market.infrastructure.elasticsearch", sqlSessionFactoryRef = "elasticsearchSqlSessionFactory")
    static class ElasticsearchMyBatisConfig {

        @Bean("elasticsearchDataSource")
        @ConfigurationProperties(prefix = "spring.elasticsearch.datasource")
        public DataSource igniteDataSource() {
            return new EsDataSource();
        }

        @Bean("elasticsearchSqlSessionFactory")
        public SqlSessionFactory elasticsearchSqlSessionFactory(@Qualifier("elasticsearchDataSource") DataSource elasticsearchDataSource) throws Exception {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(elasticsearchDataSource);
            factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:/mybatis/mapper/elasticsearch/*.xml"));
            return factoryBean.getObject();
        }
    }

    @Configuration
    @MapperScan(basePackages = "com.dyx.market.infrastructure.dao", sqlSessionFactoryRef = "mysqlSqlSessionFactory")
    static class MysqlMyBatisConfig {

        @Bean("mysqlSqlSessionFactory")
        public SqlSessionFactory mysqlSqlSessionFactory(DataSource mysqlDataSource, Interceptor dbRouterDynamicMybatisPlugin) throws Exception {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(mysqlDataSource);
            factoryBean.setPlugins(dbRouterDynamicMybatisPlugin);
            factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:/mybatis/mapper/mysql/*.xml"));
            return factoryBean.getObject();
        }
    }

}
