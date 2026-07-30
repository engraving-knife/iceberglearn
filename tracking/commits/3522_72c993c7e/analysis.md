# 提交 3522：Build: Bump software.amazon.awssdk:bom from 2.42.23 to 2.42.28 (#15952)

## 提交信息

- **序号**：3522 / 4088
- **哈希**：72c993c7ee50b3dc4b1b056080e8f7e7d0596540
- **短哈希**：72c993c7e
- **日期**：2026-04-11 23:30:31 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.42.23 to 2.42.28 (#15952)
- **PR/Issue**：#15952

## 总体目的

Dependabot 自动生成的依赖升级 PR，将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.42.23 升级到 2.42.28。这是一个 semver patch 版本升级，主要包含 bug 修复与小改进。

AWS SDK BOM 在 Iceberg 项目中被 `aws` 模块以及 S3 相关集成测试广泛使用，通过 BOM 统一管理所有 AWS SDK 子模块（如 s3、sts、kms、glue、dynamodb、lakeformation 等）的版本，确保各模块版本兼容。

## 如何达成设计目的

由于 AWS SDK 各子模块通过 BOM 统一管理版本，只需在 `gradle/libs.versions.toml` 中将 `awssdk-bom` 版本号升级，所有引用该 BOM 的子模块版本会同步更新。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 AWS SDK BOM 版本从 2.42.23 升级到 2.42.28。

**工作逻辑**：
```toml
-awssdk-bom = "2.42.23"
+awssdk-bom = "2.42.28"
```
该版本号被 `software.amazon.awssdk:bom` 依赖引用，并通过 platform 机制传递给所有 AWS SDK 子模块。

## 总结

Dependabot 自动升级 AWS SDK BOM 至 2.42.28 patch 版本，属于例行依赖维护。通过 BOM 集中管理版本，一次改动同步升级所有 AWS SDK 子模块，获取上游 bug 修复与稳定性改进。
