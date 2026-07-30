# 提交 3059：Build: Bump software.amazon.awssdk:bom from 2.40.16 to 2.41.1 (#14961)

## 提交信息

- **序号**：3059 / 4088
- **哈希**：01b59d37f562295de6064f3898f6241639e1817e
- **短哈希**：01b59d37f
- **日期**：2026-01-03
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.40.16 to 2.41.1 (#14961)
- **PR/Issue**：#14961

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 项目依赖的 AWS SDK for Java BOM（`software.amazon.awssdk:bom`）从 `2.40.16` 升级到 `2.41.1`。AWS SDK BOM 是 Iceberg 与 AWS 生态集成的核心依赖：Iceberg 的 `aws-bundle`、`s3`、`glue`、`dynamodb`、`kms`、`s3accessgrants` 等模块均通过该 BOM 统一管理 AWS SDK 各子模块（如 S3 客户端、Glue 客户端、STS、DynamoDB 等）的版本，从而保证各 AWS 客户端版本相互兼容，避免因单独指定子模块版本而引入版本冲突。升级 BOM 即可整体抬升这些客户端的版本。

从版本号看，这是一次 semver-minor（次版本）升级：`2.40.16 → 2.41.1`，次版本号从 40 升到 41。按照 AWS SDK 的语义版本策略，minor 升级通常包含新增功能与缺陷修复，保持向后兼容，预期不会引入破坏性 API 变更。Dependabot 元数据也明确标注 `update-type: version-update:semver-minor`、`dependency-type: direct:production`，表明这是一个直接的生产依赖、次版本级别的常规升级。此类升级的预期影响是获取 AWS SDK 近期的新特性与 bug 修复（如 S3 客户端稳定性改进、新 API 支持等），同时保持与 Iceberg 现有调用方式的兼容。

## 如何达成设计目的

只需在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中把 `awssdk-bom` 的版本字符串从 `2.40.16` 改为 `2.41.1`，所有通过该 BOM 引入版本的 AWS SDK 子模块会自动跟随升级，无需逐个修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本以统一抬升各 AWS 客户端版本。

**工作逻辑**：
将 `awssdk-bom = "2.40.16"` 改为 `awssdk-bom = "2.41.1"`。该属性在版本目录中被 `aws-bundle`、`s3`、`glue`、`dynamodb`、`kms`、`s3accessgrants` 等模块的依赖通过 `platform("software.amazon.awssdk:bom:${awssdk-bom}")` 方式引用，作为 BOM 导入后，这些模块声明的 AWS SDK 子依赖（不显式写版本）会自动解析为 BOM 中定义的版本。因此单点修改此属性即可完成全量升级。

## 总结

本提交通过将 `awssdk-bom` 从 `2.40.16` 升级到 `2.41.1`（semver-minor），统一抬升了 Iceberg 各 AWS 集成模块所依赖的 AWS SDK 客户端版本，以获取近期的新特性与缺陷修复，同时保持向后兼容，属于常规的依赖维护。
