# 提交 3858：Spark 3.5, 4.0: Add ignore_missing_files to snapshot procedure (#16754)

## 提交信息

- **序号**：3858 / 4088
- **哈希**：48b4f2addd3110c4d234ce9c12b4f0d114f543f0
- **短哈希**：48b4f2add
- **日期**：2026-06-11 09:38:39 -0700
- **作者**：drexler-sky
- **提交说明**：Spark 3.5, 4.0: Add ignore_missing_files to snapshot procedure (#16754)
- **PR/Issue**：#16754

## 总体目的

本提交是将提交 3849（#16710，Spark 4.1 的 `ignore_missing_files` 功能）向后移植（backport）到 Spark 3.5 和 4.0 版本。原提交为 `snapshot` 存储过程新增了 `ignore_missing_files` 选项，允许在快照时跳过因并发清理而消失的源数据文件。

由于 Iceberg 同时维护多个 Spark 版本（3.5、4.0、4.1），功能增强需要同步到所有受支持的版本。本提交将 3849 中引入的 API 方法、Action 实现、Procedure 参数和测试应用到 Spark 3.5 和 4.0 模块。

## 如何达成设计目的

将 3849 提交中针对 `spark/v4.1/` 的所有修改，逐一复制到 `spark/v3.5/` 和 `spark/v4.0/` 对应的文件路径下。注意 API 层（`SnapshotTable.java` 接口）和文档层（`spark-procedures.md`）的修改已在 3849 中完成，本提交只涉及 Spark 模块特有的 Action 和 Procedure 实现。

## 修改详情

### Spark 3.5 模块（3 个文件，+72/-2 lines）

以下文件的变更与提交 3849 中对应文件一致：

- `spark/v3.5/spark-extensions/src/test/java/.../TestSnapshotTableProcedure.java` (+41)：新增 `testSnapshotMissingFilesFailByDefault` 和 `testSnapshotIgnoreMissingFiles` 测试及 `createPartitionedSourceWithMissingFiles` 辅助方法。
- `spark/v3.5/spark/src/main/java/.../SnapshotTableSparkAction.java` (+17/-1)：新增 `ignoreMissingFiles` 字段和方法，传递给 `SparkTableUtil.importSparkTable()`。
- `spark/v3.5/spark/src/main/java/.../SnapshotTableProcedure.java` (+14/-1)：新增 `IGNORE_MISSING_FILES_PARAM` 参数定义和解析逻辑。

### Spark 4.0 模块（3 个文件，+72/-2 lines）

与 Spark 3.5 完全相同的变更，应用到 `spark/v4.0/` 路径下。

## 总结

这是提交 3849 的向后移植，将 Spark 4.1 的 `ignore_missing_files` 功能同步到 Spark 3.5 和 4.0 版本。变更内容与原提交的 Spark 模块部分完全一致，确保所有受支持的 Spark 版本都获得该功能。这体现了 Iceberg 多版本维护策略中功能增强同步的重要性。
