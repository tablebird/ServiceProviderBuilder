package com.tablebird.serviceproviderbuilder.plugin


import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import org.apache.commons.io.FileUtils
import org.gradle.api.GradleException
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
        val incremental = transformInvocation.isIncremental
        if (!incremental) {
            transformInvocation.outputProvider.deleteAll()
            mServiceBuilderAction.clearCache()
            mRegistryQualifiedContent = null
            mRegistryOutFile = null
        } else {
            deleteOldRegistryFile()
        }
        mServiceBuilderAction.setTempDir(transformInvocation.context.temporaryDir)
        transformInvocation.inputs.forEach { input ->
            input.jarInputs.forEach { jarInput ->
                var jarName = jarInput.name
                if (jarName.endsWith(".jar")) {
                    jarName = jarName.substring(0, jarName.length - 4)
                }
                val dest = transformInvocation.outputProvider
                    .getContentLocation(jarName, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                if (incremental) {
                    when(val status = jarInput.status) {
                        Status.ADDED,
                        Status.CHANGED -> {
                            transformJar(jarInput, dest, status == Status.CHANGED)
                        }
                        Status.REMOVED -> {
                            if (dest.exists()) {
                                FileUtils.forceDelete(dest)
                                mServiceBuilderAction.removeJar(JarFile(dest))
                            }
                        }
                        else -> {
                        }
                    }
                } else {
                    transformJar(jarInput, dest)
                }
            }
            input.directoryInputs.forEach { dirInput ->
                val destDir = transformInvocation.outputProvider.getContentLocation(
                    dirInput.name,
                    dirInput.contentTypes,
                    dirInput.scopes,
                    Format.DIRECTORY
                )
                FileUtils.forceMkdir(destDir)
                if (incremental) {
                    val srcDirPath: String = dirInput.file.absolutePath
                    val destDirPath: String = destDir.absolutePath
                    dirInput.changedFiles?.forEach { inputFile, status ->
                        val replace = inputFile.absolutePath.replace(srcDirPath, destDirPath)
                        val destFile  = File(replace)
                        when (status) {
                            Status.ADDED,
                            Status.CHANGED -> {
                                FileUtils.touch(destFile)
                                transformFile(inputFile, destFile, status == Status.CHANGED)
                            }
                            Status.REMOVED -> {
                                if (destFile.exists()) {
                                    mServiceBuilderAction.removeFile(destFile)
                                    FileUtils.forceDelete(destFile)
                                }
                            }
                            else -> {
                            }
                        }
                    }

                } else {
                    transformDir(dirInput, destDir)
                }
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

    private fun transformDir(
        dirInput: DirectoryInput,
        destDir: File?
    ) {
        mServiceBuilderAction.loadFile(dirInput.file)
        FileUtils.copyDirectory(dirInput.file, destDir)
    }

    private fun transformFile(inputFile: File, destFile: File, change: Boolean = false) {
        mServiceBuilderAction.loadFile(inputFile, change)
        FileUtils.copyFile(inputFile, destFile)
    }

    private fun transformJar(jarInput: JarInput, dest: File?, change: Boolean = false) {
        if (mServiceBuilderAction.loadJar(JarFile(jarInput.file), change)) {
            mRegistryQualifiedContent = jarInput
            mRegistryOutFile = dest
        } else {
            FileUtils.copyFile(jarInput.file, dest)
            mProject.logger.info("scan file:\t ${jarInput.file} status:${jarInput.status}")
        }
    }

    private fun deleteOldRegistryFile() {
        if (mRegistryOutFile?.exists() == true) {
            FileUtils.forceDelete(mRegistryOutFile)
        }
    }

    @Throws(GradleException::class)
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
                    jarOutputStream.putNextEntry(JarEntry(nextElement.name))
                    jarOutputStream.write(streamToByte(jarFile.getInputStream(nextElement)))
                }
            }
        } catch (e: GradleException){
            throw e
        } catch (e: Exception) {
            val message = "registry service builder in jar fail: ${e.message}"
            mProject.logger.error(message)
            throw GradleException(e.message ?: message)
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
        } catch (e: IOException) {
            throw e
        } finally {
            try {
                outSteam.close()
                inputStream.close()
            } catch (e: IOException) {
            }
        }

        return outSteam.toByteArray()
    }
}