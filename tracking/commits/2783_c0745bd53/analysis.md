# 提交 2783：Core: Remove duplicate test assertion in delete file index tests (#14382)

## 提交信息

- **序号**：2783 / 4088
- **哈希**：c0745bd53dbc3ac3dfc0f4e214067f285f53bdc3
- **短哈希**：c0745bd53
- **日期**：2025-10-21 11:11:24 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Remove duplicate test assertion in delete file index tests (#14382)
- **PR/Issue**：#14382

## 总体目的

本提交移除 `DeleteFileIndexTestBase` 中一个重复的测试断言。

在测试代码中，存在两行几乎完全相同的断言，都验证 `index.forDataFile(4, unpartitionedFile)` 的结果。第一行的断言消息是 "All deletes should apply to seq 4"，第二行是 "Last 3 deletes should apply to seq 4"。两者的期望值相同（`Arrays.copyOfRange(deleteFiles, 1, 4)`），但第一行的断言消息描述有误——对于 seq 4，不是"All deletes"而是"Last 3 deletes"。

这显然是一个复制粘贴遗留的问题。第一行是错误的重复断言（消息不正确），第二行才是正确的断言。本提交删除第一行重复且消息不正确的断言，保留第二行正确的断言。

## 如何达成设计目的

直接删除 `DeleteFileIndexTestBase.java` 中第 215-218 行的重复断言块（`assertThat(index.forDataFile(4, unpartitionedFile)).as("All deletes should apply to seq 4").isEqualTo(Arrays.copyOfRange(deleteFiles, 1, 4))`），保留下方消息正确的断言（"Last 3 deletes should apply to seq 4"）。

## 修改详情

### `core/src/test/java/org/apache/iceberg/DeleteFileIndexTestBase.java` (-4 lines)

**修改目的**：删除重复且消息不正确的测试断言。

**工作逻辑**：移除 4 行代码——一个 `assertThat` 调用，其消息 "All deletes should apply to seq 4" 对于 seq 4 的场景描述不准确（实际只应用最后 3 个删除文件），且与紧随其后的正确断言（"Last 3 deletes should apply to seq 4"）重复。

## 总结

本提交是一个简单的测试代码清理，移除了 `DeleteFileIndexTestBase` 中因复制粘贴产生的重复断言。该重复断言的消息描述也不正确（"All deletes" 应为 "Last 3 deletes"），删除后测试逻辑更清晰。
