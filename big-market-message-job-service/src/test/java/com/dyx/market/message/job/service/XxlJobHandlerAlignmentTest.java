package com.dyx.market.message.job.service;

import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * BM-003：{@code @XxlJob} 处理器名称必须预置在 docs/dev-ops/mysql/sql/xxl_job.sql 中。
 */
public class XxlJobHandlerAlignmentTest {

    private static final Pattern HANDLER_PATTERN = Pattern.compile("'FIRST','([^']+)'");

    @Test
    public void sqlSeedsCoverAllXxlJobHandlers() throws Exception {
        Set<String> codeHandlers = collectCodeHandlers();
        Set<String> sqlHandlers = collectSqlHandlers();

        for (String handler : codeHandlers) {
            assertTrue("Missing xxl_job_info seed for handler: " + handler, sqlHandlers.contains(handler));
        }
    }

    @Test
    public void sqlAppNameMatchesMessageJobConfig() throws Exception {
        String yml = StreamUtils.copyToString(
                new ClassPathResource("application.yml").getInputStream(), StandardCharsets.UTF_8);
        Matcher appMatcher = Pattern.compile("appname:\\s*(\\S+)").matcher(yml);
        assertTrue(appMatcher.find());
        String ymlAppName = appMatcher.group(1);

        String sql = readXxlJobSql();
        assertTrue("xxl_job.sql must seed app_name=" + ymlAppName,
                sql.contains("'" + ymlAppName + "'"));
    }

    private Set<String> collectCodeHandlers() throws Exception {
        Set<String> handlers = new HashSet<>();
        Path triggerRoot = Paths.get("..", "big-market-trigger", "src", "main", "java");
        Path messageJobRoot = Paths.get("src", "main", "java");
        scanHandlers(triggerRoot, handlers);
        scanHandlers(messageJobRoot, handlers);
        assertFalse("Expected at least one @XxlJob handler", handlers.isEmpty());
        return handlers;
    }

    private void scanHandlers(Path root, Set<String> handlers) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    String className = toClassName(root, p);
                    Class<?> clazz = Class.forName(className);
                    for (Method method : clazz.getDeclaredMethods()) {
                        XxlJob xxlJob = method.getAnnotation(XxlJob.class);
                        if (xxlJob != null) {
                            handlers.add(xxlJob.value());
                        }
                    }
                } catch (ClassNotFoundException ignored) {
                    // IDE 局部编译时跳过无法加载的源码。
                }
            });
        }
    }

    private String toClassName(Path root, Path file) {
        String relative = root.relativize(file).toString().replace('/', '.').replace('\\', '.');
        return relative.substring(0, relative.length() - ".java".length());
    }

    private Set<String> collectSqlHandlers() throws Exception {
        String sql = readXxlJobSql();
        Set<String> handlers = new HashSet<>();
        Matcher matcher = HANDLER_PATTERN.matcher(sql);
        while (matcher.find()) {
            handlers.add(matcher.group(1));
        }
        return handlers;
    }

    private String readXxlJobSql() throws Exception {
        Path sqlPath = Paths.get("..", "docs", "dev-ops", "mysql", "sql", "xxl_job.sql");
        return new String(Files.readAllBytes(sqlPath), StandardCharsets.UTF_8);
    }
}
