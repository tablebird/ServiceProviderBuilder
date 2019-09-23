# Service Provider Builder

Implementation of Service Interface Based on SPI (Service Provider Interface),Generate builder code using annotation,Easy access to the implementation of the service interface

+ Declare the service interface using `@ServiceProvider` on the interface
+ Use `@ServiceImplementation` to specify the service to be built
+ Use `@BuildService` on the static method of the service to specify how to build the service.
+ Eliminate add **META-INF.services** file by using annotation on service

## Usage

### Installation

First,add following code in  `build.gradle` of you project module：
```groovy
dependencies {
    implementation 'com.github.tablebird:service-provider-builder:0.1.0'
    annotationProcessor 'com.github.tablebird:service-provider-builder-compiler:0.1.0'
}
```
Add if **Kotlin** is used：
```groovy
apply plugin: 'kotlin-kapt'
dependencies {
	...
	implementation 'com.github.tablebird:service-provider-builder:0.1.0'
	kapt 'com.github.tablebird:service-provider-builder-compiler:0.1.0'
}
```

### Tutorial

#### Service provider interface define（Common module）
```java
@ServiceProvider(ServiceProviderPolicy.SINGLE)
public interface IService {
    String getName();
}
```
`ServiceProviderPolicy.SINGLE` represents the implementation of the service is unique, not allowed to exist multiple, by default using `ServiceProviderPolicy.MULTIPLE` allows multiple instances

#### Service implementation（Business module）
**Java**

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

**Kotlin**
```kotlin
@ServiceImplementation
internal Service private constructor() : IService {
    companion object {
        @BuildService
        @JvmStatic
        fun getInstance() = Holder.INSTANCE
    }

    private object Holder {
        val INSTANCE = Service()
    }
}
```

The service build will first look for the static method specified by `@BuildService`, if not specified, the constructor will be used.
The implementation of the service is recommended to be non-public, avoiding direct external references, and the builder will be responsible for finding the implementation of the service.


#### Using service provider
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

### proguard

Add following code in proguard file：
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
