# 提交 3307：Revert "Move deleted files to Hadoop trash if configured (#14501)" (#15386)

## 提交信息

- **序号**：3307 / 4088
- **哈希**：a97b4ecc4db52ee2b0ddcecd34219275df002876
- **短哈希**：a97b4ecc4
- **日期**：2026-02-23
- **作者**：Daniel Weeks
- **提交说明**：Revert "Move deleted files to Hadoop trash if configured (#14501)" (#15386)
- **PR/Issue**：#15386（回移 #14501）

## 总体目的

此前提交 #14501 为 `HadoopFileIO` 增加了将删除的文件移动到 Hadoop Trash（回收站）的功能，而非直接物理删除。该功能在 `deleteFile` 和 `deletePrefix` 路径中引入了一个 `deletePath` 私有方法：当文件系统是 `LocalFileSystem` 或 `DistributedFileSystem` 且 Trash 启用时，调用 `trash.moveToTrash(toDelete)`，否则回退到 `fs.delete`。

本提交将该功能完整回退。回退的原因在于该行为改变了 Iceberg 文件删除的语义——Iceberg 在执行压缩（compaction）、快照过期（expire snapshots）等维护操作时会删除旧数据文件，将这些文件移入 Trash 而非真正删除会导致存储空间无法及时释放，且 Trash 的保留策略可能与 Iceberg 自身的生命周期管理产生冲突。此外，Trash 行为依赖于底层文件系统类型判断，在 S3、GCS 等对象存储上语义不一致，可能引入意想不到的副作用。提交说明仅注明 `This reverts commit 06c1e0a0be75b2dd419b2a97fcb47676cf4da279.`，属于标准的回退提交。

## 如何达成设计目的

通过 `git revert` 方式移除 #14501 引入的所有改动，恢复 `HadoopFileIO` 的 `deleteFile`/`deletePrefix` 直接调用 `fs.delete` 的原始行为，删除 `deletePath` 私有方法及相关的 Trash 导入，并移除对应的两个 Trash 测试用例。

## 修改详情

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopFileIO.java` (+2/-17 lines)

**修改目的**：移除 Trash 回收功能，恢复直接删除行为。

**工作逻辑**：
- 移除了对 `LocalFileSystem`、`Trash`、`DistributedFileSystem` 三个类的导入。
- `deleteFile` 方法中将 `deletePath(fs, toDelete, false)` 恢复为 `fs.delete(toDelete, false /* not recursive */)`。
- `deletePrefix` 方法中将 `deletePath(fs, prefixToDelete, true)` 恢复为 `fs.delete(prefixToDelete, true /* recursive */)`。
- 完全删除了 `deletePath` 私有方法，该方法原逻辑为：创建 `Trash` 实例，判断文件系统类型和 Trash 是否启用，若满足条件则 `trash.moveToTrash(toDelete)`，否则 `fs.delete(toDelete, recursive)`。

### `core/src/test/java/org/apache/iceberg/hadoop/TestHadoopFileIO.java` (+0/-62 lines)

**修改目的**：移除与 Trash 功能相关的两个测试用例。

**工作逻辑**：
- 删除了 `testDeletePrefixWithTrashEnabled` 测试：该测试设置 `FS_TRASH_INTERVAL_KEY` 为 60 秒，创建随机文件后调用 `deletePrefix`，验证文件被移入 Trash 路径而非直接删除。
- 删除了 `testDeleteFilesWithTrashEnabled` 测试：类似逻辑，验证 `deleteFiles` 后文件存在于 Trash 中。
- 同时移除了对 `FS_TRASH_INTERVAL_KEY` 的静态导入。

## 总结

本提交回退了将删除文件移入 Hadoop Trash 的功能（#14501），恢复 `HadoopFileIO` 直接物理删除文件的原始行为。这避免了 Trash 回收机制与 Iceberg 自身数据生命周期管理的语义冲突，以及在不同存储后端上行为不一致的问题，保证了维护操作后存储空间的及时释放。
