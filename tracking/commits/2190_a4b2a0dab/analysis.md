# 提交 2190：API, Core: Rename RowDelta deleteFile() to removeRows() (#13184)

## 提交信息

- **序号**：2190 / 4088
- **哈希**：a4b2a0dab092821d4843749b8abc30208622e164
- **短哈希**：a4b2a0dab
- **日期**：2025-06-02 07:55:29 -0500
- **作者**：Russell Spitzer
- **提交说明**：API, Core: Rename RowDelta deleteFile() to removeRows() (#13184)
- **PR/Issue**：#13184

## 总体目的

这个提交对 RowDelta API 中的 `deleteFile(DataFile)` 方法进行重命名，改为 `removeRows(DataFile)`。原有的 `deleteFile` 命名存在语义上的歧义：在 Iceberg 中，"delete file" 通常指 position delete 或 equality delete 这类删除文件（delete file），而 `RowDelta.deleteFile()` 实际的语义是"移除一个数据文件（DataFile）所包含的行数据"。这种命名混淆容易让使用者误以为该方法用于添加删除文件，而非移除数据行。通过将方法重命名为 `removeRows`，可以更准确地表达其真实意图——即从表中移除某个数据文件对应的行，从而提升 API 的可读性和易用性，减少误用风险。

## 如何达成设计目的

- 修改 `RowDelta` 接口中的默认方法签名，将 `deleteFile` 重命名为 `removeRows`，并同步更新 Javadoc 中"Delete a DataFile"为"Remove a DataFile"。
- 在 `BaseRowDelta` 实现类中，将方法名及内部字段 `deletedDataFiles` 重命名为 `removedDataFiles`，并更新所有引用该字段的地方（验证逻辑、冲突检测逻辑）。
- 更新 `TestRowDelta` 测试类中所有调用 `deleteFile()` 的地方为 `removeRows()`，并将相关测试方法名中的 `DeleteFile` 替换为 `RemoveRows`，保持测试命名与新语义一致。

## 修改详情

### `api/src/main/java/org/apache/iceberg/RowDelta.java` (修改, +2/-2 lines)

**修改目的**：重命名接口默认方法，使 API 语义更清晰。

**工作逻辑**：将 `default RowDelta deleteFile(DataFile file)` 改为 `default RowDelta removeRows(DataFile file)`，Javadoc 注释由"Delete a DataFile from the table"改为"Remove a DataFile from the table"，异常消息中保留旧方法名引用的提示不变。

### `core/src/main/java/org/apache/iceberg/BaseRowDelta.java` (修改, +6/-6 lines)

**修改目的**：实现类同步重命名方法与内部字段。

**工作逻辑**：
- 将私有字段 `deletedDataFiles` 重命名为 `removedDataFiles`。
- 将 `deleteFile(DataFile)` 方法重命名为 `removeRows(DataFile)`，内部逻辑不变（将文件加入 removedDataFiles 集合并调用 `delete(file)`）。
- 在 `validate` 方法中，将引用 `deletedDataFiles` 的验证逻辑（validateNoNewDeletesForDataFiles）改用 `removedDataFiles`。
- 在 `validateNoConflictingFileAndPositionDeletes` 中，将 `deletedDataFiles.stream()` 改为 `removedDataFiles.stream()`，用于检测同一文件既被移除又被引用的冲突。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java` (修改, +14/-14 lines)

**修改目的**：测试代码同步更新方法调用与方法名。

**工作逻辑**：将所有 `.deleteFile(FILE_X)` 调用替换为 `.removeRows(FILE_X)`，并将多个测试方法名中的 `DeleteFile` 替换为 `RemoveRows`（如 `testAddDeleteFile` → `testAddRemoveRows`、`testValidateDeleteFile` → `testValidateRemoveRows`、`testConcurrentDeletesRewriteSameDeleteFile` → `testConcurrentDeletesRewriteSameRemoveRows` 等），共涉及约 10 处方法名重命名与 3 处方法调用替换。

## 总结

该提交是一次纯 API 命名重构，将容易引起歧义的 `deleteFile` 方法重命名为语义更准确的 `removeRows`，明确了"移除数据文件对应行"的含义，避免与"添加删除文件"混淆。修改局限于 API 接口、核心实现和测试，属于破坏性 API 变更，需要下游使用者适配。
