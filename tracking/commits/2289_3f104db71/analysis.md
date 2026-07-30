# 提交 2289：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#13421)

## 提交信息

- **序号**：2289 / 4088
- **哈希**：3f104db715f63d7db9dc9b7f5a5a15f2089c8ba0
- **短哈希**：3f104db71
- **日期**：2025-06-30 09:32:31 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#13421)
- **PR/Issue**：#13421

## 总体目的

本提交由 Dependabot 自动生成，目的是将 OpenAPI Generator Gradle 插件从 7.13.0 升级到 7.14.0。OpenAPI Generator 是一个用于根据 OpenAPI 规范自动生成 API 客户端、服务端存根和文档的工具，其 Gradle 插件在 Iceberg 项目中用于 REST 相关代码的自动生成。

依赖升级是项目维护中的常规工作，旨在获取最新版本的 bug 修复、功能改进和安全补丁。此升级属于 semver-minor（次要版本）更新，意味着包含向后兼容的新功能。

## 如何达成设计目的

Dependabot 通过扫描项目构建文件中的依赖声明，检测到新版本可用后自动创建 PR。升级仅涉及修改 `build.gradle` 中的插件版本号声明，从 `7.13.0` 改为 `7.14.0`。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：将 OpenAPI Generator Gradle 插件版本从 7.13.0 升级到 7.14.0。

**工作逻辑**：在 Gradle 构建文件的插件声明部分，将 `org.openapitools:openapi-generator-gradle-plugin` 的版本号从 `7.13.0` 更新为 `7.14.0`。这是一个单行版本号修改，不涉及任何配置逻辑的变化。

## 总结

这是一个常规的依赖版本升级提交，通过自动化工具 Dependabot 完成。升级 OpenAPI Generator 插件有助于确保 REST API 代码生成使用最新版本的工具链，获取相关的改进和修复。
