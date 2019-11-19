package com.tablebird.serviceproviderbuilder;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author tablebird
 * @date 2019/11/11
 */
public final class ServiceBuilderRegistry {

    private static final Map<Class<?>, Set<ServiceBuilder>> sServices = new LinkedHashMap<Class<?>, Set<ServiceBuilder>>();

    public static Set<ServiceBuilder> get(Class<?> key) {
        return sServices.get(key);
    }

    private static void register(Class<?> key, ServiceBuilder<?> value) {
        Set<ServiceBuilder> result = sServices.get(key);
        if (result == null) {
            result = new LinkedHashSet<ServiceBuilder>();
            sServices.put(key, result);
        }
        result.add(value);
    }
}
