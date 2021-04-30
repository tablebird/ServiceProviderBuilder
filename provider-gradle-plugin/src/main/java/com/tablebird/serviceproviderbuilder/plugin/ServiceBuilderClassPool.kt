package com.tablebird.serviceproviderbuilder.plugin

import javassist.ClassPool
import javassist.CtClass
import javassist.Loader

class ServiceBuilderClassPool(useDefaultPath: Boolean) : ClassPool(useDefaultPath) {
    public override fun removeCached(classname: String?): CtClass? {
        if (classname == null) {
            return null
        }
        return super.removeCached(classname)
    }

    override fun getClassLoader(): ClassLoader {
        return Loader()
    }
}