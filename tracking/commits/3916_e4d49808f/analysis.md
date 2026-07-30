# 提交 3916：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#16903)

## 提交信息

- **序号**：3916 / 4088
- **哈希**：e4d49808f89c70f003ab91b6677c70f956e3ccf2
- **短哈希**：e4d49808f
- **日期**：2026-06-21 00:05:54 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#16903)
- **PR/Issue**：#16903

## 总体目的

这是由 Dependabot 自动发起的依赖升级提交，目的是将 OpenAPI Generator Gradle 插件从 7.22.0 升级到 7.23.0。OpenAPI Generator 是一个用于根据 OpenAPI 规范自动生成 API 客户端、服务端代码以及文档的工具。Iceberg 项目使用该插件在构建过程中根据 REST catalog 的 OpenAPI 规范生成相应的代码（例如 Python 模型类等）。

通过定期升级依赖，项目能够获取上游的新功能、错误修复以及潜在的安全补丁，从而保持生成代码的质量与上游规范的一致性。此次升级为 semver-minor（次要版本）更新，通常意味着包含新功能但保持向后兼容。

## 如何达成设计目的

通过修改根目录下的 `build.gradle` 文件中 `buildscript` 块的 classpath 依赖声明，将插件版本号从 7.22.0 更新为 7.23.0。这是一次最小化的、机械化的版本号变更，符合 Dependabot 升级直接用于生产环境的构建依赖的常规做法。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 OpenAPI Generator Gradle 插件版本。

**工作逻辑**：
将 `buildscript` 块中的 `classpath 'org.openapitools:openapi-generator-gradle-plugin:7.22.0'` 改为 `7.23.0`。该插件在构建脚本阶段加载，用于在编译期执行 OpenAPI 规范到代码的生成任务。

## 总结

这是一次常规的依赖维护升级，通过将 OpenAPI Generator Gradle 插件升级到 7.23.0，使项目能够利用上游的最新改进。作为 semver-minor 版本更新，预期向后兼容，风险较低。
