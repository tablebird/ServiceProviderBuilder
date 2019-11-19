package com.tablebird.serviceproviderbuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Service build for android service provider interface. Use this class to simplify build service
 * by define service provider interface
 * <p>
 * an service provider define
 * <pre><code>
 *      {@literal @}ServiceProvider(ServiceProviderPolicy.SINGLE)
 *     interface iService{
 *         String getName();
 *
 *     }
 *
 *     {@literal @}ServiceImplementation
 *     class Service implement iService{
 *         public String getName() {
 *             return "Service";
 *         }
 *     }
 *     </code></pre>
 * build service provider
 * <pre><code>
 *     iService service = ServiceProviderBuilder.buildSingleService(iService);
 *     System.out.println(service.getName);
 *     </code></pre>
 * </p>
 *
 * @author tablebird
 * @date 2019/8/3
 */
public class ServiceProviderBuilder {

    private static final Map<Class<?>, Set<? extends ServiceBuilder>> mServiceBuilderMap = new HashMap<>();

    private ServiceProviderBuilder() {
        throw new AssertionError("No instances.");
    }

    /**
     * Build policy is the implementation of single service provider
     *
     * @param service single service provider class
     * @param <S>     single service provider
     * @return service provider implementation
     * @throws java.lang.IllegalArgumentException service provider policy not single
     * @throws BuilderInstantiationException      service provider policy is single, but service implementation not single
     */
    @Nullable
    public static <S> S buildSingleService(@NonNull Class<S> service) {
        return buildSingleService(service, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Build policy is the implementation of single service provider
     *
     * @param service     single service provider class
     * @param <S>         single service provider
     * @param classLoader The class loader to be used to load providerBuilder-configuration files and provider classes
     * @return service provider implementation
     * @throws java.lang.IllegalArgumentException service provider policy not single
     * @throws BuilderInstantiationException      service provider policy is single, but service implementation not single
     */
    @Nullable
    public static <S> S buildSingleService(@NonNull Class<S> service, ClassLoader classLoader) {
        ServiceProviderPolicy providerPolicy = getServiceProviderPolicy(service);
        if (providerPolicy != ServiceProviderPolicy.SINGLE) {
            throw new IllegalArgumentException(String.format("%s not single policy", service.getSimpleName()));
        }
        Iterator<S> sIterator = getServiceIterator(service, providerPolicy, classLoader);
        return sIterator.hasNext() ? sIterator.next() : null;
    }

    /**
     * Build implementation of service provider
     *
     * @param service service provider class
     * @param <S>     service provider
     * @return iterator of service provider implementation
     * @throws BuilderInstantiationException service provider policy is single, but service implementation not single
     */
    @NonNull
    public static <S> Iterator<S> buildServiceSet(@NonNull Class<S> service) {
        return buildServiceSet(service, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Build implementation of service provider
     *
     * @param service     service provider class
     * @param <S>         service provider
     * @param classLoader The class loader to be used to load providerBuilder-configuration files and provider classes
     * @return iterator of service provider implementation
     * @throws BuilderInstantiationException service provider policy is single, but service implementation not single
     */
    @NonNull
    public static <S> Iterator<S> buildServiceSet(@NonNull Class<S> service, ClassLoader classLoader) {
        ServiceProviderPolicy providerPolicy = getServiceProviderPolicy(service);
        return getServiceIterator(service, providerPolicy, classLoader);
    }

    @NonNull
    private static <S> Iterator<S> getServiceIterator(@NonNull Class<S> service,
                                                      ServiceProviderPolicy providerPolicy,
                                                      ClassLoader classLoader) {
        if (mServiceBuilderMap.containsKey(service)) {
            Set<? extends ServiceBuilder> serviceBuilders = mServiceBuilderMap.get(service);
            if (serviceBuilders == null || serviceBuilders.isEmpty()) {
                mServiceBuilderMap.remove(service);
            } else {
                return getIterator(service, serviceBuilders);
            }
        }
        Set<ServiceBuilder> serviceBuilders = ServiceBuilderRegistry.get(service);

        if (serviceBuilders == null) {
            serviceBuilders = loadServiceBuilders(service, classLoader);
        }

        checkProviderPolicy(service, providerPolicy, serviceBuilders);

        if (!serviceBuilders.isEmpty()) {
            mServiceBuilderMap.put(service, serviceBuilders);
        }
        return getIterator(service, serviceBuilders);
    }

    private static <S> void checkProviderPolicy(@NonNull Class<S> service, ServiceProviderPolicy providerPolicy, Set<ServiceBuilder> serviceBuilders) {
        if (providerPolicy == ServiceProviderPolicy.SINGLE && serviceBuilders.size() > 1) {
            StringBuilder serviceAchieveNames = new StringBuilder("[");
            for (ServiceBuilder serviceBuilder : serviceBuilders) {
                String builderName = serviceBuilder.getClass().getSimpleName();
                serviceAchieveNames.append(builderName.substring(0, builderName.lastIndexOf("_"))).append(", ");
            }
            serviceAchieveNames.replace(serviceAchieveNames.length() - 2, serviceAchieveNames.length(), "]");
            throw new BuilderInstantiationException(String.format("%s policy is single, but service achieve not single : %s ", service.getSimpleName(), serviceAchieveNames.toString()));
        }
    }

    private static <S> Set<ServiceBuilder> loadServiceBuilders(@NonNull Class<S> service, ClassLoader classLoader) {
        Set<ServiceBuilder> serviceBuilders;
        if (classLoader == null) {
            classLoader = service.getClassLoader();
        }

        ServiceBuilderLoader<S> serviceBuilderLoader = ServiceBuilderLoader.load(service, classLoader);
        Iterator<ServiceBuilder<S>> builderServiceIterator = serviceBuilderLoader.iterator();
        serviceBuilders = new HashSet<>();
        while (builderServiceIterator.hasNext()) {
            serviceBuilders.add(builderServiceIterator.next());
        }
        return serviceBuilders;
    }

    @NonNull
    private static <S> ServiceProviderPolicy getServiceProviderPolicy(Class<S> service) {
        if (service == null) {
            throw new NullPointerException("service is null");
        }

        ServiceProvider serviceProvider = service.getAnnotation(ServiceProvider.class);
        if (serviceProvider == null) {
            throw new IllegalArgumentException(String.format("%s not annotation present by %s", service.getSimpleName(), ServiceProvider.class.getSimpleName()));
        }
        return serviceProvider.value();
    }

    @NonNull
    private static <S> Iterator<S> getIterator(final Class<S> service, final Set<? extends ServiceBuilder> serviceBuilderSet) {
        return new Iterator<S>() {
            Iterator<? extends ServiceBuilder> mIterator = serviceBuilderSet != null ? serviceBuilderSet.iterator() : null;

            @Override
            public boolean hasNext() {
                return mIterator != null && mIterator.hasNext();
            }

            @Override
            public S next() {
                return mIterator != null ? service.cast(mIterator.next().build()) : null;
            }
        };
    }
}
