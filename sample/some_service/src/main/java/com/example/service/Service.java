package com.example.service;

import com.example.common.services.IService;
import com.tablebird.serviceproviderbuilder.BuildService;
import com.tablebird.serviceproviderbuilder.ServiceImplementation;

/**
 * @author tablebird
 * @date 2019/8/7
 */
@ServiceImplementation
class Service implements IService {
    private String name = "Build service";
    private static final Service INSTANCE = new Service();

    @BuildService
    public static Service getInstance() {
        return INSTANCE;
    }

    @Override
    public String getName() {
        return name;
    }
}
