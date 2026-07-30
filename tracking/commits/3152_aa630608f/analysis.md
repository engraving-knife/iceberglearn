# 提交 3152：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#15129)

## 提交信息

- **序号**：3152 / 4088
- **哈希**：aa630608f79d06c1c3929801fd7266b082170758
- **短哈希**：aa630608f
- **日期**：2026-01-25 09:30:44 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#15129)
- **PR/Issue**：#15129

## 总体目的

本提交是 Dependabot 自动发起的依赖升级，将 `org.openapitools:openapi-generator-gradle-plugin` 从 `7.18.0` 升级到 `7.19.0`。该插件是 OpenAPITools 提供的 Gradle 插件，用于根据 OpenAPI 规范文件自动生成 API 客户端/服务端桩代码。在 Iceberg 项目中，它在根 `build.gradle` 的 `buildscript` classpath 中声明，配合仓库中的 `open-api/rest-catalog-open-api.yaml`（Iceberg REST Catalog 的 OpenAPI 规范）使用，用于生成 REST Catalog 相关的 API 代码，并由 `.github/workflows/open-api.yml` CI 工作流校验生成的代码与规范的一致性。

本次升级为次版本（minor）升级（`version-update:semver-minor`，7.18.0 → 7.19.0），按语义化版本约定可包含新功能与向后兼容的改进。由于该插件仅用于构建期代码生成，不进入 Iceberg 发布产物，升级影响范围限于代码生成结果与 CI 校验流程。Dependabot 在提交信息中附带了上游 release notes、changelog 与 commits 对比链接。

## 如何达成设计目的

直接在根 `build.gradle` 的 `buildscript.dependencies.classpath` 中将插件版本字符串从 `7.18.0` 改为 `7.19.0`，无需其他改动。该插件在构建脚本解析阶段被加载，应用于 REST Catalog API 代码生成任务。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：将 openapi-generator-gradle-plugin 版本从 7.18.0 提升到 7.19.0。

**工作逻辑**：将根 `build.gradle` 中 `buildscript` 块内的 `classpath 'org.openapitools:openapi-generator-gradle-plugin:7.18.0'` 修改为 `7.19.0`。该插件基于 `open-api/rest-catalog-open-api.yaml` 规范生成 Iceberg REST Catalog 的 API 桩代码，并由 CI 工作流校验生成结果。升级到 7.19.0 获取上游次版本的新功能与改进，属 semver-minor 级别；由于插件仅参与构建期代码生成、不进入发布产物，影响范围限于生成代码与 CI 校验。

## 总结

本提交通过将 OpenAPI 代码生成 Gradle 插件从 7.18.0 升级到 7.19.0，获取上游次版本改进，保持 REST Catalog API 代码生成工具链的及时更新；该插件仅用于构建期，不影响 Iceberg 发布产物，影响范围限于生成代码与 CI 校验流程。
