package com.dyx.market.message.job.service;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;

/**
 * BM-002：确保 message-job 的 MyBatis Mapper XML 加载时不存在重复 statement ID。
 */
public class MessageJobMapperXmlLoadTest {

    @Test
    public void mapperXmlLoadsWithoutDuplicateStatementIds() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/mybatis/mapper/mysql/*.xml");
        Configuration configuration = new Configuration();

        for (Resource resource : resources) {
            try (InputStream inputStream = resource.getInputStream()) {
                new XMLMapperBuilder(inputStream, configuration, resource.getURI().toString(),
                        configuration.getSqlFragments()).parse();
            }
        }
    }
}
