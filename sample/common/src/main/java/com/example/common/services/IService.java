package com.example.common.services;


import com.tablebird.serviceproviderbuilder.ServiceProvider;
import com.tablebird.serviceproviderbuilder.ServiceProviderPolicy;

/**
 * @author tablebird
 * @date 2019/8/7
 */
@ServiceProvider(ServiceProviderPolicy.SINGLE)
public interface IService {
    String getName();
}
