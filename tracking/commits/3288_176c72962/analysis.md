# 提交 3288：Spark 4.1: Rename expectedSchema to projection for clarity (#15366)

## 提交信息

- **序号**：3288 / 4088
- **哈希**：176c729623fd370f777d78330f404bce1494a631
- **短哈希**：176c72962
- **日期**：2026-02-19
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 4.1: Rename expectedSchema to projection for clarity (#15366)
- **PR/Issue**：#15366

## 总体目的

在 Spark 4.1 模块的扫描（scan）与读取（reader）体系中，长期存在一个语义不够准确的命名：`expectedSchema`。该字段实际表示的是经过列裁剪（column pruning）后、即将由底层 Iceberg `Scan.project(Schema)` 投影下去的"投影 schema"——即用户最终要读的列集合，而非"期望校验"的 schema。`expectedSchema` 这个名字容易让人误以为它是某种契约校验或反序列化时的期望格式，与其实际承担的"投影下推"职责不符。

本提交的目标是把这个在 `SparkScan`、`SparkScanBuilder`、`SparkBatch`、`SparkInputPartition`、各 reader 等十多个类之间传递的核心字段统一更名为 `projection`，使命名与其语义、以及与 Iceberg Core 的 `Scan.project()` / `TableScan#projection()` API 保持一致，提升代码可读性与可维护性。这是一次纯粹的机械式重命名重构，不改变任何运行时行为，属于 Spark 4.1 适配工作链中的代码清理步骤（后续多个 Spark 4.1 提交也是在此基线上继续演进）。

之所以针对 Spark 4.1 单独做，是因为 Iceberg 为不同 Spark 大版本维护独立的源码目录（如 `spark/v4.1/spark/`），各版本的扫描实现会随 Spark API 变化而分叉，重命名需要在该版本目录内自洽完成，避免波及其他版本。

## 如何达成设计目的

整体思路是把 `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/` 下所有涉及 `expectedSchema` 字段、参数、访问方法（`expectedSchema()`）的地方，统一替换为 `projection` / `projection()`，并连带更新序列化用的字符串字段名（`expectedSchemaString` → `projectionString`）。涉及 13 个文件，均为字段、形参、局部变量与方法名的同义替换，调用链与数据流保持不变。由于 `SparkInputPartition` 是可序列化并广播到 executor 的对象，其内部用 `SchemaParser.toJson` 序列化 schema、用 `transient Schema` 字段做懒反序列化缓存的机制也一并保留，仅改名。

## 修改详情

### `core` 无关，仅限 `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/`

### `BatchDataReader.java` (+1/-1 lines)

**修改目的**：适配 partition 访问方法改名。

**工作逻辑**：构造器中 `partition.expectedSchema()` 改为 `partition.projection()`，将投影 schema 传给父类 `BaseBatchReader`，语义不变。

### `ChangelogRowReader.java` (+1/-1 lines)

**修改目的**：适配 partition 访问方法改名。

**工作逻辑**：构造器中 `partition.expectedSchema()` 改为 `partition.projection()`。

### `PositionDeletesRowReader.java` (+1/-1 lines)

**修改目的**：适配 partition 访问方法改名。

**工作逻辑**：构造器中 `partition.expectedSchema()` 改为 `partition.projection()`。

### `RowDataReader.java` (+1/-1 lines)

**修改目的**：适配 partition 访问方法改名。

**工作逻辑**：构造器中 `partition.expectedSchema()` 改为 `partition.projection()`。

### `SparkBatch.java` (+7/-7 lines)

**修改目的**：把 Batch 层持有的投影 schema 字段改名。

**工作逻辑**：字段 `expectedSchema` → `projection`，构造器形参同步改名。在 `createPartitionFactory`/分区构建处把 `SchemaParser.toJson(expectedSchema)` 改为 `SchemaParser.toJson(projection)`，对应变量名 `expectedSchemaString` → `projectionString`。在判断是否走 Parquet/Comet 向量化批读的 `useParquetBatchReads()`/`useCometBatchReads()` 中，`expectedSchema.columns()` 改为 `projection.columns()`，用于校验所有投影列都支持批读。

### `SparkBatchQueryScan.java` (+5/-5 lines)

**修改目的**：把查询扫描中投影 schema 的使用与传递改名。

**工作逻辑**：构造器形参 `expectedSchema` → `projection` 并透传给父类 `SparkPartitioningAwareScan`。在 `pruneColumnStats` 等逻辑中，`expectedSchema()` 调用改为 `projection()`：包括 `SparkSchemaUtil.indexQuotedNameById(projection())`、`projection().findField(fieldId) != null`（过滤出投影中存在的分区字段源 ID）、以及运行时过滤绑定时 `Binder.bind(projection().asStruct(), expr, caseSensitive())`。一处日志文案 "expected schema" 保留原文未改（属诊断信息，不影响逻辑）。

### `SparkChangelogScan.java` (+6/-6 lines)

**修改目的**：把增量 changelog 扫描的投影字段改名。

**工作逻辑**：字段 `expectedSchema` → `projection`，构造器形参改名。`validateMetadataColumnReferences(table.schema(), projection)` 校验元数据列引用；`readSchema()` 中 `SparkSchemaUtil.convert(projection)` 转换为 Spark `StructType`；`toBatch()` 中把 `projection` 传入 `SparkBatch`。

### `SparkCopyOnWriteScan.java` (+4/-4 lines)

**修改目的**：把 COW 扫描构造器形参改名。

**工作逻辑**：两个构造器重载的形参 `expectedSchema` → `projection`，并透传给父类 `SparkPartitioningAwareScan`。

### `SparkInputPartition.java` (+8/-8 lines)

**修改目的**：把可序列化分区中持有的投影 schema（含序列化字符串与懒加载缓存）改名。

**工作逻辑**：字符串字段 `expectedSchemaString` → `projectionString`（经广播序列化传到 executor），构造器形参同步改名；懒加载的 `transient Schema expectedSchema` → `transient Schema projection`，访问方法 `expectedSchema()` → `projection()`，内部 `SchemaParser.fromJson(projectionString)` 反序列化逻辑不变。这是改名中唯一涉及序列化与懒加载的字段，机制完全保留。

### `SparkMicroBatchStream.java` (+4/-4 lines)

**修改目的**：把微批流的投影 schema 字段改名。

**工作逻辑**：字段 `expectedSchema`（保存为 JSON 字符串）→ `projection`，构造器形参 `Schema expectedSchema` → `Schema projection`，`SchemaParser.toJson(expectedSchema)` → `SchemaParser.toJson(projection)`；构建 `SparkInputPartition` 时传入 `projection`。

### `SparkPartitioningAwareScan.java` (+3/-3 lines)

**修改目的**：把分区感知扫描基类构造器形参与分组键计算改名。

**工作逻辑**：构造器形参 `expectedSchema` → `projection` 并透传父类 `SparkScan`；`computeGroupingKeyType()` 中 `Partitioning.groupingKeyType(expectedSchema(), specs())` → `projection()`，用于计算数据分组键类型。

### `SparkScan.java` (+10/-9 lines)

**修改目的**：把所有扫描的基类中持有的核心投影字段及其访问方法改名。

**工作逻辑**：字段 `expectedSchema` → `projection`，构造器形参改名，并在构造时 `SparkSchemaUtil.validateMetadataColumnReferences(snapshotSchema, projection)` 校验元数据列引用。访问方法 `protected Schema expectedSchema()` → `protected Schema projection()`，供子类获取投影。`toBatch()` 传入 `projection`、`toMicroBatchStream()` 传入 `projection`、`readSchema()` 中 `SparkSchemaUtil.convert(projection)` 转换。这是整个改名的中枢，子类的 `expectedSchema()` 调用统一改为 `projection()`。

### `SparkScanBuilder.java` (+19/-19 lines)

**修改目的**：把扫描构建器中构建各扫描类型时的局部变量与传参改名。

**工作逻辑**：在 `buildBatchScan()`、`buildIcebergBatchScan()`、`buildBatchScan(snapshotId,...)`、`buildIncrementalAppendScan()`、`buildChangelogScan()`、`buildCopyOnWriteScan()` 等多个方法中，局部变量 `Schema expectedSchema = projectionWithMetadataColumns()` 改名为 `projection`，并相应在调用 `newBatchScan().project(projection)`、`newIncrementalAppendScan().project(projection)`、`newIncrementalChangelogScan().project(projection)` 以及构造 `SparkBatchQueryScan`/`SparkChangelogScan`/`SparkCopyOnWriteScan` 时传 `projection`。这里变量名与 Iceberg `Scan.project()` API 名称对齐，是本次重命名语义收益最明显处。

## 总结

本提交是 Spark 4.1 模块的一次纯命名清理，将贯穿扫描/读取体系的 `expectedSchema` 统一更名为 `projection`，使字段名准确表达"投影下推"语义并与 Iceberg Core 的 `project()` API 对齐，无行为变化，为后续 Spark 4.1 适配工作提供更清晰的代码基线。
