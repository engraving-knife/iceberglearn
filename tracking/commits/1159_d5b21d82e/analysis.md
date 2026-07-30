# 提交 1159：Spark 3.4: Add utility to load table state reliably (#11115)

## 提交信息

- **序号**：1159 / 4088
- **哈希**：d5b21d82e3adb351c3295465e76c772e2dcb3a54
- **短哈希**：d5b21d82e
- **日期**：2024-09-16（Mon Sep 16 14:46:24 2024 -0700）
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Spark 3.4: Add utility to load table state reliably (#11115)
- **PR/Issue**：#11115

## 总体目的

在 Spark 3.4 模块中，`ComputeTableStatsSparkAction`（提交 1156 引入）读取表数据时用的是 `spark.read.format("iceberg").option(SparkReadOptions.SNAPSHOT_ID, ...).load(table.name())`。这条路径要靠"表名字符串"经由 Spark catalog 解析回 Iceberg `Table` 对象，存在两个隐患：
1. 必须依赖表所在的 Spark catalog 已正确注册且 `table.name()` 能被 Spark 识别。如果调用方拿到的是从其他 catalog（或直接通过 `CatalogUtil.loadCatalog`）加载的 `Table` 对象，Spark 这一侧可能并不认识该表名，加载会失败。
2. 字符串名字解析路径可能因为 catalog 配置差异（如同名表在不同 catalog 中）加载到错误的表，无法保证"操作的就是传入的 `Table` 对象"。

本提交在 `SparkTableUtil` 中新增 `loadTable(spark, table, snapshotId)` 工具方法，直接基于已有的 `Table` 对象构造 `SparkTable`（带 snapshotId）和 `DataSourceV2Relation`，跳过 catalog 名字解析，保证读到的就是传入的表与 snapshot。同时把 `NDVSketchUtil.computeNDVSketches` 切换到新方法，并复用同一套 `createRelation` 辅助方法重构 `loadMetadataTable`。

## 如何达成设计目的

1. 在 `SparkTableUtil` 中新增 `public static Dataset<Row> loadTable(SparkSession spark, Table table, long snapshotId)`：用 `new SparkTable(table, snapshotId, false)` 包装 Iceberg `Table`（第三个参数 `false` 表示不缓存），然后通过 `createRelation` 构造 `DataSourceV2Relation`，最后 `Dataset.ofRows(spark, relation)` 返回 DataFrame。这条路径完全不经过 Spark catalog，直接用 Iceberg `Table` 对象驱动读。
2. 抽取 `private static DataSourceV2Relation createRelation(SparkTable sparkTable, Map<String, String> extraOptions)` 作为公共辅助方法，被 `loadTable` 与 `loadMetadataTable` 共用。在抽取过程中把 `Some.empty()` 替换为 `Option.empty()`（Spark 3.4 起 `Some.empty()` 已弃用，推荐 `Option.empty()`），同时把 `import` 从 `scala.Some` 改为 `scala.Option`。
3. 改造 `loadMetadataTable(spark, table, type, extraOptions)`：把 `MetadataTableUtils.createMetadataTableInstance(...)` 的结果先赋给 `Table metadataTable`，再用 `new SparkTable(metadataTable, false)` 包装，最后走 `createRelation`。逻辑等价但与 `loadTable` 共用代码路径。
4. 改造 `NDVSketchUtil.computeNDVSketches`：删掉对 `spark.read().format("iceberg").option(SparkReadOptions.SNAPSHOT_ID, ...).load(table.name())` 的调用，改为 `Dataset<Row> inputDF = SparkTableUtil.loadTable(spark, table, snapshot.snapshotId()); return inputDF.select(toAggColumns(colNames)).first();`。`import` 也相应从 `SparkReadOptions` 改为 `SparkTableUtil` 与 `Dataset`。
5. 新增 `testLoadingTableDirectly` 测试：用 SQL 建表并插入一行，通过 `validationCatalog.loadTable(tableIdent)` 拿到 Iceberg `Table` 对象，调 `SparkActions.get().computeTableStats(table).execute()`（不指定 columns，让默认逻辑取所有 primitive 列），断言生成的 statistics file 大小非 0 且 blob 数为 2（id 与 data 两列）。这覆盖了"用 `Table` 对象直接驱动 action"的场景，而原有测试用例是用 `Spark3Util.loadIcebergTable(spark, tableName)` 通过 Spark catalog 解析表名，覆盖不到这条新路径。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`

**修改目的**：新增基于 `Table` 对象直接加载 DataFrame 的入口，并把 `loadMetadataTable` 与之共享 relation 构造逻辑。

**工作逻辑**：
- 新增公共方法：
  ```java
  public static Dataset<Row> loadTable(SparkSession spark, Table table, long snapshotId) {
    SparkTable sparkTable = new SparkTable(table, snapshotId, false);
    DataSourceV2Relation relation = createRelation(sparkTable, ImmutableMap.of());
    return Dataset.ofRows(spark, relation);
  }
  ```
  其中 `new SparkTable(table, snapshotId, false)` 表示用指定 snapshot 的数据，不缓存（每次都重新读）。
- 改造 `loadMetadataTable(SparkSession, Table, MetadataTableType, Map<String, String>)`：
  - 原：直接 `new SparkTable(MetadataTableUtils.createMetadataTableInstance(table, type), false)` 内联构造 + `DataSourceV2Relation.create(...)` 内联构造。
  - 新：先 `Table metadataTable = MetadataTableUtils.createMetadataTableInstance(table, type);` 再 `SparkTable sparkMetadataTable = new SparkTable(metadataTable, false);` 最后 `DataSourceV2Relation relation = createRelation(sparkMetadataTable, extraOptions); return Dataset.ofRows(spark, relation);`。
- 新增私有辅助方法：
  ```java
  private static DataSourceV2Relation createRelation(
      SparkTable sparkTable, Map<String, String> extraOptions) {
    CaseInsensitiveStringMap options = new CaseInsensitiveStringMap(extraOptions);
    return DataSourceV2Relation.create(sparkTable, Option.empty(), Option.empty(), options);
  }
  ```
  把 `Some.empty()` 改为 `Option.empty()`（Spark 3.4 弃用清理）。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/NDVSketchUtil.java`

**修改目的**：让 `ComputeTableStatsSparkAction` 通过新工具方法加载表数据。

**工作逻辑**：
- `import` 调整：删除 `import org.apache.iceberg.spark.SparkReadOptions;`，新增 `import org.apache.iceberg.spark.SparkTableUtil;` 与 `import org.apache.spark.sql.Dataset;`。
- `computeNDVSketches(spark, table, snapshot, colNames)` 方法体由原本的：
  ```java
  return spark
      .read()
      .format("iceberg")
      .option(SparkReadOptions.SNAPSHOT_ID, snapshot.snapshotId())
      .load(table.name())
      .select(toAggColumns(colNames))
      .first();
  ```
  改为：
  ```java
  Dataset<Row> inputDF = SparkTableUtil.loadTable(spark, table, snapshot.snapshotId());
  return inputDF.select(toAggColumns(colNames)).first();
  ```
  逻辑等价（都是按 snapshotId 读表数据并 select 聚合列后取第一行），但加载路径从"字符串名解析"变为"直接用 Table 对象"。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputeTableStatsAction.java`

**修改目的**：覆盖通过 `Table` 对象直接驱动 `computeTableStats` 的场景。

**工作逻辑**：新增 `testLoadingTableDirectly` 测试：
- `sql("CREATE TABLE %s (id int, data string) USING iceberg", tableName)` 建表。
- `sql("INSERT into %s values(1, 'abcd')", tableName)` 插入一行数据。
- `Table table = validationCatalog.loadTable(tableIdent);` 直接从 catalog 拿到 Iceberg `Table` 对象。
- `SparkActions.get().computeTableStats(table).execute()`（不指定 columns，使用默认 primitive 列）。
- 断言 `statisticsFile.fileSizeInBytes() != 0` 且 `blobMetadata().size() == 2`（id 与 data 两列各一个 blob）。

该测试与 `testComputeTableStatsAction` 的区别在于：后者用 `Spark3Util.loadIcebergTable(spark, tableName)` 通过 Spark 解析表名，而前者用 `validationCatalog.loadTable(tableIdent)` 直接拿到 `Table` 对象，覆盖了新工具方法 `SparkTableUtil.loadTable` 的代码路径。

## 小结

- **成效**：`ComputeTableStatsSparkAction` 现在通过 `SparkTableUtil.loadTable` 直接用 Iceberg `Table` 对象构造 Spark DataFrame，不再依赖 Spark catalog 名字解析，加载更可靠（避免名字冲突或 catalog 配置缺失导致的失败）。同时把 `loadMetadataTable` 的 relation 构造抽为公共方法 `createRelation`，减少重复代码；顺带把 `Some.empty()` 替换为 `Option.empty()` 跟上 Spark 3.4 的弃用建议。
- **影响范围**：仅 `spark/v3.4` 模块。修改 `SparkTableUtil`、`NDVSketchUtil` 两个生产文件与一个测试文件。无 core 模块变更，无 API 兼容性变更（`SparkTableUtil.loadTable` 是新增 public 方法）。
- **回迁到 1.4.x 的注意事项**：
  1. 该改动是 1156 引入的 `ComputeTableStatsSparkAction` 的可靠性与可维护性优化。1.4.x 若已回迁 1156，则建议一并回迁本提交以保证两者一致；若 1.4.x 未回迁 1156，则本提交无意义。
  2. 依赖 `SparkTable` 的 `(Table, long snapshotId, boolean)` 三参构造函数，1.4.x 的 Spark 3.4 模块必须已有该构造（在 Spark 3.4 模块引入时就存在）。
  3. `DataSourceV2Relation.create(table, Option, Option, CaseInsensitiveStringMap)` 的签名需要与 1.4.x 的 Spark 3.4 依赖版本兼容；`Option.empty()` 替换 `Some.empty()` 是 Scala 标准库 API，无版本风险。
  4. 改动不改变 `ComputeTableStatsSparkAction` 对外行为（输出仍然是一个 statistics file），只是加载路径更稳健。无元数据格式变更，与 1.4.x 已有 statistics 文件完全兼容。
  5. 测试 `testLoadingTableDirectly` 需要 `validationCatalog` 与 `tableIdent` 等 `SparkCatalogTestBase` 提供的成员，1.4.x 若已有该测试基础设施可直接复用。
