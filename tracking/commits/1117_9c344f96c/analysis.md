# 提交 1117：Spark 3.5: Use FileGenerationUtil in PlanningBenchmark (#11027)

## 提交信息

- **序号**：1117 / 4088
- **哈希**：9c344f96c874dcdb88c57dff15adba0647ce47e5
- **短哈希**：9c344f96c
- **日期**：2024-08-29（Thu Aug 29 08:38:32 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Spark 3.5: Use FileGenerationUtil in PlanningBenchmark (#11027)
- **PR/Issue**：#11027

## 总体目的

`spark/v3.5/spark-extensions/src/jmh/java/org/apache/iceberg/spark/PlanningBenchmark.java` 用于基准测试 Spark 端的 Iceberg 文件规划（planning）性能，尤其在大量数据文件 + 删除文件的场景下测量扫描与过滤开销。

原实现采用"真实写文件"的方式构造测试数据：用 Spark `RandomData.generateSpark` 生成 DataFrame，`df.coalesce(1).writeTo(TABLE_NAME).append()` 真正写 Parquet 文件，然后通过 `table.currentSnapshot().addedDataFiles()` 拿到刚写入的 DataFile，再用 `DataFiles.builder(spec).copy(dataFile).withPath(...)` 复制元数据生成大量"副本"文件（每分区 5 万个），删除文件同理。这种方式有几个问题：

1. **依赖 Spark 写入路径**：必须真正启动 Spark 写 Parquet，初始化慢、占用资源多，benchmark setup 耗时长。
2. **耦合 Spark 数据生成**：用 `randomDataDF` + `withColumn` 构造分区与排序列的值，再 `appendAsFile` 写表，逻辑复杂且与规划性能本身无关。
3. **维护成本高**：`loadAddedDataFile`、`loadAddedDeleteFile`、`appendAsFile`、`randomDataDF` 等辅助方法承担了大量与"被测目标"无关的样板代码。
4. **统计信息控制不直接**：要让某些文件匹配/不匹配 sort key 谓词，需要通过控制真实数据的列值间接实现，不够直接。

本提交用 core 模块新增的 `FileGenerationUtil`（与提交 1116 配套）替换上述真实写入路径：直接生成虚拟 DataFile/DeleteFile 元数据（带指定的 lower/upper bounds），不再启动 Spark 写数据。这让基准 setup 更快、更可控、代码更简洁，专注测量 planning 性能本身。

## 如何达成设计目的

通过重写 `PlanningBenchmark.initDataAndDeletes()` 方法及辅助方法实现：

1. **删除旧的辅助方法**：移除 `loadAddedDataFile`、`loadAddedDeleteFile`、`appendAsFile`、`randomDataDF` 及相关 Spark/RandomData 导入。
2. **简化常量**：将 `NUM_REAL_DATA_FILES_PER_PARTITION`（25）、`NUM_REPLICA_DATA_FILES_PER_PARTITION`（5 万）、`NUM_ROWS_PER_DATA_FILE`（500）三个常量合并为单一 `NUM_DATA_FILES_PER_PARTITION = 50_000`，因为不再需要"先写少量真实文件再复制"的两阶段，直接生成 5 万个虚拟文件即可。
3. **改用 `FileGenerationUtil`**：循环 5 万次调用 `FileGenerationUtil.generateDataFile(table, partition, lowerBounds, upperBounds)` 生成数据文件；对每个分区额外生成一个 sort key 谓词匹配的数据文件（用 `SORT_KEY_VALUE` 而非 `Integer.MIN_VALUE` 作为上下界）；删除文件调用 `FileGenerationUtil.generatePositionDeleteFile(table, partition)`。
4. **改用 `RowDelta`**：原 `initDataAndDeletes` 在数据文件阶段用 `AppendFiles`，删除文件阶段才用 `RowDelta`；新版统一用单个 `RowDelta` 同时添加数据文件与删除文件，一次 commit 完成，减少 commit 次数。
5. **新增 `generateDataFile` 私有方法**：封装 sort key 上下界到 `Map<Integer, ByteBuffer>` 的转换（用 `Conversions.toByteBuffer`），让调用方只传 sort key 的 min/max。

## 修改详情

### `spark/v3.5/spark-extensions/src/jmh/java/org/apache/iceberg/spark/PlanningBenchmark.java`

**修改目的**：用 `FileGenerationUtil` 替换真实 Spark 写入路径，简化并加速 benchmark setup。

**工作逻辑**：

1. **import 调整**：
   - 移除：`org.apache.spark.sql.functions.lit`、`AppendFiles`、`DataFiles`、`FileMetadata`、`PartitionSpec`、`Schema`、`LocationProvider`、`Iterables`、`RandomData`、`JavaRDD`、`JavaSparkContext`、`Dataset`、`Row`、`InternalRow`、`StructType`。
   - 新增：`ByteBuffer`、`Map`、`FileGenerationUtil`、`StructLike`、`TestHelpers`、`Conversions`、`Types`。
   - 保留：`UUID`（用于 warehouse 目录名）。

2. **常量调整**：

```java
// 旧
private static final int NUM_REAL_DATA_FILES_PER_PARTITION = 25;
private static final int NUM_REPLICA_DATA_FILES_PER_PARTITION = 50_000;
private static final int NUM_DELETE_FILES_PER_PARTITION = 50;
private static final int NUM_ROWS_PER_DATA_FILE = 500;

// 新
private static final int NUM_DATA_FILES_PER_PARTITION = 50_000;
private static final int NUM_DELETE_FILES_PER_PARTITION = 50;
```

3. **`initDataAndDeletes()` 重写**：

```java
private void initDataAndDeletes() {
  for (int partitionOrdinal = 0; partitionOrdinal < NUM_PARTITIONS; partitionOrdinal++) {
    StructLike partition = TestHelpers.Row.of(partitionOrdinal);
    RowDelta rowDelta = table.newRowDelta();

    for (int fileOrdinal = 0; fileOrdinal < NUM_DATA_FILES_PER_PARTITION; fileOrdinal++) {
      DataFile dataFile = generateDataFile(partition, Integer.MIN_VALUE, Integer.MIN_VALUE);
      rowDelta.addRows(dataFile);
    }

    // add one data file that would match the sort key predicate
    DataFile sortKeyDataFile = generateDataFile(partition, SORT_KEY_VALUE, SORT_KEY_VALUE);
    rowDelta.addRows(sortKeyDataFile);

    for (int fileOrdinal = 0; fileOrdinal < NUM_DELETE_FILES_PER_PARTITION; fileOrdinal++) {
      DeleteFile deleteFile = FileGenerationUtil.generatePositionDeleteFile(table, partition);
      rowDelta.addDeletes(deleteFile);
    }

    rowDelta.commit();
  }
}
```

每个分区：生成 5 万个 sort key 为 `Integer.MIN_VALUE`（不匹配 sort key 谓词）的数据文件 + 1 个 sort key 为 `SORT_KEY_VALUE`（匹配谓词）的数据文件 + 50 个位置删除文件，全部通过单个 `RowDelta` commit。

4. **新增 `generateDataFile(StructLike, int, int)` 私有方法**：

```java
private DataFile generateDataFile(StructLike partition, int sortKeyMin, int sortKeyMax) {
  int sortKeyFieldId = table.schema().findField(SORT_KEY_COLUMN).fieldId();
  ByteBuffer lower = Conversions.toByteBuffer(Types.IntegerType.get(), sortKeyMin);
  Map<Integer, ByteBuffer> lowerBounds = ImmutableMap.of(sortKeyFieldId, lower);
  ByteBuffer upper = Conversions.toByteBuffer(Types.IntegerType.get(), sortKeyMax);
  Map<Integer, ByteBuffer> upperBounds = ImmutableMap.of(sortKeyFieldId, upper);
  return FileGenerationUtil.generateDataFile(table, partition, lowerBounds, upperBounds);
}
```

把 sort key 的 min/max 转换为 `Map<Integer, ByteBuffer>` 形式的 lowerBounds/upperBounds，传给 `FileGenerationUtil.generateDataFile` 生成带列统计的虚拟 DataFile。这样 planning 阶段可基于这些 bounds 进行谓词过滤，与真实文件行为一致。

5. **删除的辅助方法**：
   - `loadAddedDataFile()`：原从 snapshot 读取刚写入的 DataFile。
   - `loadAddedDeleteFile()`：原从 snapshot 读取刚写入的 DeleteFile。
   - `appendAsFile(Dataset<Row>)`：原用 `df.coalesce(1).writeTo(TABLE_NAME).append()` 写真实文件。
   - `randomDataDF(Schema, int)`：原用 `RandomData.generateSpark` + `JavaSparkContext.parallelize` 构造 DataFrame。

整体减少 92 行、新增 26 行，大幅简化。

## 小结

- **成效**：PlanningBenchmark 不再依赖 Spark 真实写入路径，setup 直接生成虚拟 DataFile/DeleteFile（含可控的列统计 bounds），更快、更可控、代码更简洁（净减 66 行）；与 core 模块 `FileGenerationUtil` 形成跨模块复用，统一了 benchmark 数据生成方式。
- **影响范围**：1 个文件、26 增 92 删，仅位于 `spark/v3.5/spark-extensions/src/jmh/java`，不影响主代码与测试，对发布产物无影响。
- **回迁到 1.4.x 的注意事项**：这是开发期 benchmark 重构，不影响运行时行为，**通常无需回迁到 1.4.x**。若 1.4.x 也希望复用 `FileGenerationUtil` 简化 PlanningBenchmark，需确认 1.4.x 的 core 模块是否已引入 `FileGenerationUtil` 及其 `generateDataFile(Table, StructLike, Map, Map)`、`generatePositionDeleteFile(Table, StructLike)` 签名（这些是 main 上较新的 API）；若 1.4.x core 缺少该工具类，则需先回迁 `FileGenerationUtil` 本身，再回迁本基准改动。回迁属可选操作，风险主要在于 API 兼容性核对。
