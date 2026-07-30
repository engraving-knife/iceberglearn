# 提交 0175：Spark 3.4, 3.3: Support metadata columns in staged scans (#9098)

## 提交信息

- **序号**：0175 / 4088
- **哈希**：abbfdae108dd342191dcc685021edbc0113fe302
- **短哈希**：abbfdae10
- **日期**：2023-11-17 17:26:32 -0800
- **作者**：zhen
- **提交说明**：Spark 3.4, 3.3: Support metadata columns in staged scans (#9098)
- **PR/Issue**：#9098（cherry-pick 自 #8872）

## 总体目的

这个提交把 PR #8872（在 staged scan 中支持元数据列投影）向后移植（cherry-pick）到 Spark 3.4 和 Spark 3.3 两个维护分支。提交说明明确："This change cherry-picks PR #8872 to Spark 3.4 and 3.3."。

"staged scan" 是 Iceberg-Spark 集成中的一个特殊读取路径：通过 `ScanTaskSetManager.stageTasks` 把预先规划好的 `ScanTask` 集合"暂存"到一张表上，再用 `spark.read.format("iceberg").option(SCAN_TASK_SET_ID, ...).load(location)` 读取。这种模式常用于分布式 compute 引擎（如分布式 rewrite/compaction 任务）把"已规划好的文件集合"分片发给 worker，由 worker 用 Spark 读取，是 Iceberg `rewrite_data_files` 的 distributed 模式与若干增量计算场景的基础设施。

此前 `SparkStagedScanBuilder` 只实现 `ScanBuilder`，不接受 Spark 下推的 required columns，也不感知 metadata columns。这意味着用户在 staged scan 上无法 `select("*", "_pos")`、`select("_file_path")` 之类的元数据列——Spark 一旦下推列裁剪，`SparkStagedScan` 仍以 `table.schema()` 为预期 schema，既不会按需裁剪普通列，也不会注入元数据列，导致元数据列读取缺失或 schema 不匹配。

本提交让 `SparkStagedScanBuilder` 实现 `SupportsPushDownRequiredColumns`，在 `pruneColumns(StructType)` 中分离普通列与元数据列：普通列走 `SparkSchemaUtil.prune` 做列裁剪，元数据列收集到 `metaColumns` 列表，`build()` 时通过 `TypeUtil.join(schema, meta)` 把元数据列拼接到裁剪后的 schema 末尾，传给 `SparkStagedScan` 作为 `expectedSchema`。`SparkStagedScan` 同步增加接受 `expectedSchema` 的构造函数，并把 `readSchema()` 纳入 `equals`/`hashCode`，确保 Spark 缓存与 plan 复用正确。

对 Iceberg 演进的意义：补齐了 Spark 3.3/3.4 维护分支上 staged scan 的元数据列能力，使分布式的 rewrite/compaction 等场景能像普通扫描那样读取 `_pos`、`_file_path`、`_spec_id` 等元数据列，与主分支行为对齐。

## 如何达成设计目的

由于是 cherry-pick，Spark 3.3 与 3.4 两侧的改动几乎完全一致，每个分支改 3 个文件：[`SparkStagedScanBuilder`](../../../spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkStagedScanBuilder.java)（实现 `SupportsPushDownRequiredColumns`、新增 `pruneColumns` 与 `schemaWithMetadataColumns`）、[`SparkStagedScan`](../../../spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkStagedScan.java)（新增带 `expectedSchema` 的构造函数、equals/hashCode 纳入 readSchema）、新增测试 [`TestMetaColumnProjectionWithStageScan`](../../../spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetaColumnProjectionWithStageScan.java)。整体设计：把"列裁剪"和"元数据列注入"都放到 ScanBuilder 阶段，Scan 仅接收最终 schema。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkStagedScanBuilder.java` 与 `spark/v3.3/.../SparkStagedScanBuilder.java`

**修改目的**：让 staged scan 支持列裁剪与元数据列投影。

**工作逻辑**：两侧实现一致。关键变化：

1. 类签名从 `implements ScanBuilder` 改为 `implements ScanBuilder, SupportsPushDownRequiredColumns`，新增 import：`MetadataColumns`、`Schema`、`TypeUtil`、`Types`、`SparkSchemaUtil`、`StructField`、`StructType`、`SupportsPushDownRequiredColumns`、`Lists`、`Collectors`、`Stream`。

2. 新增字段：

   ```java
   private final List<String> metaColumns = Lists.newArrayList();
   private Schema schema = null;
   ```

   构造函数末尾设 `this.schema = table.schema();`（初始为表全量 schema）。

3. `build()` 改为传 `schemaWithMetadataColumns()`：

   ```java
   @Override
   public Scan build() {
     return new SparkStagedScan(spark, table, schemaWithMetadataColumns(), readConf);
   }
   ```

4. 新增 `pruneColumns(StructType requestedSchema)`（`SupportsPushDownRequiredColumns` 接口方法，Spark 在优化阶段调用以做列裁剪）：

   ```java
   @Override
   public void pruneColumns(StructType requestedSchema) {
     StructType requestedProjection = removeMetaColumns(requestedSchema);
     this.schema = SparkSchemaUtil.prune(schema, requestedProjection);

     Stream.of(requestedSchema.fields())
         .map(StructField::name)
         .filter(MetadataColumns::isMetadataColumn)
         .distinct()
         .forEach(metaColumns::add);
   }
   ```

   逻辑：先把请求 schema 中的元数据列剥离，得到 `requestedProjection`（仅普通列），用它 `SparkSchemaUtil.prune` 当前的 `schema` 做列裁剪；再把请求里的元数据列名收集进 `metaColumns`（distinct 防重复）。

5. 辅助方法：

   ```java
   private StructType removeMetaColumns(StructType structType) {
     return new StructType(
         Stream.of(structType.fields())
             .filter(field -> MetadataColumns.nonMetadataColumn(field.name()))
             .toArray(StructField[]::new));
   }

   private Schema schemaWithMetadataColumns() {
     List<Types.NestedField> fields =
         metaColumns.stream()
             .distinct()
             .map(name -> MetadataColumns.metadataColumn(table, name))
             .collect(Collectors.toList());
     Schema meta = new Schema(fields);
     return TypeUtil.join(schema, meta);
   }
   ```

   `schemaWithMetadataColumns()` 把 `metaColumns` 转为 `MetadataColumns.metadataColumn(table, name)` 得到对应的 `NestedField`，组成只含元数据列的 `Schema meta`，再用 `TypeUtil.join(schema, meta)` 拼接到裁剪后的普通 schema 末尾，作为最终 `expectedSchema` 传给 `SparkStagedScan`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkStagedScan.java` 与 `spark/v3.3/.../SparkStagedScan.java`

**修改目的**：让 `SparkStagedScan` 接收外部传入的 `expectedSchema` 而非硬编码 `table.schema()`，并把 `readSchema()` 纳入缓存键。

**工作逻辑**：

1. 新增 import `org.apache.iceberg.Schema`。

2. Spark 3.4 侧：原构造函数 `SparkStagedScan(spark, table, readConf)` 直接调 `super(..., table.schema(), ImmutableList.of(), null)`；改为只保留一个接收 `expectedSchema` 的构造函数：

   ```java
   SparkStagedScan(SparkSession spark, Table table, Schema expectedSchema, SparkReadConf readConf) {
     super(spark, table, readConf, expectedSchema, ImmutableList.of(), null);
     // ...
   }
   ```

   Spark 3.3 侧：保留无 `expectedSchema` 的旧构造函数以兼容，内部转调新构造函数：

   ```java
   SparkStagedScan(SparkSession spark, Table table, SparkReadConf readConf) {
     this(spark, table, table.schema(), readConf);
   }

   SparkStagedScan(SparkSession spark, Table table, Schema expectedSchema, SparkReadConf readConf) {
     super(spark, table, readConf, expectedSchema, ImmutableList.of());
     // ...
   }
   ```

   两侧的 `super(...)` 参数列表与各自 `SparkScan` 父类签名匹配（3.4 比 3.3 多一个 `extraFilter` 参数 `null`，这是两版父类签名差异，与本次改动无关）。

3. `equals` 与 `hashCode` 把 `readSchema()` 纳入：

   ```java
   // equals
   return table().name().equals(that.table().name())
       && Objects.equals(taskSetId, that.taskSetId)
       && readSchema().equals(that.readSchema())
       && Objects.equals(splitSize, that.splitSize)
       && Objects.equals(splitLookback, that.splitLookback)
       && Objects.equals(openFileCost, that.openFileCost);

   // hashCode
   return Objects.hash(
       table().name(), taskSetId, readSchema(), splitSize, splitSize, openFileCost);
   ```

   原 hashCode 中 `splitSize` 出现两次（疑似历史 bug），本次未修。把 `readSchema()` 加入缓存键是必要的：同一个表/同一个 taskSetId 下，不同列裁剪投影会产出不同的 `SparkStagedScan`，若不区分会导致 Spark 复用错误的 scan 实例。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetaColumnProjectionWithStageScan.java` 与 `spark/v3.3/.../TestMetaColumnProjectionWithStageScan.java`

**修改目的**：回归验证 staged scan 上的元数据列投影行为。

**工作逻辑**：两侧测试代码完全一致，继承 `SparkExtensionsTestBase`，参数化使用 HADOOP catalog。`testReadStageTableMeta`：

1. 建表 `(id bigint, data string)`，format-version=2，`write.delete.mode=merge-on-read`。
2. 写入 4 条记录 `(1,"a")`、`(2,"b")`、`(3,"c")`、`(4,"d")`，`coalesce(1)` 落到一个文件。
3. 第一段：`table.newBatchScan().planFiles()` 取出 `ScanTask`，用 `ScanTaskSetManager.stageTasks` 暂存为某 `fileSetID`；用 `spark.read.format("iceberg").option(FILE_OPEN_COST, "0").option(SCAN_TASK_SET_ID, fileSetID).load(tableLocation)` 读取，断言 `columns().length == 2`——即默认读取只返回 2 个普通列，不包含元数据列。
4. 第二段：重新 stage 一份 task，read 时 `.select("*", "_pos")`，断言结果为 `[(1,"a",0),(2,"b",1),(3,"c",2),(4,"d",3)]`——`_pos` 是 Iceberg 的元数据列，表示行在文件内的位置。能正确读出 0/1/2/3 证明元数据列投影在 staged scan 上已生效。

`FILE_OPEN_COST=0` 强制 Spark 不合并小 split，确保每个 task 独立读取。`UUID.randomUUID()` 作为 fileSetID 避免两次 stage 之间互相污染。

## 小结

通过把 #8872 cherry-pick 到 Spark 3.4/3.3，让 `SparkStagedScanBuilder` 支持列裁剪与元数据列注入、`SparkStagedScan` 接收 expectedSchema 并纳入缓存键，补齐了维护分支上 staged scan 读取 `_pos` 等元数据列的能力，使分布式 rewrite/compaction 场景与主分支行为对齐。
