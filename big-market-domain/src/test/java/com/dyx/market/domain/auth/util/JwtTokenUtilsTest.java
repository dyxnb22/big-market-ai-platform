package com.dyx.market.domain.auth.util;

import org.junit.Assert;
import org.junit.Test;

public class JwtTokenUtilsTest {

    @Test
    public void should_strip_bearer_prefix() {
        Assert.assertEquals("jwt-token", JwtTokenUtils.extractToken("Bearer jwt-token"));
        Assert.assertEquals("jwt-token", JwtTokenUtils.extractToken("bearer jwt-token"));
        Assert.assertEquals("jwt-token", JwtTokenUtils.extractToken("jwt-token"));
    }

    @Test
    public void should_trim_surrounding_whitespace() {
        Assert.assertEquals("jwt-token", JwtTokenUtils.extractToken("  Bearer   jwt-token  "));
    }
}
