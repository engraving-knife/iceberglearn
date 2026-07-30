# 提交 1201：Core: Support combining position deletes during writes (#11222)

## 提交信息

- **序号**：1201 / 4088
- **哈希**：c8fe01e71f9996fcaae973c6bfaa8d90f7dd8c6c
- **短哈希**：c8fe01e71
- **日期**：2024-09-30（Mon Sep 30 21:12:56 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Support combining position deletes during writes (#11222)
- **PR/Issue**：#11222

## 总体目的

Iceberg 的 position delete（按行号删除）以独立 delete 文件的形式存储。当一个数据文件被多次删除时，会累积多个针对同一数据文件的 position delete 文件，导致后续读取时需要打开并合并多个 delete 文件，增加扫描开销与元数据膨胀。

本提交为 `SortingPositionOnlyDeleteWriter` / `FanoutPositionOnlyDeleteWriter` 增加"在写入新 delete 时合并已有 position delete"的能力：写入器在 flush 前可通过 `loadPreviousDeletes` 回调加载同一数据文件已有的 file-scoped position delete，把旧位置合并进新索引后一次性写出，并把被合并的旧 delete 文件作为 `rewrittenDeleteFiles` 上报，供上层在 `RowDelta` 提交时通过 `removeDeletes` 移除。这样每次写入都产生"全量"的 position delete 文件，避免 delete 文件累积，类似 position delete 的增量重写（rewrite）。

为支持该能力，提交还：

- 给 `PositionDeleteIndex` 接口增加 `deleteFiles()` 默认方法，返回索引对应的源 delete 文件（未知时返回空集合），用于追踪被合并的源文件；
- 给 `BitmapPositionDeleteIndex` 增加持有 `DeleteFile` 列表的构造器与字段，并在 `merge` 时合并源文件列表；
- 在 `PositionDeleteIndex.merge` 默认实现中校验对方 `deleteFiles()` 非空时拒绝合并（仅 `BitmapPositionDeleteIndex` 等支持源文件追踪的实现可合并），防止隐式丢失源文件信息；
- 给 `Deletes` 工具类增加带 `DeleteFile` 参数的 `toPositionIndexes` / `toPositionIndex` 重载，使构建索引时能携带源 delete 文件信息；
- 在 `SortingPositionOnlyDeleteWriter` 中校验"previous deletes 必须是 file-scoped"（即每个 delete 文件只针对一个数据文件），因为 partition-scoped delete 可能作用于多个数据文件，无法安全丢弃；
- 给 `PositionDelete` 增加 `set(path, pos)` 重载（不带 row），用于合并写出时只关心位置的场景；
- 同步更新 `BaseDeleteLoader` 使用新的带 `DeleteFile` 参数的重载；
- 在 `TestPartitioningWriters` 中新增 `testRewriteOfPreviousDeletes` 端到端测试，验证两轮 delete 写入后旧 delete 文件被正确重写、表数据正确。

## 如何达成设计目的

整体思路是**让 delete writer 在 flush 时把"已有 delete"和"新增 delete"合并成一个全量 delete 文件**，并通过 `DeleteWriteResult.rewrittenDeleteFiles()` 暴露被重写的旧文件，使上层提交时能同时 `addDeletes`（新文件）与 `removeDeletes`（旧文件）。

具体实现分几层：

1. **`PositionDeleteIndex` 携带源 delete 文件信息**：接口新增 `deleteFiles()` 默认方法返回空集合；`BitmapPositionDeleteIndex` 增加 `List<DeleteFile> deleteFiles` 字段与三个构造器（空、单个、集合），`merge` 时把对方的 `deleteFiles` 也并入自己。这样合并后的索引能追溯到所有被合并的源 delete 文件。

2. **`merge` 安全校验**：`PositionDeleteIndex.merge` 默认实现中，若对方 `deleteFiles()` 非空则抛 `UnsupportedOperationException`，强制只有支持源文件追踪的实现（如 `BitmapPositionDeleteIndex`）才能合并，避免在 `EmptyPositionDeleteIndex` 等实现上合并导致源文件信息丢失。

3. **`Deletes` 工具类重载**：`toPositionIndexes(posDeletes, file)` 在构建索引时把 `file` 传入 `BitmapPositionDeleteIndex` 构造器；`toPositionIndex(dataLocation, posDeletes, file)` 单数据文件版本同样携带 `file`。保留旧的无 `file` 重载（传 `null` 表示未知源文件）以兼容既有调用。

4. **`SortingPositionOnlyDeleteWriter` 合并逻辑**：构造器新增 `Function<CharSequence, PositionDeleteIndex> loadPreviousDeletes` 参数（默认 `path -> null`）。在 `writePath`（写出单个数据文件的 delete）时，先调用 `loadPreviousDeletes.apply(path)` 获取该数据文件的已有索引，若非空则：
   - 调用 `validatePreviousDeletes` 校验所有源 delete 文件都是 file-scoped（通过 `ContentFileUtil.referencedDataFile(deleteFile) != null` 判断）；
   - 调用 `positions.merge(previousPositions)` 合并位置与源文件列表；
   - 把 `previousPositions.deleteFiles()` 收集到 `rewrittenDeleteFiles` 列表；
   - 最后用合并后的索引遍历写出，并用 `new DeleteWriteResult(deleteFiles, referencedDataFiles, rewrittenDeleteFiles)` 返回结果。

5. **`FanoutPositionOnlyDeleteWriter` 透传**：新增带 `loadPreviousDeletes` 的构造器，透传给内部每个分区的 `SortingPositionOnlyDeleteWriter`；`addResult` 与 `aggregatedResult` 同步聚合 `rewrittenDeleteFiles`。

6. **`PositionDelete.set(path, pos)` 重载**：合并写出时只需 path+pos（无 row），新增不带 row 的 `set` 重载，避免传 `null` row 的二义性。

7. **`BaseDeleteLoader` 适配**：读取 position delete 时用新的带 `DeleteFile` 参数的重载，使加载出的索引携带源文件信息，为后续合并场景铺路。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java`

**修改目的**：让位图索引能持有并合并源 `DeleteFile` 列表。

**工作逻辑**：

- 新增字段 `private final List<DeleteFile> deleteFiles;`；
- 原 `BitmapPositionDeleteIndex()` 无参构造器初始化 `deleteFiles = Lists.newArrayList()`；
- 新增 `BitmapPositionDeleteIndex(Collection<DeleteFile> deleteFiles)` 构造器，用传入集合初始化；
- 新增 `BitmapPositionDeleteIndex(DeleteFile deleteFile)` 单文件构造器（`null` 时初始化为空列表）；
- `merge(BitmapPositionDeleteIndex that)` 中除原有的 `roaring64Bitmap.or(...)` 外，新增 `deleteFiles.addAll(that.deleteFiles)`；
- `merge(PositionDeleteIndex that)`（接口默认实现的覆写）的 else 分支新增 `deleteFiles.addAll(that.deleteFiles())`；
- 新增 `@Override public Collection<DeleteFile> deleteFiles()` 返回 `deleteFiles`。

### `core/src/main/java/org/apache/iceberg/deletes/PositionDeleteIndex.java`

**修改目的**：在接口层暴露源 delete 文件信息，并对 merge 做安全约束。

**工作逻辑**：

- 新增 import `java.util.Collection`、`org.apache.iceberg.DeleteFile`、`ImmutableList`；
- 修改 `default void merge(PositionDeleteIndex that)`：在方法开头新增校验——若 `!that.deleteFiles().isEmpty()` 则抛 `UnsupportedOperationException(getClass().getName() + " does not support merge")`，避免在不支持源文件追踪的实现上隐式合并；
- 新增 `default Collection<DeleteFile> deleteFiles()` 方法，默认返回 `ImmutableList.of()`（即"源文件未知"），由 `BitmapPositionDeleteIndex` 覆写返回真实列表。

### `core/src/main/java/org/apache/iceberg/deletes/Deletes.java`

**修改目的**：让构建 position delete 索引时能携带源 `DeleteFile`。

**工作逻辑**：

- 新增 import `org.apache.iceberg.DeleteFile`；
- 新增无 `file` 参数的 `toPositionIndexes(CloseableIterable<T> posDeletes)` 重载，内部调用 `toPositionIndexes(posDeletes, null)`（表示源文件未知），保持向后兼容；
- 原 `toPositionIndexes(posDeletes)` 改为 `toPositionIndexes(posDeletes, DeleteFile file)`，在 `computeIfAbsent` 时用 `new BitmapPositionDeleteIndex(file)` 替代 `new BitmapPositionDeleteIndex()`；
- 新增 `toPositionIndex(CharSequence dataLocation, CloseableIterable<T> posDeletes, DeleteFile file)` 单数据文件版本：用 `extractPositions` 过滤并提取位置后，调用私有的 `toPositionIndex(posDeletes, ImmutableList.of(file))`；
- 新增私有 `extractPositions(dataLocation, rows)`：用 `DataFileFilter` 过滤出匹配 `dataLocation` 的行，再 `transform` 提取 `POSITION_ACCESSOR` 的 long 值；
- 原 `toPositionIndex(CloseableIterable<Long> posDeletes)` 改为调用私有 `toPositionIndex(posDeletes, ImmutableList.of())`（无源文件）；
- 新增私有 `toPositionIndex(CloseableIterable<Long> posDeletes, List<DeleteFile> files)`：用 `new BitmapPositionDeleteIndex(files)` 构建索引。

### `core/src/main/java/org/apache/iceberg/deletes/PositionDelete.java`

**修改目的**：支持只设置 path+pos（不带 row）的场景。

**工作逻辑**：新增 `set(CharSequence newPath, long newPos)` 重载，内部调用字段赋值并把 `row` 设为 `null`，返回 `this`。与原有 `set(newPath, newPos, newRow)` 区分，避免调用方误传 `null` row。

### `core/src/main/java/org/apache/iceberg/deletes/SortingPositionOnlyDeleteWriter.java`

**修改目的**：核心合并逻辑——在 flush 前加载并合并已有 file-scoped position delete。

**工作逻辑**：

- import 调整：移除 `PeekableLongIterator`、`Roaring64Bitmap`，新增 `Function`、`Preconditions`、`ContentFileUtil`；
- 字段 `CharSequenceMap<Roaring64Bitmap> positionsByPath` 改为 `CharSequenceMap<PositionDeleteIndex> positionsByPath`；新增 `Function<CharSequence, PositionDeleteIndex> loadPreviousDeletes` 字段；
- 原 2 参构造器委托给新的 3 参构造器（默认 `loadPreviousDeletes = path -> null`）；
- 新增 3 参构造器 `(writers, granularity, loadPreviousDeletes)`；
- `write(PositionDelete)`：用 `positionsByPath.computeIfAbsent(path, key -> new BitmapPositionDeleteIndex())` 获取索引，调用 `positions.delete(position)` 替代原来的 `roaring64Bitmap.add(position)`；
- `writeFileDeletes()`：新增 `rewrittenDeleteFiles` 列表，在每个 path 的 `writeDeletes` 结果中收集 `rewrittenDeleteFiles`，最终用 `new DeleteWriteResult(deleteFiles, referencedDataFiles, rewrittenDeleteFiles)` 返回；
- `writePath(paths)`（写出单数据文件 delete 的核心）：
  - 遍历排序后的 path，取出 `positions` 索引；
  - 调用 `loadPreviousDeletes.apply(path)` 获取已有索引；
  - 若非空且 `isNotEmpty()`：`validatePreviousDeletes(previousPositions)` + `positions.merge(previousPositions)` + `rewrittenDeleteFiles.addAll(previousPositions.deleteFiles())`；
  - 用 `positions.forEach(position -> writer.write(positionDelete.set(path, position)))` 写出（注意 `set(path, position)` 用的是新增的无 row 重载）；
  - 最终用 `new DeleteWriteResult(deleteFiles, referencedDataFiles, rewrittenDeleteFiles)` 返回；
- 新增 `validatePreviousDeletes(index)`：校验索引的 `deleteFiles()` 全部满足 `isFileScoped`；
- 新增 `isFileScoped(deleteFile)`：`return ContentFileUtil.referencedDataFile(deleteFile) != null;`（即该 delete 文件绑定了具体数据文件路径，而非 partition-scoped）。

### `core/src/main/java/org/apache/iceberg/io/FanoutPositionOnlyDeleteWriter.java`

**修改目的**：把 `loadPreviousDeletes` 透传到内部 writer，并聚合 `rewrittenDeleteFiles`。

**工作逻辑**：

- 新增 import `Function`、`PositionDeleteIndex`；
- 新增字段 `List<DeleteFile> rewrittenDeleteFiles` 与 `Function<CharSequence, PositionDeleteIndex> loadPreviousDeletes`；
- 原 5 参构造器委托给新的 6 参构造器（默认 `path -> null`）；
- 新增 6 参构造器，初始化 `rewrittenDeleteFiles = Lists.newArrayList()` 与 `loadPreviousDeletes`；
- `newWriter(...)` 创建 `SortingPositionOnlyDeleteWriter` 时传入 `loadPreviousDeletes`；
- `addResult(DeleteWriteResult)`：新增 `rewrittenDeleteFiles.addAll(result.rewrittenDeleteFiles())`；
- `aggregatedResult()`：改为 `new DeleteWriteResult(deleteFiles, referencedDataFiles, rewrittenDeleteFiles)`。

### `core/src/test/java/org/apache/iceberg/TestBase.java`

**修改目的**：为测试提供只带 path+pos 的 `positionDelete` 辅助方法。

**工作逻辑**：新增 `protected <T> PositionDelete<T> positionDelete(CharSequence path, long pos)`，委托给原有 `positionDelete(path, pos, null)`。

### `data/src/main/java/org/apache/iceberg/data/BaseDeleteLoader.java`

**修改目的**：加载 position delete 索引时携带源 `DeleteFile`，为后续合并场景提供源文件信息。

**工作逻辑**：

- `readPosDeletes(DeleteFile deleteFile)`（读全量）：`Deletes.toPositionIndexes(deletes)` 改为 `Deletes.toPositionIndexes(deletes, deleteFile)`；
- `readPosDeletes(DeleteFile deleteFile, CharSequence filePath)`（按数据文件过滤）：`Deletes.toPositionIndex(filePath, ImmutableList.of(deletes))` 改为 `Deletes.toPositionIndex(filePath, deletes, deleteFile)`，使用新的单数据文件重载。

### `data/src/test/java/org/apache/iceberg/io/TestPartitioningWriters.java`

**修改目的**：端到端验证两轮 delete 写入后旧 delete 文件被正确重写。

**工作逻辑**：

- 新增 import `Assumptions.assumeThat`、`Map`、`Function`、`DeleteFile`、`Table`、`BaseDeleteLoader`、`DeleteLoader`、`PositionDeleteIndex`、`Maps`、`ContentFileUtil`；
- 新增 `@TestTemplate testRewriteOfPreviousDeletes()`：
  1. 仅在 Parquet/ORC 格式下运行（`assumeThat(format()).isIn(PARQUET, ORC)`）；
  2. 写入两个数据文件 `dataFile1`、`dataFile2` 并 fast append 提交；
  3. 用**不带** `loadPreviousDeletes` 的 `FanoutPositionOnlyDeleteWriter` 写入初始 delete（删除每个数据文件的第 1 行），验证 `result1.rewrittenDeleteFiles()` 为空，提交 `RowDelta`；
  4. 校验表数据正确（各保留 2 行）；
  5. 构建 `previousDeletes` 映射：遍历 `result1.deleteFiles()`，用 `ContentFileUtil.referencedDataFile(deleteFile)` 取得每个 delete 文件对应的数据文件路径，建立 `dataLocation -> DeleteFile` 映射；
  6. 用**带** `loadPreviousDeletes`（`PreviousDeleteLoader`）的 `FanoutPositionOnlyDeleteWriter` 写入第二轮 delete（删除每个数据文件的第 0 行），验证 `result2.rewrittenDeleteFiles()` 有 2 个文件（即第一轮的两个 delete 文件被重写）；
  7. 提交 `RowDelta`：`addDeletes` 新文件 + `removeDeletes` 旧文件；
  8. 校验表数据正确（各保留 1 行）。
- 新增内部类 `PreviousDeleteLoader implements Function<CharSequence, PositionDeleteIndex>`：持有 `Map<String, DeleteFile>` 与 `BaseDeleteLoader`，`apply(path)` 时按数据文件路径查找对应 `DeleteFile`，调用 `deleteLoader.loadPositionDeletes(ImmutableList.of(deleteFile), path)` 返回已有索引。

## 小结

- **成效**：position delete writer 现在可在写入时合并已有 file-scoped position delete，避免 delete 文件随删除次数线性累积；合并后被重写的旧 delete 文件通过 `DeleteWriteResult.rewrittenDeleteFiles()` 上报，上层可在 `RowDelta` 提交时移除，实现 position delete 的增量重写。`PositionDeleteIndex` 接口新增 `deleteFiles()` 源文件追踪能力，`merge` 增加安全校验防止源文件信息丢失。
- **影响范围**：改动 9 个文件，新增 264 行、删除 20 行。涉及 core 的 deletes 子包（`BitmapPositionDeleteIndex`、`Deletes`、`PositionDelete`、`PositionDeleteIndex`、`SortingPositionOnlyDeleteWriter`）、io 子包（`FanoutPositionOnlyDeleteWriter`）、测试基类（`TestBase`）以及 data 模块（`BaseDeleteLoader`、`TestPartitioningWriters`）。属于功能增强型改动，向后兼容（旧构造器与无 `file` 参数的重载保留）。
- **回迁到 1.4.x 的注意事项**：本提交**依赖两个前置提交**，回迁前必须先回迁：
  1. **`DeleteWriteResult` 增加 `rewrittenDeleteFiles` 字段与 3 参构造器**：来自提交 `2fa8c7d86`（PR #11203 "Core: Add rewritten delete files to write results"）。1.4.x 的 `DeleteWriteResult` 目前只有 `deleteFiles` 与 `referencedDataFiles` 两个字段及 1 参/2 参构造器，本提交多处使用 `new DeleteWriteResult(deleteFiles, referencedDataFiles, rewrittenDeleteFiles)` 与 `result.rewrittenDeleteFiles()`，缺此前置将无法编译。
  2. **`ContentFileUtil` 工具类**：本提交在 `SortingPositionOnlyDeleteWriter` 与测试中调用 `ContentFileUtil.referencedDataFile(deleteFile)` 判断 delete 文件是否 file-scoped。1.4.x 当前不存在该类（`find` 未命中），需确认该类由哪个前置提交引入并一并回迁。
  - 回迁时还需确认 1.4.x 的 `SortingPositionOnlyDeleteWriter` 字段类型与 main 一致（1.4.x 应仍使用 `Roaring64Bitmap` 而非 `PositionDeleteIndex`，需按本提交切换）。
  - 由于涉及核心写入路径与 delete 语义，**回迁风险较高**，建议在 1.4.x 上跑完整的 `TestPartitioningWriters`（含新增的 `testRewriteOfPreviousDeletes`）以及 Spark/Flink 的 delete 相关集成测试。
  - 若 1.4.x 不计划支持 position delete 重写，可暂不回迁；该功能属于优化型增强，非缺陷修复，不影响 1.4.x 现有写入正确性。
