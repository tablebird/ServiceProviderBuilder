package com.tablebird.serviceproviderbuilder.compiler;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.squareup.javapoet.JavaFile;
import com.tablebird.serviceproviderbuilder.BuildService;
import com.tablebird.serviceproviderbuilder.ServiceImplementation;
import com.tablebird.serviceproviderbuilder.ServiceProvider;
import com.tablebird.serviceproviderbuilder.ServiceProviderPolicy;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * @author tablebird
 * @date 2019/7/29
 */
@AutoService(Processor.class)
public class ServiceProviderProcessor extends AbstractProcessor {

    private static boolean isDebug = true;

    private Messager mMessage;
    private Filer mFiler;

    private Multimap<String, String> mProviderInterfaceMap = HashMultimap.create();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mMessage = processingEnv.getMessager();
        mFiler = processingEnv.getFiler();
        isDebug = processingEnv.getOptions().containsKey("debug");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            generateConfigFile();
        } else {
            processAnnotations(roundEnv);
        }
        return false;
    }

    private void generateConfigFile() {
        if (mProviderInterfaceMap.isEmpty()) {
            return;
        }
        debug(" providerInterfaceMap : %s ", mProviderInterfaceMap.toString());
        for (String providerInterface : mProviderInterfaceMap.keySet()) {
            debug("Working on service : %s", providerInterface);
            String resourceFile = ServicesFiles.getPath(providerInterface);
            SortedSet<String> allServices = Sets.newTreeSet();
            readOldServices(resourceFile, allServices);

            Set<String> newServices = new HashSet<String>(mProviderInterfaceMap.get(providerInterface));
            if (allServices.containsAll(newServices)) {
                debug("No new service entries being added.");
                continue;
            }
            try {
                allServices.addAll(newServices);
                debug("New service builder file contents: %s", allServices.toString());
                FileObject existingFile = mFiler.createResource(StandardLocation.CLASS_OUTPUT, "", resourceFile);
                OutputStream outputStream = existingFile.openOutputStream();
                ServicesFiles.writeServiceFile(allServices, outputStream);
                outputStream.close();
                debug("Wrote to: %s", existingFile.toUri());
            } catch (IOException e) {
                error(null, "Unable to create %s , %s", resourceFile, e);
            }
        }
    }

    private void readOldServices(String resourceFile, SortedSet<String> allServices) {
        try {
            // would like to be able to print the full path
            // before we attempt to get the resource in case the behavior
            // of filer.getResource does change to match the spec, but there's
            // no good way to resolve CLASS_OUTPUT without first getting a resource.
            FileObject existingFile = mFiler.getResource(StandardLocation.CLASS_OUTPUT, "",
                    resourceFile);
            debug("Looking for existing resource file at " + existingFile.toUri());
            Set<String> oldServices = ServicesFiles.readServiceFile(existingFile.openInputStream());
            debug("Existing service entries: " + oldServices);
            allServices.addAll(oldServices);
        } catch (IOException e) {
            // According to the javadoc, Filer.getResource throws an exception
            // if the file doesn't already exist.  In practice this doesn't
            // appear to be the case.  Filer.getResource will happily return a
            // FileObject that refers to a non-existent file but will throw
            // IOException if you try to open an input stream for it.
            debug("Resource file did not already exist. %s", e.getMessage());
        }
    }

    private void processAnnotations(RoundEnvironment roundEnv) {
        Map<TypeElement, BuilderJava> parseService = findAndParseService(roundEnv);
        if (!checkSingleServiceProvider(parseService)) {
            return;
        }

        for (Map.Entry<TypeElement, BuilderJava> elementBuilderEntry : parseService.entrySet()) {
            TypeElement typeElement = elementBuilderEntry.getKey();
            BuilderJava builder = elementBuilderEntry.getValue();
            Set<TypeElement> serviceInterfaces = getProviderInterfaces(typeElement);
            for (TypeElement serviceInterface : serviceInterfaces) {
                String serviceName = getClassName(serviceInterface);
                mProviderInterfaceMap.put(serviceName, builder.getBuilderClassName().reflectionName());
            }
            JavaFile javaFile = builder.brewJava();
            try {
                javaFile.writeTo(mFiler);
            } catch (IOException e) {
                error(typeElement, "Unable to write binding for type %s: %s", typeElement, e.getMessage());
            }
        }
    }

    private boolean checkSingleServiceProvider(Map<TypeElement, BuilderJava> parseService) {
        Multimap<TypeElement, TypeElement> elementMultimap = HashMultimap.create();
        for (TypeElement typeElement : parseService.keySet()) {
            Set<TypeElement> serviceInterfaces = getProviderInterfaces(typeElement);
            for (TypeElement serviceInterface : serviceInterfaces) {
                elementMultimap.put(serviceInterface, typeElement);
            }
        }
        for (TypeElement serviceInterface : elementMultimap.keySet()) {
            Set<TypeElement> serviceAchieves = new HashSet<>(elementMultimap.get(serviceInterface));
            String serviceName = getClassName(serviceInterface);
            if (getProviderPolicy(serviceInterface) == ServiceProviderPolicy.SINGLE) {
                if (serviceAchieves.size() > 1) {
                    for (TypeElement serviceAchieve : serviceAchieves) {
                        error(serviceAchieve, "multiple achieve, %s policy is single", serviceName);
                    }
                    return false;
                }
            }
        }
        return true;
    }

    private Map<TypeElement, BuilderJava> findAndParseService(RoundEnvironment roundEnv) {
        Map<TypeElement, BuilderJava> typeElementServiceMap = new HashMap<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(ServiceImplementation.class)) {
            TypeElement typeElement = (TypeElement) element;
            if (typeElement.getModifiers().contains(Modifier.PRIVATE)) {
                error(typeElement, "%s can't be private ", ServiceImplementation.class.getSimpleName());
                throw new RuntimeException("ServiceImplementation can't be private");
            }
            if (!checkInterfaces(typeElement)) {
                warning(typeElement, "%s not implement annotated %s interfaces", ServiceImplementation.class.getSimpleName(), ServiceProvider.class.getSimpleName());
                continue;
            }
            if (!parseBuilderService(typeElementServiceMap, typeElement)) {
                throw new RuntimeException("find service fail");
            }
        }
        return typeElementServiceMap;
    }

    private boolean parseBuilderService(Map<TypeElement, BuilderJava> typeElementServiceMap, TypeElement typeElement) {
        List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
        if (enclosedElements == null) {
            return false;
        }
        Element builderElement = null;
        boolean canBuilderConstructor = false;
        for (Element element : enclosedElements) {
            if (element instanceof ExecutableElement) {
                ExecutableElement executableElement = (ExecutableElement) element;
                if (element.getKind() == ElementKind.CONSTRUCTOR) {
                    if (!element.getModifiers().contains(Modifier.PRIVATE)
                            && (executableElement.getParameters() == null || executableElement.getParameters().size() == 0)) {
                        canBuilderConstructor = true;
                    }
                    continue;
                }
                BuildService buildService = element.getAnnotation(BuildService.class);
                if (buildService == null) {
                    continue;
                }
                if (!checkBuilderService(typeElement, executableElement)) {
                    return false;
                }
                if (builderElement != null) {
                    error(executableElement, "%s annotation repeat", BuildService.class.getSimpleName());
                    return false;
                }
                builderElement = executableElement;
            }
        }
        if (builderElement == null && !canBuilderConstructor) {
            error(typeElement, "%s can not builder", ServiceImplementation.class.getSimpleName());
            return false;
        }
        BuilderJava.Builder builder = BuilderJava.newBuilder(typeElement);
        if (builderElement != null) {
            builder.setConstructor(false);
            builder.setMethod(builderElement.getSimpleName().toString());
        } else {
            builder.setConstructor(true);
        }
        typeElementServiceMap.put(typeElement, builder.build());
        return true;
    }

    private boolean checkBuilderService(TypeElement typeElement, ExecutableElement executableElement) {
        if (executableElement.getParameters() != null && executableElement.getParameters().size() > 0) {
            error(executableElement, "%s method parameters not empty", BuildService.class.getSimpleName());
            return false;
        }
        if (executableElement.getReturnType() != typeElement.asType()) {
            error(executableElement, "%s method return type not %s", BuildService.class.getSimpleName(), typeElement.getSimpleName());
            return false;
        }
        Set<Modifier> modifierSet = executableElement.getModifiers();
        if (modifierSet.contains(Modifier.PRIVATE)) {
            error(executableElement, "%s method is private", BuildService.class.getSimpleName());
            return false;
        }
        if (!modifierSet.contains(Modifier.STATIC)) {
            error(executableElement, "%s method not static", BuildService.class.getSimpleName());
            return false;
        }
        return true;
    }

    private boolean checkInterfaces(TypeElement typeElement) {
        List<? extends TypeMirror> typeElementInterfaces = typeElement.getInterfaces();
        if (typeElementInterfaces == null) {
            return false;
        }
        boolean hasServiceProvider = false;
        for (TypeMirror typeMirror : typeElementInterfaces) {
            if (!hasServiceProvider && typeMirror.getKind() == TypeKind.DECLARED) {
                if (typeMirror instanceof DeclaredType) {
                    Element asElement = ((DeclaredType) typeMirror).asElement();
                    ServiceProvider serviceProvider = asElement.getAnnotation(ServiceProvider.class);
                    hasServiceProvider = serviceProvider != null;
                }
            }
        }
        return hasServiceProvider;
    }

    private Set<TypeElement> getProviderInterfaces(TypeElement typeElement) {
        List<? extends TypeMirror> typeElementInterfaces = typeElement.getInterfaces();
        Set<TypeElement> set = new HashSet<>();
        if (typeElementInterfaces == null) {
            return set;
        }
        for (TypeMirror typeMirror : typeElementInterfaces) {
            if (typeMirror.getKind() == TypeKind.DECLARED) {
                Element asElement = ((DeclaredType) typeMirror).asElement();
                ServiceProvider serviceProvider = asElement.getAnnotation(ServiceProvider.class);
                if (serviceProvider != null) {
                    set.add((TypeElement) asElement);
                }
            }
        }
        return set;
    }

    private ServiceProviderPolicy getProviderPolicy(TypeElement typeElement) {
        ServiceProvider serviceProvider = typeElement.getAnnotation(ServiceProvider.class);
        return serviceProvider != null ? serviceProvider.value() : null;
    }

    private String getClassName(TypeElement element) {
        String packageName = MoreElements.getPackage(element).getQualifiedName().toString();
        String className = element.getQualifiedName().toString().substring(
                packageName.length() + 1).replace('.', '$');
        return packageName + "." + className;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        HashSet<String> stringHashSet = new HashSet<>();
        Set<Class<? extends Annotation>> classes = getSupportedAnnotations();
        for (Class<? extends Annotation> aClass : classes) {
            stringHashSet.add(aClass.getCanonicalName());
        }
        return stringHashSet;
    }

    private Set<Class<? extends Annotation>> getSupportedAnnotations() {
        HashSet<Class<? extends Annotation>> hashSet = new HashSet<>();
        hashSet.add(ServiceProvider.class);
        hashSet.add(ServiceImplementation.class);
        hashSet.add(BuildService.class);
        return hashSet;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private void error(Element element, String message, Object... args) {
        printMessage(Diagnostic.Kind.ERROR, element, message, args);
    }

    private void warning(Element element, String mssage, Object... args) {
        printMessage(Diagnostic.Kind.WARNING, element, mssage, args);
    }

    private void note(Element element, String message, Object... args) {
        printMessage(Diagnostic.Kind.NOTE, element, message, args);
    }

    private void debug(String message, Object... args) {
        debug(null, message, args);
    }

    private void debug(Element element, String message, Object... args) {
        if (isDebug) {
            printMessage(Diagnostic.Kind.NOTE, element, message, args);
        }
    }

    private void printMessage(Diagnostic.Kind kind, Element element, String message, Object[] args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        if (element == null) {
            mMessage.printMessage(kind, message);
        } else {
            mMessage.printMessage(kind, message, element);
        }
    }
}
