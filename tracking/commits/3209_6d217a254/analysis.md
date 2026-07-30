# 提交 3209：Spark 4.0: Upgrade to Spark 4.0.2 (#15218)

## 提交信息

- **序号**：3209 / 4088
- **哈希**：6d217a2548643eac788e2606c9183af7206cc3ba
- **短哈希**：6d217a254
- **日期**：2026-02-05
- **作者**：Manu Zhang
- **提交说明**：Spark 4.0: Upgrade to Spark 4.0.2 (#15218)
- **PR/Issue**：#15218

## 总体目的

Iceberg 为不同的 Spark 主版本维护彼此独立的集成模块（`spark/v3.4`、`spark/v3.5`、`spark/v4.0`、`spark/v4.1`），每个模块通过 Gradle 版本目录 `gradle/libs.versions.toml` 中的版本变量（`spark34`、`spark35`、`spark40`、`spark41`）统一声明其编译与测试所依赖的 Apache Spark 版本。其中 `spark40` 变量专门驱动 `spark/v4.0` 模块——该模块是 Iceberg 适配 Spark 4.0 的专属集成层，涵盖 DataSource V2、SQL 扩展（IcebergSqlExtensions）、过程调用、Scala 扩展、JMH 基准测试等。

本提交将 `spark40` 依赖从 `4.0.1` 升级到 `4.0.2`。Spark 4.0.2 是 Spark 4.0 主版本线下的维护版本（patch release），按语义版本约定属于 PATCH 升级，主要包含缺陷修复与稳定性改进，原则上向后兼容、不引入破坏性 API 变更。提交说明还包含一条"Remove staging maven repo"——PR 在开发过程中曾临时引入 Spark 的 staging Maven 仓库以拉取 4.0.2 候选/发布产物，正式发布后既升级版本又移除了该临时仓库；最终落盘的净 diff 只保留了版本号变更这一行，staging 仓库的添加与移除相互抵消、未残留于代码。

## 如何达成设计目的

整体思路是依托 Gradle 版本目录的集中式版本管理：仅需在 `gradle/libs.versions.toml` 中把 `spark40` 的值从 `4.0.1` 改为 `4.0.2`，`spark/v4.0` 模块下所有通过 `version.ref` 引用 `spark40` 的 Spark 坐标（spark-sql、spark-catalyst、spark-hive 等）便会统一同步到新版本，无需逐个修改各构建文件。同区还声明了 `spark34 = "3.4.4"`、`spark35 = "3.5.8"`、`spark41 = "4.1.1"`，本次仅升级 `spark40` 一项，不影响其它 Spark 版本的集成。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1)

**修改目的**：将 Spark 4.0 集成模块依赖的 Spark 版本提升至 4.0.2。

**工作逻辑**：
将版本目录中的 `spark40 = "4.0.1"` 修改为 `spark40 = "4.0.2"`。该变量被 `spark/v4.0` 模块下所有引用 Spark 的依赖坐标通过 `version.ref` 使用，单点修改即可让该模块的编译产物与测试绑定 Spark 4.0.2。本次属于语义版本的 PATCH 升级，预期影响为获得 4.0.2 的缺陷修复与稳定性提升，不涉及 API 破坏性变更，因此无需改动调用代码。其余 Spark 版本变量（`spark34`、`spark35`、`spark41`）保持不变。

## 总结

本提交将 Iceberg Spark 4.0 集成模块依赖的 Apache Spark 版本从 4.0.1 升级到 4.0.2，属于常规的依赖维护升级。通过版本目录单点修改即可同步整个 `spark/v4.0` 模块，确保 Iceberg 跟随 Spark 4.0 线的最新维护版本，获取缺陷修复与稳定性改进，同时不波及其它 Spark 版本的集成。
