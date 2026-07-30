# 提交 1152：Core: Parallelize manifest writing for many new files (#11086)

## 提交信息

- **序号**：1152 / 4088
- **哈希**：d2087a04bd6ba62b13f2540480a18b5edc710e8e
- **短哈希**：d2087a04b
- **日期**：2024-09-12（Thu Sep 12 16:41:05 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Parallelize manifest writing for many new files (#11086)
- **PR/Issue**：#11086

## 总体目的

Iceberg 在执行 `AppendFiles` / `OverwriteFiles` / `RowDelta` 等快照提交时，需要将新增的数据文件（`DataFile`）和删除文件（`DeleteFile`）写入到 manifest 文件中。原来的实现是**单线程**串行写入：当一个提交涉及大量新文件（百万级）时，manifest 写入会成为整个 commit 的瓶颈，造成提交耗时显著增加。

本提交将 manifest 写入改造为**可并行**：当待写文件总数足够大时，按 worker 线程池大小把文件分成多组，多线程并发地写出多个 manifest，再合并到结果列表中。同时对很小的提交（文件数较少）保持单线程，避免产生过多过小的 manifest 文件反而拖累读取端性能。

## 如何达成设计目的

1. 在 `SnapshotProducer` 抽象基类中新增统一的 `writeDataManifests` / `writeDeleteManifests` 入口和私有的 `writeManifests` 通用并行框架，让 `FastAppend` 与 `MergingSnapshotProducer` 都走这条路径，消除两份重复的串行写入代码。
2. 引入常量 `MIN_FILE_GROUP_SIZE = 10_000` 作为"是否值得分组并行"的阈值；用 `manifestWriterCount(workerPoolSize, fileCount)` 计算实际并行度：`floor(fileCount / 10_000)` 四舍五入后再与 worker 池大小取 min，至少为 1。
3. 用 `divide(list, groupCount)`（基于 `Lists.partition`，按"向上取整"的 groupSize 切分）把文件均匀分成 N 组，再用 `Tasks.foreach(groups).executeWith(ThreadPools.getWorkerPool())` 并发执行，结果汇入 `Queues.newConcurrentLinkedQueue`。
4. 把原本在 `MergingSnapshotProducer` 内部私有的 `DeleteFileHolder` 提升到 `SnapshotProducer` 中作为 `protected static` 内部类，供新的 `writeDeleteManifests` 使用。
5. 新增 `TestSnapshotProducer` 单元测试覆盖 `manifestWriterCount` 的边界行为；在 `TestFastAppend` / `TestMergeAppend` 中新增 `testAddManyFiles`，写入 `2 * MIN_FILE_GROUP_SIZE` 个文件验证并行路径下数据完整性。
6. 改造 JMH 基准 `AppendBenchmark`：在 schema 中增加 7 个字符串列（增大单条记录体积，让 manifest 文件更大、更贴近真实场景），新增 `50000` / `100000` 两个较小的 `numFiles` 参数便于衡量小规模并行效果，移除不再使用的 `Blackhole` 参数。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`

**修改目的**：在快照生成基类中提供统一的并行 manifest 写入能力。

**工作逻辑**：
- 新增常量 `MIN_FILE_GROUP_SIZE = 10_000`：每个 manifest 至少承载 1 万个文件，避免拆得过细。
- 新增 `protected List<ManifestFile> writeDataManifests(List<DataFile> files, PartitionSpec spec)`：不带 `dataSeq` 重载，等价于沿用最新 sequence number。
- 新增 `protected List<ManifestFile> writeDataManifests(List<DataFile> files, Long dataSeq, PartitionSpec spec)`：内部委托给 `writeManifests(files, group -> writeDataFileGroup(group, dataSeq, spec))`。
- 新增 `private List<ManifestFile> writeDataFileGroup(...)`：原"创建 `RollingManifestWriter` → 逐个 `add(file[, dataSeq])` → close → `toManifestFiles()`"的串行逻辑被搬入这里，作为单个分组的写入函数。
- 新增 `protected List<ManifestFile> writeDeleteManifests(List<DeleteFileHolder> files, PartitionSpec spec)` 与 `private List<ManifestFile> writeDeleteFileGroup(...)`：删除文件的等价实现。
- 新增 `private static <F> List<ManifestFile> writeManifests(List<F> files, Function<List<F>, List<ManifestFile>> writeFunc)`：核心并行框架。先按 `manifestWriterCount(WORKER_THREAD_POOL_SIZE, files.size())` 计算并行度；`divide(files, parallelism)` 把文件分成 N 组；用 `Tasks.foreach(groups).stopOnFailure().throwFailureWhenFinished().executeWith(ThreadPools.getWorkerPool()).run(group -> manifests.addAll(writeFunc.apply(group)))` 并发写出，结果汇入 `Queues.newConcurrentLinkedQueue()`，最终返回 `ImmutableList.copyOf(manifests)`。
- 新增 `private static <T> List<List<T>> divide(List<T> list, int groupCount)`：用 `IntMath.divide(list.size(), groupCount, RoundingMode.CEILING)` 算出 groupSize，再 `Lists.partition(list, groupSize)` 切分。
- 新增 `@VisibleForTesting static int manifestWriterCount(int workerPoolSize, int fileCount)`：`limit = round(fileCount / 10_000)`（`RoundingMode.HALF_UP`），返回 `max(1, min(workerPoolSize, limit))`。即只有当文件数 ≥ 1.5 倍 `MIN_FILE_GROUP_SIZE`（15000）时才会用到 2 个 writer，依此类推；文件数太少时退化为 1 个 writer。
- 新增 `protected static class DeleteFileHolder`：从 `MergingSnapshotProducer` 上移过来，字段与方法保持一致（`deleteFile`、`dataSequenceNumber`），只是访问级别改为 `protected` 以便子类共享。

### `core/src/main/java/org/apache/iceberg/FastAppend.java`

**修改目的**：让 fast append 走新的并行入口。

**工作逻辑**：原本在 `if (newManifests == null && !newFiles.isEmpty())` 分支内手动开 `RollingManifestWriter`、`newFiles.forEach(writer::add)`、`close`、`toManifestFiles()` 的约 8 行代码，整体替换为一行 `this.newManifests = writeDataManifests(newFiles, spec);`，逻辑等价但获得并行能力。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`

**修改目的**：让 merge 类快照（overwrite / rowDelta 等）的 data 与 delete manifest 写入走新的并行入口，并删除重复代码。

**工作逻辑**：
- 删除 `import org.apache.iceberg.exceptions.RuntimeIOException;`（不再需要手动 try/catch 抛 `RuntimeIOException`）。
- `cachedNewDataManifests` 写入分支：原 18 行手写 try/finally 替换为 `List<ManifestFile> newDataManifests = writeDataManifests(newDataFiles, newDataFilesDataSequenceNumber, dataSpec); cachedNewDataManifests.addAll(newDataManifests);`。
- `cachedNewDeleteManifests` 写入分支：原 22 行手写 try/finally 替换为 `List<ManifestFile> newDeleteManifests = writeDeleteManifests(deleteFiles, spec); cachedNewDeleteManifests.addAll(newDeleteManifests);`。
- 文件末尾删除原本的 `private static class DeleteFileHolder { ... }` 整段（约 35 行），因为它已上移到 `SnapshotProducer`。

### `core/src/jmh/java/org/apache/iceberg/AppendBenchmark.java`

**修改目的**：增强基准测试以衡量并行 manifest 写入在不同文件规模下的收益。

**工作逻辑**：
- schema 中新增 7 个 `str_col1`~`str_col7` 字符串列，让每行数据体积更大，更接近实际生产中的 manifest 大小。
- `numFiles` 参数从 `{"500000", "1000000", "2500000"}` 扩展为 `{"50000", "100000", "500000", "1000000", "2500000"}`，新增小规模场景，便于观察并行化在小数据量下的边界。
- 移除未使用的 `import org.openjdk.jmh.infra.Blackhole;`，方法签名 `appendFiles(Blackhole blackhole)` 改为 `appendFiles()`（JMH 基准方法本身不需要消费返回值）。

### `core/src/test/java/org/apache/iceberg/TestBase.java`

**修改目的**：让 `validateTableFiles` 支持以 `Collection<DataFile>` 形式传入，方便新测试用例传 `List`。

**工作逻辑**：新增 `import java.util.Collection;`，原有 `validateTableFiles(Table tbl, DataFile... expectedFiles)` 改为委托给新的 `validateTableFiles(Table tbl, Collection<DataFile> expectedFiles)`，逻辑不变。

### `core/src/test/java/org/apache/iceberg/TestFastAppend.java`

**修改目的**：覆盖 FastAppend 路径下大文件量并行写入的正确性。

**工作逻辑**：新增 `testAddManyFiles` 测试：循环 `2 * MIN_FILE_GROUP_SIZE`（即 20000）次生成 `DataFile`，分区按 `ordinal % 2` 交替；通过 `table.newAppend()` 全部 append 后 commit；用 `validateTableFiles` 校验所有文件都正确出现在表中。该规模会触发 2 个并行 writer。

### `core/src/test/java/org/apache/iceberg/TestMergeAppend.java`

**修改目的**：覆盖 MergeAppend 路径下大文件量并行写入的正确性。

**工作逻辑**：与 `TestFastAppend.testAddManyFiles` 几乎相同（同样 20000 个文件、交替分区），区别是这里走的是 `table.newAppend()`（MergeAppend 而非 FastAppend），同样触发 `MergingSnapshotProducer` 中的并行写入分支。新增 `import Lists` 与 `TestHelpers.Row`。

### `core/src/test/java/org/apache/iceberg/TestSnapshotProducer.java`（新增）

**修改目的**：单独覆盖 `manifestWriterCount` 静态方法的边界行为。

**工作逻辑**：通过 `assertManifestWriterCount(workerPoolSize, fileCount, expected, errMsg)` 辅助方法断言 7 种场景：
- 4 worker / 100 files → 1（文件太少，单 writer）
- 4 worker / `MIN_FILE_GROUP_SIZE`(10000) → 1
- 4 worker / `MIN_FILE_GROUP_SIZE + 1` → 1（仍未达到 1.5x 阈值）
- 4 worker / `1.25 * MIN_FILE_GROUP_SIZE`(12500) → 1
- 4 worker / `1.5 * MIN_FILE_GROUP_SIZE`(15000) → 2（首次触发并行）
- 3 worker / `100 * MIN_FILE_GROUP_SIZE`(1000000) → 3（受限于 worker 池大小）
- 32 worker / `5 * MIN_FILE_GROUP_SIZE`(50000) → 5（避免产生过小 manifest）

## 小结

- **成效**：当单次提交涉及大量新文件时，manifest 写入现在可以按 worker 线程池并发执行，显著缩短 commit 耗时；同时通过 `MIN_FILE_GROUP_SIZE` 阈值保证小提交仍走单线程，不会产生过多过小的 manifest 而拖累后续读取。代码层面还顺带消除了 `FastAppend` 与 `MergingSnapshotProducer` 中两份重复的 manifest 写入样板代码。
- **影响范围**：核心 `core` 模块，涉及 `SnapshotProducer` / `FastAppend` / `MergingSnapshotProducer` 三个生产类，外加 JMH 基准和测试。无 API 兼容性变更（新增方法均为 `protected` / `private` / package-private）。
- **回迁到 1.4.x 的注意事项**：该改动是性能优化，行为语义保持等价（写入的 manifest 内容与原来一致，只是分组粒度可能不同）。回迁时需注意：
  1. 必须同时回迁 `SnapshotProducer`（新增的并行框架与 `DeleteFileHolder`）、`FastAppend`、`MergingSnapshotProducer`（删除原内部类与重复逻辑）三个文件，否则会编译失败。
  2. 1.4.x 若 worker 线程池配置（`ThreadPools.WORKER_THREAD_POOL_SIZE`）与 main 不同，需要确认并行度上限符合预期；默认值通常是 `max(2, availableProcessors)`，行为一致。
  3. 由于 manifest 拆分粒度变化，部分依赖"单次 append 只产生 1 个 manifest"的测试可能需要调整（1.4.x 现有测试若涉及大量文件应关注此点）。
  4. 改动本身不改变元数据格式与表协议，与 1.4.x 已有 manifest 完全兼容，无升级风险。
