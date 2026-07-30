# 提交 3054：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#14934)

## 提交信息

- **序号**：3054 / 4088
- **哈希**：4db39095867843d4031cf5a2f04b878e930de13b
- **短哈希**：4db390958
- **日期**：2025-12-27
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#14934)
- **PR/Issue**：#14934

## 总体目的

这是一个 Dependabot 自动依赖升级提交。`openapi-generator-gradle-plugin` 是 OpenAPITools 提供的 Gradle 插件，用于从 OpenAPI 规范自动生成客户端/服务端代码（如 REST 客户端存根、模型类等）。Iceberg 在根 `build.gradle` 的 `buildscript` classpath 中加载该插件，用于基于 `open-api/` 下的 REST Catalog OpenAPI 规范生成多语言客户端代码（Iceberg 对外暴露 REST Catalog API，需要为不同集成方生成对应的客户端）。

本次升级从 `7.17.0` 到 `7.18.0`，属于 **semver-minor**（次版本）升级。Dependabot 将其归类为 `direct:production` 依赖、`version-update:semver-minor`。次版本升级通常包含新功能与改进，向后兼容。升级动机是跟进上游代码生成器的改进与修复，保持生成的客户端代码质量。

## 如何达成设计目的

Dependabot 直接修改根 `build.gradle` 中 `buildscript.dependencies.classpath` 里该插件的版本字符串，从 `7.17.0` 改为 `7.18.0`。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 openapi-generator-gradle-plugin 版本。

**工作逻辑**：在 `buildscript { dependencies { classpath ... } }` 块中，将 `'org.openapitools:openapi-generator-gradle-plugin:7.17.0'` 改为 `'org.openapitools:openapi-generator-gradle-plugin:7.18.0'`。该插件在构建脚本解析阶段被加载，后续 Gradle 子工程（如 REST Catalog 客户端生成任务）会使用该版本执行代码生成。预期影响：获得 7.18.0 上游的生成器改进（如对新 OpenAPI 3.1 特性、各语言模板的修复），生成的客户端代码在边界处理上可能更准确；属构建时插件，不影响 Iceberg 运行时 Java/Scala 产物本身。

## 总结

此提交是 OpenAPI 代码生成 Gradle 插件的次版本依赖升级，保持代码生成工具链与上游同步，属于低风险的构建依赖维护，不涉及运行时功能变更。
