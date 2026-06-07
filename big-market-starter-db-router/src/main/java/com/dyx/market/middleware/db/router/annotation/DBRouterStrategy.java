package com.dyx.market.middleware.db.router.annotation;

import java.lang.annotation.*;

/**
 * Declares whether a DAO mapper uses physical table sharding.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DBRouterStrategy {

    boolean splitTable() default false;

}
