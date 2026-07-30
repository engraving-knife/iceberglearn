# 提交 1859：Spark 3.4: Backport partition spec inference in spark ADD_FILES procedure (#12508) (#12319 #12327 #12456)

## 提交信息

- **序号**：1859 / 4088
- **哈希**：c991e0d9258b41ba995b9bd56aa8e56e7ae67e16
- **短哈希**：c991e0d92
- **日期**：2025-03-14 17:37:06 -0500
- **作者**：Bharath Krishna
- **提交说明**：Spark 3.4: Backport partition spec inference in spark ADD_FILES procedure (#12508) (#12319 #12327 #12456)
- **PR/Issue**：#12508（backport #12319、#12327、#12456）

## 总体目的

Iceberg 的 `ADD_FILES` 存储过程用于把已有的 Parquet/ORC/Avro 数据文件"导入"到 Iceberg 表中（不复制数据，只注册文件元数据）。此前的实现有一个限制：**始终用目标 Iceberg 表的 `table.spec()`（默认 spec）来导入文件**，而不是根据源数据实际的分区目录结构推断分区 spec。

这导致几个问题：

1. **多 spec 表无法正确导入**：Iceberg 表可以有多个 partition spec（通过 `specId` 区分），但旧实现只用默认 spec。如果源数据的分区列与默认 spec 不匹配，导入会失败或分区值错误。
2. **非 identity 分区校验过严**：旧 `validatePartitionSpec` 在导入开始前就拒绝任何含非 identity transform 的目标表，即使源数据其实是按 identity 分区的。
3. **partition_filter 校验位置不当**：校验在导入早期、尚未确定实际 spec 时进行，用的是 `table.spec()` 而非与源数据匹配的 spec。

main 分支已通过 #12319、#12327、#12456 三个 PR 解决了这些问题：让 `ADD_FILES` 调用 Spark 的 `InMemoryFileIndex` 推断源数据的分区 spec，再在 Iceberg 表的多个 spec 中找一个与源分区列"名字+顺序"都兼容的 spec（`findCompatibleSpec`），用这个兼容 spec 来导入；partition_filter 校验也改为基于兼容 spec。本提交把这套修复 backport 到 Spark 3.4 模块。

## 如何达成设计目的

1. **`Spark3Util.getInferredSpec(spark, rootPath)`**：用 Spark 的 `InMemoryFileIndex`（传 empty user-specified schema 触发自动推断）拿到源路径的 `PartitionSpec`（Spark 的分区列推断结果）。
2. **`SparkTableUtil.findCompatibleSpec(partitionNames, icebergTable)`**：在 Iceberg 表的所有 specs 中，找一个"全部字段都是 identity 且字段名（小写比较）与顺序与源分区列完全一致"的 spec；找不到则抛 `IllegalArgumentException`。另有重载版本 `findCompatibleSpec(icebergTable, spark, sparkTable)` 通过 `spark.catalog().listColumns(...)` 拿源 Spark 表的分区列。
3. **`SparkTableUtil.validatePartitionFilter(spec, partitionFilter, tableName)`**：把旧的 `AddFilesProcedure.validatePartitionSpec` 搬到 `SparkTableUtil` 并基于兼容 spec 校验：分区表+有 filter 时检查 filter 列数 ≤ spec 字段数且 filter 列都在 spec 中；非分区表不允许传 filter。移除了对非 identity transform 的早期拒绝（因为现在通过 `findCompatibleSpec` 已保证只用 identity spec）。
4. **`AddFilesProcedure` 改造**：`importFileTable` 中先 `getInferredSpec` 拿源分区列名 → `findCompatibleSpec` 找兼容 Iceberg spec → `validatePartitionFilter` 校验 → 用兼容 spec 调 `getPartitions` 与 `importSparkPartitions`。删除原 `validatePartitionSpec` 方法与对 `table.spec()` 的硬依赖。
5. **测试**：`TestAddFilesProcedure` 大幅扩充（+217/-? 行），覆盖多 spec 表、兼容 spec 推断、partition_filter 校验等场景。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/Spark3Util.java` (修改, +17 lines)

**修改目的**：新增 `getInferredSpec` 让 Spark 推断源路径的分区 spec。

**工作逻辑**：`getInferredSpec(spark, rootPath)` 构造 `InMemoryFileIndex`，传 `Option.empty()` 作为 user-specified schema（触发 Spark 自动分区推断），`scala.collection.immutable.Map$.MODULE$.empty()` 作为参数 map，再用 `fileIndex.partitionSpec()` 返回 Spark 的 `PartitionSpec`（含 `partitionColumns` 列表与分区值）。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (修改, +94/-1 lines)

**修改目的**：新增 `findCompatibleSpec` 与 `validatePartitionFilter`，并改造 `importSparkTable` 使用兼容 spec。

**工作逻辑**：
- `findCompatibleSpec(List<String> partitionNames, Table icebergTable)`：把 `partitionNames` 转小写；遍历 `icebergTable.specs().values()`，对每个全 identity 的 spec，取其字段名转小写后与源列表比较，相等则返回；找不到抛异常。
- `findCompatibleSpec(Table, SparkSession, String sparkTable)`：解析 `db.table`，用 `spark.catalog().listColumns(db, table)` 过滤 `isPartition` 列，再调上面的方法。
- `validatePartitionFilter(PartitionSpec, Map<String,String>, String)`：从 `AddFilesProcedure` 搬来并改为基于传入 spec 而非 `table.spec()`；移除非 identity transform 校验。
- `importSparkTable` 内 `SparkSchemaUtil.specForTable(...)` → `findCompatibleSpec(targetTable, spark, sourceTableIdentWithDB.unquotedString())` + `validatePartitionFilter(spec, partitionFilter, targetTable.name())`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/AddFilesProcedure.java` (修改, +14/-79 lines)

**修改目的**：用兼容 spec 替代 `table.spec()`，删除旧 `validatePartitionSpec`。

**工作逻辑**：
- `importFileTable` 中先 `Spark3Util.getInferredSpec(spark(), tableLocation)` 拿 Spark 推断的分区列 → `JavaConverters.seqAsJavaList(inferredSpec.partitionColumns()).stream().map(StructField::name)` 取列名 → `SparkTableUtil.findCompatibleSpec(sparkPartNames, table)` 找兼容 Iceberg spec → `SparkTableUtil.validatePartitionFilter(compatibleSpec, partitionFilter, table.name())` → 用 `compatibleSpec` 调 `Spark3Util.getPartitions` 与 `importPartitions`。
- `importPartitions` 方法签名新增 `PartitionSpec spec` 参数，传给 `SparkTableUtil.importSparkPartitions`。
- 删除 `validatePartitionSpec` 方法（逻辑搬到 `SparkTableUtil.validatePartitionFilter`）。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAddFilesProcedure.java` (修改, +217/-? lines)

**修改目的**：覆盖分区 spec 推断与多 spec 表导入。

**工作逻辑**：新增 `implementation` 字段；新增大量 import（`HasTableOperations`/`ManifestFiles`/`ManifestReader`/`PartitionSpec`/`Table`/`FileIO`/`Spark3Util` 等）；新增测试用例验证多 spec 场景、兼容 spec 查找、partition_filter 校验、通过 manifest 文件验证导入结果等。

## 小结

- **成效**：Spark 3.4 的 `ADD_FILES` 存储过程现在能根据源数据实际的分区目录结构推断分区 spec，并在 Iceberg 表的多个 spec 中找到兼容的 identity spec 来导入，而非死板使用 `table.spec()`。修复了多 spec 表导入与 partition_filter 校验的问题。
- **影响范围**：spark 3.4 模块，3 主代码文件（+125/-80）、1 测试文件（+217）。属于功能增强与 bug 修复，影响所有使用 `ADD_FILES` 过程的 Spark 3.4 用户。
- **回迁到 1.4.x 的注意事项**：本提交本身就是 backport（从 main 到 spark 3.4），1.4.x 的 spark 3.4 模块若仍用旧 `validatePartitionSpec` + `table.spec()` 逻辑，建议回迁。回迁时需同步引入 `Spark3Util.getInferredSpec`、`SparkTableUtil.findCompatibleSpec`/`validatePartitionFilter`，并改造 `AddFilesProcedure.importFileTable`。注意测试改动量大，需一并回迁以保证覆盖。无外部 API 破坏。
