package com.dyx.market.trigger.http;

import com.dyx.market.trigger.api.IDCCService;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.trigger.api.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 动态配置管理
 * @create 2024-07-13 08:57
 */
@Slf4j
@RestController()
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/dcc/")
public class DCCController implements IDCCService {

    @Autowired(required = false)
    private CuratorFramework client;

    @Value("${dcc.admin.token:${app.admin.token:admin-dev-token}}")
    private String adminToken;

    private static final String BASE_CONFIG_PATH = "/big-market-dcc";
    private static final String BASE_CONFIG_PATH_CONFIG = BASE_CONFIG_PATH + "/config";

    @Override
    public Response<Boolean> updateConfig(String key, String value) {
        return updateConfig(key, value, null);
    }

    /**
     * 更新配置
     * <p>
     * curl --request GET --url 'http://localhost:8091/api/v1/raffle/dcc/update_config?key=degradeSwitch&value=open'
     * curl --request GET --url 'http://localhost:8091/api/v1/raffle/dcc/update_config?key=rateLimiterSwitch&value=open'
     */
    @RequestMapping(value = "update_config", method = RequestMethod.GET)
    @Override
    public Response<Boolean> updateConfig(@RequestParam String key, @RequestParam String value,
                                          @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        try {
            if (!adminToken.equals(token)) {
                log.warn("DCC 动态配置值变更拒绝，非法token key:{} value:{}", key, value);
                return Response.<Boolean>builder()
                        .code(ResponseCode.APP_TOKEN_ERROR.getCode())
                        .info(ResponseCode.APP_TOKEN_ERROR.getInfo())
                        .build();
            }
            log.info("DCC 动态配置值变更开始 key:{} value:{}", key, value);
            if (null == client){
                log.warn("DCC 动态配置值变更拒绝，CuratorFramework 未初始化启动「配置未开启」 key:{} value:{}", key, value);
                return Response.<Boolean>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info(ResponseCode.UN_ERROR.getInfo())
                        .build();
            }
            String keyPath = BASE_CONFIG_PATH_CONFIG.concat("/").concat(key);
            if (null == client.checkExists().forPath(keyPath)) {
                client.create().creatingParentsIfNeeded().forPath(keyPath);
                log.info("DCC 节点监听 base node {} not absent create new done!", keyPath);
            }
            Stat stat = client.setData().forPath(keyPath, value.getBytes(StandardCharsets.UTF_8));
            log.info("DCC 动态配置值变更完成 key:{} value:{} time:{}", key, value, stat.getCtime());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("DCC 动态配置值变更失败 key:{} value:{}", key, value, e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

}
