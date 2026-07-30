# 提交 2019：Spark: Add _row_id and _last_updated_sequence_number readers (#12836)

## 提交信息

- **序号**：2019 / 4088
- **哈希**：ab92d6e66b61195a8e9845d3eb592f3e67ae67e1
- **短哈希**：ab92d6e66
- **日期**：2025-04-21 11:37:54 -0600
- **作者**：Ryan Blue
- **提交说明**：Spark: Add _row_id and _last_updated_sequence_number readers (#12836)
- **PR/Issue**：#12836

## 总体目的

这个提交为 Spark 和 Core 的 Parquet 读取器添加了对行血统元数据列 `_row_id` 和 `_last_updated_sequence_number` 的读取支持，使查询引擎能够在读取数据时为每一行提供行 ID 和最后更新的序列号。

行血统是 Iceberg V3 格式引入的特性，每行数据有两个元数据字段：
- `_row_id`：行的全局唯一标识符，由数据文件的 `first_row_id` 加上行在文件内的位置（`_pos`）计算得出。如果数据文件中已存储了 `_row_id` 值（行被移动时保留），则直接使用存储的值。
- `_last_updated_sequence_number`：行最后更新时的序列号。如果数据文件中已存储了该值，则直接使用；否则使用文件自身的序列号。

本提交实现了这两个元数据列在读取时的计算逻辑，是行血统特性端到端可用的关键一环（配合提交 2014 的 first-row-id 分配机制）。

## 如何达成设计目的

整体设计思路是通过 Parquet 读取器架构中的常量注入机制来支持行血统列：

1. **PartitionUtil 注入常量**：在任务准备阶段，将数据文件的 `firstRowId` 作为 `_row_id` 的基础值、`fileSequenceNumber` 作为 `_last_updated_sequence_number` 的基础值注入到 `idToConstant` 映射中。
2. **ParquetValueReaders 新增读取器**：新增 `RowIdReader`（读取文件中的 `_row_id`，若为 null 则用 `first_row_id + pos` 计算）和 `LastUpdatedSeqReader`（读取文件中的序列号，若为 null 则用文件序列号）。
3. **replaceWithMetadataReader 统一分发**：新增工厂方法替代原来分散在 `BaseParquetReaders` 和 `SparkParquetReaders` 中的 if-else 逻辑，统一处理元数据列（包括 `_row_id`、`_last_updated_sequence_number`、`_file`、`_pos`、`_deleted`）的读取器选择。
4. **ManifestReader 确保字段投影**：在读取清单时确保 `FIRST_ROW_ID` 字段被投影，以便 `PartitionUtil` 能获取到值。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestReader.java` (修改, +3/-0 lines)

**修改目的**：确保读取清单时投影 `FIRST_ROW_ID` 字段。

**工作逻辑**：
在 `entries()` 方法的投影构建中，新增检查：如果投影中不包含 `DataFile.FIRST_ROW_ID`，则自动添加。这与之前对 `RECORD_COUNT` 的处理方式一致，确保行 ID 分配所需的字段在读取时可用。

### `core/src/main/java/org/apache/iceberg/util/PartitionUtil.java` (修改, +11/-0 lines)

**修改目的**：将行血统基础值注入到任务常量映射中。

**工作逻辑**：
在 `fillFromProjectedSpec` 方法（或类似方法）中，新增两处注入：
1. 如果 `task.file().firstRowId()` 非 null，将其作为 `_row_id`（field ID = `MetadataColumns.ROW_ID.fieldId()`）的常量值注入。
2. 将 `task.file().fileSequenceNumber()` 作为 `_last_updated_sequence_number`（field ID = `MetadataColumns.LAST_UPDATED_SEQUENCE_NUMBER.fieldId()`）的常量值注入（始终注入，即使为 null）。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReaders.java` (修改, +134/-29 lines)

**修改目的**：新增行血统列的 Parquet 读取器和统一分发方法。

**工作逻辑**：

1. **`rowIds(Long baseRowId, ParquetValueReader<?> idReader)`**：工厂方法，如果 `baseRowId` 非 null，创建 `RowIdReader`；否则返回 `nulls()`（行血统未启用时）。

2. **`lastUpdated(Long baseRowId, Long fileLastUpdated, ParquetValueReader<?> seqReader)`**：工厂方法，如果两个参数都非 null，创建 `LastUpdatedSeqReader`；否则返回 `nulls()`。

3. **`replaceWithMetadataReader(int id, ParquetValueReader<?>, Map<Integer,?> idToConstant, int constantDL)`**：统一分发方法，根据字段 ID 选择合适的读取器：`ROW_ID` → `rowIds`，`LAST_UPDATED_SEQUENCE_NUMBER` → `lastUpdated`，其他常量 → `constant`，`ROW_POSITION` → `position`，`IS_DELETED` → `constant(false)`，默认 → 原始 reader。这替代了原来分散在 `BaseParquetReaders` 和 `SparkParquetReaders` 中的重复逻辑。

4. **`RowIdReader`**：内部类，组合 `idReader`（读取文件中存储的 `_row_id`）和 `posReader`（读取行位置）。`read()` 方法中：先读位置（保持位置计数器同步），再读文件中的 `_row_id`，如果非 null 则返回，否则返回 `firstRowId + pos`。

5. **`LastUpdatedSeqReader`**：内部类，组合 `seqReader`（读取文件中存储的序列号）。`read()` 方法中：先读行级序列号，如果非 null 则返回，否则返回 `fileLastUpdated`（文件序列号）。

6. **`ConstantReader` 重构**：将内部 `TripleIterator` 提取为独立的 `ConstantDLColumn` 内部类，支持通过 parentDl 构造，使常量列的正确定义级别处理更加清晰。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetReaders.java` (修改, +30/-45 lines)

**修改目的**：使用 `replaceWithMetadataReader` 统一元数据列处理，简化 struct 读取器。

**工作逻辑**：
1. `createStructReader` 签名移除了 `List<Type> types` 参数（不再需要）。
2. `struct` 方法中，将原来分散的 if-else 逻辑（处理常量、ROW_POSITION、IS_DELETED、普通字段、默认值、可选字段）替换为：先调用 `replaceWithMetadataReader` 获取 reader，再调用 `defaultReader` 处理默认值和可选字段。
3. 新增 `defaultReader` 私有方法处理字段缺失时的默认值逻辑。
4. 处理 `expected == null` 的情况（返回空 struct reader）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetReaders.java` (修改, +26/-34 lines)

**修改目的**：与 `BaseParquetReaders` 相同的重构，使用 `replaceWithMetadataReader`。

**工作逻辑**：
与 `BaseParquetReaders` 完全一致的重构模式：移除 `typesById` 和 `maxDefinitionLevelsById` 映射，使用 `replaceWithMetadataReader` 和 `defaultReader` 方法替代原来的 if-else 逻辑。`constantDefinitionLevel` 统一使用父级 max definition level。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java` (修改, +2/-3 lines)

**修改目的**：适配 `createStructReader` 签名变更。

### 其他文件修改

`GenericParquetReaders`、`InternalReader`、`DataTest`、`DataTestHelpers`、`TestGenericData`、`AvroDataTest`、`GenericsHelpers`、`TestSparkParquetReader`、`SparkTestHelperBase`、`TestRowLineageAssignment` 等文件同步更新以适配接口变更和新增测试用例。

## 总结

本提交为 Spark 和 Core 的 Parquet 读取器添加了 `_row_id` 和 `_last_updated_sequence_number` 元数据列的读取支持。核心机制是通过 `PartitionUtil` 注入 `firstRowId` 和 `fileSequenceNumber` 常量，然后由新增的 `RowIdReader`（优先使用文件中存储的值，否则用 first_row_id + pos 计算）和 `LastUpdatedSeqReader`（优先使用行级值，否则用文件序列号）在读取时计算每行的值。同时通过 `replaceWithMetadataReader` 方法统一了元数据列的读取器选择逻辑，消除了 `BaseParquetReaders` 和 `SparkParquetReaders` 中的重复代码。
