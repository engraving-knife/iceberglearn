# 提交 2820：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#14477)

## 提交信息

- **序号**：2820 / 4088
- **哈希**：74e2ea7b140c2a18c6013bc81840e3a151b15e19
- **短哈希**：74e2ea7b1
- **日期**：2025-11-02 12:29:24 +0530
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.openapitools:openapi-generator-gradle-plugin (#14477)
- **PR/Issue**：#14477

## 总体目的

这是 Dependabot 自动生成的依赖版本升级提交。OpenAPI Generator Gradle 插件（`org.openapitools:openapi-generator-gradle-plugin`）用于从 OpenAPI 规范自动生成客户端/服务端代码。Iceberg 项目使用该插件从 REST API 的 OpenAPI 规范生成客户端代码存根，用于 REST catalog 客户端与服务端的交互。

本次从 7.16.0 升至 7.17.0，属于次版本（minor）升级，可能包含生成代码模板的改进、新语言支持或生成器行为修复。升级该插件可能影响生成的客户端代码，需验证生成结果与项目代码兼容。

## 如何达成设计目的

通过修改 `build.gradle` 中的 `buildscript.classpath` 声明，将 openapi-generator-gradle-plugin 从 `7.16.0` 更新为 `7.17.0`。该插件在构建脚本的 buildscript 块中加载，影响代码生成阶段。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 OpenAPI Generator Gradle 插件版本。

**工作逻辑**：在 `buildscript { dependencies { classpath ... } }` 块中，将 `classpath 'org.openapitools:openapi-generator-gradle-plugin:7.16.0'` 改为 `7.17.0`。注意此处使用直接 classpath 声明而非版本目录属性，因为该插件需要在构建脚本自身解析阶段可用。

## 总结

将 OpenAPI Generator Gradle 插件从 7.16.0 升级到 7.17.0，属于次版本升级，用于获取代码生成器的改进。该插件影响 REST API 客户端代码的生成。这是 Dependabot 批量依赖升级（2811-2819）的一部分。
