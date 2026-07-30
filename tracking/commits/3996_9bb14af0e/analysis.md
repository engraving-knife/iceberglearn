# 提交 3996：Parquet: Refactor ParquetMetrics to produce field stats (#17116)

## 提交信息

- **序号**：3996 / 4088
- **哈希**：9bb14af0e3018170ce3ca2d88a3f8d980f44dbc8
- **短哈希**：9bb14af0e
- **日期**：2026-07-08 08:55:56 -0700
- **作者**：Ryan Blue
- **提交说明**：Parquet: Refactor ParquetMetrics to produce field stats (#17116)
- **PR/Issue**：#17116

## 总体目的

本提交对 Iceberg 的 Parquet 指标（metrics）计算进行重构，目的是让 `ParquetMetrics` 类能够直接产生字段级别的统计信息（field stats），而不是只生成传统的 `Metrics` 对象（其中 lower/upper bounds 以 `ByteBuffer` 形式存储）。

之前 `ParquetMetrics` 在生成 `Metrics` 时，会通过 `Conversions.toByteBuffer` 将 bound 值转换为 `ByteBuffer`，导致后续如果需要原始类型的 bound 值（用于 `ContentStats`/`FieldStats`）必须再次从 `ByteBuffer` 反向转换回来。`MetricsUtil.fromMetrics` 就是用来做这种反向转换的，但这种方式低效且增加了维护成本。本次重构使得 `FieldMetrics` 直接保存原始类型的 bound 值，并暴露 `fieldMetrics(...)` 方法以供后续使用。

这是为 variant/geometry/geography 等新类型支持字段统计工作的前置清理，让 metrics 流水线更通用、类型安全。

## 如何达成设计目的

主要设计变更：
1. 将 `ParquetMetrics.metrics(...)` 拆分为两部分：`fieldMetrics(...)` 返回 `Iterable<FieldMetrics<?>>`（保留原始类型 bound），`metrics(...)` 在其基础上聚合为最终的 `Metrics` 对象，并通过 `Conversions.toByteBuffer` 转换 bound。
2. `MetricsVisitor` 的泛型从 `Iterable<FieldMetrics<ByteBuffer>>` 改为 `Iterable<FieldMetrics<?>>`，使其能携带任意类型的 bound 值。
3. variant 类型直接使用 `Variant.of(metadata, lowerBounds)` 作为 bound 值而非先转 `ByteBuffer`。
4. 删除 `MetricsUtil.fromMetrics` 及其辅助方法（不再需要从 `Metrics` 反向构造 `ContentStats`）。
5. 测试代码相应地从 `MetricsWithStats` 包装模式切换为直接对 `Metrics` 调用 `assertCounts`/`assertBounds`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetricsUtil.java` (+0/-60 lines)

**修改目的**：删除 `fromMetrics` 方法及其辅助方法 `mergeCountMetric`、`mergeBoundMetric`，这些方法用于从已转换好的 `Metrics` 反向构造 `ContentStats`，重构后已不需要。

**工作逻辑**：
原本 `fromMetrics` 通过遍历 `valueCounts`、`nullValueCounts`、`nanValueCounts`、`lowerBounds`、`upperBounds` 等 map，调用 `Conversions.fromByteBuffer` 把 `ByteBuffer` 转回原始类型来构造 `BaseFieldStats`。重构后 `ParquetMetrics` 直接产出原始类型 bound，所以这段反向转换逻辑被移除。同时移除了 `import java.util.function.BiFunction`。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetMetrics.java` (+96/-73 lines)

**修改目的**：将 metrics 生成过程重构为两步——先生成保留原始类型 bound 的 `FieldMetrics`，再聚合成 `Metrics`。

**工作逻辑**：
- 新增 `fieldMetrics(...)` 方法，构建 `columns` Multimap 并通过 `TypeWithSchemaVisitor.visit` + `MetricsVisitor` 返回 `Iterable<FieldMetrics<?>>`。
- 抽取出 `rowCount(ParquetMetadata)` 和 `columnSizes(...)` 两个私有方法，使原 `metrics(...)` 方法更清晰。
- `metrics(...)` 现在调用 `fieldMetrics(...)` 获取字段级 metrics，然后在循环中通过 `Conversions.toByteBuffer(metrics.originalType(), metrics.lowerBound())` 把原始 bound 转为 `ByteBuffer` 放入 `lowerBounds`/`upperBounds`。
- `MetricsVisitor` 泛型从 `FieldMetrics<ByteBuffer>` 改为 `FieldMetrics<?>`，相关方法签名同步更新。
- `metricsFromFieldMetrics` 改为接收 `FieldMetrics<T>` 而非 `fieldId`，返回类型也从 `FieldMetrics<ByteBuffer>` 改为 `FieldMetrics<T>`，bound 直接保留原始类型 `T`，不再提前转 `ByteBuffer`。
- `bounds` 方法同样改为返回 `FieldMetrics<T>`，直接传入原始类型的 `lowerBound`/`upperBound`。
- variant 的 primitive 方法中改用 `Variant.of(metadata, lowerBounds)` 作为 bound 值，而不是 `ParquetVariantUtil.toByteBuffer(...)`。

### `core/src/test/java/org/apache/iceberg/TestMetrics.java` (+173/-249 lines)

**修改目的**：适配重构——不再通过 `MetricsUtil.fromMetrics` 构造 `MetricsWithStats`，而是直接对 `Metrics` 调用断言方法。

**工作逻辑**：
所有测试用例中将 `MetricsWithStats metricsWithStats = new MetricsWithStats(metrics, MetricsUtil.fromMetrics(schema, metrics));` 删除，并把 `assertCounts(..., metricsWithStats)` / `assertBounds(..., metricsWithStats)` 改为传 `metrics`。这表明 `assertCounts`/`assertBounds` 的签名也已调整为接受 `Metrics`（在抽象基类或工具中实现）。测试逻辑本身不变，仅切换到新 API。

### `core/src/test/java/org/apache/iceberg/orc/TestOrcMetrics.java` (+4/-4 lines)

**修改目的**：同步适配测试 API 变更。

**工作逻辑**：少量断言从 `metricsWithStats` 切换为 `metrics`。

## 总结

本提交是一次结构性重构，将 Parquet metrics 计算从"先转 ByteBuffer 再反向解析"的模式改为"先保留原始类型 bound 再按需转换"，提升了类型安全性和代码可维护性，并为后续 variant、geometry、geography 等类型的字段统计支持铺平道路。删除了 `MetricsUtil.fromMetrics` 这一反向转换路径，简化了整体设计。测试同步迁移到新 API。
