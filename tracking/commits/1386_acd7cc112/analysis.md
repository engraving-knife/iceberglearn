# 提交 1386：Spark 3.5: Add DVReaderBenchmark (#11537)

## 提交信息

- **序号**：1386 / 4088
- **哈希**：acd7cc1126b192ccb53ad8198bda37e983aa4c6c
- **短哈希**：acd7cc112
- **日期**：2024-11-15（Fri Nov 15 22:15:08 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Spark 3.5: Add DVReaderBenchmark
- **PR/Issue**：#11537

## 总体目的

DV（Deletion Vector）作为 v3 表格式的新型位置删除载体，其核心优势之一是读取开销更小：DV 以 Puffin 文件存储一个紧凑的 Roaring 位图，读取时只需反序列化一次位图即可得到完整的 `PositionDeleteIndex`；而传统位置删除文件（Parquet 格式）需要逐行读取 `(file_path, position)` 记录，再构建位图索引。

本提交新增 `DVReaderBenchmark`，在统一的 JMH 基准下直接对比三种删除文件的读取性能：

1. **DV**（Puffin 格式）：通过 `BaseDVFileWriter` 写入，`DeleteLoader.loadPositionDeletes` 读取；
2. **file-scoped Parquet 位置删除**：每个数据文件配一个位置删除文件；
3. **partition-scoped Parquet 位置删除**：每个分区一个位置删除文件。

基准通过两个 `@Param` 维度覆盖不同负载：

- `referencedDataFileCount`（5/10）：模拟一个删除文件引用多少个数据文件（DV 与 file-scoped 是 1:1，partition-scoped 是 N:1）；
- `deletedRowsRatio`（0.01/0.03/0.05/0.10/0.2）：删除行数占总行数的比例，从 1% 到 20%。

这可以量化 DV 相对传统位置删除在不同删除密度下的读取加速比，为 DV 的性能优势提供数据支撑。

## 如何达成设计目的

- 在 `setupBenchmark` 阶段用同一批位置删除数据 `(dataFilePath, position)` 分别写入三种格式：
  - `writeDVs(deletes)`：用 `BaseDVFileWriter`（Puffin）写入 DV；
  - `writePositionDeletes(deletes, FILE)`：用 `FanoutPositionOnlyDeleteWriter`（Parquet，file 粒度）写入位置删除；
  - `writePositionDeletes(deletes, PARTITION)`：用 `FanoutPositionOnlyDeleteWriter`（Parquet，partition 粒度）写入位置删除。
- 三个 `@Benchmark` 方法分别用 `BaseDeleteLoader.loadPositionDeletes` 读取对应格式的删除文件，构建 `PositionDeleteIndex`，用 `Blackhole` 消费结果防止死码消除。
- 数据规模：每个数据文件 200 万行（`DATA_FILE_RECORD_COUNT = 2_000_000`），`TARGET_FILE_SIZE = Long.MAX_VALUE`（不滚动），保证每个删除文件只产出一个。

## 修改详情

### `spark/v3.5/spark/src/jmh/java/org/apache/iceberg/spark/source/DVReaderBenchmark.java`（新增，267 行）

**修改目的**：对比 DV 与传统位置删除的读取性能。

**工作逻辑**：

- **JMH 配置**：`@Fork(1)`、`@Warmup(iterations=3)`、`@Measurement(iterations=15)`、`@Timeout(20 minutes)`、`@BenchmarkMode(Mode.SingleShotTime)`、单线程。Measurement 迭代 15 次比一般基准多（通常 5 次），因为单次读取可能很快，需要更多迭代降低噪声。
- **参数**：
  ```java
  @Param({"5", "10"})
  private int referencedDataFileCount;

  @Param({"0.01", "0.03", "0.05", "0.10", "0.2"})
  private double deletedRowsRatio;
  ```
  共 2 × 5 = 10 种参数组合，每种组合跑 3 个 benchmark（DV、file-scoped、partition-scoped），共 30 次测量。
- **常量**：
  - `DATA_FILE_RECORD_COUNT = 2_000_000`：每个数据文件的行数；
  - `TARGET_FILE_SIZE = Long.MAX_VALUE`：写入器不按大小滚动，保证每个删除粒度只产出一个文件。
