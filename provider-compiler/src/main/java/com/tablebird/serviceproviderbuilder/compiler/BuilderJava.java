package com.tablebird.serviceproviderbuilder.compiler;

import androidx.annotation.NonNull;

import com.google.auto.common.MoreElements;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.tablebird.serviceproviderbuilder.Build;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * @author tablebird
 * @date 2019/7/30
 */
final class BuilderJava {

    private static final ClassName SERVICE_BUILDER = ClassName.get("com.tablebird.serviceproviderbuilder", "ServiceBuilder");
    private ClassName mBuilderClassName;
    private TypeName mServiceName;
    private HashSet<ClassName> mServiceProviders;
    private String mParameter;
    private boolean mIsConstructor;

    private BuilderJava(TypeName serviceName, ClassName builderClassName, HashSet<ClassName> serviceProviders, String parameter, boolean isConstructor) {
        mServiceName = serviceName;
        mBuilderClassName = builderClassName;
        mServiceProviders = serviceProviders;
        mParameter = parameter;
        mIsConstructor = isConstructor;
    }

    public ClassName getBuilderClassName() {
        return mBuilderClassName;
    }

    private void setBuilderClassName(ClassName builderClassName) {
        mBuilderClassName = builderClassName;
    }

    JavaFile brewJava() {
        TypeSpec serviceBuilderConfiguration = createType();
        return JavaFile.builder(mBuilderClassName.packageName(), serviceBuilderConfiguration)
                .addFileComment("Generated code from Service provider builder. Do not modify!")
                .build();
    }

    private TypeSpec createType() {
        TypeSpec.Builder builder = TypeSpec.classBuilder(mBuilderClassName.simpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(createBuildAnnotation());

        builder.addSuperinterface(ParameterizedTypeName.get(SERVICE_BUILDER, mServiceName));

        builder.addMethod(createConstructorMethod());

        builder.addMethod(createBuilderLoadMethod());

        return builder.build();
    }

    private AnnotationSpec createBuildAnnotation() {
        AnnotationSpec.Builder builder = AnnotationSpec.builder(ClassName.get(Build.class));
        if (mServiceProviders != null && !mServiceProviders.isEmpty()) {
            Iterator<ClassName> iterator = mServiceProviders.iterator();
            ClassName firstClass = iterator.next();
            CodeBlock.Builder codeBlockBuilder = CodeBlock.builder();
            codeBlockBuilder.add("{$T.class", firstClass);
            while (iterator.hasNext()) {
                ClassName className = iterator.next();
                codeBlockBuilder.add(", $T.class", className);
            }
            codeBlockBuilder.add("}");
            builder.addMember("serviceProviders", codeBlockBuilder.build());
        }
        return builder.build();
    }

    private MethodSpec createConstructorMethod() {
        MethodSpec.Builder builder = MethodSpec.constructorBuilder();
        builder.addModifiers(Modifier.PUBLIC);
        return builder.build();
    }

    private MethodSpec createBuilderLoadMethod() {
        MethodSpec.Builder result = MethodSpec.methodBuilder("build")
                .addAnnotation(Override.class)
                .addAnnotation(NonNull.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(mServiceName);
        if (mIsConstructor) {
            result.addStatement("return new $T()", mServiceName);
        } else {
            result.addStatement("return $T.$N()", mServiceName, mParameter);
        }
        return result.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BuilderJava builderJava = (BuilderJava) o;

        return Objects.equals(mBuilderClassName, builderJava.mBuilderClassName);
    }

    @Override
    public int hashCode() {
        return mBuilderClassName != null ? mBuilderClassName.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "BuilderJava{" +
                "mBuilderClassName=" + mBuilderClassName +
                ", mServiceName=" + mServiceName +
                ", mServiceProviders=" + mServiceProviders +
                ", mParameter='" + mParameter + '\'' +
                ", mIsConstructor=" + mIsConstructor +
                '}';
    }

    static Builder newBuilder(TypeElement enclosingElement, HashSet<Element> serviceProviderElements) {
        TypeMirror typeMirror = enclosingElement.asType();

        TypeName targetType = TypeName.get(typeMirror);
        if (targetType instanceof ParameterizedTypeName) {
            targetType = ((ParameterizedTypeName) targetType).rawType;
        }

        String packageName = MoreElements.getPackage(enclosingElement).getQualifiedName().toString();
        String className = enclosingElement.getQualifiedName().toString().substring(
                packageName.length() + 1).replace('.', '$');
        ClassName builderClassName = ClassName.get(packageName, className + "_Builder");

        HashSet<ClassName> serviceProviders = getElementClassNames(serviceProviderElements);
        return new Builder(targetType, builderClassName, serviceProviders);
    }

    private static HashSet<ClassName> getElementClassNames(HashSet<Element> serviceProviderElements) {
        HashSet<ClassName> serviceProviders = new HashSet<>();
        for (Element serviceProviderElement : serviceProviderElements) {
            if (serviceProviderElement instanceof TypeElement) {
                String serviceProviderPackageName = MoreElements.getPackage(serviceProviderElement).getQualifiedName().toString();
                String serviceProviderClassName = ((TypeElement) serviceProviderElement).getQualifiedName().toString().substring(
                        serviceProviderPackageName.length() + 1).replace('.', '$');
                serviceProviders.add(ClassName.get(serviceProviderPackageName, serviceProviderClassName));
            }
        }
        return serviceProviders;
    }

    static class Builder {
        private TypeName mServiceName;
        private ClassName mBuilderClassName;
        private HashSet<ClassName> mServiceProviders;
        private boolean mIsConstructor;
        private String mMethod;

        private Builder(TypeName serviceName, ClassName builderClassName, HashSet<ClassName> serviceProviders) {
            mServiceName = serviceName;
            mBuilderClassName = builderClassName;
            mServiceProviders = serviceProviders;
        }

        void setConstructor(boolean constructor) {
            mIsConstructor = constructor;
        }

        void setMethod(String method) {
            mMethod = method;
        }

        BuilderJava build() {
            return new BuilderJava(mServiceName, mBuilderClassName, mServiceProviders, mMethod, mIsConstructor);
        }
    }
}
