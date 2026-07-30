# 提交 1899：Spark 3.4: Backport DVs related parts (#12603)

## 提交信息

- **序号**：1899 / 4088
- **哈希**：74051b44f933bf3f33bb9ab9b04ed987e16b40dc
- **短哈希**：74051b44f
- **日期**：2025-03-21 18:11:32 +0100
- **作者**：Tom Tanaka
- **提交说明**：Spark 3.4: Backport DVs related parts (#12603)
- **PR/Issue**：#12603

## 总体目的

这个提交将 DV（Deletion Vector）相关的代码部分向后移植到 Spark 3.4 模块。DV 是 Iceberg format-version v3 引入的新删除格式，与传统的 position delete 文件不同，DV 使用 Puffin 文件格式存储紧凑的删除向量。

此次 backport 主要涉及两个方面：

1. **`SparkContentFile` 支持 DV 相关字段**：DV 文件除了常规的文件元数据外，还需要 `referenced_data_file`（引用的数据文件路径）、`content_offset`（内容偏移量）和 `content_size`（内容大小）三个字段。`SparkContentFile` 是 Spark 中表示内容文件（数据文件/删除文件）的包装类，需要支持这些新字段。

2. **`RewritePositionDeleteFilesSparkAction` 添加 v3 限制**：在 v3 表上，position delete rewrite 的行为与 v2 不同（v3 中 delete 以 DV 格式存储），因此需要添加检查确保不在 v3 表上执行旧的 rewrite 逻辑。

## 如何达成设计目的

- 在 `SparkContentFile` 中新增 `referencedDataFilePosition`、`contentOffsetPosition`、`contentSizePosition` 三个字段位置索引和对应的 getter 方法
- 在 `RewritePositionDeleteFilesSparkAction` 中添加 `Preconditions.checkArgument` 检查表 format-version 不超过 v2
- 更新相关测试类适配 DV 相关的变化

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java` (修改, +27 lines)

**修改目的**：为 SparkContentFile 添加 DV 相关字段的读取支持。

**工作逻辑**：

1. 新增三个字段位置索引：`referencedDataFilePosition`、`contentOffsetPosition`、`contentSizePosition`，在构造函数中从 Spark StructType 的字段位置映射中获取。

2. 新增三个 getter 方法：
   - `referencedDataFile()`：返回 DV 引用的数据文件路径（String）
   - `contentOffset()`：返回 DV 在 Puffin 文件中的内容偏移量（Long）
   - `contentSizeInBytes()`：返回 DV 的内容大小（Long）

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java` (修改, +4 lines)

**修改目的**：添加 v3 表检查，防止在 v3 表上执行旧的 position delete rewrite。

**工作逻辑**：在 `validateAndInitOptions` 方法中添加 `Preconditions.checkArgument(TableUtil.formatVersion(table) <= 2, "Cannot rewrite position deletes for V3 table")`，确保该 action 不会在 v3 表上执行（后续提交 #12250 会修改此逻辑以支持 v3）。

### 测试文件 (5 files, various changes)

**修改目的**：更新测试以适配 DV 相关变化。

**工作逻辑**：`TestDeleteReachableFilesAction`、`TestExpireSnapshotsAction`、`TestRewriteManifestsAction`、`TestRewritePositionDeleteFilesAction` 等测试类进行适配性修改，包括 DV 相关的测试数据和断言更新。

## 总结

本提交将 DV 相关的基础设施代码 backport 到 Spark 3.4，包括 `SparkContentFile` 对 DV 字段的支持和 `RewritePositionDeleteFilesSparkAction` 的 v3 安全检查。这是 Spark 3.4 支持 v3 DV 功能的基础准备工作。
