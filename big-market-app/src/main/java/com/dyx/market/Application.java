package com.dyx.market;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Legacy monolith launcher kept for integration tests and historical comparison.
 * Use the microservice launchers in {@code docker-compose.yml} for the supported path.
 *
 * @deprecated See {@code big-market-app/README.md}.
 */
@Deprecated
@SpringBootApplication
@Configurable
@EnableScheduling
@EnableDubbo
@ImportResource(locations = {"classpath:spring-config.xml"})
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class Application {

    public static void main(String[] args){
        SpringApplication.run(Application.class);
    }

}
