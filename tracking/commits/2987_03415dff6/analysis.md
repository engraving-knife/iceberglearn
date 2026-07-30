# 提交 2987：Spark: Backport add comet reader test (#14809)

## 提交信息

- **序号**：2987 / 4088
- **哈希**：03415dff66c7ba4efd34cea2bb7f3dbcb40bb67e
- **短哈希**：03415dff6
- **日期**：2025-12-09
- **作者**：pvary
- **提交说明**：Spark: Backport add comet reader test (#14809)
- **PR/Issue**：#14809

## 总体目的

本提交是上一提交（2986，PR #14807）的回移（backport）。原 PR #14807 仅在 Spark v4.0 模块下新增了 Comet 读取器的扫描测试 `TestParquetCometVectorizedScan`。Apache Iceberg 同时维护了 v3.4 与 v3.5 两个仍受支持的 Spark 分支，这两个分支同样集成了 Comet 读取器能力，但缺乏对应的回归测试。

为了保持各 Spark 版本之间测试覆盖的一致性，避免老版本分支上出现 Comet 读取器回归却无法被捕获的情况，本提交将 v4.0 中新增的测试类原样回移到 v3.4 与 v3.5 两个模块。回移内容与原 PR 完全一致，未做任何适配性修改，说明 Comet 读取器在三个 Spark 版本上的测试基础设施（`TestParquetScan`、`ScanTestBase`）保持一致。

## 如何达成设计目的

在 `spark/v3.4/spark/src/test/...` 与 `spark/v3.5/spark/src/test/...` 两个目录下分别创建与 v4.0 完全相同的 `TestParquetCometVectorizedScan.java` 文件，内容一字不差。通过同样的继承 `TestParquetScan` 并切换 reader-type 至 `COMET` 的方式，为 v3.4 和 v3.5 复制 v4.0 的测试覆盖。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestParquetCometVectorizedScan.java` (+33/-0 lines)

**修改目的**：为 Spark v3.4 模块回移 Comet 读取器扫描测试。

**工作逻辑**：

新增类继承 `TestParquetScan`，通过 `@BeforeAll` 的 `setComet()` 方法设置 `spark.sql.iceberg.parquet.reader-type` 为 `COMET`，并覆盖 `vectorized()` 返回 `true`。逻辑与 v4.0 版本完全一致，使父类全部扫描用例以 Comet 读取器路径执行。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestParquetCometVectorizedScan.java` (+33/-0 lines)

**修改目的**：为 Spark v3.5 模块回移 Comet 读取器扫描测试。

**工作逻辑**：

与 v3.4 文件内容完全相同，同样继承 `TestParquetScan`、设置 `COMET` reader-type、覆盖 `vectorized()` 返回 `true`，为 v3.5 提供 Comet 读取器的回归保护。

## 总结

本提交是典型的回移类改动，将 PR #14807 在 Spark v4.0 引入的 Comet 读取器测试原样同步到 v3.4 与 v3.5 两个仍受维护的 Spark 分支，确保三个版本的 Comet 集成路径拥有同等的测试覆盖，避免老分支出现未被发现的行为回归。
