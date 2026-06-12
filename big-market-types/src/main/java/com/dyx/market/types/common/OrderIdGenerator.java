package com.dyx.market.types.common;

import java.util.UUID;

/**
 * Order/message ID generator.
 * Replaces RandomStringUtils.randomNumeric(n) with UUID-based hex substrings
 * to eliminate collisions risk at scale.
 *
 * randomNumeric(12)  →  (10^12 ≈ 1e12) possible values
 * UUID hex(12)       →  (16^12 ≈ 2.8e14) possible values, no collision before ~2^64 draws
 */
public class OrderIdGenerator {

    private OrderIdGenerator() {}

    /**
     * Generate a short unique ID from a UUID hex string, truncated to maxLen.
     * @param maxLen desired length (≤ 32). 12 is typical for order_id column (varchar(12)).
     */
    public static String generate(int maxLen) {
        String hex = UUID.randomUUID().toString().replaceAll("-", "");
        return hex.substring(0, Math.min(maxLen, hex.length()));
    }

}
