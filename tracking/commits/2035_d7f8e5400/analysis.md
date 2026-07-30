# 提交 2035：Flink: Remove Flink 1.18 support

## 提交信息

- **序号**：2035 / 4088
- **哈希**：d7f8e5400519f84cd2af82d7769713b24e916809
- **短哈希**：d7f8e5400
- **日期**：2025-04-23 13:21:24 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Remove Flink 1.18 support
- **PR/Issue**：无（属于 Flink 2.0 支持系列的一部分）

## 总体目的

本提交是 Flink 2.0 支持重构系列（提交 2032-2035）的最后一步。在 Flink 2.0 被添加支持后（提交 2034），Iceberg 项目支持的 Flink 版本变为 1.18、1.19、1.20 和 2.0 共四个版本。维护过多版本的 Flink 适配代码会带来显著的维护负担。

Flink 1.18 发布于 2023 年 11 月，按照 Flink 社区的版本维护策略已接近生命周期终点。本提交移除了对 Flink 1.18 的支持，使项目维护的 Flink 版本缩减为 1.19、1.20 和 2.0 三个版本，降低维护成本并使 CI 资源可以分配给更新版本的测试。

## 如何达成设计目的

整体移除策略分三个层面：

1. **删除源码目录**：完整删除 `flink/v1.18/` 目录及其下全部 334 个文件（约 58802 行），包括 build.gradle、主源码、测试代码、SPI 注册文件、LICENSE/NOTICE 等。

2. **更新构建配置**：
   - `flink/build.gradle`：移除对 v1.18 目录的 apply from 引用
   - `settings.gradle`：移除 iceberg-flink-1.18 和 iceberg-flink-runtime-1.18 子项目注册
   - `jmh.gradle`：移除 v1.18 的 JMH 基准测试项目
   - `gradle.properties`：从 `knownFlinkVersions` 中移除 `1.18`
   - `gradle/libs.versions.toml`：移除 flink118 版本定义和所有 flink118-* 依赖坐标

3. **保持 CI 和发布脚本不变**：因为提交 2034 已经将 CI 矩阵中的 1.18 替换为 2.0，本提交无需再修改 CI 配置。

## 修改详情

### `flink/v1.18/` (删除, -58802 lines)

**修改目的**：完整移除 Flink 1.18 的适配代码。

**工作逻辑**：
删除 `flink/v1.18/` 下的 334 个文件，涵盖完整 Flink 集成模块：build.gradle、flink-runtime/、flink/src/main/（Catalog、Sink、Source、Data、Maintenance、Util 等）、flink/src/test/（所有测试类）、flink/src/main/resources/（SPI 注册文件）。

### `flink/build.gradle` (修改, -4 lines)

**修改目的**：移除 v1.18 构建脚本引用。

**工作逻辑**：
删除 `if (flinkVersions.contains("1.18")) { apply from: file("$projectDir/v1.18/build.gradle") }` 条件块。

### `settings.gradle` (修改, -9 lines)

**修改目的**：移除 Flink 1.18 子项目注册。

**工作逻辑**：
删除 `iceberg-flink:flink-1.18` 和 `iceberg-flink:flink-runtime-1.18` 的 include 和项目配置。

### `gradle.properties` (修改, +1/-1 lines)

**修改目的**：从已知 Flink 版本列表中移除 1.18。

**工作逻辑**：
`knownFlinkVersions` 从 `1.18,1.19,1.20,2.0` 改为 `1.19,1.20,2.0`。

### `gradle/libs.versions.toml` (修改, -12 lines)

**修改目的**：移除 Flink 1.18 的版本和依赖定义。

**工作逻辑**：
删除 `flink118 = { strictly = "1.18.1" }` 版本定义，以及 flink118-avro、flink118-connector-base、flink118-connector-files、flink118-metrics-dropwizard、flink118-streaming-java、flink118-table-api-java-bridge（编译依赖）和 flink118-connector-test-utils、flink118-core、flink118-runtime、flink118-test-utils、flink118-test-utilsjunit（测试依赖）共 11 个依赖坐标。

### `jmh.gradle` (修改, -4 lines)

**修改目的**：移除 v1.18 的 JMH 基准测试项目。

**工作逻辑**：
删除 `if (flinkVersions.contains("1.18")) { jmhProjects.add(project(":iceberg-flink:iceberg-flink-1.18")) }` 条件块。

## 总结

本提交是 Flink 2.0 支持系列的收尾步骤，通过删除 `flink/v1.18/` 目录（334 个文件、58802 行）和清理所有相关的构建配置，正式移除了对 Flink 1.18 的支持。至此，Iceberg 项目支持的 Flink 版本从 1.18/1.19/1.20 升级为 1.19/1.20/2.0。整个系列（提交 2032-2035）通过"移动-复制-适配-清理"的策略完成了 Flink 版本的升级与淘汰。
