package com.tablebird.serviceproviderbuilder.plugin

import com.android.build.api.transform.Status
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.squareup.javapoet.CodeBlock
import com.tablebird.serviceproviderbuilder.*
import javassist.ClassPool
import javassist.CtClass
import javassist.CtConstructor
import javassist.Loader
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.annotation.ArrayMemberValue
import javassist.bytecode.annotation.ClassMemberValue
import javassist.bytecode.annotation.EnumMemberValue
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.*
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashSet


/**
 *
 * @author tablebird
 * @date 2019/11/8
 */
open class ServiceBuilderAction constructor(private val mProject: Project) {

    private val mPool: ClassPool = object : ClassPool(true) {
        override fun getClassLoader(): ClassLoader {
            return Loader()
        }
    }

    companion object {
        const val CACHE_FILE_NAME = "ProviderClasses.txt"
    }

    private val mBuilders = ArrayList<BuilderElement>()

    private var mRecordFile: File? = null

    private var mRegistryClass: CtClass? = null
    var mRegistryFilePath: String? = null
        private set

    fun setTempDir(dir: File) {
        mRecordFile = File(dir, CACHE_FILE_NAME)
        if (!mRecordFile!!.exists()) {
            return
        }
        val builderElement: List<BuilderElement>? = Gson().fromJson(
            FileReader(mRecordFile!!),
            object : TypeToken<List<BuilderElement>>() {}.type
        )

        if (builderElement?.isNotEmpty() == true) {
            mBuilders.addAll(builderElement)
        }
    }

    fun loadJar(jarFile: JarFile) : Boolean{
        var findRegistry = false
        val entries = jarFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.endsWith(".class")) {
                findRegistry = maybeChangeByJar(jarFile, entry) || findRegistry
            }
        }
        return findRegistry
    }

    fun loadDirectory(file: File?): Boolean{
        val stack = Stack<File>()
        stack.push(file)
        var findRegistry = false
        while (!stack.isEmpty()) {
            val pop = stack.pop()
            when {
                pop.isDirectory -> pop.listFiles()?.forEach { childFile ->
                    stack.push(childFile)
                }
                pop.name.endsWith(".class") -> {
                    findRegistry = maybeChangeByFile(pop) || findRegistry
                }
                pop.name.endsWith(".jar") -> {
                    val jarFile = JarFile(pop)
                    findRegistry = loadJar(jarFile) || findRegistry
                }
            }
        }
        return findRegistry
    }

    fun loadChangedFiles(changedFiles: Map<File, Status>): Boolean{
        var findRegistry = false
        changedFiles.keys.forEach { file ->
            mProject.logger.info("scan file:\t $file status:${changedFiles[file]}")
            findRegistry = maybeChangeByFile(file, true) || findRegistry
        }
        return findRegistry
    }

    private fun maybeChangeByFile(file: File, change: Boolean = false) : Boolean {
        val fileInputStream = FileInputStream(file)
        val ctClass = mPool.makeClass(fileInputStream)
        return maybeChangeByClass(ctClass, change)
    }

    private fun maybeChangeByJar(jarFile: JarFile, jarEntry: JarEntry) : Boolean {
        val stream: InputStream? = jarFile.getInputStream(jarEntry)
        return if (stream != null) {
            val ctClass = mPool.makeClass(stream)
            if (ServiceBuilderRegistry::class.java.name == ctClass.name) {
                mRegistryClass = ctClass
                mRegistryFilePath = jarEntry.name
                true
            } else {
                maybeChangeByClass(ctClass, true)
            }
        } else {
            false
        }
    }

    private fun maybeChangeByClass(ctClass: CtClass, change: Boolean = false): Boolean {
        if (ctClass.isAnnotation || ctClass.isEnum) {
            return false
        }
        if (ctClass.hasAnnotation(ServiceProvider::class.java)) {
            anaylizeProviderClass(ctClass)
            return false
        }
        if (ctClass.isInterface) {
            return false
        }
        if(ctClass.hasAnnotation(Build::class.java)
            && ctClass.subtypeOf(mPool.makeInterface(ServiceBuilder::class.java.name))
        ) {
            if (change) {
                removeOldValue(ctClass.name)
            }
            anaylizeBuilderClass(ctClass)
            return false
        }
        if (ServiceBuilderRegistry::class.java.name == ctClass.name) {
            mRegistryClass = ctClass
            return true
        }
        return false
    }

    private fun anaylizeProviderClass(ctClass: CtClass) {
        val classFile = ctClass.classFile
        val attribute =
            classFile.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute
        val annotation = attribute.getAnnotation(ServiceProvider::class.java.name)
        val serviceProviderPolicy = annotation.getMemberValue(ServiceProvider::value.name) as EnumMemberValue
        val element = getElement(ctClass.name)
        element.isSingle = serviceProviderPolicy.value == ServiceProviderPolicy.SINGLE.name
    }

    private fun anaylizeBuilderClass(ctClass: CtClass) {
        val classFile = ctClass.classFile
        val attribute =
            classFile.getAttribute(AnnotationsAttribute.invisibleTag) as AnnotationsAttribute
        val annotation = attribute.getAnnotation(Build::class.java.name)
        val serviceProviders =
            annotation.getMemberValue(Build::serviceProviders.name) as ArrayMemberValue
        serviceProviders.value.forEach { memberValue ->
            val classMemberValue = memberValue as ClassMemberValue
            val value = classMemberValue.value
            val builderElement = getElement(value)
            builderElement.values?.add(ctClass.name)
        }
    }

    private fun getElement(key: String): BuilderElement {
        mBuilders.forEach { builderElement ->
            if (key == builderElement.key) {
                return builderElement
            }
        }
        return addElement(key)
    }

    private fun addElement(key: String, isSingle: Boolean = false): BuilderElement{
        return BuilderElement(key, isSingle).also {
            it.values = LinkedHashSet<String>()
            mBuilders.add(it)
        }
    }

    private fun removeOldValue(name: String?) {
        mBuilders.forEach { builder ->
            builder.values?.remove(name)
        }
    }

    fun injectRegistryByte(): ByteArray? {
        createCache()
        return mRegistryClass?.let {ctClass ->
            if (ctClass.isFrozen) {
                ctClass.defrost()
            }
            var staticConstructor: CtConstructor? = ctClass.classInitializer
            if (staticConstructor == null) {
                staticConstructor = ctClass.makeClassInitializer()
            }

            val block = generateStaticBlock()
            staticConstructor?.insertAfter(block.toString())
            ctClass.detach()
            ctClass.toBytecode()
        }
    }

    private fun createCache() {
        var outputStreamWriter: OutputStreamWriter? = null
        try {
            outputStreamWriter = OutputStreamWriter(FileOutputStream(mRecordFile!!))
            outputStreamWriter.write(Gson().toJson(mBuilders.filter { it.values?.isNotEmpty() == true }))
            outputStreamWriter.flush()
            outputStreamWriter.close()
        } catch (e: IOException) {

        } finally {
            outputStreamWriter?.let {
                try {
                    outputStreamWriter.close()
                } catch (e: IOException) {

                }
            }
        }
    }

    private fun generateStaticBlock(): CodeBlock {
        val builder = CodeBlock.builder()
        mBuilders.forEach { element ->
            element.values?.let { set ->
                if (element.isSingle && set.size > 1) {
                    throw GradleException("Service provider ${element.key} implementation not single, values $set ")

                }
                set.forEach { value ->
                    builder.addStatement("register(\$L.class, new \$L())", element.key, value)
                }
            }
        }
        return builder.build()

    }
}