# 提交 3684：Build: Bump org.openapitools:openapi-generator-gradle-plugin from 7.21.0 to 7.22.0 (#16278)

## 提交信息

- **序号**：3684 / 4088
- **哈希**：0e0e795197a268ce651f86d2573b5d70d5facd1b
- **短哈希**：0e0e79519
- **日期**：2026-05-11 13:57:38 +0200
- **作者**：Huaxin Gao
- **提交说明**：Build: Bump org.openapitools:openapi-generator-gradle-plugin from 7.21.0 to 7.22.0 (#16278)
- **PR/Issue**：#16278

## 总体目的

这个提交将 OpenAPI Generator Gradle 插件从 7.21.0 升级到 7.22.0，同时修复了在新版本中由于 API 变更导致的构建配置兼容性问题。OpenAPI Generator 是一个用于根据 OpenAPI 规范生成客户端/服务端代码的工具，Iceberg 项目使用它来验证 S3 Signer 和 REST Catalog 的 OpenAPI 规范文件。

此次升级为次版本（minor version）升级。除了简单的版本号更新外，还需要适配新版本中 `ValidateTask` 的 API 变更——新版本要求 `inputSpec` 属性使用 `RegularFileProperty` 类型而非简单的字符串。

## 如何达成设计目的

通过修改 `build.gradle` 文件完成两件事：
1. 升级 buildscript 中的插件版本号
2. 将两处 `inputSpec` 的赋值从字符串方式改为使用 `layout.projectDirectory.file()` 方式，以适配新版本 API

## 修改详情

### `build.gradle` (+3/-3 lines)

**修改目的**：升级 OpenAPI Generator 插件版本并适配 API 变更。

**工作逻辑**：

版本升级：
```groovy
-classpath 'org.openapitools:openapi-generator-gradle-plugin:7.21.0'
+classpath 'org.openapitools:openapi-generator-gradle-plugin:7.22.0'
```

API 适配（iceberg-aws 模块）：
```groovy
-  def s3SignerSpec = "$projectDir/src/main/resources/s3-signer-open-api.yaml"
+  def s3SignerSpec = layout.projectDirectory.file("src/main/resources/s3-signer-open-api.yaml")
```

API 适配（iceberg-open-api 模块）：
```groovy
-  def restCatalogSpec = "$projectDir/rest-catalog-open-api.yaml"
+  def restCatalogSpec = layout.projectDirectory.file("rest-catalog-open-api.yaml")
```

新版本的 `ValidateTask.inputSpec` 属性期望接收 `RegularFileProperty` 类型，因此需要使用 `layout.projectDirectory.file(...)` 来创建正确类型的文件引用，替代原来的字符串插值方式。这种方式也是 Gradle 推荐的最佳实践，能够更好地支持构建缓存和配置缓存。

## 总结

这是一个依赖升级提交，除了更新版本号外，还需要适配新版本 API 的变更。从字符串方式改为 `layout.projectDirectory.file()` 方式不仅是兼容性修复，也符合 Gradle 的最佳实践，提升了构建配置的健壮性。
