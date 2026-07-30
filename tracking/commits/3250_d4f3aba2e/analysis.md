# 提交 3250：Build: Bump software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin (#15325)

## 提交信息

- **序号**：3250 / 4088
- **哈希**：d4f3aba2eda26a01b3f5ccac19afd5793ecddd27
- **短哈希**：d4f3aba2e
- **日期**：2026-02-14
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin (#15325)
- **PR/Issue**：#15325

## 总体目的

`software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin` 是 AWS S3 Access Grants 的 Java 插件，用于为 S3 访问提供基于身份和权限的细粒度访问控制。在 Iceberg 项目中，该插件与 AWS SDK v2 集成，使 Iceberg 的 S3 文件 IO 能够通过 S3 Access Grants 机制获取数据访问授权，而非直接使用 IAM 策略。该依赖属于 `direct:production` 类型。

本次提交由 Dependabot 自动生成，将该插件从 `2.4.0` 升级到 `2.4.1`。根据语义版本规范，这是一个 patch 级别升级（`version-update:semver-patch`），即修订号从 0 变为 1。patch 升级通常只包含 bug 修复，不引入破坏性变更，预期对 Iceberg 的 S3 访问授权流程无行为影响。

## 如何达成设计目的

仅修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `awssdk-s3accessgrants` 的版本声明，从 `2.4.0` 改为 `2.4.1`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 AWS S3 Access Grants 插件版本从 2.4.0 升级至 2.4.1。

**工作逻辑**：
在版本目录的第 38 行，将 `awssdk-s3accessgrants = "2.4.0"` 修改为 `awssdk-s3accessgrants = "2.4.1"`。该版本变量被 Iceberg 的 AWS/S3 集成模块引用，升级后 S3 Access Grants 插件将获得最新的 bug 修复。作为 patch 级别升级，不涉及 API 兼容性变更。

## 总结

本次提交是 Dependabot 自动执行的 AWS S3 Access Grants 插件 patch 升级（2.4.0 → 2.4.1），保持 Iceberg S3 文件 IO 的访问授权插件处于最新补丁版本，获取 bug 修复和稳定性改进，对项目功能无影响。
