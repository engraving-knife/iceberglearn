# 提交 3422：Spark 3.4, 3.5, 4.1: Fix "AlreadyExistsException: Location already exists" at rewrite_table_path (#15616)

## 提交信息

- **序号**：3422 / 4088
- **哈希**：9057bf32e94278262cc0084b0d043e757c007f12
- **短哈希**：9057bf32e9
- **日期**：2026-03-20 10:34:02 +0100
- **作者**：Bhargav Kumar Konidena
- **提交说明**：Spark 3.4, 3.5, 4.1: Fix "AlreadyExistsException: Location already exists" at rewrite_table_path (#15616)
- **PR/Issue**：#15616

## 总体目的

修复 `rewrite_table_path` 操作中出现的 `AlreadyExistsException: Location already exists` 错误。该错误发生在重写位置删除文件时，原因是使用 `Collectors.toSet()` 收集 DeleteFile 时，DeleteFile 的 `equals`/`hashCode` 实现基于文件路径，当多个删除文件可能映射到相同位置时会导致集合中丢失条目。改用 `DeleteFileSet::create` 收集删除文件，该集合类型正确处理了删除文件的唯一性。

## 如何达成设计目的

1. 将 `Collectors.toSet()` 改为 `Collectors.toCollection(DeleteFileSet::create)`
2. `DeleteFileSet` 是专门为删除文件设计的集合类型，正确处理了删除文件的唯一性和比较
3. 在 Spark 3.4、3.5、4.1 三个版本中应用相同修复
4. 每个版本新增测试验证修复

## 修改详情

### `spark/v{3.4,3.5,4.1}/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+3/-1 lines each)

**修改目的**：修复删除文件集合的收集方式。

**工作逻辑**：
- 原有代码：`.collect(Collectors.toSet())` — 使用标准 Set，DeleteFile 的 equals/hashCode 可能不正确
- 修复后：`.collect(Collectors.toCollection(DeleteFileSet::create))` — 使用专门的 DeleteFileSet

### `spark/v{3.4,3.5,4.1}/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+73 lines each)

**修改目的**：新增测试验证修复。

**工作逻辑**：
- 新增测试覆盖导致 AlreadyExistsException 的场景，验证重写表路径操作能正确处理删除文件

## 总结

本提交修复了 `rewrite_table_path` 中的 `AlreadyExistsException` 错误，通过将删除文件收集方式从 `Collectors.toSet()` 改为 `Collectors.toCollection(DeleteFileSet::create)`，使用专门的 DeleteFileSet 正确处理删除文件的唯一性。修复应用到 Spark 3.4、3.5、4.1 三个版本。
