# 提交 2327：Core: Keep track of data files to be removed for orphaned DV detection (#13222)

## 提交信息

- **序号**：2327 / 4088
- **哈希**：061ae58986db3495ff3af6f1932a96dd086e5fbd
- **短哈希**：061ae5898
- **日期**：2025-07-08 09:04:01 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Keep track of data files to be removed for orphaned DV detection (#13222)
- **PR/Issue**：#13222

## 总体目的

这个提交是提交 2312（Propagate orphaned delete files when rewriting data files）的后续完善，解决了在通过表达式或路径删除数据文件时，引用该数据文件的 DV（删除向量）不会被自动清理的问题。

在提交 2312 中，孤立 DV 的清理主要针对数据文件重写场景。但在更常见的删除场景中（如 `DELETE FROM` 或 `table.newDelete().deleteFile(path)`），当数据文件被删除时，引用该数据文件的 DV 会变成孤立的。这些孤立 DV 会残留在删除清单中，因为 DV 通过 `referencedDataFile` 字段引用数据文件路径，当数据文件不存在时该引用无效。

此提交的核心改进是：在 `ManifestFilterManager` 中跟踪即将被删除的数据文件路径，并在过滤删除清单时检测并移除引用这些路径的孤立 DV。此外还修复了 `canTrustManifestReferences` 方法的一个边界条件。

## 如何达成设计目的

1. **数据文件路径跟踪**：在 `ManifestFilterManager` 中新增 `removedDataFilePaths` 集合，记录即将被删除的数据文件路径
2. **跨管理器传递**：在 `MergingSnapshotProducer` 中，从 `DataFileFilterManager` 获取待删除的数据文件，传递给 `DeleteManifestFilterManager`
3. **孤立 DV 检测**：在过滤删除清单时，检查每个 DV 的 `referencedDataFile` 是否在 `removedDataFilePaths` 中，如果是则标记为删除
4. **表达式删除支持**：当通过表达式删除数据文件时，将被删除的数据文件添加到 `deleteFiles` 集合，确保对应的 DV 也能被检测到

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestFilterManager.java` (+36/-8 lines)

**修改目的**：添加孤立 DV 检测和清理逻辑。

**工作逻辑**：
- 新增 `removedDataFilePaths` 集合字段
- 新增 `filesToBeDeleted()` 方法返回 `deleteFiles` 集合（供跨管理器传递）
- 新增 `removeDanglingDeletesFor(Set<DataFile>)` 方法：提取数据文件路径到 `removedDataFilePaths`
- 新增 `isDanglingDV(DeleteFile)` 方法：检查 DV 是否引用了被删除的数据文件路径
- 在 `filterManifestWithDeletedFiles` 方法中，检测到孤立 DV 时标记为删除（`isDanglingDV` 加入 `markedForDelete` 条件）
- 修复 `canTrustManifestReferences`：增加 `!manifestsWithDeletes.isEmpty()` 检查，避免空集合时的误判
- 在 `canContainDeletedFiles` 方法中，当 `removedDataFilePaths` 非空时返回 true
- 表达式删除时，将删除的文件添加到 `deleteFiles` 集合

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java` (+11/-0 lines)

**修改目的**：跨管理器传递待删除数据文件信息。

**工作逻辑**：在 `filterManifests` 方法中，从 `filterManager`（DataFileFilterManager）获取 `filesToBeDeleted()`，传递给 `deleteFilterManager`（DeleteManifestFilterManager）的 `removeDanglingDeletesFor` 方法。在 `DataFileFilterManager` 中覆写 `removeDanglingDeletesFor` 抛出 `UnsupportedOperationException`（仅 DeleteManifestFilterManager 支持此操作）。

### `core/src/test/java/org/apache/iceberg/TestDeleteFiles.java` (+92/-0 lines)

**修改目的**：验证通过表达式和路径删除数据文件时孤立 DV 被清理。

**工作逻辑**：
- `removingDataFileByExpressionAlsoRemovesDV`：创建带 DV 的数据文件，通过行过滤器（`Expressions.lessThan("id", 5)`）删除数据，验证对应 DV 被标记为 DELETED
- `removingDataFileByPathAlsoRemovesDV`：通过文件路径删除数据文件，验证对应 DV 被标记为 DELETED

### `core/src/test/java/org/apache/iceberg/TestReplacePartitions.java` (+52/-2 lines)

**修改目的**：验证替换分区时孤立 DV 的清理。

### `core/src/test/java/org/apache/iceberg/TestRewriteFiles.java` (+44/-2 lines)

**修改目的**：验证重写文件时孤立 DV 的清理。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java` (+34/-12 lines)

**修改目的**：调整 RowDelta 测试以适配 DV 清理行为。

### `core/src/test/java/org/apache/iceberg/RewriteDataFilesBenchmark.java` (+196/-0 lines, 新文件)

**修改目的**：添加数据文件重写性能基准测试。

### 三个版本的 `TestRewriteDataFiles.java` (+3/-1 lines each)

**修改目的**：调整测试以适配 DV 清理行为。

## 总结

这个提交完善了孤立 DV 检测机制，将其扩展到通过表达式和路径删除数据文件的场景。通过在 `ManifestFilterManager` 中跟踪待删除数据文件路径，并在过滤删除清单时检测引用这些路径的 DV，实现了自动清理孤立 DV 的能力。这是 Iceberg format version 3 DV 支持的重要完善，确保了删除操作后的数据一致性。
