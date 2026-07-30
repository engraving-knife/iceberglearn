# 提交 2209：[REST] Add option to configure TLS settings in REST client (#13190)

## 提交信息

- **序号**：2209 / 4088
- **哈希**：b3adeb12e21c56d742c408f67c3cdb96b3e02ff0
- **短哈希**：b3adeb12e
- **日期**：2025-06-04 11:17:07 -0700
- **作者**：Bryan Keller
- **提交说明**：[REST] Add option to configure TLS settings in REST client (#13190)
- **PR/Issue**：#13190

## 总体目的

这个提交为 Iceberg 的 REST 客户端（HTTPClient）增加了通过自定义实现类配置 TLS（传输层安全）设置的能力。在此之前，REST 客户端的 TLS 配置无法被用户自定义，限制了在企业环境（例如需要使用自定义信任证书、特定协议版本或加密套件的场景）下的适用性。通过引入一个可插拔的 `TLSConfigurer` 接口，用户可以根据自身需求提供 SSL 上下文、主机名验证器、支持的协议和加密套件等配置。这种设计保持了核心代码的稳定性，同时将 TLS 配置的灵活性交给下游用户和集成商实现。该改动为需要细粒度 TLS 控制的部署场景（如内部 CA、双向 TLS 认证等）提供了必要的扩展点。

## 如何达成设计目的

- 引入 `TLSConfigurer` 接口，作为 TLS 配置的抽象扩展点，提供 SSLContext、HostnameVerifier、supportedProtocols、supportedCipherSuites 四个核心方法的默认实现。
- 在 `HTTPClient` 中新增配置项 `rest.client.tls.configurer-impl`，通过该属性指定自定义 `TLSConfigurer` 实现类的全限定名。
- 使用 Iceberg 的 `DynConstructors` 反射机制动态加载并实例化用户提供的 `TLSConfigurer` 实现，要求实现类具备无参构造函数。
- 在连接管理器构建过程中，如果配置了 `TLSConfigurer`，则使用 `DefaultClientTlsStrategy` 包装其配置并设置到 `PoolingHttpClientConnectionManagerBuilder` 上。
- 提供完整的单元测试覆盖：包括正常加载、无参构造函数缺失、类不存在、未实现接口等多种边界场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (修改, +61/-3 lines)

**修改目的**：在 REST 客户端的连接管理器构建流程中集成自定义 TLS 配置能力。

**工作逻辑**：
- 新增配置常量 `REST_TLS_CONFIGURER = "rest.client.tls.configurer-impl"` 用于指定实现类。
- 修改 `configureConnectionManager` 方法：原本直接调用 `connectionManagerBuilder.build()` 返回，现在改为先在 builder 上设置系统属性、最大连接数等参数，然后调用 `loadTlsConfigurer(properties)` 加载配置器。如果存在配置器，则创建 `DefaultClientTlsStrategy`（使用配置器提供的 sslContext、supportedProtocols、supportedCipherSuites、SSLBufferMode.STATIC、hostnameVerifier），并通过 `setTlsSocketStrategy` 设置到 builder 上，最后再 build。
- 新增 `loadTlsConfigurer` 方法：通过 `DynConstructors` 反射加载并实例化指定实现类，处理 `NoSuchMethodException`（无无参构造函数）、`ClassNotFoundException`（类不存在）、`ClassCastException`（未实现接口）等异常，转换为带提示信息的 `IllegalArgumentException`。实例化后调用 `initialize(properties)` 完成初始化。
- 导入了 `DefaultClientTlsStrategy`、`SSLBufferMode`、`DynConstructors`、`TLSConfigurer` 等相关类。

### `core/src/main/java/org/apache/iceberg/rest/auth/TLSConfigurer.java` (新增, +46 lines)

**修改目的**：定义可插拔的 TLS 配置接口，作为用户自定义 TLS 设置的扩展点。

**工作逻辑**：
- 接口包含 5 个方法，全部提供默认实现：
  - `initialize(Map<String, String> properties)`：默认空实现，供实现类接收配置属性。
  - `sslContext()`：返回默认的 `SSLContext`（通过 `SSLContexts.createDefault()`）。
  - `hostnameVerifier()`：返回默认的 `HostnameVerifier`（通过 `HttpsSupport.getDefaultHostnameVerifier()`）。
  - `supportedProtocols()`：返回 null，表示使用默认协议。
  - `supportedCipherSuites()`：返回 null，表示使用默认加密套件。
- 用户实现该接口后，可选择性覆盖需要自定义的方法，其余保持默认行为。

### `core/src/test/java/org/apache/iceberg/rest/TestHTTPClient.java` (修改, +55 lines)

**修改目的**：为新增的 TLS 配置器加载逻辑提供测试覆盖。

**工作逻辑**：
- 新增两个静态内部测试类：
  - `DefaultTLSConfigurer`：实现 `TLSConfigurer`，包含计数器 `count` 验证实例化次数。
  - `TLSConfigurerMissingNoArgCtor`：仅有带参数的构造函数，用于测试无参构造函数缺失场景。
- 新增 4 个测试用例：
  - `testLoadTLSConfigurer`：验证正常加载配置器并被实例化一次。
  - `testLoadTLSConfigurerNoArgConstructorNotFound`：验证无无参构造函数时抛出 `IllegalArgumentException`。
  - `testLoadTLSConfigurerClassNotFound`：验证类不存在时抛出 `IllegalArgumentException`，包含 `ClassNotFoundException` 信息。
  - `testLoadTLSConfigurerNotImplementTLSConfigurer`：验证未实现接口时抛出 `IllegalArgumentException`，提示 "does not implement TLSConfigurer"。

## 总结

该提交通过引入可插拔的 `TLSConfigurer` 接口，为 Iceberg REST 客户端提供了灵活的 TLS 配置能力，解决了企业环境下需要自定义 SSL 上下文、证书、协议版本等场景的需求。设计上采用反射加载机制和默认实现，既保持了向后兼容，又提供了完整的扩展点和错误处理。测试覆盖全面，确保各种异常场景都能给出有意义的错误提示。
