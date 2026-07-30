# 提交 2072：Build: Bump parquet from 1.15.1 to 1.15.2

## 提交信息

- **序号**：2072 / 4088
- **哈希**：cb02daf32dc14002a38780724fdc8085b934a184
- **短哈希**：cb02daf32
- **日期**：2025-05-05 08:13:35 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump parquet from 1.15.1 to 1.15.2 (#12964)
- **PR/Issue**：#12964

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Apache Parquet 依赖从 1.15.1 升级到 1.15.2。这是一个 patch 版本升级，通常包含 Bug 修复和小改进。本次升级涉及三个 Parquet 子构件：`parquet-avro`、`parquet-column` 和 `parquet-hadoop`，它们均从 1.15.1 升级到 1.15.2。

Parquet 是 Iceberg 的核心依赖之一，用于读写 Parquet 格式的数据文件。保持 Parquet 依赖为最新 patch 版本有助于获得最新的 Bug 修复和稳定性改进。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `parquet` 版本号定义，将所有引用该版本变量的 Parquet 子构件统一升级。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：将 Parquet 版本号从 1.15.1 升级到 1.15.2。

**工作逻辑**：
将 `parquet = "1.15.1"` 修改为 `parquet = "1.15.2"`。该版本变量被 `parquet-avro`、`parquet-column`、`parquet-hadoop` 三个构件引用，因此一次修改即完成全部升级。

## 总结

本提交由 Dependabot 自动生成，将 Apache Parquet 依赖从 1.15.1 升级到 1.15.2（patch 版本升级），涉及 parquet-avro、parquet-column、parquet-hadoop 三个构件，仅修改版本目录文件中一行版本号定义。
