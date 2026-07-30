# 提交 3113：Bump to Parquet 1.17.0 (#14924)

## 提交信息

- **序号**：3113 / 4088
- **哈希**：046298f63bdfe3a6dc9d6cf6ed37223eda3ea6cc
- **短哈希**：046298f63
- **日期**：2026-01-14
- **作者**：Fokko Driesprong
- **提交说明**：Bump to Parquet 1.17.0 (#14924)
- **PR/Issue**：#14924

## 总体目的

本提交是一次纯依赖升级，将 Apache Parquet 从 1.16.0 升级到 1.17.0。Apache Parquet 是 Iceberg 最核心的列式存储格式库——Iceberg 表的数据文件最主要就是以 Parquet 格式写入和读取（此外也支持 ORC、Avro）。在 `gradle/libs.versions.toml` 中，Parquet 通过版本变量 `parquet` 统一管理，并被三个坐标引用：`parquet-avro`（Parquet 与 Avro schema 互转的桥接）、`parquet-column`（列式读写核心）、`parquet-hadoop`（与 Hadoop FileSystem 集成）。因此 Parquet 版本直接关系到 Iceberg 数据文件的读/写性能、正确性与兼容性，升级通常是为了获取上游的 bug 修复、性能改进与新特性。

从 1.16.0 到 1.17.0 属于语义化版本中的 minor 升级，按 Parquet 项目的兼容性承诺应保持 API/格式向后兼容，预期影响是正向的（修复缺陷、提升性能），不会破坏 Iceberg 现有功能。提交正文中的 "Remove staging" 表明在开发过程中曾临时使用 Parquet 1.17.0 的 staging 仓库（Apache 发布流程中的暂存仓库）进行验证，正式版发布后移除了对 staging 的依赖，仅保留版本号提升这一最终改动。

## 如何达成设计目的

改动极小：仅在 `gradle/libs.versions.toml` 中把 `parquet` 版本变量从 `"1.16.0"` 改为 `"1.17.0"`。由于 `parquet-avro`、`parquet-column`、`parquet-hadoop` 三个坐标都通过 `version.ref = "parquet"` 引用该变量，单点修改即可让所有 Parquet 相关模块同步升级，无需逐处改动。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Parquet 版本变量从 1.16.0 提升到 1.17.0。

**工作逻辑**：将 `parquet = "1.16.0"` 改为 `parquet = "1.17.0"`。该变量被同文件中 `parquet-avro`、`parquet-column`、`parquet-hadoop` 三个库坐标以 `version.ref = "parquet"` 引用，是 Iceberg 读写 Parquet 数据文件格式的底层依赖。升级为 minor 版本（1.16 → 1.17），按 Parquet 的兼容性约定保持 API 与文件格式兼容，预期带来上游的 bug 修复与性能改进，Iceberg 侧无需配套代码改动。

## 总结

本提交把 Iceberg 依赖的 Apache Parquet 从 1.16.0 升级到 1.17.0（minor 升级），通过单点修改 `libs.versions.toml` 的版本变量让 parquet-avro/column/hadoop 三个坐标同步升级。Parquet 是 Iceberg 最核心的列式存储格式依赖，此次升级用于获取上游的缺陷修复与性能改进，属于低风险的常规依赖维护，并移除了此前验证用的 staging 仓库引用。
