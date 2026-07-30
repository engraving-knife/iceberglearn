# 提交 1385：Spark 3.5: Adapt PlanningBenchmark for DVs (#11531)

## 提交信息

- **序号**：1385 / 4088
- **哈希**：7e4fd1ba6e4479376128312572ed41378b2ed9a5
- **短哈希**：7e4fd1ba6
- **日期**：2024-11-15（Fri Nov 15 22:14:32 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Spark 3.5: Adapt PlanningBenchmark for DVs
- **PR/Issue**：#11531

## 总体目的

`PlanningBenchmark` 是 Iceberg Spark 3.5 模块下的 JMH 基准，用于度量扫描规划（scan planning）阶段的开销——包括读取 manifest、构建 `DeleteFileIndex`、应用分区/文件级过滤、生成 `FileScanTask` 列表等。此前该基准只支持 partition-scoped 位置删除一种布局。

本提交把 `PlanningBenchmark` 改造为支持三种删除布局（与 1384 对 `DeleteFileIndexBenchmark` 的改造对称）：

- `partition`：partition-scoped 位置删除；
- `file`：file-scoped 位置删除；
- `dv`：DV（Deletion Vector，每个数据文件配一个 DV）。

这样可以在统一的扫描规划基准下对比三种删除布局的规划开销，量化 DV 引入后对扫描规划性能的影响（DV 在 `DeleteFileIndex` 中走单独的 `dvByPath` 路径，与传统位置删除的索引路径不同，可能影响规划耗时）。

## 如何达成设计目的

- 新增 `@Param({"partition", "file", "dv"}) private String type` 参数（此前无参数，固定 partition-scoped）。
- 把原 `initDataAndDeletes()` 重命名为 `initDataAndPartitionScopedDeletes()`，新增 `initDataAndFileScopedDeletes()` 与 `initDataAndDVs()`，在 `initDataAndDeletes()` 中按 `type` 三分发。
- 表 format version 从固定 `2` 改为 `type.equals("dv") ? 3 : 2`。
- `file` 与 `dv` 布局额外生成一个 sort-key 匹配的数据文件（`generateDataData(partition, SORT_KEY_VALUE, SORT_KEY_VALUE)`），用于基准中的 sort-key 过滤场景。

## 修改详情

### `spark/v3.5/spark-extensions/src/jmh/java/org/apache/iceberg/spark/PlanningBenchmark.java`（修改，+57/-1 行）

**修改目的**：让基准支持 partition/file/dv 三种删除布局。

**工作逻辑**：

1. 新增 import `org.openjdk.jmh.annotations.Param`；
2. 新增参数字段：
   ```java
   @Param({"partition", "file", "dv"})
   private String type;
   ```
3. `initDataAndDeletes()` 改为分发：
   ```java
   private void initDataAndDeletes() {
     if (type.equals("partition")) {
       initDataAndPartitionScopedDeletes();
     } else if (type.equals("file")) {
       initDataAndFileScopedDeletes();
     } else {
       initDataAndDVs();
     }
   }
   ```
4. 原 `initDataAndDeletes()` 方法体重命名为 `initDataAndPartitionScopedDeletes()`（内容不变，仍是每分区一个位置删除文件 + 一个 sort-key 数据文件）。
5. 新增 `initDataAndFileScopedDeletes()`：
   ```java
   private void initDataAndFileScopedDeletes() {
     for (int partitionOrdinal = 0; partitionOrdinal < NUM_PARTITIONS; partitionOrdinal++) {
       StructLike partition = TestHelpers.Row.of(partitionOrdinal);
       RowDelta rowDelta = table.newRowDelta();
       for (int fileOrdinal = 0; fileOrdinal < NUM_DATA_FILES_PER_PARTITION; fileOrdinal++) {
         DataFile dataFile = generateDataFile(partition, Integer.MIN_VALUE, Integer.MIN_VALUE);
         DeleteFile deleteFile = FileGenerationUtil.generatePositionDeleteFile(table, dataFile);
         rowDelta.addRows(dataFile);
         rowDelta.addDeletes(deleteFile);
       }
       // add one data file that would match the sort key predicate
       DataFile sortKeyDataFile = generateDataFile(partition, SORT_KEY_VALUE, SORT_KEY_VALUE);
       rowDelta.addRows(sortKeyDataFile);
       rowDelta.commit();
     }
   }
   ```
   每个数据文件配一个 file-scoped 位置删除文件（`FileGenerationUtil.generatePositionDeleteFile`），额外加一个 sort-key 匹配的数据文件供基准过滤。
6. 新增 `initDataAndDVs()`：与 `initDataAndFileScopedDeletes` 结构相同，但用 `FileGenerationUtil.generateDV(table, dataFile)` 生成 DV 而非位置删除文件。
7. 表 format version：
   ```java
   TableProperties.FORMAT_VERSION,
   type.equals("dv") ? 3 : 2);
   ```

## 小结

- **成效**：`PlanningBenchmark` 现支持 `partition`/`file`/`dv` 三种删除布局，可度量扫描规划阶段在 DV 场景下的开销并与传统位置删除对比。与 1384（`DeleteFileIndexBenchmark`）形成互补：前者度量 `DeleteFileIndex` 构建本身，后者度量完整扫描规划（含 manifest 读取、任务生成等）。
- **影响范围**：仅 `spark/v3.5/spark-extensions` 的 JMH 基准一个文件，纯基准代码变更，无生产代码改动。
- **回迁到 1.4.x 的注意事项**：
  1. 依赖 `FileGenerationUtil.generateDV(table, dataFile)` 与 `FileGenerationUtil.generatePositionDeleteFile(table, dataFile)`，1.4.x 上必须已有这两个方法；
  2. 依赖 v3 表格式支持与 DV 提交链路（同 1384）；
  3. `PlanningBenchmark` 此前无 `@Param` 参数，本提交引入后基准运行时间变为原来的 3 倍（三种布局各跑一次），CI 时间会增长；
  4. 建议与 1384、1386 一并回迁，构成完整的 DV benchmark 套件。
