# 提交 3380：Spark 4.0: Fix "AlreadyExistsException: Location already exists" at rewrite_table_path (#14859)

## 提交信息

- **序号**：3380 / 4088
- **哈希**：49295323319744a1bb1120f7b2e4b106e174d650
- **短哈希**：492953233
- **日期**：2026-03-13
- **作者**：Bhargav Kumar Konidena
- **提交说明**：Spark 4.0: Fix "AlreadyExistsException: Location already exists" at rewrite_table_path (#14859)
- **PR/Issue**：#14859（对应 Issue #14814）

## 总体目的

本提交修复了 Spark 4.0 模块中 `rewrite_table_path`（重写表路径）操作在处理位置删除文件（position delete files）时因去重失败而抛出 `AlreadyExistsException` 的缺陷。

问题场景是：当同一个 position delete 文件出现在多个 manifest 中时（例如同一个删除文件被多个快照引用），`RewriteTablePathSparkAction` 在收集待重写的 delete 文件时使用了 `Collectors.toSet()`。由于 `DeleteFile` 接口没有覆盖 `equals()`/`hashCode()` 方法，`HashSet` 无法识别两个不同对象实例是否指向同一个底层文件。结果同一文件被当作两个不同文件处理，`rewritePositionDeletes` 试图为同一目标路径写两次文件，第二次写入时抛出 `AlreadyExistsException: Location already exists`，导致整个重写表路径操作失败。

这一问题对应 GitHub Issue #14814，仅影响 format version 2（格式版本 3+ 使用存储在 Puffin 文件中的 Deletion Vector，有不同的校验规则，不会出现同一删除文件被多次添加的场景）。

## 如何达成设计目的

整体思路是用一个专门的、能正确去重 `DeleteFile` 的集合类型替代普通 `HashSet`。Iceberg 工具类中已存在 `DeleteFileSet`（`org.apache.iceberg.util.DeleteFileSet`），它内部基于文件路径等标识进行去重，不依赖 `DeleteFile.equals()`。将 `Collectors.toSet()` 改为 `Collectors.toCollection(DeleteFileSet::create)` 即可让相同文件的多个对象实例被正确去重，避免重复处理。同时新增回归测试精确复现该场景。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+2/-1 lines)

**修改目的**：将 delete 文件收集方式改为使用 `DeleteFileSet` 去重。

**工作逻辑**：
新增 `import org.apache.iceberg.util.DeleteFileSet;`，并将收集 delete 文件的流操作终止符从 `.collect(Collectors.toSet())` 改为 `.collect(Collectors.toCollection(DeleteFileSet::create))`。`DeleteFileSet.create()` 返回一个基于文件标识（而非对象引用相等性）去重的 `Set<DeleteFile>`，从而确保即使同一个删除文件作为不同对象实例出现在多个 manifest 中，也只会被收集一次，下游 `rewritePositionDeletes` 不会重复写同一目标路径。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+73/-0 lines)

**修改目的**：新增回归测试覆盖 delete 文件去重场景。

**工作逻辑**：
新增测试 `testPositionDeletesDeduplication`，首先用 `assumeThat(formatVersion).isEqualTo(2)` 限定仅 format version 2 执行（因 v3+ 使用 DV 校验规则不同）。测试步骤如下：

1. 创建带 2 个快照的表，并设置 `DELETE_DEFAULT_FILE_FORMAT=parquet`。
2. 取一个数据文件，为其创建一个 position delete 文件（删除位置 0），提交一次 `newRowDelta().addDeletes(positionDeletes).commit()`。
3. **关键步骤**：将**同一个** `positionDeletes` 文件再次通过 `newRowDelta().addDeletes(positionDeletes).commit()` 提交，使其出现在第二个快照的新 manifest 中。这样该文件在两个 manifest 中各有一条记录，处理时会得到两个不同对象实例。
4. 执行 `rewriteTablePath` 操作，断言不抛异常且 `rewrittenDeleteFilePathsCount()` 等于 1（重复的文件被去重后只重写一次）。

测试注释详细解释了修复前的失败链路：两个 manifest 包含同一文件的条目 → 处理返回两个不同 `DeleteFile` 对象 → `HashSet` 因 `DeleteFile` 未覆盖 `equals()` 无法去重 → `rewritePositionDeletes` 重复写同一文件 → `AlreadyExistsException`。这构成精确的回归保护。

## 总结

本提交修复了 `rewrite_table_path` 操作在 position delete 文件跨 manifest 重复时因 `DeleteFile` 未实现 `equals()` 而去重失败、导致 `AlreadyExistsException` 的缺陷。通过将文件收集器从 `Collectors.toSet()` 改为 `Collectors.toCollection(DeleteFileSet::create)`，利用专门基于文件标识去重的集合类型正确消除重复，并辅以精确复现 issue #14814 场景的回归测试，保障了表路径重写操作的可靠性。
