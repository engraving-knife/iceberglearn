# 提交 1628：Core, Spark: Rewrite data files with high delete ratio (#11825)

## 提交信息

- **序号**：1628 / 4088
- **哈希**：2e2b728ff0d420cebf06b5c548252ae47aa2ca3e
- **短哈希**：2e2b728ff
- **日期**：2025-01-24（Fri Jan 24 07:47:01 2025 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core, Spark: Rewrite data files with high delete ratio
- **PR/Issue**：#11825

## 总体目的

Iceberg 的 `rewriteDataFiles` 动作此前在 `SizeBasedDataRewriter` 中判断一个数据文件是否需要被重写，主要依据三条：

1. 文件大小异常（过小 `wronglySized` 或过大）；
2. 文件附带的 delete 文件数量超过 `delete-file-threshold`（`tooManyDeletes`，默认 `Integer.MAX_VALUE`，即默认不触发）；
3. 分组维度上是否满足 `MIN_INPUT_FILES`、`MIN_FILE_SIZE_BYTES` 等"足够多输入"的触发条件。

但这套机制有一个明显的盲区：当数据文件已经被删除了很高比例的记录（例如 30% 甚至 100% 以上）时，由于"delete 文件数量"并不直接反映"被删除记录数"，`tooManyDeletes` 在默认配置下不会触发，`wronglySized` 也未必命中（文件字节数没变小）。结果是这些"行数已严重缩水但文件大小仍在阈值内"的数据文件长期保留，每次扫描都要带上对应的 delete 文件做行级过滤，造成读取放大与存储浪费。

本提交新增"删除比率（delete ratio）"维度：当某个数据文件中已被删除的记录比例达到 `>= 30%`（硬编码常量 `DELETE_RATIO_THRESHOLD = 0.3`）时，无论文件大小、delete 文件数量是否达标，都将其纳入重写候选；并在分组维度上也加上 `anyTaskHasTooHighDeleteRatio` 触发条件，使即便分组文件数不足 `MIN_INPUT_FILES` 也会被重写。

关键设计点是：删除比率只统计"文件级删除"（file-scoped deletes），即 `ContentFileUtil.isFileScoped` 返回 true 的 delete 文件——这些是 DV（deletion vector）和带 `referencedDataFile` 的位置删除，它们明确指向当前数据文件，能准确表达"该数据文件被删了多少行"；而等值删除（equality deletes）没有具体引用的数据文件，无法归因，故不计入比率，避免误判。

## 如何达成设计目的

在 `SizeBasedDataRewriter` 中：

1. 新增私有常量 `DELETE_RATIO_THRESHOLD = 0.3`；
2. 新增私有方法 `tooHighDeleteRatio(FileScanTask)`：累加任务所带 `task.deletes()` 中所有"file-scoped" delete 文件的 `recordCount`，与数据文件自身的 `recordCount` 比较，得到 `deletedRecords / recordCount`，达到阈值返回 true；
3. 把 `tooHighDeleteRatio` 串联进文件级过滤 `shouldRewrite`（替代原来直接写的 lambda）与分组级过滤 `filterFileGroups` 中新增的 `anyTaskHasTooHighDeleteRatio`；
4. `MockFileScanTask` 新增工厂方法，便于单元测试构造带 file-scoped 删除记录的 mock 任务；
5. Spark 3.5 模块的 `TestRewriteDataFilesAction` 新增 3 个集成测试覆盖 >100%、40%、25% 三档删除比率下重写是否触发；`TestSparkFileRewriter` 新增单元测试验证 30%/29% 边界。

## 修改详情

### `core/src/main/java/org/apache/iceberg/actions/SizeBasedDataRewriter.java`（修改，+30/-2）

**修改目的**：新增"删除比率过高"判断，并在文件级与分组级过滤中接入。

**工作逻辑**：

- 引入 `ContentFile` 与 `ContentFileUtil`；
- 新增常量 `private static final double DELETE_RATIO_THRESHOLD = 0.3;`
- `filterFiles`：把原 lambda 改写为 `this::shouldRewrite`；
- 新增 `shouldRewrite(FileScanTask)`：`return wronglySized(task) || tooManyDeletes(task) || tooHighDeleteRatio(task);`
- `filterFileGroups` 中判断改为 `... || anyTaskHasTooManyDeletes(group) || anyTaskHasTooHighDeleteRatio(group);`
- 新增 `anyTaskHasTooHighDeleteRatio(List<FileScanTask>)`：分组内任意任务命中 `tooHighDeleteRatio` 即重写整组；
- 新增 `tooHighDeleteRatio(FileScanTask)`：
  ```
  if (task.deletes() == null || task.deletes().isEmpty()) return false;
  long knownDeletedRecordCount =
      task.deletes().stream()
          .filter(ContentFileUtil::isFileScoped)
          .mapToLong(ContentFile::recordCount)
          .sum();
  double deletedRecords = (double) Math.min(knownDeletedRecordCount, task.file().recordCount());
  double deleteRatio = deletedRecords / task.file().recordCount();
  return deleteRatio >= DELETE_RATIO_THRESHOLD;
  ```
  - 仅统计 `isFileScoped` 为 true 的删除文件（DV / 带 `referencedDataFile` 的位置删除），等值删除不计入；
  - `Math.min(knownDeletedRecordCount, recordCount)` 截断：DV 在格式版本 v3 下"删除位置数"可能超过数据文件实际行数（如重复删除同一行），截断避免比率 >1 时的异常；
  - 比率 `>= 0.3` 触发重写。

### `core/src/test/java/org/apache/iceberg/MockFileScanTask.java`（修改，+16）

