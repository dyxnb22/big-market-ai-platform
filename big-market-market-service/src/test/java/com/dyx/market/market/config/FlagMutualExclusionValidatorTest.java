package com.dyx.market.market.config;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

/** GOV-C05: keeps the account quota routing warning executable. */
public class FlagMutualExclusionValidatorTest {

    private FlagMutualExclusionValidator validator;

    @Before
    public void setUp() throws Exception {
        validator = new FlagMutualExclusionValidator();
        set(validator, "guardEnabled", true);
        set(validator, "remoteQuotaWrite", false);
        set(validator, "remoteQuotaDecrement", false);
    }

    @Test
    public void default_account_paths_ok() {
        validator.run();
    }

    @Test
    public void guard_disabled_returns_without_checking() throws Exception {
        set(validator, "guardEnabled", false);
        validator.run();
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = FlagMutualExclusionValidator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
