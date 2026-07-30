# 提交 1916：Build: Bump parquet from 1.15.0 to 1.15.1 (#12616)

## 提交信息

- **序号**：1916 / 4088
- **哈希**：6f0dfd976eb88ac4f2c8f4ab9855b88e245f969f
- **短哈希**：6f0dfd976
- **日期**：2025-03-24 19:35:27 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump parquet from 1.15.0 to 1.15.1 (#12616)
- **PR/Issue**：#12616

## 总体目的

dependabot 自动把 Apache Parquet 依赖从 1.15.0 升级到 1.15.1（semver patch 补丁版本升级）。该版本同时影响 `parquet-avro`、`parquet-column`、`parquet-hadoop` 等多个 parquet 子模块（它们共享同一版本号）。Patch 升级通常包含缺陷修复，API 兼容。

除版本目录外，还同步更新了打包分发包 LICENSE 文件中登记的 parquet 各子模块版本号，以及 open-api bundle 中 jetty 依赖版本（jetty 11.0.24 → 11.0.25，属于传递依赖随此次构建更新）。

## 如何达成设计目的

1. 在 `gradle/libs.versions.toml` 中把 `parquet = "1.15.0"` 改为 `"1.15.1"`，作为 parquet 版本单一来源。
2. 同步更新 `kafka-connect-runtime/hive/LICENSE`、`kafka-connect-runtime/main/LICENSE` 中登记的 `parquet-avro/parquet-column/parquet-common/parquet-encoding/parquet-format-structures/parquet-hadoop/parquet-jackson` 版本号到 1.15.1。
3. 同步更新 `open-api/LICENSE` 中 jetty 相关条目从 11.0.24 到 11.0.25（传递依赖更新）。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 parquet 版本声明。

**工作逻辑**：

```toml
-parquet = "1.15.0"
+parquet = "1.15.1"
```

### `kafka-connect/kafka-connect-runtime/hive/LICENSE` (修改, +7/-7 lines)

**修改目的**：同步 hive runtime bundle 中 parquet 各子模块版本号到 1.15.1。

### `kafka-connect/kafka-connect-runtime/main/LICENSE` (修改, +7/-7 lines)

**修改目的**：同步 main runtime bundle 中 parquet 各子模块版本号到 1.15.1。

### `open-api/LICENSE` (修改, +6/-6 lines)

**修改目的**：同步 open-api bundle 中 jetty 传递依赖版本号到 11.0.25。

## 总结

dependabot 自动把 Apache Parquet 从 1.15.0 升级到 1.15.1，并同步更新各分发包 LICENSE 中 parquet 子模块与 jetty 传递依赖的版本登记，无业务代码改动。
