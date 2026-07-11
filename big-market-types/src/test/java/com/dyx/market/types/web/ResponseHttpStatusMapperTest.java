package com.dyx.market.types.web;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class ResponseHttpStatusMapperTest {

    @Test
    public void mapsCanonicalCodes() {
        Assertions.assertEquals(HttpStatus.OK, ResponseHttpStatusMapper.toHttpStatus("0000"));
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ResponseHttpStatusMapper.toHttpStatus("0002"));
        Assertions.assertEquals(HttpStatus.CONFLICT, ResponseHttpStatusMapper.toHttpStatus("0003"));
        Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, ResponseHttpStatusMapper.toHttpStatus("0005"));
        Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ResponseHttpStatusMapper.toHttpStatus("0007"));
        Assertions.assertEquals(HttpStatus.FORBIDDEN, ResponseHttpStatusMapper.toHttpStatus("0008"));
        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, ResponseHttpStatusMapper.toHttpStatus("0009"));
        Assertions.assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ResponseHttpStatusMapper.toHttpStatus("ERR_BIZ_003"));
    }
}
