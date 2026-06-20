package com.dyx.market.infrastructure.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.elasticsearch.xpack.sql.jdbc.EsDataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * Elasticsearch SQL JDBC MyBatis 配置（ERP 订单查询）。
 * <p>
 * MySQL 侧由 infrastructure 模块共享 {@link MysqlMybatisConfiguration} 提供。
 * 仅当配置了 {@code spring.elasticsearch.datasource.url} 时启用。
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.elasticsearch.datasource", name = "url")
@MapperScan(basePackages = "com.dyx.market.infrastructure.elasticsearch", sqlSessionFactoryRef = "elasticsearchSqlSessionFactory")
public class ElasticsearchMybatisConfiguration {

    @Bean("elasticsearchDataSource")
    @ConfigurationProperties(prefix = "spring.elasticsearch.datasource")
    public DataSource elasticsearchDataSource() {
        return new EsDataSource();
    }

    @Bean("elasticsearchSqlSessionFactory")
    public SqlSessionFactory elasticsearchSqlSessionFactory(
            @Qualifier("elasticsearchDataSource") DataSource elasticsearchDataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(elasticsearchDataSource);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath:/mybatis/mapper/elasticsearch/*.xml"));
        return factoryBean.getObject();
    }
}
