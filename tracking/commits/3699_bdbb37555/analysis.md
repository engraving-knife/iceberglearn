# 提交 3699：Spark 3.4: Set data file sort_order_id in manifest for writes from Spark (#16308)

## 提交信息

- **序号**：3699 / 4088
- **哈希**：bdbb37555c9db9b134122dc4fd998e5ebbeb41af
- **短哈希**：bdbb37555
- **日期**：2026-05-12 19:04:00 -0700
- **作者**：Kevin Liu
- **提交说明**：Spark 3.4: Set data file sort_order_id in manifest for writes from Spark (#16308)
- **PR/Issue**：#16308

## 总体目的

这个提交是 PR #15832 向 Spark 3.4 模块的回移植。它为 Spark 写入操作添加了 `sort_order_id` 支持，使得通过 Spark 写入的数据文件在 manifest 中记录其排序顺序 ID。

Iceberg 的 manifest 中每个数据文件可以记录一个 `sort_order_id`，表示该文件是按照哪个排序规范写入的。这一信息对于查询优化非常重要——如果查询的过滤条件与排序顺序匹配，可以利用排序信息跳过不相关的文件。此前 Spark 3.4 写入的数据文件不记录 `sort_order_id`，导致优化器无法利用排序信息。

此回移植添加了 output-sort-order-id 写入选项，并将解析后的 sort order id 通过 `SparkWrite` 和 `SparkPositionDeltaWrite` 传递，使写入的数据文件在 manifest 条目中记录排序顺序。

## 如何达成设计目的

通过以下方式实现回移植：
1. 在 `SparkWriteConf` 中添加 sort order id 的解析逻辑
2. 在 `SparkWriteOptions` 中添加新的写入选项
3. 修改 `SparkWrite` 和 `SparkPositionDeltaWrite` 传递 sort order id
4. 修改 `SparkShufflingFileRewriteRunner` 支持 sort order id
5. 添加测试验证

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java` (+20 lines)

**修改目的**：添加 sort order id 解析逻辑。

**工作逻辑**：新增方法解析写入选项中的 sort order 配置，确定写入操作使用的排序规范 ID。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkWriteOptions.java` (+1 line)

**修改目的**：添加新的写入选项。

**工作逻辑**：新增 sort order 相关的写入选项定义。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java` (+7/-3 lines)

**修改目的**：在写入流程中传递 sort order id。

**工作逻辑**：将解析的 sort order id 传递给数据文件写入器。适配说明：v3.4 仍使用 `partitionedFanoutEnabled`（未像 v3.5 那样重命名为 `useFanoutWriter`），保留 v3.4 名称并添加新的 `sortOrderId` 参数。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java` (+8/-3 lines)

**修改目的**：在 Position Delta 写入中传递 sort order id。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkShufflingFileRewriteRunner.java` (+12/-2 lines)

**修改目的**：在文件重写操作中支持 sort order id。

### 测试文件 (+372 lines)

**修改目的**：添加测试验证 sort order id 设置。

**工作逻辑**：在 `TestCopyOnWriteDelete`、`TestCopyOnWriteMerge`、`TestCopyOnWriteUpdate`、`TestMergeOnReadMerge`、`TestMergeOnReadUpdate`、`TestSparkWriteConf`、`TestRewriteDataFilesAction`、`TestSparkDataWrite`、`TestStructuredStreaming` 中添加测试用例。

## 总结

这是一个 Spark 3.4 功能回移植提交，为写入操作添加 sort_order_id 支持，使写入的数据文件在 manifest 中记录排序信息。这对于查询优化器利用排序信息进行文件跳过、提升查询性能具有重要意义。回移植时适配了 v3.4 的命名差异（`partitionedFanoutEnabled` vs `useFanoutWriter`）。
