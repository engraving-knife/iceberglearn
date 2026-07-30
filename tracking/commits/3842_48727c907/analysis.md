# 提交 3842：Parquet: Pre-size row-group filter maps to the column count (#16723)

## 提交信息

- **序号**：3842 / 4088
- **哈希**：48727c9072a017f582045c429cfe861bae0c0537
- **短哈希**：48727c907
- **日期**：2026-06-08 13:57:36 +0200
- **作者**：Vova Kolmakov
- **提交说明**：Parquet: Pre-size row-group filter maps to the column count (#16723)
- **PR/Issue**：#16723

## 总体目的

本提交是一个 Parquet 读取路径的性能优化。在 Iceberg 的 Parquet 读取流程中，行组过滤器（row-group filter）用于在读取 Parquet 行组（row group）之前根据统计信息、布隆过滤器、字典等决定是否可以跳过该行组。这些过滤器内部使用多个 HashMap 来缓存列级元数据。

在此之前，这些 HashMap 都是使用 `Maps.newHashMap()` 创建的，即默认初始容量（16）和负载因子（0.75）。当 Parquet 文件包含的列数与默认容量差异较大时（特别是列数较多的大宽表），HashMap 需要经历多次 rehash 扩容，造成不必要的 CPU 和内存开销。本提交通过预先设置 HashMap 的预期大小为行组的列数，避免 rehash 操作，提升过滤性能。

## 如何达成设计目的

在三个行组过滤器的 `eval` 方法中，将 `Maps.newHashMap()` 替换为 `Maps.newHashMapWithExpectedSize(columnCount)`，其中 `columnCount` 从行组元数据（`rowGroup.getColumns().size()`）或文件 schema（`fileSchema.getColumns().size()`）获取。`newHashMapWithExpectedSize` 会计算合适的初始容量，使得在放入预期数量的元素时不需要 rehash。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetBloomRowGroupFilter.java` (+7/-3 lines)

**修改目的**：预设置布隆过滤器行组过滤器的 Map 大小。

**工作逻辑**：
获取行组列数后，将 `columnMetaMap`、`parquetPrimitiveTypes`、`types` 三个 Map 预设大小：
```java
int columnCount = rowGroup.getColumns().size();
this.fieldsWithBloomFilter = Sets.newHashSet();
this.columnMetaMap = Maps.newHashMapWithExpectedSize(columnCount);
this.bloomCache = Maps.newHashMap();
this.parquetPrimitiveTypes = Maps.newHashMapWithExpectedSize(columnCount);
this.types = Maps.newHashMapWithExpectedSize(columnCount);
```
注意 `bloomCache` 未预设大小，因为它的大小取决于布隆过滤器列数而非全部列数。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetDictionaryRowGroupFilter.java` (+10/-4 lines)

**修改目的**：预设置字典行组过滤器的 Map 大小。

**工作逻辑**：
区分文件 schema 列数和行组列数，分别预设不同 Map：
```java
int fileColumnCount = fileSchema.getColumns().size();
int rowGroupColumnCount = rowGroup.getColumns().size();
this.dictCache = Maps.newHashMap();
this.isFallback = Maps.newHashMapWithExpectedSize(rowGroupColumnCount);
this.mayContainNulls = Maps.newHashMapWithExpectedSize(rowGroupColumnCount);
this.cols = Maps.newHashMapWithExpectedSize(fileColumnCount);
this.conversions = Maps.newHashMapWithExpectedSize(fileColumnCount);
```
`isFallback` 和 `mayContainNulls` 基于行组列数（按行组列遍历），而 `cols` 和 `conversions` 基于文件 schema 列数（按文件 schema 遍历）。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetMetricsRowGroupFilter.java` (+7/-3 lines)

**修改目的**：预设置指标行组过滤器的 Map 大小。

**工作逻辑**：
```java
int columnCount = rowGroup.getColumns().size();
this.stats = Maps.newHashMapWithExpectedSize(columnCount);
this.valueCounts = Maps.newHashMapWithExpectedSize(columnCount);
this.conversions = Maps.newHashMapWithExpectedSize(columnCount);
```

## 总结

这是一个典型的微优化，通过预设置 HashMap 容量避免 rehash，提升 Parquet 行组过滤的性能。在大宽表场景下效果尤为明显。修改简单直接、风险低，但属于查询规划热路径上的优化，对整体查询性能有正向贡献。
