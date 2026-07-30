# 提交 2023：Spark 3.5: Add Parallelism Parameter Validation to AddFilesProcedure. (#12784)

## 提交信息

- **序号**：2023 / 4088
- **哈希**：04bb3eed3451522a4ee53daf38b198853844d815
- **短哈希**：04bb3eed3
- **日期**：2025-04-22 08:57:19 +0200
- **作者**：slfan1989
- **提交说明**：Spark 3.5: Add Parallelism Parameter Validation to AddFilesProcedure. (#12784)
- **PR/Issue**：#12784

## 总体目的

这个提交为 Spark 3.5 的 `AddFilesProcedure` 添加了 parallelism 参数的校验逻辑，确保传入的 parallelism 值必须大于 0。

`AddFilesProcedure` 是用于将外部数据文件导入 Iceberg 表的过程，支持 `parallelism` 参数控制导入并行度。此前该过程虽然通过 `input.asInt(PARALLELISM, 1)` 读取了 parallelism 参数（默认值为 1），但没有对值进行校验。如果用户传入 0 或负数，可能导致后续的迁移服务（`SparkTableUtil.migrationService(parallelism)`）行为异常。

其他过程（如 `MigrateTableProcedure` 和 `SnapshotTableProcedure`）已有类似的 parallelism 校验（`Preconditions.checkArgument(parallelism > 0, "Parallelism should be larger than 0")`），本提交为 `AddFilesProcedure` 补充了相同的校验，保持一致性。

## 如何达成设计目的

在 `AddFilesProcedure` 的 `call` 方法中，读取 parallelism 参数后立即添加 `Preconditions.checkArgument` 校验，并新增对应的测试用例验证非法值会抛出 `IllegalArgumentException`。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/AddFilesProcedure.java` (修改, +1/-0 lines)

**修改目的**：添加 parallelism 参数校验。

**工作逻辑**：
在 `int parallelism = input.asInt(PARALLELISM, 1);` 之后新增一行 `Preconditions.checkArgument(parallelism > 0, "Parallelism should be larger than 0");`，与其他过程的校验逻辑完全一致。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAddFilesProcedure.java` (修改, +16/-0 lines)

**修改目的**：新增 parallelism 非法值的测试用例。

**工作逻辑**：
新增 `testAddFilesWithInvalidParallelism` 测试方法，创建非分区 Hive 源表和分区 Iceberg 目标表，然后调用 `system.add_files` 过程传入 `parallelism => -1`，验证抛出 `IllegalArgumentException` 且消息为"Parallelism should be larger than 0"。

## 总结

本提交为 `AddFilesProcedure` 补充了 parallelism 参数的校验逻辑，确保值必须大于 0，与其他 Spark 过程（MigrateTable、SnapshotTable）保持一致，防止非法并行度值导致后续行为异常。
