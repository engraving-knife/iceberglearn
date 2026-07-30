# 提交 2611：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#14011)

## 提交信息

- **序号**：2611 / 4088
- **哈希**：300532cd3b45812d60c621d6fc5d1801b295e4b6
- **短哈希**：300532cd3
- **日期**：2025-09-08 17:29:27 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#14011)
- **PR/Issue**：#14011

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，用于将 `openapi-generator-gradle-plugin` 从 7.14.0 升级到 7.15.0。该插件用于在构建过程中根据 OpenAPI 规范自动生成 API 客户端/服务端代码，Iceberg 项目在 REST catalog 相关模块中依赖它来生成与 Iceberg REST API 规范对应的代码。

Dependabot 持续监控项目依赖的最新版本，并在有新版本发布时自动创建 PR 推动升级。此次属于 semver-minor 级别升级（7.14.0 → 7.15.0），按照语义化版本约定，通常包含向后兼容的新功能与缺陷修复，不会引入破坏性变更。

## 如何达成设计目的

通过修改项目根 `build.gradle` 的 `buildscript` 依赖声明，将插件版本号从 `7.14.0` 改为 `7.15.0`。Gradle 在构建脚本执行时会解析该 classpath 依赖，从而在后续模块构建中加载新版本的 openapi-generator 插件。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 openapi-generator-gradle-plugin 版本号。

**工作逻辑**：在 `buildscript { dependencies { classpath ... } }` 块中，将 `'org.openapitools:openapi-generator-gradle-plugin:7.14.0'` 修改为 `'org.openapitools:openapi-generator-gradle-plugin:7.15.0'`。该 classpath 声明使插件在整个 Gradle 构建中可用，供需要 OpenAPI 代码生成的子模块（如 REST 相关模块）应用。

## 总结

这是一次常规的依赖维护升级，将 openapi-generator-gradle-plugin 升至 7.15.0，以获取最新功能与修复。属于 semver-minor 升级，风险较低，有助于保持构建工具链的现代化。
