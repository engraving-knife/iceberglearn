# 提交 3612：Core: Validate v2 deletes against concurrent format upgrade (#16146)

## 提交信息

- **序号**：3612 / 4088
- **哈希**：099ef477c8024b3bcb4d12abca18944c25b0eb45
- **短哈希**：099ef477c
- **日期**：2026-04-28 21:11:36 -0600
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core: Validate v2 deletes against concurrent format upgrade (#16146)
- **PR/Issue**：#16146

## 总体目的

这个提交修复了在并发格式升级场景下，已缓冲的 v2 删除文件可能被错误提交到更高格式版本表的问题。

Iceberg 支持不同的表格式版本（v1、v2、v3）。在 v3 中，位置删除（position deletes）必须使用 Deletion Vector（DV），而不能使用传统的位置删除文件。当一个写操作在 v2 格式下缓冲了位置删除文件，然后在此期间另一个操作将表升级到 v3 时，如果 v2 的位置删除文件被提交到 v3 表上，就会违反 v3 的约束。

之前，删除文件的格式版本验证只在添加文件时进行（`validateNewDeleteFile`），使用的是操作开始时的格式版本。在 `apply()` 阶段（实际提交前的规划阶段）没有重新验证已缓冲的删除文件是否仍然符合当前表的格式版本。这个提交在 `apply()` 方法中添加了针对当前表格式版本的重新验证。

## 如何达成设计目的

1. 将 `validateNewDeleteFile` 中的验证逻辑提取为静态方法 `validateDeleteFileForVersion(DeleteFile, int formatVersion)`，使其可以针对任意格式版本进行验证。
2. 新增 `validateDeleteFilesForVersion(int currentFormatVersion)` 方法，遍历所有已缓冲的 v2 删除文件，对每个文件调用 `validateDeleteFileForVersion` 进行验证。
3. 在 `apply()` 方法的开头调用 `validateDeleteFilesForVersion(base.formatVersion())`，使用表元数据中的当前格式版本进行验证。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java` (+15/-3 lines)

**修改目的**：在 apply 阶段重新验证已缓冲删除文件的格式兼容性。

**工作逻辑**：

1. **提取验证逻辑为静态方法**：
```java
private static void validateDeleteFileForVersion(DeleteFile file, int formatVersion) {
  switch (formatVersion) {
    case 1:
      throw new IllegalArgumentException("Deletes are supported in V2 and above");
    case 2:
      // ... v2 验证逻辑
      break;
    case 3:
      Preconditions.checkArgument(
          file.content() == FileContent.EQUALITY_DELETES || ContentFileUtil.isDV(file),
          "Must use DVs for position deletes in V%s: %s",
          formatVersion, file.location());
      break;
    default:
      throw new IllegalArgumentException("Unsupported format version: " + formatVersion);
  }
}
```
原来的 `validateNewDeleteFile` 方法现在调用此静态方法。

2. **新增批量验证方法**：
```java
// guard buffered deletes against concurrent format upgrade
private void validateDeleteFilesForVersion(int currentFormatVersion) {
  for (DeleteFile file : v2Deletes) {
    validateDeleteFileForVersion(file, currentFormatVersion);
  }
}
```
遍历所有已缓冲的 v2 删除文件（`v2Deletes`），对每个文件使用当前格式版本进行验证。

3. **在 apply 方法中调用验证**：
```java
@Override
public List<ManifestFile> apply(TableMetadata base, Snapshot snapshot) {
  validateDeleteFilesForVersion(base.formatVersion());
  // ... 后续过滤逻辑
}
```
`base` 参数是表的当前元数据，`base.formatVersion()` 返回当前格式版本。如果在操作期间表被升级，这里会使用升级后的版本进行验证。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java` (+21/-0 lines)

**修改目的**：添加并发格式升级场景的测试。

**工作逻辑**：
```java
@TestTemplate
public void testV2StagedPositionDeleteCannotCommitToV3() {
  assumeThat(formatVersion).isEqualTo(2);

  Snapshot initial = commit(table, table.newAppend().appendFile(FILE_A), branch);

  // Stage RowDelta at v2: position delete for FILE_A + add new data FILE_B.
  RowDelta rowDelta = table.newRowDelta().addDeletes(FILE_A_DELETES).addRows(FILE_B);

  // upgrade the table
  table.updateProperties().set(TableProperties.FORMAT_VERSION, "3").commit();

  assertThatThrownBy(rowDelta::commit)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Must use DVs for position deletes in V3");

  table.refresh();
  assertThat(table.operations().current().formatVersion()).isEqualTo(3);
  assertThat(table.snapshot(branch)).isEqualTo(initial);
}
```
测试验证：在 v2 表上创建 RowDelta（包含位置删除文件），然后将表升级到 v3，提交 RowDelta 时应该抛出 `IllegalArgumentException`，且提交不会成功（表快照不变）。

## 总结

这个提交修复了一个并发安全问题：当表格式在操作期间从 v2 升级到 v3 时，已缓冲的 v2 位置删除文件不应该被提交到 v3 表上（因为 v3 要求使用 DV）。通过在 `apply()` 阶段使用当前表元数据的格式版本重新验证已缓冲的删除文件，确保了格式升级的并发安全性。这是一个重要的数据一致性保障。
