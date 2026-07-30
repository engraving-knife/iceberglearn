# 提交 1895：Spark 3.4: Include content offset/size in PositionDeletesTable

## 提交信息

- **序号**：1895 / 4088
- **哈希**：a4816c1c99063770473920cd6d62f88f90a292dc
- **短哈希**：a4816c1c9
- **日期**：2025-03-21 09:46:32 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.4: Include content offset/size in PositionDeletesTable
- **PR/Issue**：无 PR 号（提交说明中未包含）

## 总体目的

这个提交是对前一个提交（1894，DV 读取支持）的补充，在 `DVIterator` 中新增了对 `content_offset` 和 `content_size_in_bytes` 两个元数据列的支持。

在 Iceberg v3 中，DV 文件可以包含多个 DV（每个对应不同的数据文件），`content_offset` 和 `content_size_in_bytes` 用于定位 DV 文件中特定 DV 的位置和大小。当用户查询 `.position_deletes` 表并请求这些元数据列时，`DVIterator` 需要能返回这些值。

此前 `DVIterator` 只处理了 `DELETE_FILE_PATH`、`DELETE_FILE_POS`、`PARTITION_COLUMN_ID`、`SPEC_ID_COLUMN_ID`、`FILE_PATH_COLUMN_ID` 等列，遗漏了 `CONTENT_OFFSET_COLUMN_ID` 和 `CONTENT_SIZE_IN_BYTES_COLUMN_ID` 两个元数据列。

## 如何达成设计目的

在 `DVIterator.next()` 方法中新增两个条件分支，处理这两个元数据列的投影请求。使用 `deleteFile.contentOffset()` 获取内容偏移量，使用 `ScanTaskUtil.contentSizeInBytes(deleteFile)` 获取内容大小。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/DVIterator.java` (修改, +5 lines)

**修改目的**：在 DV 迭代器中支持 `content_offset` 和 `content_size_in_bytes` 元数据列。

**工作逻辑**：在 `next()` 方法构建 row 值的循环中，新增两个分支：
- 当字段 ID 为 `MetadataColumns.CONTENT_OFFSET_COLUMN_ID` 时，添加 `deleteFile.contentOffset()`
- 当字段 ID 为 `MetadataColumns.CONTENT_SIZE_IN_BYTES_COLUMN_ID` 时，添加 `ScanTaskUtil.contentSizeInBytes(deleteFile)`

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestPositionDeletesReader.java` (修改, +54/-21 lines)

**修改目的**：更新测试以验证 content offset/size 列的正确输出。

**工作逻辑**：调整现有测试用例，增加对 `content_offset` 和 `content_size_in_bytes` 列的断言验证。

## 总结

本提交是对 DV 读取功能的补充，使 `DVIterator` 能正确返回 `content_offset` 和 `content_size_in_bytes` 元数据列，完善了 `.position_deletes` 表对 DV 文件的元数据展示能力。
