# 提交 1384：Spark 3.5: Adapt DeleteFileIndexBenchmark for DVs (#11529)

## 提交信息

- **序号**：1384 / 4088
- **哈希**：315e1543743c7e72e5d66f0604f99414357ad772
- **短哈希**：315e15437
- **日期**：2024-11-15（Fri Nov 15 22:12:45 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Spark 3.5: Adapt DeleteFileIndexBenchmark for DVs
- **PR/Issue**：#11529

## 总体目的

`DeleteFileIndexBenchmark` 是 Iceberg Spark 3.5 模块下的 JMH 基准，用于度量 `DeleteFileIndex` 构建性能（即扫描时索引删除文件、按数据文件过滤出"应用哪些删除"的开销）。此前该基准只支持两种删除文件布局：

- `oneToOneMapping=true`：file-scoped 位置删除（每个数据文件对应一个位置删除文件）；
- `oneToOneMapping=false`：partition-scoped 位置删除（每个分区一个位置删除文件，覆盖分区内所有数据文件）。

随着 DV（Deletion Vector）功能的落地，需要把 DV 作为第三种删除文件布局纳入基准，度量 `DeleteFileIndex` 在 DV 场景下的构建开销，以便与传统的位置删除对比。DV 与位置删除在索引层面不同：DV 按 `referencedDataFile` 一对一绑定数据文件，而位置删除可能按分区或按文件粒度组织。

本提交把基准的 `@Param` 从布尔 `oneToOneMapping` 改为三值字符串 `type`（`partition`/`file`/`dv`），新增 `initDataAndDVs()` 方法生成 DV 删除文件，并在表创建时按 `type` 决定 format version（DV 需要 v3）。

## 如何达成设计目的

- 把 `@Param({"true", "false"}) private boolean oneToOneMapping` 改为 `@Param({"partition", "file", "dv"}) private String type`，语义更清晰且可扩展。
- `initDataAndDeletes()` 改为三分支：`partition` → `initDataAndPartitionScopedDeletes()`；`file` → `initDataAndFileScopedDeletes()`；其它 → `initDataAndDVs()`。
- 新增 `initDataAndDVs()`：对每个分区，循环生成数据文件 + 对应 DV，用 `RowDelta` 提交。每个 DV 通过 `FileGenerationUtil.generateDV(table, dataFile)` 生成，与数据文件一对一绑定。
- 表属性 `FORMAT_VERSION` 从固定 `2` 改为 `type.equals("dv") ? 3 : 2`，因为 DV 仅在 v3 表上支持。

## 修改详情

### `spark/v3.5/spark-extensions/src/jmh/java/org/apache/iceberg/DeleteFileIndexBenchmark.java`（修改，+24/-5 行）

**修改目的**：让基准支持 DV 布局。

**工作逻辑**：

1. 参数变更：
   ```java
   // before
   @Param({"true", "false"})
   private boolean oneToOneMapping;
   // after
   @Param({"partition", "file", "dv"})
   private String type;
   ```

2. 分发逻辑：
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
   注意原来的 `oneToOneMapping=true` 对应 file-scoped（一对一映射），`false` 对应 partition-scoped，语义不直观；新的 `type` 参数直接用布局名，更清晰。

3. 新增 `initDataAndDVs()`：
   ```java
   private void initDataAndDVs() {
     for (int partitionOrdinal = 0; partitionOrdinal < NUM_PARTITIONS; partitionOrdinal++) {
       StructLike partition = TestHelpers.Row.of(partitionOrdinal);
       RowDelta rowDelta = table.newRowDelta();
       for (int fileOrdinal = 0; fileOrdinal < NUM_DATA_FILES_PER_PARTITION; fileOrdinal++) {
         DataFile dataFile = FileGenerationUtil.generateDataFile(table, partition);
         DeleteFile dv = FileGenerationUtil.generateDV(table, dataFile);
         rowDelta.addRows(dataFile);
         rowDelta.addDeletes(dv);
       }
       rowDelta.commit();
     }
   }
   ```
   每个数据文件配一个 DV（`FileGenerationUtil.generateDV`），通过 `RowDelta.addRows` + `addDeletes` 一起提交。这与 file-scoped 位置删除的布局一致（一对一），但删除文件格式为 Puffin（DV）而非 Parquet（位置删除）。

4. 表 format version：
   ```java
   TableProperties.FORMAT_VERSION,
   type.equals("dv") ? 3 : 2);
   ```
   DV 仅在 v3 表上支持，partition/file 布局仍用 v2。

## 小结

- **成效**：`DeleteFileIndexBenchmark` 现支持 `partition`/`file`/`dv` 三种删除布局，可度量 `DeleteFileIndex` 在 DV 场景下的构建开销并与传统位置删除对比。依赖 `FileGenerationUtil.generateDV`（由前序 DV 写入链路提交提供）。
- **影响范围**：仅 `spark/v3.5/spark-extensions` 的 JMH 基准一个文件，纯基准代码变更，无生产代码改动。
- **回迁到 1.4.x 的注意事项**：
  1. 依赖 `FileGenerationUtil.generateDV(table, dataFile)` 方法，1.4.x 上必须已有该方法（由 DV 写入链路提交提供）；
  2. 依赖 v3 表格式支持（`TableProperties.FORMAT_VERSION = 3`），1.4.x 必须支持 v3；
  3. 依赖 `RowDelta.addDeletes(DeleteFile)` 能接受 DV 类型的 DeleteFile（format=PUFFIN），1.4.x 的 commit 链路必须已支持 DV 提交（如 1367 `Core: Support commits with DVs`）；
  4. 这是 JMH 基准代码，回迁不影响运行时行为，但需确保 DV 写入基础设施在 1.4.x 上已就绪，否则基准无法运行；
  5. 建议与 1385、1386 一并回迁，构成完整的 DV benchmark 套件。
