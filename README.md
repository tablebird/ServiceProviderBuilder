# Service Provider Builder

[English README](README_en.md)

基于SPI(Service Provider Interface)思想获取服务接口的实现，使用注解生成构建器代码，便于获取服务接口的实现

+ 在接口上使用`@ServiceProvider`声明服务接口
+ 在服务接口的实现上使用`@ServiceImplementation`指定需要构建的服务
+ 在服务的静态方法上使用`@BuildService`指定构建服务的方式
+ 消除了需要手动添加**META-INF.services**指定SPI接口与实现

## 开始使用

### 安装

在模块的`build.gradle`添加：
```groovy
dependencies {
    implementation 'com.github.tablebird:service-provider-builder:0.1.0'
    annotationProcessor 'com.github.tablebird:service-provider-builder-compiler:0.1.0'
}
```

### 示例代码

#### 服务接口定义（公共模块）
```java
@ServiceProvider(ServiceProviderPolicy.SINGLE)
public interface IService {
    String getName();
}
```
`ServiceProviderPolicy.SINGLE`代表服务的实现是唯一的，不允许存在多个，默认使用`ServiceProviderPolicy.MULTIPLE`允许多个实例

#### 服务具体实现（具体业务模块）
```java
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
```
服务的构建会优先寻找`@BuildService`指定的静态方法，如果未指定则会使用构造函数
服务的实现建议使非公开的，避免直接被外部引用，构建器会负责找到服务的实现


#### 服务接口的调用
```java
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        IService iService = ServiceProviderBuilder.buildSingleService(IService.class);
        TextView textView = findViewById(R.id.text);
        if (iService != null) {
            textView.setText(iService.getName());
        }
    }
}
```

### 混淆问题

需要添加如下内容：
```proguard
-keep class com.tablebird.serviceproviderbuilder.*
-keep public class * extends com.tablebird.serviceproviderbuilder.ServiceBuilder{
}
-keep @com.tablebird.serviceproviderbuilder.ServiceProvider class * {}
```

## License

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
