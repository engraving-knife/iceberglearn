# 提交 4022：Parquet: add adaptive bloom filter sizing (PARQUET-2254) (#16363)

## 提交信息

- **序号**：4022 / 4088
- **哈希**：304d6ffe21afc18b11eb271193e9bdb791755d5e
- **短哈希**：304d6ffe2
- **日期**：2026-07-13 13:47:28 +0200
- **作者**：Raghvendra Singh
- **提交说明**：Parquet: add adaptive bloom filter sizing (PARQUET-2254) (#16363)
- **PR/Issue**：#16363（关联 PARQUET-2254）

## 总体目的

本提交为 Iceberg 的 Parquet 写入器添加自适应 Bloom Filter 大小（adaptive bloom filter sizing）支持，对应上游 Parquet 项目的 PARQUET-2254 改进。

传统的 Parquet Bloom Filter 大小是固定的（由 FPP 和 NDV 估算决定），但当列的基数（NDV）估算不准或数据分布不均时，固定大小可能导致 Bloom Filter 过大（浪费空间）或过小（FPP 升高）。自适应大小允许 Parquet writer 根据实际数据动态调整 Bloom Filter 大小，在空间和误报率之间取得更好平衡。

本提交通过新增表属性 `write.parquet.bloom-filter-adaptive-enabled`（默认 false）来开启此功能，并将配置传递给 Parquet writer 的 `withAdaptiveBloomFilterEnabled`。

## 如何达成设计目的

1. 在 `TableProperties` 新增 `PARQUET_BLOOM_FILTER_ADAPTIVE_ENABLED` 属性键和默认值（false）。
2. 在 `Parquet.WriteBuilder.build()` 中，从配置解析该属性，通过 `WriteContext` 传递。
3. 在构造 Parquet `ParquetFileWriter` 时调用 `.withAdaptiveBloomFilterEnabled(context.adaptiveBloomFilterEnabled())`。
4. `WriteContext` 新增 `adaptiveBloomFilterEnabled` 字段、构造参数、getter，并在 `create(Context)` 中传递。
5. 补充文档和测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (+4/-0 lines)

**修改目的**：定义自适应 Bloom Filter 表属性。

**工作逻辑**：
```java
public static final String PARQUET_BLOOM_FILTER_ADAPTIVE_ENABLED =
    "write.parquet.bloom-filter-adaptive-enabled";
public static final boolean PARQUET_BLOOM_FILTER_ADAPTIVE_ENABLED_DEFAULT = false;
```
默认关闭，保持向后兼容。

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java` (+19/-1 lines)

**修改目的**：解析配置并传递给 Parquet writer。

**工作逻辑**：
- `build()` 中构造 writer 时新增：
  ```java
  .withMaxBloomFilterBytes(bloomFilterMaxBytes)
  .withAdaptiveBloomFilterEnabled(context.adaptiveBloomFilterEnabled());
  ```
- `WriteContext` 新增 `adaptiveBloomFilterEnabled` 字段、构造参数、getter。
- 从 config 解析：
  ```java
  boolean adaptiveBloomFilterEnabled = PropertyUtil.propertyAsBoolean(
      config, PARQUET_BLOOM_FILTER_ADAPTIVE_ENABLED, PARQUET_BLOOM_FILTER_ADAPTIVE_ENABLED_DEFAULT);
  ```
- `create(Context)` 工厂方法同步传递该字段，默认构造路径使用 `PARQUET_BLOOM_FILTER_ADAPTIVE_ENABLED_DEFAULT`。

### `docs/docs/configuration.md` (+1/-0 lines)

**修改目的**：文档中补充该配置项说明。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquet.java` (+49/-0 lines)

**修改目的**：新增测试验证自适应 Bloom Filter 配置的解析和传递。

## 总结

本提交为 Iceberg Parquet 写入器接入上游 Parquet 的自适应 Bloom Filter 大小功能（PARQUET-2254），通过新增表属性 `write.parquet.bloom-filter-adaptive-enabled`（默认关闭）控制开关。开启后 Parquet writer 会根据实际数据动态调整 Bloom Filter 大小，优化空间占用与误报率平衡。改动是配置透传性质，核心逻辑由 Parquet 库实现，配套补齐了文档和测试。
