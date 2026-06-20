package com.dyx.market.trigger.http;

import com.dyx.market.trigger.api.IDCCService;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import com.dyx.market.trigger.api.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.data.Stat;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * 动态配置管理（HTTP 鉴权由 {@code OperationalAuthInterceptor} 统一处理）。
 */
@Slf4j
@RestController()
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/dcc/")
public class DCCController implements IDCCService {

    private final CuratorFramework client;

    private static final String BASE_CONFIG_PATH = "/big-market-dcc";
    private static final String BASE_CONFIG_PATH_CONFIG = BASE_CONFIG_PATH + "/config";

    public DCCController(@org.springframework.beans.factory.annotation.Autowired(required = false) CuratorFramework client) {
        this.client = client;
    }

    @Override
    public Response<Boolean> updateConfig(String key, String value) {
        return doUpdateConfig(key, value);
    }

    @PostMapping("update_config")
    public Response<Boolean> updateConfigPost(@RequestParam String key, @RequestParam String value,
                                              @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        return doUpdateConfig(key, value);
    }

    /**
     * @deprecated 请使用 POST {@link #updateConfigPost(String, String, String)}
     */
    @Deprecated
    @SuppressWarnings("java:S1133")
    @GetMapping("update_config")
    @Override
    public Response<Boolean> updateConfig(@RequestParam String key, @RequestParam String value,
                                          @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        return doUpdateConfig(key, value);
    }

    private Response<Boolean> doUpdateConfig(String key, String value) {
        log.info("DCC 动态配置值变更开始 key:{} value:{}", key, value);
        if (null == client) {
            log.warn("DCC 动态配置值变更拒绝，CuratorFramework 未初始化启动「配置未开启」 key:{} value:{}", key, value);
            throw new AppException(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
        try {
            String keyPath = BASE_CONFIG_PATH_CONFIG.concat("/").concat(key);
            if (null == client.checkExists().forPath(keyPath)) {
                client.create().creatingParentsIfNeeded().forPath(keyPath);
                log.info("DCC 节点监听 base node {} not absent create new done!", keyPath);
            }
            Stat stat = client.setData().forPath(keyPath, value.getBytes(StandardCharsets.UTF_8));
            log.info("DCC 动态配置值变更完成 key:{} value:{} time:{}", key, value, stat.getMtime());
            return TriggerApiResponses.ok(true);
        } catch (Exception e) {
            log.error("DCC 动态配置值变更失败 key:{} value:{}", key, value, e);
            throw new AppException(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
    }
}
