package com.tablebird.serviceproviderbuilder.compiler;

import androidx.annotation.NonNull;
import com.google.auto.common.MoreElements;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Objects;

/**
 * @author tablebird
 * @date 2019/7/30
 */
final class BuilderJava {

    private static final ClassName SERVICE_BUILDER = ClassName.get("com.tablebird.serviceproviderbuilder", "ServiceBuilder");
    private ClassName mBuilderClassName;
    private TypeName mServiceName;
    private String mParameter;
    private boolean mIsConstructor;

    private BuilderJava(TypeName serviceName, ClassName builderClassName, String parameter, boolean isConstructor) {
        mServiceName = serviceName;
        mBuilderClassName = builderClassName;
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
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        builder.addSuperinterface(ParameterizedTypeName.get(SERVICE_BUILDER, mServiceName));

        builder.addMethod(createConstructorMethod());

        builder.addMethod(createBuilderLoadMethod());

        return builder.build();
    }

    private MethodSpec createConstructorMethod() {
        MethodSpec.Builder builder = MethodSpec.constructorBuilder();
        builder.addModifiers(Modifier.PUBLIC);
        return builder.build();
    }

    private MethodSpec createBuilderLoadMethod() {
        MethodSpec.Builder result = MethodSpec.methodBuilder("load")
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
                ", mParameter='" + mParameter + '\'' +
                ", mIsConstructor=" + mIsConstructor +
                '}';
    }

    static Builder newBuilder(TypeElement enclosingElement) {
        TypeMirror typeMirror = enclosingElement.asType();

        TypeName targetType = TypeName.get(typeMirror);
        if (targetType instanceof ParameterizedTypeName) {
            targetType = ((ParameterizedTypeName) targetType).rawType;
        }

        String packageName = MoreElements.getPackage(enclosingElement).getQualifiedName().toString();
        String className = enclosingElement.getQualifiedName().toString().substring(
                packageName.length() + 1).replace('.', '$');
        ClassName builderClassName = ClassName.get(packageName, className + "_Builder");
        return new Builder(targetType, builderClassName);
    }

    static class Builder {
        private TypeName mServiceName;
        private ClassName mBuilderClassName;
        private boolean mIsConstructor;
        private String mMethod;

        private Builder(TypeName serviceName, ClassName builderClassName) {
            mServiceName = serviceName;
            mBuilderClassName = builderClassName;
        }

        void setConstructor(boolean constructor) {
            mIsConstructor = constructor;
        }

        void setMethod(String method) {
            mMethod = method;
        }

        BuilderJava build() {
            return new BuilderJava(mServiceName, mBuilderClassName, mMethod, mIsConstructor);
        }
    }
}
