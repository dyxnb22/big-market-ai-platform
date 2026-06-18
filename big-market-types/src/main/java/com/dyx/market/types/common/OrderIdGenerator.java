package com.dyx.market.types.common;

import java.util.UUID;

/**
 * 订单/消息 ID 生成器。
 * <p>
 * 使用 UUID 十六进制子串替代 {@code RandomStringUtils.randomNumeric}，
 * 降低大规模下的碰撞风险（12 位 hex 约 2.8×10¹⁴ 种取值）。
 */
public class OrderIdGenerator {

    private OrderIdGenerator() {}

    /**
     * 从 UUID 十六进制串截取指定长度（≤32，订单号列常用 12）。
     */
    public static String generate(int maxLen) {
        String hex = UUID.randomUUID().toString().replaceAll("-", "");
        return hex.substring(0, Math.min(maxLen, hex.length()));
    }

}
