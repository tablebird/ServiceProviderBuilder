package com.tablebird.serviceproviderbuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An service provider interface.
 * <pre><code>
 *     {@literal @}ServiceProvider
 *     interface iService{}
 * </code></pre>
 *
 * @author tablebird
 * @date 2019/7/28
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceProvider {

    ServiceProviderPolicy value() default ServiceProviderPolicy.MULTIPLE;
}
