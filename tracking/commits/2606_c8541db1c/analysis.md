# 提交 2606：Bump Spark version to 4.0.1 (#14019)

## 提交信息

- **序号**：2606 / 4088
- **哈希**：c8541db1c905e05891596ffe157c12392e38c93c
- **短哈希**：c8541db1c
- **日期**：2025-09-07 16:12:20 -0700
- **作者**：drexler-sky
- **提交说明**：Bump Spark version to 4.0.1 (#14019)
- **PR/Issue**：#14019

## 总体目的

本次提交将 Iceberg 项目中使用的 Spark 4.0 版本从 4.0.0 升级到 4.0.1。

Apache Spark 4.0 是一个重要的大版本发布。Iceberg 的 `spark/v4.0` 模块专门为 Spark 4.0 提供集成支持。Spark 4.0.1 是 4.0.x 系列的 patch 版本，包含对 4.0.0 中发现的 bug 修复和稳定性改进。

保持 Spark 版本与最新 patch 版本同步对于 Iceberg 的 Spark 集成至关重要，可以确保用户在使用 Iceberg + Spark 组合时获得最稳定和可靠的体验。Spark 4.0.1 可能修复了影响 Iceberg 集成的 bug。

## 如何达成设计目的

在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 Spark 4.0 的版本号从 `4.0.0` 修改为 `4.0.1`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Spark 4.0 版本号。

**工作逻辑**：将 Spark 4.0 相关的版本定义从 `4.0.0` 改为 `4.0.1`。该版本号被 `spark/v4.0` 模块的构建脚本引用，升级后所有 Spark 4.0 相关的依赖（如 spark-sql、spark-catalyst 等）将使用 4.0.1 版本。

**潜在影响**：作为 patch 级升级（4.0.0 → 4.0.1），预期包含 bug 修复和稳定性改进，不引入 API 破坏性变更。Spark 4.0.1 的修复可能涉及 SQL 执行、数据源 API、流处理等方面，这些修复有助于提升 Iceberg Spark 集成的稳定性。Iceberg 的 Spark 4.0 测试套件将自动使用新版本运行，验证兼容性。

## 总结

这是一个版本升级提交，将 Iceberg 支持的 Spark 4.0 版本从 4.0.0 升至 4.0.1。作为 Spark 大版本的 patch 升级，包含 bug 修复和稳定性改进，有助于提升 Iceberg 与 Spark 4.0 集成的可靠性。保持与最新 patch 版本同步是依赖管理的最佳实践。
