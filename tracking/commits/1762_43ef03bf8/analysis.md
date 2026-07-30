# 提交 1762：Parquet: Remove deprecated VectorizedReader.setRowGroupInfo and ParquetValueReader.setPageSource (#12321)

## 提交信息

- **序号**：1762 / 4088
- **哈希**：43ef03bf88a3015a8efaa251b9bed35e47e01679
- **短哈希**：43ef03bf8
- **日期**：2025-02-20 08:19:36 +0100
- **作者**：Yuya Ebihara
- **提交说明**：Parquet: Remove deprecated VectorizedReader.setRowGroupInfo and ParquetValueReader.setPageSource (#12321)
- **PR/Issue**：#12321

## 总体目的

本提交旨在移除 Parquet 读取器中在 1.8.0 版本标记为废弃（deprecated）的 API 方法，为 1.9.0 版本做清理准备。Iceberg 项目遵循废弃 API 在下一个主要版本中移除的实践——在 1.8.0 中标记为 `@Deprecated` 的方法将在 1.9.0 中被移除。

具体移除的废弃 API 包括：

1. **`VectorizedReader.setRowGroupInfo(PageReadStore, Map<ColumnPath, ColumnChunkMetaData>, long)`**：废弃的三参数版本，被双参数版本 `setRowGroupInfo(PageReadStore, Map)` 替代。三参数版本中的 `rowPosition` 参数在新版本中不再需要。

2. **`ParquetValueReader.setPageSource(PageReadStore, long)`**：废弃的双参数版本，被单参数版本 `setPageSource(PageReadStore)` 替代。

3. **`ParquetValueReaders.StructReader.StructReader(List<Type>, List<ParquetValueReader<?>>)`**：废弃的双参数构造器，被单参数构造器 `StructReader(List<ParquetValueReader<?>>)` 替代。types 参数在新版本中不再需要。

同时还将 `BaseParquetReaders` 和 `BaseParquetWriter` 从 `public abstract` 降级为包级别可见（package-private），这两个类在 1.8.0 中已标记废弃并计划在 1.9.0 中改为包私有。

## 如何达成设计目的

提交通过以下步骤达成目标：

1. **移除接口中的废弃方法声明**：从 `VectorizedReader` 接口移除三参数 `setRowGroupInfo` 方法声明，从 `ParquetValueReader` 接口移除双参数 `setPageSource` 默认方法。

2. **移除所有实现类中的废弃方法实现**：在 Arrow、Flink（v1.18/v1.19）、Spark（v3.3/v3.4/v3.5）等模块中，移除所有实现 `VectorizedReader` 接口的类中对三参数 `setRowGroupInfo` 的覆写实现。

3. **移除废弃构造器并更新调用方**：从 `ParquetValueReaders.StructReader` 移除双参数构造器，更新所有子类（如 Flink 的 `RowDataReader`、Spark 的 `InternalRowReader`）的构造器调用，移除不再需要的 `types` 参数。

4. **降低类可见性**：将 `BaseParquetReaders` 和 `BaseParquetWriter` 从 `public abstract class` 改为 `abstract class`（包私有）。

5. **更新 RevAPI 配置**：在 `.palantir/revapi.yml` 中记录这些 API 变更作为已接受的破坏性变更。

## 修改详情

### `.palantir/revapi.yml`（修改, +35/-0 lines）

**修改目的**：记录 1.8.0 版本中已接受的 API 破坏性变更。

**工作逻辑**：在 `acceptedBreaks` 配置的 `1.8.0` 版本下新增多条记录，包括：
- `BaseParquetReaders` 和 `BaseParquetWriter` 的可见性降低（`java.class.visibilityReduced`）
- 这两个类不再标记为 deprecated（`java.element.noLongerDeprecated`）
- `ParquetValueReader.setPageSource` 方法被移除（`java.method.removed`）
- `ParquetValueReaders.StructReader` 废弃构造器被移除（`java.method.removed`）
- `VectorizedReader.setRowGroupInfo` 三参数方法被移除（`java.method.removed`）
- `BaseParquetWriter` 构造器可见性降低（`java.method.visibilityReduced`）

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReader.java`（修改, +0/-9 lines）

**修改目的**：移除废弃的 `setPageSource(PageReadStore, long)` 默认方法。

**工作逻辑**：删除带有 `@Deprecated` 注解的双参数 `setPageSource` 方法，该方法仅调用单参数版本。保留单参数 `setPageSource(PageReadStore)` 方法。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReaders.java`（修改, +0/-8 lines）

**修改目的**：移除 `StructReader` 的废弃双参数构造器。

**工作逻辑**：删除 `protected StructReader(List<Type> types, List<ParquetValueReader<?>> readers)` 构造器，该构造器仅调用单参数版本 `this(readers)` 并忽略 types 参数。保留单参数构造器。

### `parquet/src/main/java/org/apache/iceberg/parquet/VectorizedReader.java`（修改, +0/-13 lines）

**修改目的**：移除接口中废弃的三参数 `setRowGroupInfo` 方法声明。

**工作逻辑**：删除带有 `@Deprecated` 注解的 `void setRowGroupInfo(PageReadStore pages, Map<ColumnPath, ColumnChunkMetaData> metadata, long rowPosition)` 方法声明。保留双参数版本。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetReaders.java`（修改, +1/-5 lines）

**修改目的**：将类从 `public abstract` 降级为包私有，移除 `@Deprecated` 注解。

**工作逻辑**：将 `@Deprecated public abstract class BaseParquetReaders<T>` 改为 `abstract class BaseParquetReaders<T>`（包私有，移除 `@Deprecated`）。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetWriter.java`（修改, +1/-5 lines）

**修改目的**：将类从 `public abstract` 降级为包私有，移除 `@Deprecated` 注解。

**工作逻辑**：同上，将 `@Deprecated public abstract class BaseParquetWriter<T>` 改为 `abstract class BaseParquetWriter<T>`。

### Arrow 模块（`BaseBatchReader.java`, `VectorizedArrowReader.java`）（修改, -30 lines）

**修改目的**：移除 Arrow 向量化读取器中对废弃三参数 `setRowGroupInfo` 方法的覆写实现。

**工作逻辑**：在 `BaseBatchReader` 和 `VectorizedArrowReader`（包括内部类）中移除三参数 `setRowGroupInfo` 的 `@Override` 实现，这些实现都是简单委托给双参数版本。

### Flink 模块 v1.18/v1.19（`FlinkParquetReaders.java`）（修改, 各-7/+6 lines）

**修改目的**：移除 `RowDataReader` 构造器中不再需要的 `types` 参数。

**工作逻辑**：在 `FlinkParquetReaders` 中移除 `List<Type> types` 的创建和填充逻辑，将 `RowDataReader` 构造器从 `RowDataReader(List<Type> types, List<ParquetValueReader<?>> readers)` 改为 `RowDataReader(List<ParquetValueReader<?>> readers)`，调用 `super(readers)` 而非 `super(types, readers)`。

### Spark 模块 v3.3/v3.4/v3.5（`SparkParquetReaders.java`, `ColumnarBatchReader.java`, `CometColumnReader.java`, `CometColumnarBatchReader.java`）（修改）

**修改目的**：移除 Spark 读取器中的废弃方法实现和构造器参数。

**工作逻辑**：
- 在 `SparkParquetReaders` 中移除 `types` 列表的使用，更新 `InternalRowReader` 构造器。
- 在 `ColumnarBatchReader` 中移除三参数 `setRowGroupInfo` 的覆写。
- 在 `CometColumnReader` 中移除三参数 `setRowGroupInfo` 的覆写（原本抛出 `UnsupportedOperationException`）和不再需要的 import。
- 在 `CometColumnarBatchReader` 中移除三参数 `setRowGroupInfo` 的覆写。

## 小结

- **成效**：移除了 Parquet 读取器中在 1.8.0 标记废弃的 API 方法（三参数 `setRowGroupInfo`、双参数 `setPageSource`、双参数 `StructReader` 构造器），并将 `BaseParquetReaders`/`BaseParquetWriter` 降级为包私有，为 1.9.0 版本清理了废弃代码。
- **影响范围**：涉及 Parquet 核心模块以及 Arrow、Flink（v1.18/v1.19）、Spark（v3.3/v3.4/v3.5）等多个集成模块的读取器实现。这是一个破坏性 API 变更，任何外部依赖这些废弃 API 的代码将无法编译。
- **回迁到 1.4.x 的注意事项**：不建议回迁到 1.4.x 分支。此提交移除的 API 是在 1.8.0 中才标记为废弃的，1.4.x 分支中这些 API 可能尚未标记废弃，直接移除会导致破坏性变更而未经废弃过渡期。如果 1.4.x 分支需要保持 API 稳定性，应保留这些方法。仅在 1.4.x 分支计划升级到 1.9.0 并已完成废弃周期的情况下才考虑回迁。
