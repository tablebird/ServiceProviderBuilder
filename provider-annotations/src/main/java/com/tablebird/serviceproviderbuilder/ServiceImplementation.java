package com.tablebird.serviceproviderbuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Builder a service implementation to service provider interface.
 * <pre><code>
 *
 *     {@literal @}ServiceProvider
 *     interface iService{
 *
 *     }
 *
 *     {@literal @}ServiceImplementation
 *     class Service implement iService{
 *     }
 *
 * </code></pre>
 *
 * @author tablebird
 * @date 2019/7/30
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ServiceImplementation {
}
