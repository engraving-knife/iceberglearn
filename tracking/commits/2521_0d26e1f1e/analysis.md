# 提交 2521：Spark 4.0: Add removed_delete_files_count to result of RewriteDataFilesProcedure (#13657)

## 提交信息

- **序号**：2521 / 4088
- **哈希**：0d26e1f1eec1ff67d4633b4bc38cd7ceb65a99a2
- **短哈希**：0d26e1f1e
- **日期**：2025-08-18 13:23:49 -0700
- **作者**：Manu Zhang
- **提交说明**：Spark 4.0: Add removed_delete_files_count to result of RewriteDataFilesProcedure (#13657)
- **PR/Issue**：#13657

## 总体目的

此提交为 Spark 4.0 版本的 `RewriteDataFilesProcedure` 存储过程添加了一个新的返回字段 `removed_delete_files_count`，用于在数据文件重写操作的结果中显示被移除的删除文件（delete files）数量。

Iceberg 表在执行 `rewrite_data_files` 操作时，除了重写数据文件外，还可能涉及删除文件（delete files）的清理。删除文件用于记录行级删除操作（position deletes 和 equality deletes），在数据文件重写过程中，与被重写数据文件关联的删除文件也会被移除。然而，此前的存储过程结果中只包含以下四个字段：
- `rewritten_data_files_count`：被重写的数据文件数
- `added_data_files_count`：新增的数据文件数
- `rewritten_bytes_count`：重写的字节数
- `failed_data_files_count`：处理失败的数据文件数

缺少被移除删除文件数量的信息，用户无法从存储过程结果中了解删除文件的清理情况，需要额外查询快照摘要才能获取。

## 如何达成设计目的

设计方案在 `RewriteDataFilesProcedure` 的输出 schema 中新增 `removed_delete_files_count` 字段（IntegerType），并从重写结果对象中提取 `removedDeleteFilesCount` 值填入返回行。

具体设计要点：
1. 在输出 `StructType` 中添加新的 `StructField("removed_delete_files_count", IntegerType, false, ...)`
2. 在构建返回行时，从 `result.removedDeleteFilesCount()` 获取值并追加到 `InternalRow`
3. 更新所有相关测试用例，将期望的输出数组大小从 4 改为 5，并在空表场景中添加额外的 0 值

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteDataFilesProcedure.java` (+6/-2 lines)

**修改目的**：在存储过程的输出 schema 和结果行中添加 `removed_delete_files_count` 字段。

**工作逻辑**：
1. 在 `OUTPUT_TYPE` 的 `StructType` 定义中，在 `failed_data_files_count` 字段后追加 `removed_delete_files_count` 字段
2. 在 `toOutputRow` 方法中，从 `result.removedDeleteFilesCount()` 获取值，并在构建 `InternalRow` 时将其作为第五个参数传入

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+17/-17 lines)

**修改目的**：更新测试用例以适配新增的输出字段。

**工作逻辑**：
1. 将所有 `assertThat(output.get(0)).hasSize(4)` 断言改为 `hasSize(5)`
2. 在空表和零重写场景中，将 `row(0, 0, 0L, 0)` 改为 `row(0, 0, 0L, 0, 0)`，新增第五个元素 0 表示无删除文件被移除

## 总结

此提交为 Spark 4.0 的 RewriteDataFilesProcedure 添加了 `removed_delete_files_count` 输出字段，使用户能够直接从存储过程结果中了解删除文件的清理情况。这提高了数据重写操作的透明度和可观测性，无需额外查询快照摘要即可获取完整的重写统计信息。
