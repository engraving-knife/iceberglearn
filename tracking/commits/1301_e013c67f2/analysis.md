# 提交 1301：Aliyun: Remove spring-boot dependency (#11291)

## 提交信息

- **序号**：1301 / 4088
- **哈希**：e013c67f24f0c0c256dd6a0dfeb872b7959ac267
- **短哈希**：e013c67f2
- **日期**：2024-10-28（Mon Oct 28 18:42:08 2024 +0100）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Aliyun: Remove spring-boot dependency (#11291)
- **PR/Issue**：#11291

## 总体目的

Iceberg 的 Aliyun（阿里云 OSS）集成模块（`iceberg-aliyun`）在测试代码中使用了一个本地 OSS Mock 服务来模拟阿里云对象存储，便于不依赖真实 OSS 环境进行单元/集成测试。此前该 Mock 服务基于 Spring Boot 框架构建，使用 Spring MVC 的 `@RestController`、`@RequestMapping` 等注解处理 HTTP 请求，并通过 Spring Boot 内嵌 Jetty 容器提供服务。

这带来了几个问题：
1. **依赖体积大**：Spring Boot 是一个重量级框架，仅为测试 Mock 引入它不划算，增加了构建时间和依赖树复杂度。
2. **版本管理负担**：需要维护 `spring-boot` 和 `spring-web` 的版本号及其传递依赖。
3. **过度设计**：一个简单的 HTTP 文件存储 Mock 不需要 Spring 的依赖注入、自动配置等能力。

本提交将 Spring Boot 实现替换为基于 JDK 内置 `com.sun.net.httpserver.HttpServer` 的轻量级实现，完全移除 `spring-boot-starter-jetty`、`spring-boot-starter-web` 和 `spring-web` 三个测试依赖，简化了构建并减小了依赖体积。

## 如何达成设计目的

核心思路是用 JDK 自带的 `HttpServer`（轻量级 HTTP 服务器）替代 Spring Boot 的 REST 控制器，手动实现 HTTP 请求路由和处理。具体操作：

1. **新建 `AliyunOSSMock.java`**：用 `HttpServer` 实现完整的 OSS Mock 服务，包含 bucket 和 object 的 CRUD 操作、Range 请求支持等。
2. **删除 `AliyunOSSMockApp.java`**：原 Spring Boot 应用入口类（含 `@Configuration`、`@EnableAutoConfiguration` 等）。
3. **删除 `AliyunOSSMockLocalController.java`**：原 Spring MVC `@RestController`，其逻辑迁移到 `AliyunOSSMock` 的内部 `HttpHandler`。
4. **删除 `Range.java`**：原用于 Spring `Converter` 的 Range 值对象，新实现中直接在 handler 内解析 Range 头。
5. **修改 `AliyunOSSMockExtension.java`**：将引用从 `AliyunOSSMockApp` 改为 `AliyunOSSMock`，删除 `silent()` builder 方法（Spring 的 banner/log 控制不再需要）。
6. **修改 `AliyunOSSMockLocalStore.java`**：删除 Spring 的 `@Component` 注解和 `@Value` 注入，改为普通构造函数传参。
7. **修改 `TestUtility.java`**：删除 `.silent()` 调用。
8. **修改 `TestOSSOutputStream.java`**：将测试数据从 32MB 减小到 32KB，适配新 Mock 的性能特征。
9. **修改 `build.gradle` 和 `gradle/libs.versions.toml`**：移除 spring-boot 和 spring-web 依赖声明。

## 修改详情

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/AliyunOSSMock.java`（新增，569 行）

**修改目的**：替代原 Spring Boot 实现的 OSS Mock 服务，用 JDK 内置 HttpServer 提供同等功能。

**工作逻辑**：

- 使用 `HttpServer.create(new InetSocketAddress("localhost", serverPort), 0)` 创建 HTTP 服务器，监听默认端口 9393。
- 通过 `httpServer.createContext("/", new AliyunHttpHandler())` 注册根路径处理器。
- `AliyunHttpHandler` 实现 `HttpHandler` 接口，在 `handle()` 方法中解析请求路径（`/{bucketName}/{objectName}`）和 HTTP 方法（PUT/DELETE/HEAD/GET），分发到对应处理方法：
  - **PUT /{bucket}**：创建 bucket（`putBucket`）
  - **DELETE /{bucket}**：删除 bucket（`deleteBucket`），非空时返回 409
  - **PUT /{bucket}/{object}**：上传对象（`putObject`），返回 ETag 和 Last-Modified
  - **DELETE /{bucket}/{object}**：删除对象（`deleteObject`）
  - **HEAD /{bucket}/{object}**：获取对象元数据（`getObjectMeta`）
  - **GET /{bucket}/{object}**：下载对象（`getObject`），支持 Range 请求（返回 206 Partial Content）
- Range 请求处理：解析 `Range: bytes=start-end` 头，计算读取范围，使用 `BoundedInputStream` 限制读取字节数，返回 `Content-Range` 响应头。
- 错误处理：返回 XML 格式的错误响应（`<Error><Code>...</Code><Message>...</Message></Error>`），使用 `OSSErrorCode` 常量。
- 静态方法 `start(Map<String, Object> properties)`：工厂方法，从属性中读取 `root-dir` 和 `server.port`，创建并启动 Mock 实例。
- 内部类 `BoundedInputStream`：从原 `AliyunOSSMockLocalController` 迁移，限制 InputStream 读取字节数，注释说明借用自 `org.apache.commons:commons-io`。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/AliyunOSSMockApp.java`（删除，158 行）

**修改目的**：移除 Spring Boot 应用入口。

**工作逻辑**：原文件是 Spring Boot 应用，包含 `@Configuration`、`@EnableAutoConfiguration`、`@ComponentScan` 注解，通过 `SpringApplicationBuilder` 启动内嵌 Jetty 容器。还包含 `RangeConverter`（Spring `Converter<String, Range>`）和 XML 消息转换器配置。这些功能在新实现中由 `AliyunOSSMock` 的 `HttpHandler` 直接处理。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/AliyunOSSMockLocalController.java`（删除，522 行）

**修改目的**：移除 Spring MVC REST 控制器。

**工作逻辑**：原文件是 `@RestController`，使用 `@RequestMapping` 注解定义 bucket/object 的 REST 端点。包含 `OssException` 异常类、`ErrorResponse` XML 响应模型、`OSSMockExceptionHandler` 全局异常处理器、`BoundedInputStream` 内部类。这些逻辑全部迁移到 `AliyunOSSMock.java` 的 `AliyunHttpHandler` 中，异常处理改为直接在 handler 内返回 HTTP 错误响应。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/AliyunOSSMockLocalStore.java`（修改）

**修改目的**：移除 Spring 依赖注入，改为普通 Java 类。

**工作逻辑**：
1. 删除 import：`org.springframework.beans.factory.annotation.Value`、`org.springframework.http.MediaType`、`org.springframework.stereotype.Component`。
2. 删除类级 `@Component` 注解（不再由 Spring 容器管理）。
3. 构造函数参数从 `@Value("${root-dir:}") String rootDir` 改为普通 `String rootDir`（由调用方直接传参）。
4. 删除 bucket 时的异常从 `AliyunOSSMockLocalController.OssException(409, OSSErrorCode.BUCKET_NOT_EMPTY, ...)` 改为 `RuntimeException(OSSErrorCode.BUCKET_NOT_EMPTY)`（不再依赖已删除的 OssException 类）。
5. 默认 Content-Type 从 `MediaType.APPLICATION_OCTET_STREAM_VALUE` 改为字符串字面量 `"application/octet"`（不再依赖 Spring 的 MediaType 常量），保留注释说明原值。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/AliyunOSSMockExtension.java`（修改）

**修改目的**：适配新的 Mock 实现。

**工作逻辑**：
1. 字段类型从 `AliyunOSSMockApp ossMockApp` 改为 `AliyunOSSMock ossMock`。
2. `start()` 方法：从 `ossMockApp = AliyunOSSMockApp.start(properties)` 改为 `ossMock = AliyunOSSMock.start(properties)`，并包裹 try-catch 抛出 `RuntimeException("Can't start OSS Mock")`。
3. `stop()` 方法：从 `ossMockApp.stop()` 改为 `ossMock.stop()`。
4. 常量引用从 `AliyunOSSMockApp.PROP_HTTP_PORT` 等改为 `AliyunOSSMock.PROP_HTTP_PORT` 等。
5. `Builder` 类：删除 `silent()` 方法（不再需要 Spring 的 banner/日志控制），所有 `AliyunOSSMockApp.PROP_ROOT_DIR` 引用改为 `AliyunOSSMock.PROP_ROOT_DIR`。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/Range.java`（删除，43 行）

**修改目的**：移除不再需要的 Range 值对象。

**工作逻辑**：原文件是 Spring `Converter` 使用的 Range 值对象（`start` 和 `end` 字段）。新实现中 Range 解析直接在 `AliyunOSSMock.getObject()` 方法内通过字符串操作完成，不再需要独立的 Range 类。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/TestUtility.java`（修改）

**修改目的**：移除 `silent()` 调用。

**工作逻辑**：将 `AliyunOSSMockExtension.builder().silent().build()` 改为 `AliyunOSSMockExtension.builder().build()`。因为 `Builder` 中已删除 `silent()` 方法。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/TestOSSOutputStream.java`（修改）

**修改目的**：减小测试数据量。

**工作逻辑**：将大文件写入测试的数据量从 `32 * 1024 * 1024`（32MB）减小到 `32 * 1024`（32KB）。注释仍标注 "Write large file."。这一改动可能是因为新的基于 `HttpServer` 的 Mock 在处理大文件时的性能特征与 Spring Boot 内嵌 Jetty 不同，减小数据量使测试更稳定且更快。注意：此改动可能降低了测试对大文件场景的覆盖度。

### `build.gradle`（修改）

**修改目的**：移除 Aliyun 模块的 Spring Boot 测试依赖。

**工作逻辑**：在 `project(':iceberg-aliyun')` 的 `dependencies` 块中，删除以下三行：

```groovy
testImplementation libs.spring.web
testImplementation(libs.spring.boot.starter.jetty) {
  exclude module: 'logback-classic'
  exclude group: 'org.eclipse.jetty.websocket', module: 'javax-websocket-server-impl'
  exclude group: 'org.eclipse.jetty.websocket', module: 'websocket-server'
}
testImplementation(libs.spring.boot.starter.web) {
  exclude module: 'logback-classic'
  exclude module: 'spring-boot-starter-logging'
}
```

这些依赖原用于测试中的 Spring Boot Mock 服务，现已被 JDK 内置 HttpServer 替代。

### `gradle/libs.versions.toml`（修改）

**修改目的**：移除 Spring 版本声明和库定义。

**工作逻辑**：
1. 在 `[versions]` 区块删除：
   ```toml
   spring-boot = "2.7.18"
   spring-web = "5.3.39"
   ```
2. 在 `[libraries]` 区块删除：
   ```toml
   spring-boot-starter-jetty = { module = "org.springframework.boot:spring-boot-starter-jetty", version.ref = "spring-boot" }
   spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web", version.ref = "spring-boot" }
   spring-web = { module = "org.springframework:spring-web", version.ref = "spring-web" }
   ```

注意：本次提交后，`spring-boot` 和 `spring-web` 版本号已从 libs.versions.toml 中移除。但提交 1293 的 diff 中仍显示 `spring-boot = "2.7.18"` 和 `spring-web = "5.3.39"`（因 1293 先于 1301 合入），说明这些版本声明是本提交才最终删除的。

## 小结

- **成效**：Aliyun 模块的测试 Mock 从 Spring Boot 重构为 JDK 内置 HttpServer，移除了 3 个测试依赖（spring-boot-starter-jetty、spring-boot-starter-web、spring-web），减小了依赖体积和构建时间，简化了测试基础设施。代码净减 174 行（587 增 / 761 删）。
- **影响范围**：涉及 10 个文件，全部在 `aliyun/` 测试目录和构建配置（`build.gradle`、`gradle/libs.versions.toml`）中，不影响生产代码和运行时行为。核心变更是 Mock 实现方式从 Spring MVC 迁移到 JDK HttpServer。
- **回迁到 1.4.x 的注意事项**：这是测试基础设施重构，不影响运行时产物。1.4.x 分支**无需强制回迁**。但如果 1.4.x 遇到以下情况可考虑回迁：（1）Spring Boot 依赖导致构建问题或依赖冲突；（2）OSS Mock 测试不稳定。回迁时需注意：（a）这是一个较大的重构，涉及 10 个文件，需整体回迁而非部分；（b）`TestOSSOutputStream.java` 中测试数据从 32MB 减到 32KB，可能降低大文件覆盖度，回迁后需评估是否影响测试有效性；（c）`AliyunOSSMockLocalStore` 中异常类型从 `OssException` 改为 `RuntimeException`，错误信息粒度变粗（丢失了 HTTP 状态码），回迁时需确认测试中对异常类型的断言是否依赖原类型。
