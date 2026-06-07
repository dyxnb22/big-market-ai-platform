package com.dyx.market.starter.dcc.support;

import com.dyx.market.types.annotations.DCCValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Replaces fields annotated with {@link DCCValue} from Zookeeper and updates
 * them when the corresponding config node changes.
 */
@Slf4j
public class DccValueBeanPostProcessor implements BeanPostProcessor {

    private static final String BASE_CONFIG_PATH = "/big-market-dcc";
    private static final String BASE_CONFIG_PATH_CONFIG = BASE_CONFIG_PATH + "/config";

    private final CuratorFramework client;
    private final Map<String, Object> dccObjectGroup = new HashMap<>();

    public DccValueBeanPostProcessor(CuratorFramework client) throws Exception {
        this.client = client;
        ensureConfigRootExists();
        listenForConfigChanges();
    }

    private void ensureConfigRootExists() throws Exception {
        if (null == client.checkExists().forPath(BASE_CONFIG_PATH_CONFIG)) {
            client.create().creatingParentsIfNeeded().forPath(BASE_CONFIG_PATH_CONFIG);
            log.info("DCC config root created: {}", BASE_CONFIG_PATH_CONFIG);
        }
    }

    private void listenForConfigChanges() {
        CuratorCache curatorCache = CuratorCache.build(client, BASE_CONFIG_PATH_CONFIG);
        curatorCache.start();
        curatorCache.listenable().addListener((type, oldData, data) -> {
            if (data == null || !"NODE_CHANGED".equals(type.name())) {
                return;
            }
            applyChangedValue(data.getPath(), new String(data.getData()));
        });
    }

    private void applyChangedValue(String dccValuePath, String value) {
        Object bean = dccObjectGroup.get(dccValuePath);
        if (bean == null) {
            return;
        }
        try {
            Class<?> beanClass = AopUtils.isAopProxy(bean) ? AopUtils.getTargetClass(bean) : bean.getClass();
            Field field = beanClass.getDeclaredField(dccValuePath.substring(dccValuePath.lastIndexOf("/") + 1));
            field.setAccessible(true);
            field.set(bean, value);
            field.setAccessible(false);
            log.info("DCC config changed: {} -> {}", dccValuePath, value);
        } catch (Exception e) {
            throw new RuntimeException("Apply DCC value failed: " + dccValuePath, e);
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetBeanClass = bean.getClass();
        Object targetBeanObject = bean;
        if (AopUtils.isAopProxy(bean)) {
            targetBeanClass = AopUtils.getTargetClass(bean);
            targetBeanObject = AopProxyUtils.getSingletonTarget(bean);
        }

        for (Field field : targetBeanClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(DCCValue.class)) {
                bindDccField(targetBeanObject, field);
            }
        }
        return bean;
    }

    private void bindDccField(Object targetBeanObject, Field field) {
        DCCValue dccValue = field.getAnnotation(DCCValue.class);
        String value = dccValue.value();
        if (StringUtils.isBlank(value)) {
            throw new RuntimeException(field.getName() + " @DCCValue must look like key:defaultValue");
        }

        String[] splits = value.split(":");
        String key = splits[0];
        String defaultValue = splits.length == 2 ? splits[1] : null;
        String keyPath = BASE_CONFIG_PATH_CONFIG + "/" + key;

        try {
            if (null == client.checkExists().forPath(keyPath)) {
                client.create().creatingParentsIfNeeded().forPath(keyPath);
                setFieldValue(targetBeanObject, field, defaultValue);
                log.info("DCC config node created: {}", keyPath);
            } else {
                String configValue = new String(client.getData().forPath(keyPath));
                if (StringUtils.isNotBlank(configValue)) {
                    setFieldValue(targetBeanObject, field, configValue);
                    log.info("DCC config loaded: {} -> {}", keyPath, configValue);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Bind DCC value failed: " + keyPath, e);
        }
        dccObjectGroup.put(keyPath, targetBeanObject);
    }

    private void setFieldValue(Object targetBeanObject, Field field, String value) throws IllegalAccessException {
        if (StringUtils.isBlank(value)) {
            return;
        }
        field.setAccessible(true);
        field.set(targetBeanObject, value);
        field.setAccessible(false);
    }
}
