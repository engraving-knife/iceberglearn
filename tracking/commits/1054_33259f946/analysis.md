# 提交 1054：Build: Bump software.amazon.awssdk:bom from 2.26.29 to 2.27.2 (#10913)

## 提交信息

- **序号**：1054 / 4088
- **哈希**：33259f946baabffc39e50f7a464a21d7fe32d606
- **短哈希**：33259f946
- **日期**：2024-08-12（Mon Aug 12 23:55:59 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.26.29 to 2.27.2 (#10913)
- **PR/Issue**：#10913

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交。`software.amazon.awssdk:bom` 是 AWS SDK for Java v2 的 BOM（Bill of Materials）依赖清单，统一管理 AWS SDK 各模块（如 S3、Glue、STS、DynamoDB、KMS 等）的版本组合。在 Iceberg 项目中，`aws` 模块大量使用 AWS SDK 进行 S3 对象存储读写、Glue Catalog 元数据管理等操作，是 AWS 集成的基础。

本次提交将 Gradle 版本目录中 `awssdk-bom` 版本从 `2.26.29` 升级到 `2.27.2`，属于 semver-minor 升级（2.26.x → 2.27.x）。AWS SDK v2 严格遵循 semver，minor 版本升级通常保持向后兼容，主要带来新服务支持、API 增强、bug 修复与性能改进。升级目的是保持与上游最新发布的对齐，避免累积过大的版本落差。

## 如何达成设计目的

实现方式是修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `awssdk-bom` 这一项的版本字符串，从 `2.26.29` 改为 `2.27.2`。Gradle 在构建 `aws` 模块时从 BOM 中导入对应版本的 S3、Glue、STS 等子模块，无需改动业务代码。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK v2 BOM 版本号从 2.26.29 升级到 2.27.2。

**工作逻辑**：仅修改一行版本字符串：

```diff
-awssdk-bom = "2.26.29"
+awssdk-bom = "2.27.2"
```

该变量在版本目录中声明后，被 `aws` 模块的 `build.gradle` 通过平台依赖（platform）引用，从而在构建时统一拉取 AWS SDK 各子模块的对应版本。

## 小结

- **成效**：完成 AWS SDK v2 BOM 的版本升级（2.26.29 → 2.27.2），使 Iceberg 的 AWS 集成模块对齐上游最新发布版本，获得新功能与 bug 修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件、一行改动；运行时影响 `aws` 模块依赖的所有 AWS SDK 子模块版本（S3、Glue、STS 等）。
- **回迁到 1.4.x 的注意事项**：可选择性回迁。AWS SDK v2 严格遵循 semver，minor 升级通常向后兼容。但 AWS SDK 涉及面广（S3FileIO、GlueCatalog、S3AccessGrants 等），回迁后应运行完整的 AWS 模块测试套件。如果 1.4.x 已知存在 AWS SDK 相关 bug 需要修复，则建议回迁；否则可保持现状。
