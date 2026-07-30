# 提交 1464：Spark 3.5: Write DVs in Spark for V3 tables (#11561)

## 提交信息

- **序号**：1464 / 4088
- **哈希**：c91d3b764aff67d54ecfa942326bc2ed15a0f0bd
- **短哈希**：c91d3b764
- **日期**：2024-12-06（Fri Dec 6 08:46:57 2024 -0800）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：Spark 3.5: Write DVs in Spark for V3 tables (#11561)
- **PR/Issue**：#11561

## 总体目的

Iceberg 表格式 V3 引入了 Deletion Vector（DV，删除向量）作为位置删除（position delete）的替代机制。在 V2 表中，行级删除（DELETE/UPDATE/MERGE 的 MoR 模式）通过写位置删除文件实现，文件中存储 `(file_path, row_position)` 对列表。而 V3 的 DV 采用更紧凑的位图（bitmap）表示，存储在 Puffin 文件中，能显著减少删除文件数量和体积，并提升读取效率（读取时只需加载对应数据文件的 DV 即可，无需扫描多个位置删除文件）。

本提交为 Spark 3.5 模块新增 DV 写入能力：当表格式版本（format-version）>= 3 时，Spark 的行级操作（DELETE、UPDATE、MERGE 的 MoR 模式）通过 `SparkPositionDeltaWrite` 写出 DV 文件而非传统的位置删除文件。这包括：

1. 新增 `PartitioningDVWriter`——一个按分区累积删除位置并写出 DV 文件的写入器。
2. 在 `SparkWriteConf` 中新增 `useDVs()` 判断（format-version >= 3 时为 true）。
3. 在 `SparkPositionDeltaWrite` 中根据 `useDVs()` 选择 DV 写入器或传统位置删除写入器。
4. 调整 `SparkBatchQueryScan` 的可重写删除文件判定逻辑——使用 DV 时，所有位置删除文件都需被重写为 DV（不仅仅是文件级 file-scoped 的）。
5. 扩展测试框架，新增 format-version=3 的测试参数组合，并新增"DV 与历史位置删除共存"场景的测试。

## 如何达成设计目的

整体设计围绕"何时用 DV、谁来写 DV、如何处理已有位置删除"三个问题展开：

**何时用 DV**：在 `SparkWriteConf` 中新增 `useDVs()` 方法，通过 `TableOperations` 获取表当前元数据的 `formatVersion()`，当 >= 3 时返回 true。该判断被 `SparkPositionDeltaWrite.Context` 构造时缓存。

**谁来写 DV**：新增 `PartitioningDVWriter<T>` 类（实现 `PartitioningWriter<PositionDelete<T>, DeleteWriteResult>`），内部委托给 `BaseDVFileWriter`。在 `SparkPositionDeltaWrite.newDeleteWriter()` 中，当 `context.useDVs()` 为 true 时返回 `PartitioningDVWriter`，否则保持原有逻辑（`ClusteredPositionDeleteWriter` 或 `FanoutPositionOnlyDeleteWriter`）。

**如何处理已有位置删除**：当表从 V2 升级到 V3 后，已有的位置删除文件需要被重写为 DV。为此：
- `SparkBatchQueryScan.rewritableDeletes()` 新增 `forDVs` 参数：当为 true 时，所有非 equality deletes 都被视为可重写（因为 DV 要替换全部位置删除）；当为 false 时，仅 file-scoped 的位置删除被视为可重写（保持原有 V2 行为）。
- `SparkPositionDeltaWrite` 中新增 `shouldRewriteDeletes()` 判断：当 `useDVs()` 或 `deleteGranularity == FILE` 时需要重写删除文件。
- `PreviousDeleteLoader` 重构为通过静态工厂 `create()` 创建，使得 DV 写入路径也能加载已有删除（用于 DV 合并历史删除位置）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/PartitioningDVWriter.java`（新增）

**修改目的**：提供按分区写出 DV 文件的写入器。

**工作逻辑**：新类 `PartitioningDVWriter<T>` 实现 `PartitioningWriter<PositionDelete<T>, DeleteWriteResult>` 接口。
- 构造函数接收 `OutputFileFactory`（输出文件工厂）和 `Function<CharSequence, PositionDeleteIndex>`（加载已有删除的回调），内部创建 `BaseDVFileWriter` 实例。
- `write(PositionDelete<T> row, PartitionSpec spec, StructLike partition)`：将删除位置（`row.path()` 和 `row.pos()`）连同分区信息传递给 `BaseDVFileWriter.delete()`。
- `result()`：返回 `DeleteWriteResult`，需在 `close()` 后调用（有前置检查 `result != null`）。
- `close()`：关闭底层 `BaseDVFileWriter` 并缓存结果，保证幂等（`result == null` 时才关闭）。

该类是 DV 写入路径的核心，将 Spark 的 `PositionDelete` 行级删除请求转化为 DV 文件输出。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java`

**修改目的**：提供是否使用 DV 的判断方法。

**工作逻辑**：
- 新增 import：`HasTableOperations`、`TableOperations`。
- 新增方法：
  ```java
  public boolean useDVs() {
    TableOperations ops = ((HasTableOperations) table).operations();
    return ops.current().formatVersion() >= 3;
  }
  ```
  通过 `HasTableOperations` 接口获取表的 `TableOperations`，再取当前元数据（`current()`）的 `formatVersion`，>= 3 即启用 DV。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatchQueryScan.java`

**修改目的**：调整可重写删除文件的判定逻辑以支持 DV 场景。

**工作逻辑**：
- 新增 import：`FileContent`。
- 方法签名变更：`rewritableDeletes()` → `rewritableDeletes(boolean forDVs)`。
- 在遍历删除文件时，判定条件从 `ContentFileUtil.isFileScoped(deleteFile)` 改为 `shouldRewrite(deleteFile, forDVs)`。
- 新增私有方法 `shouldRewrite(DeleteFile deleteFile, boolean forDVs)`：
  ```java
  // for DVs all position deletes must be rewritten
  // for position deletes, only file-scoped deletes must be rewritten
  private boolean shouldRewrite(DeleteFile deleteFile, boolean forDVs) {
    if (forDVs) {
      return deleteFile.content() != FileContent.EQUALITY_DELETES;
    }
    return ContentFileUtil.isFileScoped(deleteFile);
  }
  ```
  即：DV 模式下所有非 equality deletes 都需重写（位置删除要转为 DV）；传统模式下仅 file-scoped 的需重写。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java`

**修改目的**：在行级操作写入路径中集成 DV 写入器。

**工作逻辑**（多处改动）：

1. **新增 import**：`PartitioningDVWriter`。

2. **`broadcastRewritableDeletes()` 方法调整**：
   ```java
   // 修改前
   if (context.deleteGranularity() == DeleteGranularity.FILE && scan != null) {
     Map<String, DeleteFileSet> rewritableDeletes = scan.rewritableDeletes();
   // 修改后
   if (scan != null && shouldRewriteDeletes()) {
     Map<String, DeleteFileSet> rewritableDeletes = scan.rewritableDeletes(context.useDVs());
   ```
   触发条件从"仅 FILE 粒度"改为"shouldRewriteDeletes()"（含 DV 场景），并传递 `useDVs` 标志。

3. **新增 `shouldRewriteDeletes()` 方法**：
   ```java
   private boolean shouldRewriteDeletes() {
     // deletes must be rewritten when there are DVs and file-scoped deletes
     return context.useDVs() || context.deleteGranularity() == DeleteGranularity.FILE;
   }
   ```

4. **`newDeleteWriter()` 方法调整**：在方法开头提取 `previousDeleteLoader`（通过新增的 `PreviousDeleteLoader.create()` 工厂方法），然后在写入器选择逻辑最前面新增 DV 分支：
   ```java
   if (context.useDVs()) {
     return new PartitioningDVWriter<>(files, previousDeleteLoader);
   } else if (inputOrdered && rewritableDeletes == null) {
     return new ClusteredPositionOnlyDeleteWriter<>(...);
   } else {
     return new FanoutPositionOnlyDeleteWriter<>(..., previousDeleteLoader);
   }
   ```
   原有 `FanoutPositionOnlyDeleteWriter` 的 previousDeleteLoader 参数也改为使用提取出的变量（之前是内联三元判断）。

5. **`PreviousDeleteLoader` 重构**：构造函数改为 private，新增静态工厂方法 `create(Table, Map<String, DeleteFileSet>)`，当 `deleteFiles == null` 时返回 `path -> null`（无历史删除），否则创建实例。这使得 DV 写入路径也能复用该加载器。

6. **`Context` 内部类新增 `useDVs` 字段**：在构造函数中通过 `writeConf.useDVs()` 初始化，并提供 `useDVs()` 访问方法。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkRowLevelOperationsTestBase.java`

**修改目的**：扩展测试基类以支持 format-version=3 参数化测试。

**工作逻辑**：
- 新增多个静态 import（`ADDED_DVS_PROP`、`ADD_POS_DELETE_FILES_PROP`、`FORMAT_VERSION`、`TableProperties`、`DeleteGranularity`）。
- 新增 `@Parameter(index = 9) protected int formatVersion;` 字段，并更新 `@Parameters` 的 name 模板和参数数组：原有 4 组参数均补充 `formatVersion = 2`，并新增 2 组 `formatVersion = 3` 的参数（testhadoop + SparkCatalog、spark_catalog + SparkSessionCatalog）。
- `initTable()` 方法的 `ALTER TABLE ... SET TBLPROPERTIES` SQL 新增 `FORMAT_VERSION` 属性设置。
- `validateSnapshotCount` 类方法（`validateSnapshot` 相关）：当 `formatVersion >= 3` 时，额外校验 `ADDED_DVS_PROP` 等于 `addedDeleteFiles`，并断言 `ADD_POS_DELETE_FILES_PROP` 不存在（V3 不再写位置删除文件）。
- 新增辅助方法 `createTableWithDeleteGranularity(String schema, String partitionedBy, DeleteGranularity)`，封装建表并设置删除粒度的逻辑（被各 MoR 子类测试复用）。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java`

**修改目的**：适配 V3 表的删除文件断言（DV 代替位置删除）。

**工作逻辑**：
- 新增 import：`MERGE_ON_READ`、`ADDED_DVS_PROP`。
- 在多处快照校验中新增 `formatVersion >= 3` 分支：MoR 模式下 V3 表断言 `ADDED_DELETE_FILES_PROP` 和 `ADDED_DVS_PROP` 均为预期值（如 "4"），而 V2 表走原有逻辑。
- 在某 DELETE 测试中，将 `ADD_POS_DELETE_FILES_PROP` 断言改为根据 formatVersion 选择 `ADDED_DVS_PROP`（V3）或 `ADD_POS_DELETE_FILES_PROP`（V2）。
- 在另一 DELETE 测试中新增 V3 分支调用 `validateMergeOnRead`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java`

**修改目的**：跳过 V3 表不适用的合并测试。

**工作逻辑**：在 `testCoalesceMerge()` 方法开头新增 `assumeThat(formatVersion).isLessThan(3);`，因 V3 的 DV 写入逻辑与该测试的合并预期不符，跳过 V3 参数组合。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadDelete.java`

**修改目的**：为 MoR DELETE 新增 DV 相关测试，并适配 V3。

**工作逻辑**：
- 新增 import：`assumeThat`、`Set`、`Collectors`、`DeleteFile`、`TestHelpers`、`ContentFileUtil`。
- `extraTableProperties()` 不再硬编码 `FORMAT_VERSION=2`（改为由基类参数控制），仅设置 `DELETE_MODE=MERGE_ON_READ`。
- `testDeleteFileGranularity()` 和 `testDeletePartitionGranularity()` 新增 `assumeThat(formatVersion).isEqualTo(2);`（删除粒度仅适用于 V2 位置删除）。
- 新增测试 `testDeleteWithDVAndHistoricalPositionDeletes()`（仅 V2 触发，因流程需先以 V2 写位置删除再升级 V3）：
  - 先以 V2 + PARTITION 粒度执行 DELETE（产生分区级位置删除）。
  - 再切换为 FILE 粒度执行 DELETE（产生文件级位置删除）。
  - 再升级表为 V3（`FORMAT_VERSION=3`）。
  - 执行第三次 DELETE，验证产生 1 个 DV 文件，其 recordCount=3（2 个历史删除位置 + 1 个新删除位置被合并进 DV）。
- `checkDeleteFileGranularity` 重构为使用 `createTableWithDeleteGranularity` 辅助方法。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadMerge.java`

**修改目的**：为 MoR MERGE 新增 DV 相关测试，并适配 V3。

**工作逻辑**：
- 新增 import：`assumeThat`、`List`、`Set`、`Collectors`、`IntStream`、`DeleteFile`、`TestHelpers`、`ContentFileUtil`。
- `extraTableProperties()` 不再硬编码 `FORMAT_VERSION=2`，仅设置 `MERGE_MODE=MERGE_ON_READ`。
- `testMergeDeleteFileGranularity()` 和 `testMergeDeletePartitionGranularity()` 新增 `assumeThat(formatVersion).isEqualTo(2);`。
- 新增测试 `testMergeWithDVAndHistoricalPositionDeletes()`（仅 V2 触发）：流程与 DELETE 版本类似——先 V2 + PARTITION 粒度 MERGE 产生分区级位置删除，再切换 FILE 粒度 MERGE 产生文件级位置删除，再升级 V3，最后 MERGE 验证产生 1 个 recordCount=3 的 DV 文件。
- `checkMergeDeleteGranularity` 重构为使用 `createTableWithDeleteGranularity`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadUpdate.java`

**修改目的**：为 MoR UPDATE 新增 DV 相关测试，并适配 V3。

**工作逻辑**：
- 新增 import：`List`、`Set`、`Collectors`、`DeleteFile`、`TestHelpers`、`ContentFileUtil`。
- `extraTableProperties()` 不再硬编码 `FORMAT_VERSION=2`，仅设置 `UPDATE_MODE=MERGE_ON_READ`。
- `testUpdateFileGranularity()` 和 `testUpdatePartitionGranularity()` 新增 `assumeThat(formatVersion).isEqualTo(2);`。
- 两个原有测试方法重命名：`testUpdateFileGranularityMergesDeleteFiles` → `testPositionDeletesAreMaintainedDuringUpdate`，`testUpdateUnpartitionedFileGranularityMergesDeleteFiles` → `testUnpartitionedPositionDeletesAreMaintainedDuringUpdate`，并均新增 `assumeThat(formatVersion).isEqualTo(2);`。
- 新增测试 `testUpdateWithDVAndHistoricalPositionDeletes()`（仅 V2 触发）：流程类似——先 V2 + PARTITION 粒度 UPDATE，再切换 FILE 粒度 UPDATE，再升级 V3，最后 UPDATE 验证产生 1 个 recordCount=3 的 DV 文件。
- `initTable` 重构为使用 `createTableWithDeleteGranularity`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestUpdate.java`

**修改目的**：适配 V3 表的更新删除文件断言。

**工作逻辑**：
- 新增 import：`MERGE_ON_READ`。
- 在两处快照校验中新增 `formatVersion >= 3` 分支：MoR 模式下 V3 表断言 `ADDED_DELETE_FILES_PROP` 和 `ADDED_DVS_PROP`，或调用 `validateMergeOnRead`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java`

**修改目的**：新增测试辅助方法以获取快照的删除文件集合。

**工作逻辑**：
- 新增 import：`Snapshot`。
- 新增静态方法 `deleteFiles(Table table, Snapshot snapshot)`：
  ```java
  public static Set<DeleteFile> deleteFiles(Table table, Snapshot snapshot) {
    DeleteFileSet deleteFiles = DeleteFileSet.create();
    for (FileScanTask task : table.newScan().useSnapshot(snapshot.snapshotId()).planFiles()) {
      deleteFiles.addAll(task.deletes());
    }
    return deleteFiles;
  }
  ```
  通过对指定快照执行表扫描，收集所有 FileScanTask 中的删除文件。被各 MoR DV 测试用于验证 DV 文件数量和记录数。

## 小结

- **成效**：Spark 3.5 模块现支持对 V3 表（format-version >= 3）写入 DV（Deletion Vector）文件，替代传统的位置删除文件。行级操作（DELETE/UPDATE/MERGE 的 MoR 模式）在 V3 表上会写出 DV 文件，并能将已有的位置删除合并进 DV。新增 `PartitioningDVWriter` 作为 DV 写入器，`SparkWriteConf.useDVs()` 作为启用判断，`SparkBatchQueryScan` 和 `SparkPositionDeltaWrite` 的删除重写逻辑相应调整。测试新增 2 组 V3 参数组合和 3 个"DV 与历史位置删除共存"场景测试。
- **影响范围**：涉及 12 个文件（1 个新增核心类、3 个 Spark 3.5 主代码文件、8 个测试文件），新增 415 行、删除 52 行。核心改动在 Spark 3.5 模块，新增的 `PartitioningDVWriter` 在 core 模块（可供其他引擎复用）。仅影响 V3 表的行级操作行为，V2 表行为完全不变。
- **回迁到 1.4.x 的注意事项**：这是一个重要的功能新增（V3 表 DV 写入），**不建议回迁到 1.4.x**。理由：(1) 1.4.x 作为维护分支应保持功能稳定，不应引入新的表格式特性；(2) DV 写入依赖 core 模块中已有的 DV 基础设施（`BaseDVFileWriter`、`DVFileWriter` 等），若 1.4.x 的 core 模块尚未包含这些基础设施，回迁需要连带回迁大量前置改动，风险高；(3) V3 表格式本身可能在 1.4.x 中尚未完全支持或仍处于实验阶段；(4) 该改动新增了测试参数组合，回迁后会增加 1.4.x 的 CI 测试时长。若 1.4.x 确需 DV 支持，应整体评估 V3 格式在 1.4.x 的成熟度，而非单独回迁此提交。
