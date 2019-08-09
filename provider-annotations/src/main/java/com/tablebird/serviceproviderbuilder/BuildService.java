package com.tablebird.serviceproviderbuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Build service method to service implementation.
 * <pre><code>
 *     {@literal @}ServiceImplementation
 *     class Service implements iService {
 *         {@literal @}BuildService
 *         public static Service getInstance(){
 *             return new Service()
 *         }
 *     }
 * </code></pre>
 *
 * @author tablebird
 * @date 2019/7/30
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface BuildService {
}
