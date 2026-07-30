# 提交 0167：Spark 3.5: Support metadata columns in staged scan (#8872)

## 提交信息

- **序号**：0167 / 4088
- **哈希**：bfe1d03c75b4a8bd2c15c925f39cdec28c312489
- **短哈希**：bfe1d03c7
- **日期**：2023-11-15
- **作者**：zhen
- **提交说明**：Spark 3.5: Support metadata columns in staged scan (#8872)
- **PR/Issue**：#8872

## 总体目的

Iceberg 的 Spark 3.5 source 提供了 `SparkStagedScan` 这种“staged scan”模式：用户先用 `ScanTaskSetManager.stageTasks(...)` 把一组 `ScanTask` 暂存到某个 `fileSetID` 下，然后 Spark 通过 `read.format("iceberg").option(SCAN_TASK_SET_ID, ...)` 直接消费这组任务而不走正常的 manifest 规划。这种模式常用于分布式写入场景下需要直接读取已 staged 任务（例如写后校验、特殊 ETL 路径）。然而在改造前的实现中，`SparkStagedScan` 与 `SparkStagedScanBuilder` 写死了直接使用 `table.schema()` 作为返回 schema，且 `SparkStagedScanBuilder` 只实现 `ScanBuilder`、没有实现 `SupportsPushDownRequiredColumns`，因此 Spark 在做列裁剪投影时无法把“需要哪些列”下推到 staged scan，导致即使查询里 `select("*", "_pos")` 之类的元数据列（如 `_pos`、`_file`、`_spec_id` 等）也无法被读取到——staged scan 只能拿到表本身的数据列，缺乏对 Iceberg 元数据列（`MetadataColumns`）的支持。

这个提交的目的就是给 `SparkStagedScanBuilder` 增加 `SupportsPushDownRequiredColumns` 实现，接收 Spark 下推的 `requestedSchema`，从中拆分出“普通投影 schema”（用于裁剪 `schema`）和“元数据列名列表”（用于附加元数据列），并在 `build()` 时把两者用 `TypeUtil.join` 合并成最终的 expectedSchema 透传给 `SparkStagedScan`，从而让 staged scan 与普通 `SparkScan` 一样能返回 `_pos`、`_file` 等元数据列。这对 Spark 3.5 的功能完整性（让 staged scan 与原生 scan 行为对齐）和未来依赖元数据列的高级特性（如 MERGE/UPDATE 的行级定位）在 staged 路径上可用具有重要意义。

## 如何达成设计目的

整体设计思路：在 `SparkStagedScanBuilder` 中新增一个 `schema` 字段（初始化为 `table.schema()`）和一个 `metaColumns` 列表；通过实现 `SupportsPushDownRequiredColumns.pruneColumns(StructType requestedSchema)` 接收 Spark 下推的请求 schema——先用 `removeMetaColumns` 把元数据列从 requestedSchema 中过滤掉，得到纯数据投影，调用 `SparkSchemaUtil.prune` 裁剪 `schema`；然后从 requestedSchema 中挑出所有 `MetadataColumns.isMetadataColumn` 的字段名，存入 `metaColumns`。`build()` 时调用新方法 `schemaWithMetadataColumns()`：把 `metaColumns` 映射为 `MetadataColumns.metadataColumn(table, name)` 列表生成一个仅含元数据列的 `Schema`，再用 `TypeUtil.join(schema, meta)` 合并数据投影 schema 与元数据 schema，作为 expectedSchema 传给 `SparkStagedScan`。`SparkStagedScan` 构造相应改为接收 `expectedSchema` 而非写死 `table.schema()`，并把 `readSchema()` 纳入 `equals`/`hashCode` 以保证 Spark 缓存正确性。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkStagedScanBuilder.java`

**修改目的**：让 staged scan builder 接收 Spark 的列裁剪/投影下推并支持附加元数据列。

**工作逻辑**：
- 新增字段 `private final List<String> metaColumns = Lists.newArrayList();` 与 `private Schema schema = null;`，构造器里 `this.schema = table.schema();` 初始化。
- 类声明由 `implements ScanBuilder` 改为 `implements ScanBuilder, SupportsPushDownRequiredColumns`，从而 Spark 在规划阶段会调用 `pruneColumns(StructType)` 把所需列下推过来。
- 实现 `pruneColumns(StructType requestedSchema)`：先 `removeMetaColumns(requestedSchema)` 得到不含元数据列的 `requestedProjection`，再用 `SparkSchemaUtil.prune(schema, requestedProjection)` 裁剪自身 schema（保证只读需要的列）；同时遍历 `requestedSchema.fields()`，把 `MetadataColumns.isMetadataColumn(name)` 为真的字段名加入 `metaColumns`（`distinct()` 去重）。这里“数据列走 prune，元数据列单独收集”是核心分离策略。
- 新增 `removeMetaColumns(StructType)`：保留 `MetadataColumns.nonMetadataColumn(name)` 为真的字段，过滤掉元数据字段，返回新的 `StructType`。这避免了把 `_pos` 之类的字段名传给 `SparkSchemaUtil.prune`（因为 `prune` 只处理真实表列）。
- 新增 `schemaWithMetadataColumns()`：把 `metaColumns` 去重后通过 `MetadataColumns.metadataColumn(table, name)` 解析成 `Types.NestedField`，构造一个仅含元数据列的 `Schema meta`，最后 `TypeUtil.join(schema, meta)` 把数据 schema 和元数据 schema 合并成最终 expectedSchema 返回。
- `build()` 由 `new SparkStagedScan(spark, table, readConf)` 改为 `new SparkStagedScan(spark, table, schemaWithMetadataColumns(), readConf)`，让 expectedSchema 透传下去。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkStagedScan.java`

**修改目的**：让 `SparkStagedScan` 接收并使用外部传入的 expectedSchema，而非写死 `table.schema()`。

**工作逻辑**：
- 构造签名由 `SparkStagedScan(SparkSession spark, Table table, SparkReadConf readConf)` 改为 `SparkStagedScan(SparkSession spark, Table table, Schema expectedSchema, SparkReadConf readConf)`，把 `expectedSchema` 透传给父类 `SparkScan` 的 `super(spark, table, readConf, expectedSchema, ImmutableList.of(), null)`，取代原先写死的 `table.schema()`。
- `equals(...)` 增加 `readSchema().equals(that.readSchema())` 比较，避免不同投影的 staged scan 被误判为相等。
- `hashCode()` 同步增加 `readSchema()` 字段。这一对修改保证了 Spark 在对 Scan 做 cache/dedup 时能正确区分不同投影请求的 staged scan。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetaColumnProjectionWithStageScan.java`（新文件）

**修改目的**：端到端验证 staged scan 读取元数据列的能力。

**工作逻辑**：新增测试类 `TestMetaColumnProjectionWithStageScan`（继承 `SparkExtensionsTestBase`，参数化使用 HadoopCatalog）。
- 建表 `CREATE TABLE ... (id bigint, data string) ... format-version=2, write.delete.mode=merge-on-read`，写入 4 条记录。
- 第一组用例：用 `table.newBatchScan().planFiles()` 取任务，`ScanTaskSetManager.stageTasks(...)` 暂存到 `fileSetID`，Spark 读时 `option(SCAN_TASK_SET_ID, fileSetID).load(tableLocation)` 但不 select 元数据列——断言 `scanDF2.columns().length == 2`，证明不请求元数据列时 staged scan 行为与之前一致（向后兼容）。
- 第二组用例：相同暂存流程，但 Spark 读时 `select("*", "_pos")`，断言返回行 `(1,"a",0),(2,"b",1),(3,"c",2),(4,"d",3)`，即 `_pos` 元数据列被正确填充。这正是新功能的核心验证：staged scan 现在能返回 `_pos`，而 `_pos` 在行级删除/MERGE 等场景下是必需的。

## 小结

这个提交通过给 `SparkStagedScanBuilder` 实现 `SupportsPushDownRequiredColumns`、把请求 schema 拆分为“数据投影”与“元数据列集合”并用 `TypeUtil.join` 合并，让 Spark 3.5 的 staged scan 路径与普通 scan 一样支持返回 `_pos` 等 Iceberg 元数据列，补齐了 staged scan 在元数据列投影上的功能短板，为依赖行级定位的高级特性在 staged 模式下可用扫清了障碍。
