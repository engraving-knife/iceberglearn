# 提交 0906：Build: Bump software.amazon.awssdk:bom from 2.26.12 to 2.26.16 (#10650)

## 提交信息

- **序号**：0906 / 4088
- **哈希**：d06da2dd76454a8e3485fad23de4ccc123611447
- **短哈希**：d06da2dd7
- **日期**：2024-07-07
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.26.12 to 2.26.16 (#10650)
- **PR/Issue**：#10650

## 总体目的

Iceberg 通过 AWS SDK for Java v2 的 BOM（`software.amazon.awssdk:bom`）统一管理 AWS 相关依赖（如 S3、DynamoDB、Glue、STS 等客户端）的版本。dependabot 定期检查该 BOM 的新版本并提交 PR 升级。本次将 BOM 从 `2.26.12` 升级到 `2.26.16`，跨越 4 个 patch 版本，引入 AWS SDK 在该版本区间内的 bug 修复和改进。这是常规的依赖维护工作，不涉及 Iceberg 自身代码逻辑变更。

## 如何达成设计目的

采用 Gradle version catalog 统一管理依赖版本：所有版本号集中在 `gradle/libs.versions.toml` 中声明。升级时只需修改 toml 文件中 `awssdk-bom` 这一行版本号，所有引用该 BOM 的 AWS SDK 子模块会自动同步到新版本。注意此提交紧随 0904（azure-sdk-bom 升级）之后，toml 文件中 `awssdk-bom` 与 `azuresdk-bom` 相邻，但各自独立升级、互不影响。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK for Java v2 BOM 版本从 2.26.12 升级到 2.26.16。

**工作逻辑**：

```diff
-awssdk-bom = "2.26.12"
+awssdk-bom = "2.26.16"
```

该行位于 `[versions]` 段。Gradle 构建中通过 `platform("software.amazon.awssdk:bom:${awssdk-bom}")` 引入 BOM，BOM 内部统一管理各 AWS SDK 子模块（`s3`、`glue`、`sts`、`dynamodb` 等）的版本。升级 BOM 后所有未显式指定版本的 AWS SDK 子模块自动使用 2.26.16 BOM 中声明的版本。由于 AWS SDK 2.26.x 系列内部 patch 版本通常保持二进制兼容，此次升级风险较低。

## 小结

- **成效**：将 AWS SDK for Java v2 BOM 从 2.26.12 升级到 2.26.16，引入 4 个 patch 版本的 bug 修复和改进。
- **影响范围**：1 个文件 `gradle/libs.versions.toml`，1 行改动，无代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：可以回迁但非必须。AWS SDK BOM patch 版本升级通常向后兼容，1.4.x 分支可按需升级。注意 1.4.x 分支另有 `awssdk-s3accessgrants` 独立版本（`2.0.0`），不受此 BOM 升级影响。若 1.4.x 已有更新的 BOM 版本则无需回迁。S3 相关集成测试需在升级后回归验证。
