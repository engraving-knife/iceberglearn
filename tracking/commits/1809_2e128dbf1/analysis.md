# 提交 1809：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#12435)

## 提交信息

- **序号**：1809 / 4088
- **哈希**：2e128dbf15b882f9152e634164a4b732e5df883e
- **短哈希**：2e128dbf1
- **日期**：2025-03-02 20:27:20 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#12435)
- **PR/Issue**：#12435

## 总体目的

这是一个由 dependabot 自动生成的依赖版本升级提交。该提交将 `org.openapitools:openapi-generator-gradle-plugin` 从 7.11.0 升级到 7.12.0。

OpenAPI Generator Gradle 插件用于在构建过程中根据 OpenAPI 规范文件自动生成 REST API 客户端/服务端代码。在 Iceberg 项目中，它主要用于 `open-api` 模块，根据 `rest-catalog-open-api.yaml` 规范文件生成 REST Catalog 相关的客户端代码。保持该插件处于最新版本有助于获得最新的代码生成修复、新特性以及安全补丁。

此次升级属于 semver-minor 级别（次要版本升级），按照语义化版本约定，7.12.0 相对于 7.11.0 引入了向后兼容的新功能，通常不会破坏现有构建。

## 如何达成设计目的

dependabot 通过修改根 `build.gradle` 文件中 `buildscript` 块的 classpath 依赖声明，将版本号从 `7.11.0` 更新为 `7.12.0`。由于该插件仅在构建脚本的 buildscript 阶段加载，升级仅影响代码生成任务，不会影响 Iceberg 运行时行为。

## 修改详情

### build.gradle (修改, 1 line)

修改了 `buildscript.dependencies` 块中的 classpath 依赖声明，将 `org.openapitools:openapi-generator-gradle-plugin` 的版本从 `7.11.0` 改为 `7.12.0`。这是该提交的唯一实质性变更，通过一行版本号替换完成升级。

## 小结

这是一个低风险的依赖版本升级，仅修改一行构建脚本。升级属于次要版本更新，向后兼容。回迁到 1.4.x 分支时只需确保该插件版本与 1.4.x 分支的 Gradle 配置兼容即可，一般可直接 cherry-pick。
