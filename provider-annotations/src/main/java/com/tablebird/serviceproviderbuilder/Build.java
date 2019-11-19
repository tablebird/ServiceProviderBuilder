package com.tablebird.serviceproviderbuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author tablebird
 * @date 2019/11/10
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface Build {
    Class<?>[] serviceProviders() default {};
}
