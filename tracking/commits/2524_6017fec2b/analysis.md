# 提交 2524：Spark 3.5, 3.4: Add removed_delete_files_count to result of RewriteDataFilesProcedure (#13862)

## 提交信息

- **序号**：2524 / 4088
- **哈希**：6017fec2b67884424f152e497424e48414c4a4f3
- **短哈希**：6017fec2b
- **日期**：2025-08-18 22:14:28 -0700
- **作者**：Manu Zhang
- **提交说明**：Spark 3.5, 3.4: Add removed_delete_files_count to result of RewriteDataFilesProcedure (#13862)
- **PR/Issue**：#13862（backport of #13657）
- **原PR**：#13657（提交 2521）

## 总体目的

此提交是 PR #13657（提交 2521）的回溯版本，将 `removed_delete_files_count` 输出字段添加到 Spark 3.5 和 Spark 3.4 版本的 `RewriteDataFilesProcedure` 中。

原 PR #13657 仅为 Spark 4.0 添加了该字段。由于 Iceberg 同时维护多个 Spark 版本（3.4、3.5、4.0），每个版本有独立的代码副本，因此需要在所有支持的版本中同步应用相同的改进。

`removed_delete_files_count` 字段用于在数据文件重写操作的结果中显示被移除的删除文件（delete files）数量。与重写的数据文件不同，删除文件记录行级删除操作，在数据文件重写时关联的删除文件也会被清理。此字段使用户能够直接从存储过程结果中了解删除文件的清理情况。

## 如何达成设计目的

回溯提交将完全相同的修改应用到 Spark 3.4 和 Spark 3.5 版本，修改内容与提交 2521 完全一致：

1. 在 `RewriteDataFilesProcedure` 的输出 `StructType` 中添加 `removed_delete_files_count` 字段（IntegerType）
2. 在构建返回行时从 `result.removedDeleteFilesCount()` 获取值
3. 更新所有测试用例中的断言（输出数组大小从 4 改为 5，空表场景添加额外的 0 值）

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteDataFilesProcedure.java` (+6/-2 lines)

**修改目的**：在 Spark 3.4 版本的存储过程输出中添加 `removed_delete_files_count` 字段。

**工作逻辑**：在 `OUTPUT_TYPE` 的 `StructType` 中追加 `removed_delete_files_count` 字段，在 `toOutputRow` 方法中从结果对象获取值并传入 `InternalRow`。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+17/-17 lines)

**修改目的**：更新 Spark 3.4 版本的测试用例。

**工作逻辑**：将 `hasSize(4)` 断言改为 `hasSize(5)`，将 `row(0, 0, 0L, 0)` 改为 `row(0, 0, 0L, 0, 0)`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteDataFilesProcedure.java` (+6/-2 lines)

**修改目的**：在 Spark 3.5 版本的存储过程输出中添加 `removed_delete_files_count` 字段。

**工作逻辑**：同 Spark 3.4 版本的修改。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+17/-17 lines)

**修改目的**：更新 Spark 3.5 版本的测试用例。

**工作逻辑**：同 Spark 3.4 版本的修改。

## 总结

此提交是提交 2521 的回溯版本，将 `removed_delete_files_count` 输出字段同步到 Spark 3.4 和 Spark 3.5 版本，确保所有支持的 Spark 版本在 RewriteDataFilesProcedure 的结果中提供一致的删除文件清理统计信息。
