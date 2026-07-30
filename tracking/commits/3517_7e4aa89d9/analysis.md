# 提交 3517：AWS, Core: Switch Jetty to use new Compression API for GZIP (#15043)

## 提交信息

- **序号**：3517 / 4088
- **哈希**：7e4aa89d9900a52620afd1456152b63b47f2223b
- **短哈希**：7e4aa89d9
- **日期**：2026-04-11 16:04:19 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：AWS, Core: Switch Jetty to use new Compression API for GZIP (#15043)
- **PR/Issue**：#15043

## 总体目的

Jetty 12 中旧的 `GzipHandler`（位于 `org.eclipse.jetty.server.handler.gzip` 包）已被标记为 `@Deprecated`/`@Removal`，社区推荐使用全新的 Compression API（`org.eclipse.jetty.compression` 模块）。项目中所有用到 `GzipHandler` 的测试和测试夹具都带有 `@SuppressWarnings("removal")` 注解来压制告警，这不是长久之计。本提交将所有 REST 相关测试与 OpenAPI 测试夹具中的 GZIP 处理统一迁移到新 API。

迁移后还能消除构建时的 deprecation 噪音，并保证项目在 Jetty 未来版本完全移除旧 `GzipHandler` 时仍然可用。

## 如何达成设计目的

新 API 采用「CompressionHandler + 具体压缩实现」的组合方式：先用 `CompressionHandler` 作为统一的压缩处理 handler，再通过 `putCompression(new GzipCompression())` 注册 GZIP 实现。这与旧版 `new GzipHandler()` 一把梭的写法不同，但更灵活（可同时挂多种压缩算法）。

同时作者注意到 `TestFreshnessAwareLoading` 这类测试会断言 ETag 与条件请求，而 GZIP 压缩响应会干扰这些断言，因此在 `TestBaseWithRESTServer` 中引入了一个可重写的 `useHttpCompression()` 钩子，让该子类关闭压缩以避免测试失败。

## 修改详情

### `gradle/libs.versions.toml` (+2/-1 lines)

**修改目的**：用新的 compression 模块坐标替换旧的 jetty-server 坐标。

**工作逻辑**：
```toml
-jetty-server = { module = "org.eclipse.jetty:jetty-server", version.ref = "jetty" }
+jetty-compression-server = { module = "org.eclipse.jetty.compression:jetty-compression-server", version.ref = "jetty" }
+jetty-compression-gzip = { module = "org.eclipse.jetty.compression:jetty-compression-gzip", version.ref = "jetty" }
```
新增 `jetty-compression-server` 与 `jetty-compression-gzip` 两个依赖别名，分别对应 server 端压缩框架和 GZIP 实现。

### `build.gradle` (+6/-2 lines)

**修改目的**：在 core、aws、open-api 三个模块的测试依赖中用新的 compression 依赖替换 `libs.jetty.server`。

**工作逻辑**：core 模块替换为 `libs.jetty.compression.server` + `libs.jetty.compression.gzip`；aws 模块新增这两个依赖（保留 jetty.servlet）；open-api 的 testFixtures 同样替换。这样测试代码可以使用新 API 的类。

### `core/src/test/java/org/apache/iceberg/rest/TestBaseWithRESTServer.java` (+15/-2 lines)

**修改目的**：抽取可重写的压缩开关并切换为新 API。

**工作逻辑**：
- 移除 `@SuppressWarnings("removal")`
- 引入新 import：`GzipCompression` 与 `CompressionHandler`，移除 `GzipHandler`
- 新增 `useHttpCompression()` 方法默认返回 `true`，子类可重写
- 在 `before()` 中根据该标志决定是否插入 `CompressionHandler`：
```java
if (useHttpCompression()) {
  CompressionHandler compressionHandler = new CompressionHandler();
  compressionHandler.putCompression(new GzipCompression());
  servletContext.insertHandler(compressionHandler);
}
```
这为不需要压缩的测试（如 freshness-aware）留出了关闭通道。

### `core/src/test/java/org/apache/iceberg/rest/TestFreshnessAwareLoading.java` (+5/-0 lines)

**修改目的**：关闭 HTTP 压缩以避免 GZIP 干扰 ETag/条件请求断言。

**工作逻辑**：
```java
@Override
protected boolean useHttpCompression() {
  return false;
}
```
该测试关注的是 freshness 感知加载，需要对 ETag 和 If-None-Match 等条件请求做精确断言，压缩会改变响应体与 header 行为，故禁用。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/signer/TestS3RestSigner.java` (+5/-3 lines)
**修改目的**：将 S3 REST 签名集成测试切换到新 Compression API。
**工作逻辑**：移除 `@SuppressWarnings("removal")`，将 `servletContext.setHandler(new GzipHandler())` 替换为创建 `CompressionHandler`、注册 `GzipCompression` 并 `insertHandler` 的三步写法。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+5/-3 lines)
**修改目的**：REST Catalog 测试切换新 Compression API。
**工作逻辑**：同上，移除 suppress 注解并替换为 `CompressionHandler` + `GzipCompression` + `insertHandler`。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java` (+5/-3 lines)
**修改目的**：REST View Catalog 测试切换新 Compression API。
**工作逻辑**：与 `TestRESTCatalog` 完全相同的替换模式。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalogWithAssumedViewSupport.java` (+5/-3 lines)
**修改目的**：Assumed View 支持测试同步迁移。
**工作逻辑**：同上替换模式。

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RESTCatalogServer.java` (+5/-3 lines)
**修改目的**：OpenAPI 测试夹具中的 REST Catalog 服务器迁移到新 API。
**工作逻辑**：`context.insertHandler(new GzipHandler())` 改为 `CompressionHandler` + `GzipCompression` 后 `insertHandler`，并移除 `@SuppressWarnings("removal")`。

## 总结

本提交将项目内所有 Jetty GZIP 处理从已废弃的 `GzipHandler` 迁移到 Jetty 12 的新 Compression API（`CompressionHandler` + `GzipCompression`），同时为依赖 freshness/ETag 断言的测试引入了可关闭压缩的钩子。改动涉及依赖坐标、构建脚本和 8 个测试/夹具文件，是一次较为全面的 API 升级，确保项目在 Jetty 后续版本中不会因旧 API 被移除而构建失败。