- **setup**：
  1. `setupSpark()`：启动本地 Spark 会话（`local[*]`，KryoSerializer，SparkSessionCatalog）；
  2. `initTable()`：`CREATE TABLE test_table (c1 INT, c2 INT, c3 STRING) USING iceberg`，加载为 Iceberg `Table`；
  3. `generatePositionDeletes()`：生成位置删除记录列表（`List<InternalRow>`），每条记录是 `(dataFilePath: UTF8String, pos: Long)`：
     - 对 `referencedDataFileCount` 个数据文件，每个生成 `numDeletesPerFile = DATA_FILE_RECORD_COUNT * deletedRowsRatio` 个位置；
     - 位置用 `ThreadLocalRandom` 随机生成，去重（`Set<Long>`）；
     - 最终 `Collections.shuffle(deletes)` 打乱顺序，模拟无序输入；
  4. `writeDVs(deletes)`：用 `BaseDVFileWriter`（`OutputFileFactory` 格式为 PUFFIN）写入 DV：
     ```java
     DVFileWriter writer = new BaseDVFileWriter(fileFactory, path -> null);
     for (InternalRow row : rows) {
       closableWriter.delete(path, pos, table.spec(), null);
     }
     ```
     `path -> null` 是一个忽略删除文件路径的回调（基准不需要跟踪引用关系）。每个 `(path, pos)` 调用一次 `delete`，DV 写入器内部按 `path` 分组累积位图；
  5. `writePositionDeletes(deletes, granularity)`：用 `FanoutPositionOnlyDeleteWriter`（格式 PARQUET）写入位置删除：
     ```java
     FanoutPositionOnlyDeleteWriter<InternalRow> writer = newWriter(granularity);
     PositionDelete<InternalRow> positionDelete = PositionDelete.create();
     for (InternalRow row : rows) {
       positionDelete.set(path, pos, null /* no row */);
       closableWriter.write(positionDelete, table.spec(), null);
     }
     ```
     `granularity` 控制 file（每个数据文件一个删除文件）或 partition（每个分区一个删除文件）。
- **benchmark 方法**：
  - `dv(Blackhole)`：取 `dvsResult.deleteFiles().get(0)`（第一个 DV），用 `BaseDeleteLoader` 加载，`loadPositionDeletes(ImmutableList.of(dv), dataFile)` 返回 `PositionDeleteIndex`。`dataFile` 取自 `dv.referencedDataFile()`。
  - `fileScopedParquetDeletes(Blackhole)`：取 `fileDeletesResult.deleteFiles().get(0)`，`dataFile` 取自 `ContentFileUtil.referencedDataFile(deleteFile)`。
  - `partitionScopedParquetDeletes(Blackhole)`：取 `Iterables.getOnlyElement(partitionDeletesResult.deleteFiles())`，`dataFile` 取自 `Iterables.getLast(partitionDeletesResult.referencedDataFiles())`。
  
  三个方法都用相同的 `BaseDeleteLoader` 实例（`new BaseDeleteLoader(file -> table.io().newInputFile(file), null)`），保证读取路径一致，差异仅在删除文件格式与粒度。
- **辅助方法**：
  - `newWriter(granularity)`：构造 `FanoutPositionOnlyDeleteWriter`，传入 `SparkFileWriterFactory`（Parquet 数据/删除格式）、`OutputFileFactory`、`table.io()`、`TARGET_FILE_SIZE`、`granularity`；
  - `generatePositions(numPositions)`：用 `random.nextInt(DATA_FILE_RECORD_COUNT)` 生成去重位置集合；
  - `generateDataFilePath()`：用 `FileGenerationUtil.generateFileName()` 生成文件名，再用 `table.locationProvider().newDataLocation(...)` 拼出完整路径。
- **teardown**：`dropTable()` + `tearDownSpark()`。

## 小结

- **成效**：新增 `DVReaderBenchmark`，在统一负载下直接对比 DV（Puffin）与 file-scoped/partition-scoped Parquet 位置删除的读取性能。通过 `referencedDataFileCount`（5/10）与 `deletedRowsRatio`（1%~20%）两个维度共 10 种组合，量化 DV 在不同删除密度下的读取加速比。预期 DV 读取显著快于 Parquet 位置删除，因为 DV 反序列化一个位图即可，而 Parquet 需要逐行读取。
- **影响范围**：仅 `spark/v3.5/spark` 模块新增 1 个 JMH 基准类（267 行），无生产代码改动。
- **回迁到 1.4.x 的注意事项**：
  1. 依赖 DV 写入与读取基础设施：
     - `BaseDVFileWriter`（DV 写入器，Puffin 格式）；
     - `DVFileWriter` 接口；
     - `BaseDeleteLoader` 支持 DV 读取（`loadPositionDeletes` 能处理 PUFFIN 格式的 DeleteFile）；
     - `DeleteFile.referencedDataFile()` 方法；
     - `ContentFileUtil.referencedDataFile(DeleteFile)` 工具方法；
     - `FanoutPositionOnlyDeleteWriter` 支持 `DeleteGranularity.FILE`/`PARTITION`；
     - `FileGenerationUtil.generateFileName()`。
     这些均需在 1.4.x 上已就绪；
  2. 依赖 v3 表格式支持（DV 需要 v3 表）；
  3. 基准用 `SparkSessionCatalog` 与本地 Spark，需要 Spark 3.5 环境；
  4. `@Measurement(iterations = 15)` 与 10 种参数组合 × 3 个 benchmark = 450 次测量，单次 `@Fork(1)` 下基准完整运行可能需要较长时间（受 `@Timeout(20 minutes)` 限制）；
  5. 建议与 1384、1385 一并回迁，构成完整的 DV benchmark 套件（DeleteFileIndex 构建 + 扫描规划 + 删除读取三个维度）。
