package com.tablebird.serviceproviderbuilder.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 *
 * @author tablebird
 * @date 2019/11/8
 */
class ServiceBuilderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (!project.plugins.hasPlugin(AppPlugin::class.java)) {
            return
        }
        project.extensions.findByType(AppExtension::class.java)
            ?.registerTransform(ServiceBuilderFindTransform(project))
    }
}