# 提交 2073：Build: Bump org.openapitools:openapi-generator-gradle-plugin

## 提交信息

- **序号**：2073 / 4088
- **哈希**：ed20482ab17a352eeadc6bff8e66f2729eeb83b8
- **短哈希**：ed20482ab
- **日期**：2025-05-05 08:14:19 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#12966)
- **PR/Issue**：#12966

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 OpenAPI Generator Gradle 插件从 7.12.0 升级到 7.13.0。这是一个 minor 版本升级，可能包含新功能和改进。OpenAPI Generator 插件用于根据 OpenAPI 规范自动生成 REST 客户端/服务端代码，Iceberg 使用该插件生成 REST Catalog 相关的 API 代码。

## 如何达成设计目的

通过修改 `build.gradle` 中 buildscript classpath 的插件版本号，完成升级。

## 修改详情

### `build.gradle` (修改, +1/-1 lines)

**修改目的**：将 OpenAPI Generator Gradle 插件版本从 7.12.0 升级到 7.13.0。

**工作逻辑**：
在 buildscript 的 dependencies 块中，将 `classpath 'org.openapitools:openapi-generator-gradle-plugin:7.12.0'` 修改为 `classpath 'org.openapitools:openapi-generator-gradle-plugin:7.13.0'`。

## 总结

本提交由 Dependabot 自动生成，将 OpenAPI Generator Gradle 插件从 7.12.0 升级到 7.13.0（minor 版本升级），仅修改 `build.gradle` 中一行 classpath 依赖版本号。
