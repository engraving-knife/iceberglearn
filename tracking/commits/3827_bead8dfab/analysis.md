# 提交 3827：Spark 3.5, 4.0: Add ignore_missing_files to migrate procedure (#16684)

## 提交信息

- **序号**：3827 / 4088
- **哈希**：bead8dfab3249cbc6ba7713e862ff2698fd2122c
- **短哈希**：bead8dfab
- **日期**：2026-06-05 09:42:28 -0700
- **作者**：drexler-sky <evan123wu@gmail.com>
- **提交说明**：Spark 3.5, 4.0: Add ignore_missing_files to migrate procedure (#16684)
- **PR/Issue**：#16684（回PORT PR），原实现 #16643（提交 3825）

## 总体目的

本提交将上一个提交（#16643，提交 3825）中针对 Spark 4.1 实现的 `ignore_missing_files` 迁移过程选项，回移植（backport）到 Iceberg 同时维护的另外两个 Spark 版本分支：`spark/v3.5` 与 `spark/v4.0`。Iceberg 项目为 Spark 3.5、4.0、4.1 三个版本分别维护独立的源代码目录，原实现只落在 v4.1 目录，若不回移植，使用 v3.5 或 v4.0 的用户在迁移表时遇到源数据文件丢失仍会直接失败，无法享受容错跳过能力。

回移植的内容与原实现完全一致：在 `MigrateTableSparkAction` 中实现 `ignoreMissingFiles()`，在 `MigrateTableProcedure` 中新增 `ignore_missing_files` 可选布尔参数，调用底层 `SparkTableUtil.importSparkTable` 时传入该标志，并新增覆盖默认失败与跳过行为的测试。

## 如何达成设计目的

由于 Iceberg 的 Spark 多版本目录结构平行，回移植只需把 v4.1 下的改动原样应用到 v3.5 与 v4.0 对应文件。两个版本的改动内容、行数完全一致，体现了多版本并行维护的对称性。API 接口层的 `MigrateTable.ignoreMissingFiles()` 默认方法已在原实现提交中添加，无需重复修改。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/MigrateTableSparkAction.java` (+14/-1 lines)

**修改目的**：在 Spark 3.5 实现中支持 `ignoreMissingFiles`。

**工作逻辑**：
与提交 3825 中 v4.1 改动完全一致：新增 `ignoreMissingFiles` 字段，实现 `ignoreMissingFiles()` 方法，在调用 `SparkTableUtil.importSparkTable` 时传入 `ignoreMissingFiles` 标志的重载。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/MigrateTableProcedure.java` (+12/-1 lines)

**修改目的**：在 Spark 3.5 过程中暴露 `ignore_missing_files` 参数。

**工作逻辑**：
与 v4.1 一致：新增 `IGNORE_MISSING_FILES_PARAM` 参数并加入 `PARAMETERS` 数组，从入参读取后调用 action 的 `ignoreMissingFiles()`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMigrateTableProcedure.java` (+39/-0 lines)

**修改目的**：在 Spark 3.5 测试中覆盖默认失败与跳过行为。

**工作逻辑**：
与 v4.1 一致：`createPartitionedTableWithMissingFiles` 辅助方法模拟分区目录被删除；`testMigrateMissingFilesFailByDefault` 验证默认失败；`testMigrateIgnoreMissingFiles` 验证启用后跳过缺失文件并迁移存活分区。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/MigrateTableSparkAction.java` (+14/-1 lines)

**修改目的**：在 Spark 4.0 实现中支持 `ignoreMissingFiles`。改动与 v3.5、v4.1 完全一致。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/procedures/MigrateTableProcedure.java` (+12/-1 lines)

**修改目的**：在 Spark 4.0 过程中暴露 `ignore_missing_files` 参数。改动与 v3.5、v4.1 一致。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMigrateTableProcedure.java` (+39/-0 lines)

**修改目的**：在 Spark 4.0 测试中覆盖默认失败与跳过行为。改动与 v3.5、v4.1 一致。

## 总结

本提交是提交 3825 的回移植，将 `migrate` 过程的 `ignore_missing_files` 容错选项同步到 Spark 3.5 与 4.0 两个版本分支，确保所有受支持的 Spark 版本都能受益于该能力。这体现了 Iceberg 项目对多版本并行维护纪律的严格遵守——新功能必须同步到所有受影响版本分支，避免版本间的行为差异，保持用户体验一致性。
