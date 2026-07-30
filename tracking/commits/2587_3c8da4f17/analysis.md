# 提交 2587：Test, Spark: Use Single Splits to Improve the Speed of Rewrite Tests (#13947)

## 提交信息

- **序号**：2587 / 4088
- **哈希**：3c8da4f172ec23b50bf24421db4151d1f3be9e81
- **短哈希**：3c8da4f17
- **日期**：2025-09-02 12:30:43 -0500
- **作者**：Russell Spitzer
- **提交说明**：Test, Spark: Use Single Splits to Improve the Speed of Rewrite Tests (#13947)
- **PR/Issue**：#13947

## 总体目的

此次提交优化 Spark 重写测试（Rewrite Tests）的执行速度，通过在测试读取数据时强制使用单个 split（单一分区）来减少 Spark 作业的并行度开销。

在 Iceberg 的 Spark 重写测试中，测试逻辑通常需要在重写前后读取表数据并比较一致性。这些读取操作使用默认的 split 配置，会将数据切分为多个 split/分区，Spark 会为每个分区启动 task 并行处理。然而在测试场景中，数据量很小，多分区并行反而带来了不必要的任务调度、序列化、shuffle 等开销，且 `sort` 操作在多分区时需要 shuffle，进一步拖慢测试。

通过设置较大的 `split-size`（64MB）、将 `file-open-cost` 设为 0（避免因文件打开成本估算导致额外合并分裂），并调用 `coalesce(1)` 将结果收拢到单个分区，可以使读取和排序在单个 task 内完成，避免 shuffle 和多 task 调度开销，显著加快测试执行速度。这对 CI 流水线的整体耗时优化有积极意义，因为重写测试套件本身较重。

改动覆盖 Spark v3.4、v3.5、v3.6 三个版本的 `TestRewriteDataFilesAction` 和 `TestRewritePositionDeleteFilesAction` 测试类。

## 如何达成设计目的

- 在测试中所有用于读取表数据（验证重写前后数据一致性）的 `spark.read().format("iceberg").load(...)` 调用上，追加两个读取选项：
  - `SparkReadOptions.SPLIT_SIZE = 1024 * 1024 * 64`（64MB）：设置较大的 split 大小，使小数据量被合并到单个 split。
  - `SparkReadOptions.FILE_OPEN_COST = 0`：将文件打开成本设为 0，避免 Spark 因估算文件打开成本而产生额外的 split 分裂。
- 在读取后追加 `.coalesce(1)`，确保数据收拢到单个分区，使后续 `sort` 在单分区内完成，避免 shuffle。
- 这些选项仅应用于测试中用于验证数据的读取路径，不影响重写 action 本身的执行逻辑。

## 修改详情

### `spark/v3.4/spark/src/test/java/.../spark/actions/TestRewriteDataFilesAction.java` (+34/-2)

**修改目的**：加速重写数据文件测试中的数据读取。

**工作逻辑**：
- 新增 `SparkReadOptions` import。
- 在多处数据读取调用（`originalRaw`、`postRaw`、`currentData()`、`currentDataWithLineage()`）中追加 `.option(SPLIT_SIZE, 64MB)`、`.option(FILE_OPEN_COST, 0)` 和 `.coalesce(1)`，使读取在单个 split/分区内完成，避免多 task 调度和 shuffle 开销。

### `spark/v3.4/spark/src/test/java/.../spark/actions/TestRewritePositionDeleteFilesAction.java` (+24/-2)

**修改目的**：加速位置删除文件重写测试中的数据读取。

**工作逻辑**：
- 新增 `SparkReadOptions` import。
- 在 `records()`、`deleteRecords()`、`assertLocallySorted()`、以及 position_deletes 读取等方法中追加 `SPLIT_SIZE`、`FILE_OPEN_COST` 选项和 `coalesce(1)`，同样使读取在单分区内完成。

### `spark/v3.5/...` 与 `spark/v3.6/...` 同名测试文件 (各 +34/-2 和 +24/-2)

**修改目的**：将相同的单 split 优化同步到 Spark v3.5 和 v3.6 版本。

**工作逻辑**：与 v3.4 完全一致的改动模式。

## 总结

一次测试性能优化提交，通过在 Spark 重写测试（`TestRewriteDataFilesAction` 和 `TestRewritePositionDeleteFilesAction`）的数据读取调用中设置大 split size（64MB）、file-open-cost=0 和 coalesce(1)，强制使用单个 split/分区完成读取和排序，避免小数据量下的多 task 调度和 shuffle 开销，从而加快测试执行速度。改动覆盖 Spark v3.4/v3.5/v3.6 三个版本，不影响重写 action 本身的逻辑。
