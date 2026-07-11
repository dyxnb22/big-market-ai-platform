package com.dyx.market.market.config;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * GOV-C05: documents embedded vs remote rebate/strategy mutual exclusion matrix.
 *
 * <pre>
 * rebate.remote-create-order | rebate.embedded-provider | result
 * false                      | true                     | OK (default demo)
 * true                       | false                    | OK (dedicated rebate)
 * true                       | true                     | REFUSE
 * false                      | false                    | OK (neither provider)
 *
 * strategy.remote-read | strategy.embedded-provider | result
 * false                | true                       | OK (default demo)
 * true                 | false                      | OK (dedicated strategy)
 * true                 | true                       | REFUSE
 * </pre>
 */
public class FlagMutualExclusionValidatorTest {

    private FlagMutualExclusionValidator validator;

    @Before
    public void setUp() throws Exception {
        validator = new FlagMutualExclusionValidator();
        set(validator, "guardEnabled", true);
        set(validator, "remoteCreditWrite", false);
        set(validator, "remoteQuotaWrite", false);
        set(validator, "remoteQuotaDecrement", false);
        set(validator, "remoteAward", false);
        set(validator, "rebateRemoteCreateOrder", false);
        set(validator, "rebateEmbeddedProvider", true);
        set(validator, "strategyRemoteRead", false);
        set(validator, "strategyEmbeddedProvider", true);
    }

    @Test
    public void default_embedded_rebate_and_strategy_ok() {
        validator.run();
    }

    @Test
    public void dedicated_rebate_without_embedded_ok() throws Exception {
        set(validator, "rebateRemoteCreateOrder", true);
        set(validator, "rebateEmbeddedProvider", false);
        validator.run();
    }

    @Test
    public void dedicated_strategy_without_embedded_ok() throws Exception {
        set(validator, "strategyRemoteRead", true);
        set(validator, "strategyEmbeddedProvider", false);
        validator.run();
    }

    @Test
    public void rebate_remote_and_embedded_refuses_start() throws Exception {
        set(validator, "rebateRemoteCreateOrder", true);
        set(validator, "rebateEmbeddedProvider", true);
        try {
            validator.run();
            fail("expected IllegalStateException for rebate dual-path");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("rebate.service.remote-create-order"));
            assertTrue(ex.getMessage().contains("rebate.embedded-rpc-provider"));
        }
    }

    @Test
    public void strategy_remote_and_embedded_refuses_start() throws Exception {
        set(validator, "strategyRemoteRead", true);
        set(validator, "strategyEmbeddedProvider", true);
        try {
            validator.run();
            fail("expected IllegalStateException for strategy dual-path");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("strategy.service.remote-read"));
            assertTrue(ex.getMessage().contains("strategy.embedded-rpc-provider"));
        }
    }

    @Test
    public void guard_disabled_allows_dual_path() throws Exception {
        set(validator, "guardEnabled", false);
        set(validator, "rebateRemoteCreateOrder", true);
        set(validator, "rebateEmbeddedProvider", true);
        set(validator, "strategyRemoteRead", true);
        set(validator, "strategyEmbeddedProvider", true);
        validator.run();
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = FlagMutualExclusionValidator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
