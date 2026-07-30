# 提交 2660：Flink: Remove support for Flink 1.19

## 提交信息

- **序号**：2660 / 4088
- **哈希**：487ea1ae9a449324fcadde900f9e2c36d1bbe5cf
- **短哈希**：487ea1ae9
- **日期**：2025-09-19 07:36:48 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Remove support for Flink 1.19
- **PR/Issue**：无（此提交是 Flink 2.1 迁移系列的一部分）

## 总体目的

本提交是 Flink 2.1 版本支持迁移系列的最后一步，移除了对 Flink 1.19 版本的支持。这是 Flink 版本支持生命周期管理的一部分——随着 Flink 2.x 系列的成熟，旧的 1.19 版本已到达生命周期终点（End of Life），不再需要维护。

Iceberg 的 Flink 集成采用多版本并行支持架构，每个版本都有独立的源码目录和构建配置。维护旧版本意味着持续的代码维护、CI 资源消耗和兼容性测试成本。移除 Flink 1.19 后，支持的 Flink 版本缩减为 1.20、2.0 和 2.1（2.1 是本次迁移系列新添加的）。

本提交与 2655-2658 构成完整的 Flink 版本迁移系列：添加 2.1 支持（2655-2658）的同时移除 1.19 支持（2659），保持了支持版本数量的平衡。

## 如何达成设计目的

通过以下方式完整移除 Flink 1.19 支持：

1. **删除源码目录**：删除整个 `flink/v1.19/` 目录（包含所有 Java 源码、测试、构建文件等）
2. **移除构建配置**：从 `flink/build.gradle`、`settings.gradle` 中移除 v1.19 的条件块
3. **清理依赖定义**：从 `gradle/libs.versions.toml` 中移除所有 `flink119` 相关的版本和依赖定义
4. **更新版本列表**：从 `gradle.properties` 的 `knownFlinkVersions` 中移除 1.19
5. **更新 CI 配置**：从 `.github/workflows/flink-ci.yml` 的测试矩阵中移除 1.19，同时添加 2.1

## 修改详情

### `.github/workflows/flink-ci.yml` (+1/-1 lines)

**修改目的**：更新 CI 测试矩阵中的 Flink 版本列表。

**工作逻辑**：将测试矩阵中的 Flink 版本从 `['1.19', '1.20', '2.0']` 改为 `['1.20', '2.0', '2.1']`，移除 1.19 并添加 2.1。

### `flink/build.gradle` (+0/-4 lines)

**修改目的**：移除 v1.19 的构建入口。

**工作逻辑**：删除 `if (flinkVersions.contains("1.19"))` 条件块及其内部的 `apply from` 语句。

### `flink/v1.19/` (整个目录删除)

**修改目的**：删除 Flink 1.19 的全部源码。

**工作逻辑**：整个 `flink/v1.19/` 目录被删除，包括 build.gradle、flink-runtime 的 LICENSE/NOTICE、所有 Flink-Iceberg 集成的 Java 源码和测试代码。

### `gradle/libs.versions.toml` (+0/-12 lines)

**修改目的**：移除 Flink 1.19 的版本和依赖定义。

**工作逻辑**：删除 `flink119 = { strictly = "1.19.2"}` 版本定义，以及所有 11 个 `flink119-*` 依赖库定义（avro、connector-base、connector-files、metrics-dropwizard、streaming-java、table-api-java-bridge、connector-test-utils、core、runtime、test-utils、test-utilsjunit）。

### `gradle.properties` (+1/-1 lines)

**修改目的**：从已知 Flink 版本列表中移除 1.19。

**工作逻辑**：将 `knownFlinkVersions` 从 `1.19,1.20,2.0,2.1` 改为 `1.20,2.0,2.1`。

### `settings.gradle` (+0/-9 lines)

**修改目的**：移除 v1.19 的 Gradle 子项目注册。

**工作逻辑**：删除 `if (flinkVersions.contains("1.19"))` 条件块及其内部的 include 和 project 配置。

## 总结

本提交完整移除了对 Flink 1.19 版本的支持，包括删除整个 v1.19 源码目录、清理构建配置和依赖定义、更新 CI 矩阵。这是 Flink 版本迁移系列的收尾工作——在添加 Flink 2.1 支持的同时移除到达生命周期终点的 1.19 版本，使支持的 Flink 版本保持为 1.20、2.0 和 2.1 三个版本，减少了维护负担。
