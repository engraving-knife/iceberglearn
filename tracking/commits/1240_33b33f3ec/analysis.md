# 提交 1240：Core: Rename DeleteFileHolder to PendingDeleteFile / Optimize duplicate data/delete file detection (#11254)

## 提交信息

- **序号**：1240 / 4088
- **哈希**：33b33f3ec835ce69025aa827b5b5ba6e2fbd98dd
- **短哈希**：33b33f3ec
- **日期**：2024-10-15（Tue Oct 15 20:02:49 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Rename DeleteFileHolder to PendingDeleteFile / Optimize duplicate data/delete file detection (#11254)
- **PR/Issue**：#11254

## 总体目的

本提交在 #11158（提交 6a5ae1ae6，引入 `DataFileSet`/`DeleteFileSet`）的基础上，对 `MergingSnapshotProducer` 中"新增数据/删除文件"的去重与组织方式做进一步重构，主要解决两件事：

1. **重命名 `DeleteFileHolder` 为 `PendingDeleteFile` 并让它实现 `DeleteFile` 接口**。原本 `DeleteFileHolder` 是一个包装类（持有 `DeleteFile deleteFile` + `Long dataSequenceNumber`），仅作为 `MergingSnapshotProducer` 内部传递"待提交的 delete 文件 + 期望的 dataSequenceNumber"的容器，不能直接作为 `DeleteFile` 使用，调用方需要 `.deleteFile()` 解包。这让"把待提交 delete 文件放入 `DeleteFileSet`"变得别扭——`DeleteFileSet` 按 `DeleteFile.location()` 去重，而 `DeleteFileHolder` 不是 `DeleteFile`，必须先解包再添加。重命名为 `PendingDeleteFile` 并实现 `DeleteFile` 接口后，`PendingDeleteFile` 本身就是 `DeleteFile`，可以直接放入 `DeleteFileSet`，去重基准（location）与其它 delete 文件一致；同时它仍携带 dataSequenceNumber 信息供 manifest 写入时使用。

2. **优化 `MergingSnapshotProducer` 中"按 spec 分桶 + 去重"的结构**。原结构维护了三组并行集合：`Map<PartitionSpec, List<DataFile>> newDataFilesBySpec`、`DataFileSet newDataFiles`（全局去重）、`Map<Integer, List<DeleteFileHolder>> newDeleteFilesBySpec`（按 specId 分桶）、`DeleteFileSet newDeleteFiles`（全局去重）。也就是说，"分桶"与"去重"是分开维护的，每加入一个文件既要更新分桶 Map 又要更新全局 set。重构后直接把"分桶 Map 的 value"换成 set 类型（`Map<PartitionSpec, DataFileSet>` 与 `Map<Integer, DeleteFileSet>`），让分桶 Map 本身就承担去重职责，从而移除冗余的 `newDataFiles` / `newDeleteFiles` 全局 set，简化字段、减少同步成本。

3. **顺便清理**：`addedDataFilesBySpec()` 方法在仓库中已无调用方，删除；`addedDataFiles()` 改为基于 `newDataFilesBySpec.values()` 流式收集。`writeDeleteManifests` / `writeDeleteFileGroup` 形参类型从 `Collection<DeleteFileHolder>` 改为 `Collection<DeleteFile>`，并在写入时校验 `file instanceof PendingDeleteFile` 以保留 dataSequenceNumber 语义。

## 如何达成设计目的

1. **`PendingDeleteFile` 实现 `DeleteFile`**：在 `SnapshotProducer` 中把内部静态类 `DeleteFileHolder` 重命名为 `PendingDeleteFile implements DeleteFile`。它内部仍持有 `DeleteFile deleteFile` 与 `Long dataSequenceNumber`，但所有 `DeleteFile` 接口方法都通过委托给 `deleteFile` 实现（`path`、`location`、`specId`、`partition`、`content`、`format`、`recordCount`、`fileSizeInBytes`、`columnSizes`、`valueCounts`、`nullValueCounts`、`nanValueCounts`、`lowerBounds`、`upperBounds`、`keyMetadata`、`splitOffsets`、`equalityFieldIds`、`sortOrderId`、`manifestLocation`、`pos`、`fileSequenceNumber`、`dataSequenceNumber`），以及 `copy()` / `copyWithoutStats()` / `copyWithStats(Set)` / `copy(boolean)` 通过私有 `wrap(DeleteFile)` 方法重新包装为新的 `PendingDeleteFile`（保留 dataSequenceNumber）。这样 `PendingDeleteFile` 既是 `DeleteFile` 又携带额外信息，可直接放入 `DeleteFileSet`。

2. **`MergingSnapshotProducer` 字段简化**：
   - `Map<PartitionSpec, List<DataFile>> newDataFilesBySpec` → `Map<PartitionSpec, DataFileSet> newDataFilesBySpec`；
   - 移除独立的 `DataFileSet newDataFiles`；
   - `Map<Integer, List<DeleteFileHolder>> newDeleteFilesBySpec` → `Map<Integer, DeleteFileSet> newDeleteFilesBySpec`；
   - 移除独立的 `DeleteFileSet newDeleteFiles`。
   
   `add(DataFile)` / `add(PendingDeleteFile)` 改为：先取 spec、用 `computeIfAbsent` 取/建对应 spec 的 `DataFileSet`/`DeleteFileSet`，再 `set.add(file)` 返回值判断是否为新文件。一次操作只动一个集合，逻辑更清晰。

3. **`addedDataFiles()` 简化**：`newDataFilesBySpec.values().stream().flatMap(Set::stream).collect(ImmutableList.toImmutableList())`，移除 `addedDataFilesBySpec()` 方法（已无调用方）。

4. **`writeDeleteManifests` / `writeDeleteFileGroup` 形参改 `Collection<DeleteFile>`**：内部遍历时 `Preconditions.checkArgument(file instanceof PendingDeleteFile, "Invalid delete file: must be PendingDeleteFile")`，再按 `file.dataSequenceNumber() != null` 决定 `closableWriter.add(file, dsn)` 还是 `closableWriter.add(file)`，不再需要 `fileHolder.deleteFile()` 解包。

5. **`ManifestFilterManager` 字段顺序微调**：把 `deleteFiles` 字段移到 `deletePaths` 之前（纯代码风格，无功能影响）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`

**修改目的**：把 `DeleteFileHolder` 重命名为 `PendingDeleteFile` 并实现 `DeleteFile` 接口；调整 `writeDeleteManifests` / `writeDeleteFileGroup` 签名。

**工作逻辑**：
- 新增 `import java.nio.ByteBuffer;`。
- `writeDeleteManifests(Collection<DeleteFileHolder> files, ...)` → `writeDeleteManifests(Collection<DeleteFile> files, ...)`；`writeDeleteFileGroup` 同步。循环改为 `for (DeleteFile file : files) { Preconditions.checkArgument(file instanceof PendingDeleteFile, ...); ... closableWriter.add(file, dsn) 或 closableWriter.add(file); }`。
- `protected static class DeleteFileHolder` → `protected static class PendingDeleteFile implements DeleteFile`：
  - 构造函数同名替换；
  - 新增私有方法 `private PendingDeleteFile wrap(DeleteFile file)`：根据 `dataSequenceNumber` 是否非空决定用两参构造还是单参构造；
  - 删除原 `public DeleteFile deleteFile()`；
  - 新增 `@Override public Long dataSequenceNumber()`（原方法签名加 `@Override`）；
  - 新增全套 `DeleteFile` 接口方法的委托实现：`fileSequenceNumber`、`copy`、`copyWithoutStats`、`copyWithStats`、`copy(boolean)`、`manifestLocation`、`pos`、`specId`、`content`、`path`、`location`、`format`、`partition`、`recordCount`、`fileSizeInBytes`、`columnSizes`、`valueCounts`、`nullValueCounts`、`nanValueCounts`、`lowerBounds`、`upperBounds`、`keyMetadata`、`splitOffsets`、`equalityFieldIds`、`sortOrderId`，全部委托给 `deleteFile.xxx()`；`copy*` 系列方法用 `wrap(...)` 重新包装。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`

**修改目的**：把"分桶 Map + 全局去重 set"简化为"分桶 Map 的 value 即 set"，让分桶与去重合并；适配 `PendingDeleteFile`。

**工作逻辑**：
- 移除 `import java.util.stream.Collectors;` 与 `import ... ImmutableMap;`。
- 字段：`Map<PartitionSpec, List<DataFile>> newDataFilesBySpec` → `Map<PartitionSpec, DataFileSet> newDataFilesBySpec`；移除 `DataFileSet newDataFiles`；移除 `DeleteFileSet newDeleteFiles`；`Map<Integer, List<DeleteFileHolder>> newDeleteFilesBySpec` → `Map<Integer, DeleteFileSet> newDeleteFilesBySpec`。
- `addedDataFiles()`：`newDataFilesBySpec.values().stream().flatMap(Set::stream).collect(ImmutableList.toImmutableList())`；删除 `addedDataFilesBySpec()`。
- 新增私有 `private PartitionSpec spec(int specId) { return ops.current().spec(specId); }` 助手方法。
- `add(DataFile)`：先 `PartitionSpec spec = spec(file.specId())`，校验非空；`DataFileSet dataFiles = newDataFilesBySpec.computeIfAbsent(spec, ignored -> DataFileSet.create())`；`if (dataFiles.add(file)) { addedFilesSummary.addedFile(spec, file); hasNewDataFiles = true; }`。
- `add(DeleteFile)` / `add(DeleteFile, long dsn)`：构造 `new PendingDeleteFile(file[, dsn])` 转调 `add(PendingDeleteFile)`。
- `add(PendingDeleteFile file)`：取 spec、校验、`DeleteFileSet deleteFiles = newDeleteFilesBySpec.computeIfAbsent(spec.specId(), ignored -> DeleteFileSet.create())`；`if (deleteFiles.add(file)) { addedFilesSummary.addedFile(spec, file); hasNewDeleteFiles = true; }`。
- 错误消息由 `file.path()` 改为 `file.location()`（与 #11158 风格一致）。

### `core/src/main/java/org/apache/iceberg/ManifestFilterManager.java`

**修改目的**：字段顺序微调。

**工作逻辑**：把 `Set<F> deleteFiles = newFileSet();` 字段从 `deletePaths` 之后移到 `deletePaths` 之前（紧跟 `deleteFilePartitions`）。无功能变化，仅代码风格统一。

## 小结

- **成效**：
  - `DeleteFileHolder` → `PendingDeleteFile implements DeleteFile`，让"待提交的 delete 文件"可直接作为 `DeleteFile` 进入 `DeleteFileSet`，去重基准统一为 location，无需解包；
  - `MergingSnapshotProducer` 的"分桶 Map + 全局去重 set"双轨结构简化为"分桶 Map 的 value 即专用 file set"，减少冗余字段与同步成本；
  - `writeDeleteManifests` 形参从 `Collection<DeleteFileHolder>` 改为 `Collection<DeleteFile>` 并以 `instanceof PendingDeleteFile` 校验，保留 dataSequenceNumber 语义；
  - 删除无调用方的 `addedDataFilesBySpec()` 方法，错误消息统一用 `file.location()`。
- **影响范围**：3 个文件、178 行新增/46 行删除，集中在 core 的快照生产链路。`PendingDeleteFile` 是 `SnapshotProducer` 的 `protected static` 内部类，对模块外不可见，对外 API 无变化；行为应与重构前等价（分桶与去重结果一致）。
- **回迁到 1.4.x 的注意事项**：
  - 本提交强依赖前置 PR #11158（提交 6a5ae1ae6），回迁到 1.4.x 时必须先回迁 #11158（以及更前置的 #11195 添加 `DataFileSet`/`DeleteFileSet`）。
  - `PendingDeleteFile implements DeleteFile` 涉及 `DeleteFile` 接口的方法集；如果 1.4.x 的 `DeleteFile` 接口与 main 有差异（例如新增/移除了某些 default 方法），回迁时需对齐，否则编译失败或方法缺失。
  - `addedDataFilesBySpec()` 方法删除需确认 1.4.x 中确实无外部调用方（Iceberg 内部其他模块或 Spark/Flink 适配层）。若有调用方（例如 1.4.x 特有的代码路径），需保留或调整。
  - `writeDeleteManifests` 形参类型变更可能影响 1.4.x 中重写该方法的子类（若有），需检查。
  - 整体回迁顺序建议：#11195 → #11158 → #11254（本提交），避免冲突。
