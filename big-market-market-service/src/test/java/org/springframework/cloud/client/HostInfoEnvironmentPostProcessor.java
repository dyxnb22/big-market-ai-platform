package org.springframework.cloud.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Test-classpath override of Spring Cloud's HostInfoEnvironmentPostProcessor.
 * Avoids InetAddress.getLocalHost() which fails in restricted sandboxes
 * (SocketException: Operation not permitted).
 */
public class HostInfoEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private int order = Ordered.HIGHEST_PRECEDENCE + 9;

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> map = new HashMap<>();
        map.putIfAbsent("spring.cloud.client.hostname",
                environment.getProperty("spring.cloud.client.hostname", "localhost"));
        map.putIfAbsent("spring.cloud.client.ip-address",
                environment.getProperty("spring.cloud.client.ip-address", "127.0.0.1"));
        // Always seed localhost for tests — do not call InetUtils.
        map.put("spring.cloud.client.hostname", "localhost");
        map.put("spring.cloud.client.ip-address", "127.0.0.1");
        environment.getPropertySources().addLast(new MapPropertySource("springCloudClientHostInfo", map));
    }
}
