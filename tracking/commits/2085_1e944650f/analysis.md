# 提交 2085：API, Core: Add deleteFile to RowDelta API

## 提交信息

- **序号**：2085 / 4088
- **哈希**：1e944650fcd4718dc6a8bccc34946871bbe13726
- **短哈希**：1e944650f
- **日期**：2025-05-06 11:09:35 -0500
- **作者**：Russell Spitzer
- **提交说明**：API, Core: Add deleteFile to RowDelta API (#12861)
- **PR/Issue**：#12861

## 总体目的

`RowDelta` 是 Iceberg 中用于在单个提交中同时添加数据文件和删除文件的 API（通常用于行级 update/delete/upsert 操作的 CDC 场景）。此前 `RowDelta` 可以通过 `addRows()` 添加新数据文件、通过 `addDeletes()` 添加删除文件，但缺少直接删除数据文件的能力。这意味着在 RowDelta 操作中无法移除已有的数据文件——例如在 upsert 场景中需要用新文件替换旧文件时，只能通过单独的 overwrite 操作完成。

本提交为 `RowDelta` 接口新增 `deleteFile(DataFile)` 方法，允许在同一个 RowDelta 提交中删除数据文件。这与 Spark 等引擎的行级操作需求对齐，支持"添加新文件 + 删除旧文件 + 添加删除文件"的组合提交。同时，实现中还加入了必要的冲突验证：确保被删除的数据文件不与新增的删除文件（position delete / DV）冲突，并支持 `validateDeletedFiles` 验证删除的文件确实存在。

## 如何达成设计目的

设计分为 API 层和实现层：

- **API 层**（`RowDelta` 接口）：新增 `default RowDelta deleteFile(DataFile file)` 方法，默认抛出 `UnsupportedOperationException`（保持向后兼容，使现有实现不受影响）。
- **实现层**（`BaseRowDelta`）：
  - 维护 `deletedDataFiles`（`DataFileSet`）跟踪本次提交删除的数据文件。
  - `deleteFile()` 将文件加入 `deletedDataFiles` 集合并调用父类 `delete()` 注册删除操作。
  - 在 `validate()` 中新增三处验证：
    1. `validateDeletes` 启用时调用 `failMissingDeletePaths()` 确保删除的文件存在。
    2. `validateNewDeleteFiles` 启用时，验证被删除的数据文件没有新增的删除文件（`validateNoNewDeletesForDataFiles`）。
    3. 新增 `validateNoConflictingFileAndPositionDeletes()` 验证被删除的数据文件不在 `referencedDataFiles`（新增删除文件引用的数据文件）中，避免同时删除一个文件和为它添加删除文件。

## 修改详情

### `api/src/main/java/org/apache/iceberg/RowDelta.java` (修改, +11/-0 lines)

**修改目的**：在 `RowDelta` 接口中新增 `deleteFile` 默认方法。

**工作逻辑**：
新增 default 方法：
```java
default RowDelta deleteFile(DataFile file) {
  throw new UnsupportedOperationException(getClass().getName() + " does not implement deleteFile");
}
```
使用 default 方法 + 抛出异常的模式，保持接口向后兼容——现有的 `RowDelta` 实现类无需修改即可编译，只有 `BaseRowDelta` 覆盖该方法提供实际实现。

### `core/src/main/java/org/apache/iceberg/BaseRowDelta.java` (修改, +44/-0 lines)

**修改目的**：实现 `deleteFile` 方法和相关验证逻辑。

**工作逻辑**：
- 新增 `deletedDataFiles`（`DataFileSet`）字段，跟踪本次提交删除的数据文件。
- 实现 `deleteFile(DataFile file)`：将文件加入集合，调用父类 `MergingSnapshotProducer.delete(file)` 注册删除操作，返回 this 支持链式调用。
- 在 `validate()` 方法中新增三处验证：
  1. 当 `validateDeletes` 为 true 时，调用 `failMissingDeletePaths()` 验证被删除的文件确实存在（不存在则抛出 `ValidationException`）。
  2. 当 `validateNewDeleteFiles` 为 true 且 `deletedDataFiles` 非空时，调用 `validateNoNewDeletesForDataFiles()` 验证被删除的数据文件在起始快照之后没有新增的删除文件。
  3. 调用 `validateNoConflictingFileAndPositionDeletes()`：检查 `deletedDataFiles` 中的文件路径是否出现在 `referencedDataFiles`（本次新增删除文件引用的数据文件）中，如果存在则抛出 `ValidationException`（"Cannot delete data files that are referenced by new delete files"），避免在同一提交中既删除一个数据文件又为它添加删除文件。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java` (修改, +141/-0 lines)

**修改目的**：为 `deleteFile` 新增测试，覆盖正常删除和各类冲突验证场景。

**工作逻辑**：
新增 4 个参数化测试（`@TestTemplate`）：
- `testFileDeleteAndRowDelete`：验证在一个 RowDelta 中同时添加新数据文件（FILE_A2）和删除旧数据文件（FILE_B）成功提交，验证 manifest 中 FILE_A2 为 ADDED、FILE_B 为 DELETED。
- `testValidateFileDeleteAndRowDelete`：验证当被删除的文件已有新的删除文件时，启用 `validateNoConflictingDeleteFiles` 会抛出 `ValidationException`（"found new delete for replaced data file"）。
- `testValidateFileDeleteAndRowDeleteSameFile`：验证在同一个提交中既为文件添加删除文件又删除该文件会抛出 `ValidationException`（"Cannot delete data files that are referenced by new delete files"）。
- `testValidateDeleteFile`：验证删除不存在的文件时启用 `validateDeletedFiles` 抛出异常（"Missing required files to delete"）；不启用验证时提交成功但为 no-op（不改变表状态）。

## 总结

本提交为 `RowDelta` API 新增 `deleteFile(DataFile)` 方法，允许在单个提交中删除数据文件（与添加新文件和删除文件组合），支持 CDC/upsert 场景中的文件替换。API 层使用 default 方法保持向后兼容，实现层在 `BaseRowDelta` 中维护删除文件集合并新增三类冲突验证（文件存在性验证、删除文件无新删除验证、删除文件与引用文件冲突验证），配套 4 个参数化测试覆盖正常和冲突场景。