**修改目的**：补一个专门构造"file-scoped 删除记录"任务的工厂方法。

**工作逻辑**：

```
public static MockFileScanTask mockTaskWithFileScopedDeleteRecords(
    long length, long recordCount, int numDeleteFiles, long deletedRecords) {
  DeleteFile[] mockDeletes = new DeleteFile[numDeleteFiles];
  for (int i = 0; i < numDeleteFiles; i++) {
    DeleteFile deleteFile = Mockito.mock(DeleteFile.class);
    Mockito.when(deleteFile.recordCount()).thenReturn(deletedRecords);
    Mockito.when(deleteFile.referencedDataFile()).thenReturn("random data file");
    mockDeletes[i] = deleteFile;
  }
  DataFile dataFile = Mockito.mock(DataFile.class);
  Mockito.when(dataFile.fileSizeInBytes()).thenReturn(length);
  Mockito.when(dataFile.recordCount()).thenReturn(recordCount);
  return new MockFileScanTask(dataFile, mockDeletes);
}
```

关键点：mock 的 `DeleteFile` 设置 `referencedDataFile()` 返回非空字符串，使 `ContentFileUtil.referencedDataFile` 走"显式 referencedDataFile 非空"分支返回非空，进而 `isFileScoped` 返回 true，从而被 `tooHighDeleteRatio` 计入。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java`（修改，+197）

**修改目的**：新增三个集成测试，覆盖不同删除比率下重写动作的行为，覆盖 v2（位置删除）和 v3（DV）两条路径。

**工作逻辑**：

- `testDataFilesRewrittenWithMaxDeleteRatio`：5 个数据文件各 20 行，对每个文件删除 1000 行（>> 20 行，相当于 100%+ 删除），设置 `MIN_INPUT_FILES=10`、`MIN_FILE_SIZE_BYTES=0`（关闭其他触发条件，仅靠 delete ratio 触发）。期望：5 个文件全被重写，新数据文件数为 0（所有行都被删了），delete 文件也清空。v2 走 `writePosDeletes`（每文件拆成 4 个 pos delete 文件），v3 走 `writeDV`（每文件 1 个 DV）。
- `testDataFilesRewrittenWithHighDeleteRatio`：每文件删 8/20=40%，期望全被重写，重写后只剩 1 个新数据文件（80 行存活），delete 文件清空。
- `testDataFilesNotRewrittenWithLowDeleteRatio`：每文件删 5/20=25%，低于 30% 阈值，期望不被重写，`rewrittenDataFilesCount()==0`，文件不变。

新增辅助方法 `writePosDeletes(Table, StructLike, String path, int outputDeleteFiles, int totalPositionsToDelete)`：用 `GenericAppenderFactory.newPosDeleteWriter` 写指定数量的位置删除文件，每个文件均匀分摊要删除的位置数。用于 v2 路径下精确控制删除文件数量与记录数。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestSparkFileRewriter.java`（修改，+22）

**修改目的**：单元级测试验证 `tooHighDeleteRatio` 边界（30% 触发，29% 不触发）。

**工作逻辑**：

新增 `checkDataFilesWithHighFileScopedDeleteRatio(SizeBasedDataRewriter)`：

- 构造 `tooManyDeletesTask = mockTaskWithFileScopedDeleteRecords(1000L, 100, 1, 30)`（30/100=30%，命中阈值）；
- 构造 `optimalTask = mockTaskWithFileScopedDeleteRecords(1000L, 100, 1, 29)`（29/100=29%，不命中）；
- 用 `MIN_FILE_SIZE_BYTES=0`、`DELETE_FILE_THRESHOLD=10`（关闭大小与 delete 文件数触发条件）init rewriter；
- `planFileGroups` 后期望 1 个分组，分组中只含 `tooManyDeletesTask`（optimalTask 不应进组）。

在 3 个已有的 Spark rewriter 测试方法（针对不同构造器/SortDataRewriter）末尾都追加调用本方法，确保三种构造路径都覆盖。

## 小结

- **成效**：补齐了 `rewriteDataFiles` 在"删除比率"维度上的重写触发，解决了"被删除大量记录的数据文件因文件大小未变化而长期不被重写"的盲区；尤其为 v3 的 DV（一个 DV 通常对应一个数据文件，删除记录数可精确归因）提供了天然的触发依据。预期可显著减少读取放大与存储膨胀。
- **影响范围**：仅 `SizeBasedDataRewriter`（被 Spark 3.4/3.5/3.3 等各版本的 `RewriteDataFilesSparkAction` 继承使用）的过滤逻辑，新增判断不影响原有判断；阈值 30% 硬编码未暴露为表属性，后续若需要可参数化。MockFileScanTask 与测试为纯新增，无破坏性。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支需确保已合入 `ContentFileUtil.isFileScoped`（依赖 `DeleteFile.referencedDataFile()`，DV 字段在 v3 元数据中）以及 DV 写入路径相关基础设施，否则 `tooHighDeleteRatio` 在没有 DV 的场景下只能依赖位置删除的 `referencedDataFile`（v2 位置删除通常不显式设置该字段，而是通过 `file_path` 列的 lower/upper bound 推断），效果会受限；
  - 阈值常量 `0.3` 硬编码，1.4.x 若希望可配置需自行加表属性；
  - 集成测试中的 `writeDV` 辅助方法依赖 v3 表格式与 DV 写入器，1.4.x 上需确认这些测试基础设施已就位；若未就位，可只回迁 `tooHighDeleteRatio` 主体逻辑与 `TestSparkFileRewriter` 单元测试，跳过 v3 集成测试。
