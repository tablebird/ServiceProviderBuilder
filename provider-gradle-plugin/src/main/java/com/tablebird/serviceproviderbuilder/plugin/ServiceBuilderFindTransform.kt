package com.tablebird.serviceproviderbuilder.plugin


import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import org.gradle.api.Project
import java.io.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 *
 * @author tablebird
 * @date 2019/11/8
 */
open class ServiceBuilderFindTransform constructor(private val mProject: Project) : Transform() {

    private val mServiceBuilderAction: ServiceBuilderAction = ServiceBuilderAction(mProject)
    private var mRegistryQualifiedContent: QualifiedContent? = null
    private var mRegistryOutFile: File? = null

    override fun getName(): String {
        return "ServiceBuilder"
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    override fun isIncremental(): Boolean {
        return true
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    override fun transform(transformInvocation: TransformInvocation) {
        mProject.logger.info("==================================> Service builder start working <=======================================")
        mServiceBuilderAction.setTempDir(transformInvocation.context.temporaryDir)
        var destDir: File? = null
        val classPaths = ArrayList<String>()
        transformInvocation.inputs.forEach { input ->
            input.jarInputs.forEach { jarInput ->
                var jarName = jarInput.name
                if (jarName.endsWith(".jar")) {
                    jarName = jarName.substring(0, jarName.length - 4)
                }
                val dest = transformInvocation.outputProvider
                    .getContentLocation(jarName, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                classPaths.add(dest.absolutePath)
                if(mServiceBuilderAction.loadJar(JarFile(jarInput.file))) {
                    mRegistryQualifiedContent = jarInput
                    mRegistryOutFile = dest
                } else {
                    FileUtils.copyFile(jarInput.file, dest)
                    mProject.logger.info("scan file:\t ${jarInput.file} status:${jarInput.status}")
                }
            }
            input.directoryInputs.forEach { dirInput ->
                val changedFiles: Map<File, Status>? = dirInput.changedFiles
                destDir = transformInvocation.outputProvider.getContentLocation(
                    dirInput.name,
                    dirInput.contentTypes,
                    dirInput.scopes,
                    Format.DIRECTORY
                )
                classPaths.add(destDir!!.absolutePath)
                if (changedFiles?.isNotEmpty() == true) {
                    mServiceBuilderAction.loadChangedFiles(changedFiles)
                } else {
                    mServiceBuilderAction.loadDirectory(dirInput.file)
                }
                FileUtils.copyDirectory(dirInput.file, destDir)
            }
        }
        mRegistryQualifiedContent?.let { input ->

            mProject.logger.info("injectRegistryByte:\t ${input.name}")
            if (input is JarInput) {
                registryServiceBuilderInJar(input)
            }
        }

        mProject.logger.info("==================================> Service builder work finish <=======================================")
    }

    private fun registryServiceBuilderInJar(input: JarInput) {
        val jarFile = JarFile(input.file)
        val entries = jarFile.entries()
        val fos = FileOutputStream(mRegistryOutFile!!, true)
        var jarOutputStream: JarOutputStream? = null
        try {
            jarOutputStream = JarOutputStream(fos)
            val bytes = mServiceBuilderAction.injectRegistryByte()
            while (entries.hasMoreElements()) {
                val nextElement = entries.nextElement()
                if (nextElement.name == mServiceBuilderAction.mRegistryFilePath && bytes != null) {
                    jarOutputStream.putNextEntry(JarEntry(mServiceBuilderAction.mRegistryFilePath))
                    jarOutputStream.write(bytes)
                } else {
                    jarOutputStream.putNextEntry(JarEntry(nextElement))
                    jarOutputStream.write(streamToByte(jarFile.getInputStream(nextElement)))
                }
            }
        } catch (e: Exception) {
            mProject.logger.error("registry service builder in jar fail: ${e.message}")
        } finally {
            jarOutputStream?.let {output ->
                try {
                    output.close()
                } catch (e: IOException) {

                }
            }
        }
    }

    private fun streamToByte(inputStream: InputStream): ByteArray {
        val outSteam = ByteArrayOutputStream()
        try {
            val buffer = ByteArray(1024)
            var len = 0
            while ({ len = inputStream.read(buffer); len }() != -1) {
                outSteam.write(buffer, 0, len)
            }
            outSteam.close()
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return outSteam.toByteArray()
    }
}