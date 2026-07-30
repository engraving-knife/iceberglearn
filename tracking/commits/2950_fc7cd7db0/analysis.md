# 提交 2950：Spark 3.4,3.5: Add LIMIT pushdown to Scan (#14741)

## 提交信息

- **序号**：2950 / 4088
- **哈希**：fc7cd7db00132b32cfd75f609d98916d83032209
- **短哈希**：fc7cd7db0
- **日期**：2025-12-03
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.4,3.5: Add LIMIT pushdown to Scan (#14741)
- **PR/Issue**：#14741

## 总体目的

这是上一个提交 #14615（序号 2949）的 Spark 侧 backport。#14615 在 `api`/`core` 模块引入了 `Scan.minRowsRequested` 与 `TableScanContext.minRowsRequested`，并在 Spark 4.0 的 `SparkScanBuilder` 实现了 `SupportsPushDownLimit`。由于 `api`/`core` 模块是 Spark 各版本共享的，2949 的核心改动已经对所有 Spark 版本生效；但 Spark 3.4 与 3.5 各自有独立的 `spark/v3.4`、`spark/v3.5` 源码树，`SparkScanBuilder` 是版本特定的类，必须分别改动才能让这两个 Spark 版本也支持 `LIMIT` 下推。

本提交把 #14615 中 Spark 4.0 的 `SparkScanBuilder` 改动原样应用到 Spark 3.4 与 3.5 两个分支：让 `SparkScanBuilder` 实现 `SupportsPushDownLimit`，新增 `limit` 字段与 `pushLimit` 方法，并在构建 scan 时调用 `minRowsRequested`。同时把对应的 `TestFilteredScan` 与 `TestSelect` 测试也同步过来，保证 3.4/3.5 行为与 4.0 一致。

## 如何达成设计目的

由于 `api`/`core` 层的 `minRowsRequested` 已在 2949 落地并被 3.4/3.5 共享，本提交只需在每个 Spark 版本的 `SparkScanBuilder` 上加 `SupportsPushDownLimit` 实现与 `limit` 透传逻辑，并复制两份测试。改动是 2949 Spark 部分的机械复制，无新设计。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+15/-1 lines)

**修改目的**：让 Spark 3.4 的 `SparkScanBuilder` 支持 `LIMIT` 下推。

**工作逻辑**：
与 2949 中 Spark 4.0 的改动逐字相同：导入 `SupportsPushDownLimit`，类签名追加 `implements ..., SupportsPushDownLimit`，新增 `private Integer limit = null`，在 `buildBatchScan` 末尾追加 `if (null != limit) { configuredScan = configuredScan.minRowsRequested(limit.longValue()); }`，并实现 `pushLimit(int pushedLimit)` 把 limit 存入字段并返回 `true`。返回 true 表示接受下推，但 Iceberg 把它当作 hint，Spark 仍会做最终 limit 以保证精确。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+15/-1 lines)

**修改目的**：让 Spark 3.5 的 `SparkScanBuilder` 支持 `LIMIT` 下推。

**工作逻辑**：与 v3.4 改动完全一致（diff 内容字节级相同），只是落在 `spark/v3.5` 源码树。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestFilteredScan.java` (+94/-0 lines)

**修改目的**：验证 Spark 3.4 下 `pushLimit` 后各 scan 的 context 带 `minRowsRequested`。

**工作逻辑**：
与 2949 中 Spark 4.0 的 `TestFilteredScan` 改动一致：新增 `limitPushedDownToSparkScan` 与 `limitPushedDownToSparkScanForMetadataTable` 两个测试，用 `assumeThat(fileFormat).isEqualTo(PARQUET)` 缩小矩阵，调 `pushLimit(23)` 后对 batch/changelog/CoW/MoR scan 以及 `#snapshots` 元数据表 scan 用 `extracting("scan").extracting("context").extracting("minRowsRequested")` 断言值为 23，并对 LOCAL planning mode 多解包一层。需注意 v3.4 的测试文件原本就有 `assumeThat`/`AbstractObjectAssert` 之外的不同 import 集合，本次同步补上了这两个 import。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestFilteredScan.java` (+94/-0 lines)

**修改目的**：在 Spark 3.5 上同步 limit 下推的 scan 上下文测试。

**工作逻辑**：与 v3.4 测试改动一致，落在 `spark/v3.5` 源码树。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+13/-0 lines)

**修改目的**：端到端验证 Spark 3.4 `SELECT ... LIMIT n` 结果正确。

**工作逻辑**：新增 `selectWithLimit`，插入三行后分别 `LIMIT 1/2/3` 断言返回前 N 行，与 2949 的 Spark 4.0 版本一致，注释强调即便 `isPartiallyPushed` 被覆写，Spark 也会保证最终 limit 正确。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+13/-0 lines)

**修改目的**：在 Spark 3.5 上同步端到端 limit 测试。

**工作逻辑**：与 v3.4 改动一致。

## 总结

本提交把 #14615 的 Spark 侧 `SupportsPushDownLimit` 实现与配套测试从 Spark 4.0 backport 到 Spark 3.4 与 3.5，使三个 Spark 版本在 `LIMIT` 下推能力上对齐。由于 `api`/`core` 层的 `minRowsRequested` 已被各版本共享，本次只改版本特定的 `SparkScanBuilder` 与测试，是一次直接的跨版本同步 backport。
