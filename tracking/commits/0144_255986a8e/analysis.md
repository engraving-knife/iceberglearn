# 提交 0144：Spark 3.4: Use rolling manifest writers when optimizing metadata (#9019)

## 提交信息

- **序号**：0144 / 4088
- **哈希**：255986a8e7c59915ff0f2b98c9021ddbbc45e675
- **短哈希**：255986a8e
- **日期**：2023-11-09 15:30:28 -0800
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.4: Use rolling manifest writers when optimizing metadata (#9019)
- **PR/Issue**：#9019

## 总体目的

这个提交重构了 Spark 3.4 模块下 `RewriteManifestsSparkAction` 的 manifest 写入方式，将其从"基于条目数估算的手动切分"改为使用 `RollingManifestWriter` 按"实际文件大小"滚动切分。在此提交之前，Spark 版 manifest 重写动作先根据原始 manifest 的总字节数估算目标 manifest 数量 `targetNumManifests`，再根据总条目数估算每个 manifest 应容纳的条目数 `targetNumManifestEntries`（允许 10% 上浮），然后在 `mapPartitions` 内部把每个分区的行列表按 `maxNumManifestEntries` 做简单的二分切分（至多切成 2 个 manifest）。这种方式有几个问题：条目数估算与实际字节大小脱节（条目大小因文件路径长度、分区值等差异很大），导致切分不均匀；二分切分上限为 2，无法在一个分区内产生多于 2 个 manifest；逻辑复杂且与 Spark 分区数耦合。

本提交引入 `RollingManifestWriter`（core 模块已有能力，按目标字节大小自动滚动关闭旧 writer、创建新 writer），让每个 Spark 分区内的写入器根据实际写入字节数自动切分 manifest，目标大小设为 `1.2 * targetManifestSizeBytes`（20% 上浮，比原来的 10% 更宽松以容忍估算偏差）。这使切分更准确、代码更简洁，且与 core 模块 `FastAppend`、`MergingSnapshotProducer` 等使用 `RollingManifestWriter` 的写入路径保持一致。重构后删除了约 100 行手动切分逻辑，新增约 90 行更清晰的工厂+lambda 写入逻辑，净减约 24 行。

## 如何达成设计目的

整体设计思路是：把"估算条目数→手动切片→每个切片写一个 manifest"的旧流程，替换为"每个 Spark 分区持有一个 `RollingManifestWriter`，按实际写入大小自动滚动"。具体改动分两部分：

1. 在 `RewriteManifestsSparkAction` 中新增一个 `ManifestWriterFactory` 内部类（`Serializable`，可广播到 Spark executor），它封装 `Broadcast<Table>`、formatVersion、specId、输出路径、最大 manifest 大小，提供 `newRollingManifestWriter()` 方法。`toManifests` 这个 `MapPartitionsFunction` 被简化为：从工厂获取一个 `RollingManifestWriter`，遍历分区内的行调用 `writer.existing(...)`，最后关闭并返回 `writer.toManifestFiles()` 的迭代器——一个分区可能产出 0、1 或多个 manifest，完全由实际大小决定。

2. `writeManifestsForUnpartitionedTable` 和 `writeManifestsForPartitionedTable` 不再需要传 `tableBroadcast`、`maxNumManifestEntries`、`location`、`format`、`spec` 等一堆参数，只需传 `manifestWriters()` 工厂实例加上类型信息。`targetNumManifestEntries` 方法被删除，`totalSizeBytes` 被抽取为独立辅助方法。旧的 `writeManifest` 静态方法（按起止下标切片写入）被删除。

测试侧把一个分区表重写测试从"写 50 条 Spark DataFrame 记录、断言恰好产生 2 个 manifest"改为"直接写 1000 个数据文件到 manifest、断言产生至少 2 个 manifest"，因为滚动 writer 产出的 manifest 数量由实际大小决定而非可精确预估。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

**修改目的**：用 `RollingManifestWriter` 替代手动条目数切分，简化并准确化 manifest 写入。

**工作逻辑**：

1. **导入调整**：移除 `java.io.IOException` 和 `java.util.Collections`，新增 `java.io.Serializable` 和 `org.apache.iceberg.RollingManifestWriter`。`Collections` 原用于 `toManifests` 中空分区返回 `Collections.emptyIterator()`，新实现中 `RollingManifestWriter` 关闭后 `toManifestFiles()` 自然返回空列表，不再需要。

2. **`rewriteManifests` 流程简化**：原代码在循环中累加 `totalSizeBytes` 与 `numEntries`（用 `addedFilesCount + existingFilesCount + deletedFilesCount`），再分别计算 `targetNumManifests` 和 `targetNumManifestEntries`。新代码只计算 `totalSizeBytes(matchingManifests)`（抽取为辅助方法），删去 `numEntries` 与 `targetNumManifestEntries`。`writeManifestsForPartitionedTable` 的调用不再传 `targetNumManifestEntries`。

3. **`writeManifestsForUnpartitionedTable` / `writeManifestsForPartitionedTable` 签名简化**：

   ```java
   // 修改前
   private List<ManifestFile> writeManifestsForUnpartitionedTable(
       Dataset<Row> manifestEntryDF, int numManifests) {
     Broadcast<Table> tableBroadcast = sparkContext().broadcast(SerializableTableWithSize.copyOf(table));
     // ...
     .mapPartitions(toManifests(tableBroadcast, maxNumManifestEntries, outputLocation, formatVersion, combinedPartitionType, spec, sparkType), manifestEncoder)
   }
   // 修改后
   private List<ManifestFile> writeManifestsForUnpartitionedTable(
       Dataset<Row> manifestEntryDF, int numManifests) {
     // ...
     .mapPartitions(toManifests(manifestWriters(), combinedPartitionType, partitionType, sparkType), manifestEncoder)
   }
   ```

   两个方法都新增 `Types.StructType partitionType = spec.partitionType()` 局部变量（原只算 `combinedPartitionType`），传给 `toManifests` 用于构造 `SparkDataFile`。`maxNumManifestEntries`（原对非分区表设为 `Long.MAX_VALUE`、对分区表设为 `1.1 * targetNumManifestEntries`）被移除。

4. **`manifestWriters()` 工厂方法**：新增实例方法，构造并返回一个 `ManifestWriterFactory`，封装广播表、formatVersion、specId、输出路径、以及 `1.2 * targetManifestSizeBytes` 作为滚动阈值：

   ```java
   private ManifestWriterFactory manifestWriters() {
     return new ManifestWriterFactory(
         sparkContext().broadcast(SerializableTableWithSize.copyOf(table)),
         formatVersion,
         spec.specId(),
         outputLocation,
         (long) (1.2 * targetManifestSizeBytes));  // 允许 20% 上浮
   }
   ```

   注意 20% 上浮比原来的 10% 更宽松，因为滚动 writer 是按实际大小判断（每 250 行检查一次），需要更大容忍度。

5. **`toManifests` 重写**：

   ```java
   // 修改后
   private static MapPartitionsFunction<Row, ManifestFile> toManifests(
       ManifestWriterFactory writers, Types.StructType combinedPartitionType,
       Types.StructType partitionType, StructType sparkType) {
     return rows -> {
       // ... 构造 SparkDataFile wrapper
       RollingManifestWriter<DataFile> writer = writers.newRollingManifestWriter();
       try {
         while (rows.hasNext()) {
           Row row = rows.next();
           // ... 解析 snapshotId, sequenceNumber, fileSequenceNumber, file
           writer.existing(wrapper.wrap(file), snapshotId, sequenceNumber, fileSequenceNumber);
         }
       } finally {
         writer.close();
       }
       return writer.toManifestFiles().iterator();
     };
   }
   ```

   每个分区一个 `RollingManifestWriter`，写入过程中每 250 行检查一次当前 writer 字节长度，超过阈值则关闭当前 writer、创建新 writer。最终 `toManifestFiles()` 返回该分区产出的所有 manifest 列表（可能 0、1 或多个）。原来的逻辑是把分区行收集为 List，按 `maxNumManifestEntries` 二分（至多 2 个），每个切片调 `writeManifest` 静态方法写一个 manifest。

6. **`ManifestWriterFactory` 内部类**：新增 `Serializable` 内部类，字段为 `tableBroadcast`、`formatVersion`、`specId`、`outputLocation`、`maxManifestSizeBytes`。方法：

   - `newRollingManifestWriter()`：返回 `new RollingManifestWriter<>(this::newManifestWriter, maxManifestSizeBytes)`，其中 `this::newManifestWriter` 是供应器，每次创建一个新的 `ManifestWriter`。
   - `newManifestWriter()`：用 `ManifestFiles.write(formatVersion, spec(), newOutputFile(), null)` 创建。
   - `spec()`：从广播表的 `specs().get(specId)` 获取（支持分区演化后 specId 不一定是最新的）。
   - `newOutputFile()` / `newManifestLocation()`：生成 `optimized-m-<UUID>.avro` 路径并创建输出文件。
   - `table()`：从广播取表。

7. **删除的旧代码**：`writeManifest` 静态方法（按起止下标切片写单个 manifest）、`targetNumManifestEntries` 方法、`totalSizeBytes` 内联逻辑（抽取为方法）、旧 `toManifests` 中的二分切分逻辑全部删除。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`

**修改目的**：适配滚动 writer 产出 manifest 数量不固定的特性，改用直接构造 manifest 的方式准备测试数据。

**工作逻辑**：

1. **导入新增**：`UUID`、`DataFiles`、`ManifestFiles`、`ManifestWriter`、`OutputFile`，用于直接构造数据文件与 manifest。

2. **`testRewriteLargeManifestsPartitionedTable`（分区表大 manifest 重写测试）改造**：

   ```java
   // 修改前：通过 Spark DataFrame 写 50 条记录，repartition 到 50 个文件
   List<ThreeColumnRecord> records = Lists.newArrayList();
   for (int i = 0; i < 50; i++) { records.add(new ThreeColumnRecord(i, String.valueOf(i), "0")); }
   Dataset<Row> df = spark.createDataFrame(records, ThreeColumnRecord.class);
   writeDF(df.repartition(50, df.col("c1")));
   // ... 重写后断言恰好 1 rewritten, 2 added

   // 修改后：直接构造 1000 个数据文件写入一个 manifest
   List<DataFile> dataFiles = Lists.newArrayList();
   for (int fileOrdinal = 0; fileOrdinal < 1000; fileOrdinal++) {
     dataFiles.add(newDataFile(table, "c3=" + fileOrdinal));
   }
   ManifestFile appendManifest = writeManifest(table, dataFiles);
   table.newFastAppend().appendManifest(appendManifest).commit();
   // ... 重写后断言 1 rewritten, >= 2 added
   ```

   断言从精确数量（`2`）改为 `hasSizeGreaterThanOrEqualTo(2)`，因为滚动 writer 产出的 manifest 数量取决于实际写入字节数与目标大小的比值，不再可精确预估。同时移除了"读回数据验证行匹配"的断言（`resultDF.sort("c1","c2")...`），因为测试焦点是 manifest 切分而非数据正确性。

3. **新增辅助方法 `writeManifest(Table, List<DataFile>)`**：用 `ManifestFiles.write(formatVersion, table.spec(), outputFile, null)` 写一个 manifest 文件（临时文件），返回 `ManifestFile`。供测试直接构造 append manifest，绕过 Spark 写入路径，使 manifest 大小可控。

4. **新增辅助方法 `newDataFile(Table, String partitionPath)`**：用 `DataFiles.builder(table.spec())` 构造一个路径随机、大小 10 字节、记录数 1 的数据文件，用于批量构造测试数据。

## 小结

本提交通过把 Spark 3.4 的 manifest 重写动作从"按条目数手动二分切分"重构为"按实际字节大小用 `RollingManifestWriter` 滚动切分"，使 manifest 切分更准确、代码更简洁，并与 core 模块的 manifest 写入路径统一。
