# 提交 2802：Core: Fix view version ID deduplication with schema ID assignment (#14434)

## 提交信息

- **序号**：2802 / 4088
- **哈希**：c4e480da64e516d712055c5f16e13773b4b5b8a8
- **短哈希**：c4e480da6
- **日期**：2025-10-28 13:05:51 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Fix view version ID deduplication with schema ID assignment (#14434)
- **PR/Issue**：#14434

## 总体目的

本提交修复了 View 元数据构建器中视图版本 ID 去重与 schema ID 分配同时发生时的 bug。

在 Iceberg 的 View 元数据构建过程中，当添加视图版本（ViewVersion）时可能发生两种操作：
1. **版本 ID 去重**：如果新添加的视图版本与已有版本的内容相同（基于 SQL 和代表字段），会为其分配一个已存在的版本 ID，而不是创建新 ID。
2. **Schema ID 分配**：如果视图版本的 schemaId 为 `LAST_ADDED`（-1，表示使用最后添加的 schema），会被替换为实际最后添加的 schema ID。

Bug 出现在这两个操作同时发生时：代码先对 `newVersion`（原始版本）检查 schemaId 是否为 `LAST_ADDED`，但此时版本 ID 去重已经将 `version` 变量更新为去重后的版本。当需要重新分配 schemaId 时，代码错误地从 `newVersion`（未去重的版本）构建新版本，而不是从 `version`（已去重的版本）构建。这导致 schemaId 被分配到了错误的版本实例上，最终设置 currentVersionId 时找不到对应的版本，抛出 "Cannot set current version to unknown version" 异常。

## 如何达成设计目的

将 schema ID 分配逻辑中引用的变量从 `newVersion` 改为 `version`，确保在版本 ID 去重后，schema ID 分配使用的是正确的（去重后的）版本实例。

## 修改详情

### `core/src/main/java/org/apache/iceberg/view/ViewMetadata.java` (+2/-3 lines)

**修改目的**：修复 schema ID 分配时使用了错误版本实例的问题。

**工作逻辑**：在 `addVersion` 方法的构建逻辑中，两处修改：
1. 将 `if (newVersion.schemaId() == LAST_ADDED)` 改为 `if (version.schemaId() == LAST_ADDED)` -- 检查去重后的版本的 schemaId。
2. 将 `ImmutableViewVersion.builder().from(newVersion).schemaId(lastAddedSchemaId).build()` 改为 `ImmutableViewVersion.builder().from(version).schemaId(lastAddedSchemaId).build()` -- 从去重后的版本构建新版本。

这样，当版本 ID 去重和 schema ID 分配同时发生时，schemaId 会正确地分配到去重后的版本实例上。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadata.java` (+20/-0 lines)

**修改目的**：添加版本 ID 去重与 schema ID 分配同时发生的测试。

**工作逻辑**：新增 `deduplicatingViewVersionByIdAndAssigningSchemaId` 测试：创建三个视图版本，其中第三个版本（versionId=2, schemaId=-1）会与第二个版本发生去重（因为 SQL 不同，实际不会去重，但 versionId 为 2 会与第二个版本冲突）。添加两个 schema，第三个版本使用 `LAST_ADDED`（-1）作为 schemaId。设置 currentVersionId 为 3。验证：版本数量为 3，当前版本 ID 为 3，当前版本的 schemaId 为 1（最后添加的 schema）。这个测试在修复前会抛出 "Cannot set current version to unknown version: 3" 异常。

## 总结

本提交修复了 View 元数据构建器中一个变量引用错误导致的 bug。当视图版本 ID 去重和 schema ID 分配同时发生时，代码错误地从原始版本（`newVersion`）而非去重后的版本（`version`）构建新版本，导致 schemaId 分配到错误实例。修复仅需将两处变量引用从 `newVersion` 改为 `version`，并添加了对应的测试用例。
