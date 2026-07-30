# 提交 1236：Core: Switch usage to DataFileSet / DeleteFileSet (#11158)

## 提交信息

- **序号**：1236 / 4088
- **哈希**：6a5ae1ae6a01f1395ee70e046537cd87b990c4ae
- **短哈希**：6a5ae1ae6
- **日期**：2024-10-14（Mon Oct 14 18:54:33 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Switch usage to DataFileSet / DeleteFileSet (#11158)
- **PR/Issue**：#11158

## 总体目的

Iceberg 此前已在 PR #11195（提交 e4bc593d4）新增了 `DataFileSet` / `DeleteFileSet` 两个专用集合类型（基于 `WrapperSet` 实现，通过文件 `location` 而非完整对象判等去重），目的是替换仓库中长期使用的两种去重方式：

1. `CharSequenceSet`：以 `file.path()`（路径字符串）做去重；
2. 普通 `Set<DataFile>` / `Set<DeleteFile>`（基于 `Object.equals/hashCode`，而 `ContentFile` 的 `equals` 通常也是基于路径）。

专用 `DataFileSet`/`DeleteFileSet` 提供更明确的语义与一致的去重基准（location），并附带集合类型保护，避免不同概念（DataFile 与 DeleteFile）混入同一集合。

本提交是 #11195 之后的"切换消费方"步骤：把仓库中所有还在用 `CharSequenceSet` 路径去重或 `Set<DataFile>`/`Set<DeleteFile>` 的位置，切换为 `DataFileSet`/`DeleteFileSet`。这是一次较大规模的重构，涉及 core、spark/v3.5、hive-metastore、nessie 等多个模块。同时借此机会修复一个潜在 bug：`ManifestFilterManager` 之前用 `hasPathOnlyDeletes` 布尔标记来决定能否走分区裁剪优化，但当调用方既按 path 又按 file 删除时，标记语义易出错；切换为同时维护 `deletePaths`（按 path）与 `deleteFiles`（按 file）后，分别检查两者是否为空来判断是否走分区裁剪路径，逻辑更准确。

## 如何达成设计目的

1. 在 `ManifestFilterManager`（core）中：
   - 新增字段 `Set<F> deleteFiles = newFileSet()`，引入抽象方法 `protected abstract Set<F> newFileSet();` 由子类（DataFileMergeManager、DeleteFileMergeManager）实现，分别返回 `DataFileSet.create()` / `DeleteFileSet.create()`；
   - `delete(F file)` 改为同时 `deleteFiles.add(file)` 与 `deletePaths.add(file.path())`，移除 `hasPathOnlyDeletes` 标记；
   - `delete(CharSequence path)` 仍只向 `deletePaths` 添加；
   - `containsDeletes()` 增加 `!deleteFiles.isEmpty()` 条件；
   - 验证逻辑 `validateRequiredDeletes` 把"已删除文件集合"也改成基于 `Set<F>` 的 set，分别对 `deleteFiles` 和 `deletePaths` 校验完整性，错误信息显示 `file.location()`；
   - 在 manifest 过滤逻辑中，`markedForDelete` 增加 `deleteFiles.contains(file)` 条件，重复删除检测改用 `deletedFiles.contains(file)`（基于 location），不再用 `CharSequenceWrapper` 包装；
   - `canContainDroppedFiles` 判断从 `hasPathOnlyDeletes` 改为 `!deletePaths.isEmpty()`（如有 path-only 删除则保守 true，否则按 `deleteFiles` 走分区裁剪）。

2. 在 `MergingSnapshotProducer`（core）中：
   - 将 `CharSequenceSet newDataFilePaths` / `newDeleteFilePaths` 替换为 `DataFileSet newDataFiles` / `DeleteFileSet newDeleteFiles`；
   - `add(DataFile)` 与 `add(DeleteFileHolder)` 直接用 set 的 `add` 返回值判断是否为新文件，去掉"先 paths.add 再 list.add"两步；
   - 子类 `DataFileMergeManager`/`DeleteFileMergeManager` 实现 `newFileSet()` 返回对应的 set 类型；
   - `addedDataFiles` / `writeDataManifests` / `writeDeleteManifests` 等签名从 `List<...>` 改为 `Collection<...>`。

3. 在 `SnapshotProducer`（core）中：
   - `writeDataManifests` / `writeDeleteManifests` / `writeDataFileGroup` / `writeDeleteFileGroup` / `writeManifests` / `divide` 等方法签名从 `List<F>` 改为 `Collection<F>`，以兼容 Set 输入；`divide` 内部把 Collection 复制为 ArrayList 再 `Lists.partition`。

4. 在 `BaseOverwriteFiles`、`BaseRewriteFiles`、`FastAppend`（core）中：把 `Set<DataFile>` 字段替换为 `DataFileSet`，删除原先与之并存的 `CharSequenceSet newFilePaths`，直接用 set 去重。

5. 在 `actions`（core）的 `RewriteDataFilesCommitManager`、`RewriteFileGroup`、`RewritePositionDeletesGroup` 中：把 `Set<DataFile>` / `Set<DeleteFile>` 字段与 `Collectors.toSet()` 改为 `DataFileSet` / `DeleteFileSet` 与 `Collectors.toCollection(DataFileSet::create)`，统一类型与去重基准。

6. 在 Spark v3.5 的 `SparkWrite`、`SparkPositionDeletesRewrite` 中：把 `ImmutableSet.copyOf(files(messages))` 改为 `DataFileSet.of(...)` / `DeleteFileSet.of(...)`，与协调器（FileRewriteCoordinator / PositionDeletesRewriteCoordinator）的 staging 接口保持一致。

7. 在测试侧：
   - `TestDeleteFiles`（core）：增强 `validateFilesExist` 校验失败时的断言信息，包含具体路径，并新增一个对不存在路径的删除校验用例。
   - `HiveTableTest`（hive-metastore）与 `TestNessieTable`（nessie）：把 `table.newDelete().deleteFile(file2.path()).commit()` 改为 `table.newDelete().deleteFile(file2).commit()`，验证按 file 对象删除路径可用。
   - Spark v3.5 测试（`TestFileRewriteCoordinator`、`TestHelpers`、`TestPositionDeletesTable`）：把 `Collectors.toSet()` 改为 `Collectors.toCollection(DataFileSet::create)` / `DeleteFileSet.create()`，`Sets.newHashSet()` 改为 `DeleteFileSet.create()`，与生产代码保持一致。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestFilterManager.java`

**修改目的**：把"按路径去重 + hasPathOnlyDeletes 标记"重构为"同时维护 deleteFiles（按对象/location）与 deletePaths（按路径）"，并改用专用 file set。

**工作逻辑**：
- 新增 `Set<F> deleteFiles = newFileSet()` 字段与抽象方法 `protected abstract Set<F> newFileSet();`，移除 `hasPathOnlyDeletes` 标记。
- `delete(F file)`：`deleteFiles.add(file)` 并 `deletePaths.add(file.path())`，并标记 `deleteFilePartitions`。
- `delete(CharSequence path)`：仅 `deletePaths.add(path)`。
- `containsDeletes()`：`!deletePaths.isEmpty() || !deleteFiles.isEmpty() || ...`。
- `validateRequiredDeletes`：先用 `Set<F> deletedFiles = deletedFiles(manifests)`（按对象）校验 `deleteFiles.containsAll(...)`，再由此推导 `CharSequenceSet deletedFilePaths` 校验 `deletePaths.containsAll(...)`，错误消息用 `ContentFile::location`。
- `deletedFiles(ManifestFile[])`：返回类型从 `CharSequenceSet` 改为 `Set<F>`，使用 `newFileSet()` 初始化。
- `canContainDroppedFiles` 判断：`if (!deletePaths.isEmpty()) true; else if (!deleteFiles.isEmpty()) 走分区裁剪; else false`，等价但更准确。
- 过滤主循环：`markedForDelete = deletePaths.contains(file.path()) || deleteFiles.contains(file) || ...`，重复删除检测 `deletedFiles.contains(file)` 替代 `deletedPaths.contains(wrapper)`，统计与日志改用 `file.location()`。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`

**修改目的**：把"按路径去重 + List"重构为"按对象/set 去重"，并适配子类提供 `newFileSet()`。

**工作逻辑**：
- 字段替换：`CharSequenceSet newDataFilePaths` → `DataFileSet newDataFiles`，`CharSequenceSet newDeleteFilePaths` → `DeleteFileSet newDeleteFiles`。
- `add(DataFile)`：直接 `if (newDataFiles.add(file))`，移除独立的 `List<DataFile>` 维护。
- `add(DeleteFileHolder)`：`if (newDeleteFiles.add(fileHolder.deleteFile()))`，避免双集合同步。
- `DataFileMergeManager` / `DeleteFileMergeManager` 新增 `@Override protected Set<...> newFileSet()` 实现。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`

**修改目的**：把 manifest 写入相关方法签名从 `List<F>` 放宽为 `Collection<F>`，以接收 set 输入。

**工作逻辑**：
- `writeDataManifests`、`writeDataFileGroup`、`writeDeleteManifests`、`writeDeleteFileGroup`、`writeManifests`、`divide` 的形参类型由 `List<F>` 改为 `Collection<F>`（或 `Collection<DataFile>` / `Collection<DeleteFileHolder>`）。
- `divide(Collection<T>, int)`：内部 `List<T> list = Lists.newArrayList(collection);` 后再 `Lists.partition`，避免破坏原集合的迭代语义。

### `core/src/main/java/org/apache/iceberg/BaseOverwriteFiles.java`、`BaseRewriteFiles.java`、`FastAppend.java`

**修改目的**：用专用 file set 替换 `Set<DataFile>` 与 `CharSequenceSet`。

**工作逻辑**：
- `BaseOverwriteFiles.deletedDataFiles`：`Set<DataFile>` + `Sets.newHashSet()` → `DataFileSet.create()`。
- `BaseRewriteFiles.replacedDataFiles`：同上。
- `FastAppend`：`List<DataFile> newFiles` + `CharSequenceSet newFilePaths` 合并为 `DataFileSet newFiles`，`appendFile` 直接通过 `newFiles.add(file)` 返回值判断是否新增。

### `core/src/main/java/org/apache/iceberg/actions/RewriteDataFilesCommitManager.java`、`RewriteFileGroup.java`、`RewritePositionDeletesGroup.java`

**修改目的**：rewrite actions 也切换为专用 file set。

**工作逻辑**：
- `RewriteDataFilesCommitManager.commitFileGroups`：`Set<DataFile> rewrittenDataFiles/addedDataFiles` → `DataFileSet`。
- `RewriteFileGroup`：`Set<DataFile> addedFiles` 字段改为 `DataFileSet`；`setOutputFiles` 用 `DataFileSet.of(files)` 防御性复制；`rewrittenFiles()` 用 `Collectors.toCollection(DataFileSet::create)`。
- `RewritePositionDeletesGroup`：同样把 `Set<DeleteFile> addedDeleteFiles` 改为 `DeleteFileSet`，对应 collector 同步调整。

### `core/src/test/java/org/apache/iceberg/TestDeleteFiles.java`

**修改目的**：增强 `validateFilesExist` 失败场景的断言。

**工作逻辑**：原有用例只断言抛 `ValidationException`，现改为 `.hasMessage("Missing required files to delete: /path/to/data-b.parquet")`，并新增对 `/path/to/non-existing.parquet` 的删除校验用例，预期同样抛带路径的 ValidationException。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/HiveTableTest.java` 与 `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieTable.java`

**修改目的**：把测试中的 `deleteFile(file2.path())` 改为 `deleteFile(file2)`。

**工作逻辑**：原本按路径删，现按 file 对象删，验证 `delete(F file)` 路径（经由 `deleteFiles.add`）工作正常。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java`、`SparkPositionDeletesRewrite.java`

**修改目的**：与协调器 staging 接口对齐，用专用 file set。

**工作逻辑**：
- `SparkWrite.commit`：`ImmutableSet.copyOf(files(messages))` → `DataFileSet.of(files(messages))`。
- `SparkPositionDeletesRewrite.commit`：`ImmutableSet.copyOf(...)` → `DeleteFileSet.of(...)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestFileRewriteCoordinator.java`、`data/TestHelpers.java`、`source/TestPositionDeletesTable.java`

**修改目的**：测试代码同步切换 set 类型。

**工作逻辑**：把 `Collectors.toSet()` 改为 `Collectors.toCollection(DataFileSet::create)` / `DeleteFileSet::create`，把 `Sets.newHashSet()` 改为 `DeleteFileSet.create()`。

## 小结

- **成效**：仓库多个模块（core、spark/v3.5、hive-metastore、nessie）统一改用 `DataFileSet` / `DeleteFileSet` 专用集合类型，去重基准统一为 location，类型与意图更明确；同时修复了 `ManifestFilterManager.hasPathOnlyDeletes` 标记在混合删除场景下的语义问题；`SnapshotProducer` 的 manifest 写入接口放宽为 `Collection<F>` 以兼容 set 输入。
- **影响范围**：涉及 17 个文件、115 行新增/72 行删除；改动覆盖 core 的快照生产链路、Spark 写入与 rewrite、Hive/Nessie 测试。属于内部实现重构，对外公共 API（`SnapshotUpdate`、`DeleteFiles`、`AppendFiles` 等）签名未变，行为应保持等价。
- **回迁到 1.4.x 的注意事项**：
  - 本提交强依赖前置 PR #11195（提交 e4bc593d4，添加 `DataFileSet`/`DeleteFileSet`/`WrapperSet`）。回迁到 1.4.x 时必须先把 #11195 一并回迁，否则编译失败。
  - 1.4.x 可能与 main 在 `MergingSnapshotProducer` / `ManifestFilterManager` / `SnapshotProducer` 上已有结构差异（例如后续 #11254 又进一步把 `DeleteFileHolder` 改成 `PendingDeleteFile`），回迁时需注意冲突顺序：建议按 #11195 → #11158（本提交）→ #11254 的顺序回迁。
  - `ManifestFilterManager` 的 `hasPathOnlyDeletes` 移除是行为敏感改动，回迁后需重点验证 `validateFilesExist`、按路径删除、按对象删除、混合删除、跨 manifest 重复删除等场景的快照摘要 `deleted-data-files`/`deleted-delete-files` 计数仍准确。
  - 测试改动（`TestDeleteFiles` 增强断言、`HiveTableTest`/`TestNessieTable` 改 `deleteFile(file)`）建议一并回迁，覆盖回迁后的行为。
