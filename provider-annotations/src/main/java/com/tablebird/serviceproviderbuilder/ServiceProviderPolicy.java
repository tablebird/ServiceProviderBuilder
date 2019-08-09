package com.tablebird.serviceproviderbuilder;

/**
 * Service provider interface policy.
 * <pre><code>
 *     {@literal @}ServiceProvider(ServiceProviderPolicy.SINGLE) Service provider interface
 * </code></pre>
 *
 * @author tablebird
 * @date 2019/7/30
 */
public enum ServiceProviderPolicy {

    /**
     * Service provider implementation can not be multiple.
     * <pre><b>    Service implementation in a module not single build will fail.</b></pre>
     * <pre><b>    Service implementation in running not single build will throw exception.<b></pre>
     */
    SINGLE,

    /**
     * Service provider implementation can be multiple
     */
    MULTIPLE
}
