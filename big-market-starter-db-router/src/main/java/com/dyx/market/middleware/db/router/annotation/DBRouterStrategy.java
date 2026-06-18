package com.dyx.market.middleware.db.router.annotation;

import java.lang.annotation.*;

/**
 * 声明 DAO Mapper 是否启用物理表分片。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DBRouterStrategy {

    /** 是否按路由后缀拆分物理表，默认 false。 */
    boolean splitTable() default false;

}
