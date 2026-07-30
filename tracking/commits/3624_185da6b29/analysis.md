# 提交 3624：Core: Avoid unnecessary manifest scanning during snapshot expiration incremental cleanup (#16077)

## 提交信息

- **序号**：3624 / 4088
- **哈希**：185da6b299edcf087b32f0b8d09249427c7ecc72
- **短哈希**：185da6b29
- **日期**：2026-04-30 13:12:15 -0600
- **作者**：hemanthboyina
- **提交说明**：Core: Avoid unnecessary manifest scanning during snapshot expiration incremental cleanup (#16077)
- **PR/Issue**：#16077

## 总体目的

这个提交修复了快照过期（snapshot expiration）增量清理过程中，不必要的 manifest 文件扫描问题，优化了清理性能。

在快照过期的增量清理过程中，`FileCleanupStrategy` 需要读取 manifest 文件来确定哪些文件可以安全删除。为了避免读取 manifest 的内容（只需要 manifest 的元数据），代码使用了一个 manifest 元数据投影（projection），只读取需要的列。

之前的投影包含了 `deleted_data_files_count` 列，这个列在 manifest 的 Avro schema 中可能不是一个顶层字段，可能需要扫描 manifest 文件内容才能获取。通过将投影列改为 `added_files_count` 和 `deleted_files_count`（这些是 manifest 文件本身的元数据属性），可以避免不必要的 manifest 内容扫描。

## 如何达成设计目的

修改 `FileCleanupStrategy` 中 manifest 元数据投影的列列表，将 `deleted_data_files_count` 替换为 `added_files_count` 和 `deleted_files_count`。这些列是 manifest 文件的属性，可以从 manifest 列表文件中直接读取，无需扫描 manifest 文件内容。

## 修改详情

### `core/src/main/java/org/apache/iceberg/FileCleanupStrategy.java` (+2/-1 lines)

**修改目的**：修改 manifest 元数据投影列，避免不必要的 manifest 扫描。

**工作逻辑**：
```java
// 之前
"deleted_data_files_count");

// 之后
"added_files_count",
"deleted_files_count");
```
`MANIFEST_READING_PROJECTED_FIELDS` 静态集合定义了读取 manifest 时投影的列。将 `deleted_data_files_count`（需要扫描 manifest 内容）替换为 `added_files_count` 和 `deleted_files_count`（manifest 文件元数据属性），使得清理过程只需读取 manifest 列表文件即可获取所需信息。

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java` (+43/-0 lines)

**修改目的**：添加测试验证 append-only manifest 在清理时不被扫描。

**工作逻辑**：
```java
@TestTemplate
public void testAppendOnlyManifestsNotScannedDuringCleanup() {
  // 使用 spy FileIO 来监控文件读取
  TestTables.LocalFileIO spyFileIO = Mockito.spy(new TestTables.LocalFileIO());
  // 创建表并添加文件
  testTable.newAppend().appendFile(FILE_A).commit();
  Set<String> appendOnlyManifestPaths = firstSnapshot.allManifests(testTable.io())...;
  // 添加第二个文件
  testTable.newAppend().appendFile(FILE_B).commit();
  // 过期第一个快照
  removeSnapshots(testTable).expireOlderThan(tAfterCommits).deleteWith(deletedFiles::add).commit();
  // 验证 append-only manifest 没有被读取
  appendOnlyManifestPaths.forEach(
      path -> Mockito.verify(spyFileIO, Mockito.never()).newInputFile(path));
}
```
测试使用 Mockito spy 监控 FileIO，验证在过期 append-only 快照时，对应的 manifest 文件没有被读取（`newInputFile` 从未被调用）。

## 总结

这个提交优化了快照过期增量清理的性能，通过修改 manifest 元数据投影列，避免了不必要的 manifest 文件内容扫描。对于只包含 append 操作的快照，清理时不再需要读取其 manifest 文件，减少了 I/O 开销。测试通过 Mockito spy 验证了 append-only manifest 确实没有被扫描。
