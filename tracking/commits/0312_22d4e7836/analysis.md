# 提交 0312：Spark 3.5: Parallelize reading files in add_files procedure (#9274)

## 提交信息

- **序号**：0312
- **哈希**：22d4e7836ac0bb47bed627e7c773d44105b8f0f8
- **短哈希**：22d4e7836
- **日期**：2023-12-28 07:52:21 -0800
- **作者**：Manu Zhang
- **提交说明**：Spark 3.5: Parallelize reading files in add_files procedure (#9274)
- **PR/Issue**：#9274

## 总体目的

本提交为 Spark 3.5 的 `add_files` 存储过程（procedure）新增 `parallelism` 参数，使 import 文件到 Iceberg 表时能够用多线程并行读取文件元数据，从而显著提升大数据量场景下的迁移吞吐。`add_files` 用于把已有的 Spark 表（Hive catalog 表或文件路径表）"导入"为 Iceberg 表——其核心工作是遍历源表的分区、读取每个分区下的数据文件、提取文件指标（metrics）并生成 Iceberg 的 manifest。在文件数量巨大时，逐个串行读取文件元数据成为瓶颈，因此本提交在调用链上贯通一条 `parallelism` 参数，最终落到 `TableMigrationUtil.listPartition` 内部的线程池规模，由该线程池并发地读取文件元数据。

参数 `parallelism` 默认值为 1（保持向后兼容），用户可在调用 `CALL catalog.system.add_files(..., parallelism => N)` 时显式指定线程数。需要注意的是：这里的"并行"针对的是单个节点上读取文件元数据的线程级并行（通过 `Executors.newFixedThreadPool(parallelism)` 实现），与 Spark 已有的分布式分区发现并行度（`parallelPartitionDiscoveryParallelism`，控制 `sparkContext.parallelize` 的分区数）是两个不同维度的并行——本提交为避免命名冲突，特意将原局部变量 `parallelism` 重命名为 `listingParallelism`，以区分新增的文件读取 `parallelism`。

## 如何达成设计目的

实现路径为自上而下贯通参数：`AddFilesProcedure`（过程入口）解析新参数 `parallelism`，经 `importToIceberg` / `importFileTable` / `importCatalogTable` / `importPartitions` 一路透传到 `SparkTableUtil.importSparkPartitions` / `importSparkTable` / `importUnpartitionedSparkTable`，再到 `listPartition`，最终到达 `TableMigrationUtil.listPartition` 内部由 `migrationService(parallelism)` 创建的固定线程池。同时为 `ProcedureInput` 增加 `asInt`/`asInt(param, default)` 工具方法以从过程入参中读取整数类型参数。配套新增测试 `testAddFilesWithParallelism` 验证 `parallelism => 2` 下数据正确导入，并在文档 `spark-procedures.md` 的参数表中补充该参数。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/AddFilesProcedure.java`

**修改目的**：在 `add_files` 过程中注册 `parallelism` 入参并贯穿调用链。

**工作逻辑**：新增常量 `PARALLELISM = ProcedureParameter.optional("parallelism", DataTypes.IntegerType)`，并将其加入 `PARAMETERS` 数组末尾。在 `call` 方法中通过 `int parallelism = input.asInt(PARALLELISM, 1)` 读取参数（默认 1）；将 `parallelism` 一路透传给 `importToIceberg`、`importFileTable`、`importCatalogTable`、`importPartitions` 方法签名（这些方法都新增一个 `int parallelism` 末尾参数）。各方法内部调用 `SparkTableUtil.importSparkTable` / `importSparkPartitions` 时同样传入 `parallelism`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/ProcedureInput.java`

**修改目的**：为 `ProcedureInput` 增加 `Integer` 类型参数读取能力。

**工作逻辑**：新增两个方法，与已有的 `asBoolean`/`asLong` 模式对齐：
- `public Integer asInt(ProcedureParameter param)`：读取必填整数参数，若为 null 抛出 `Preconditions.checkArgument` 异常。
- `public Integer asInt(ProcedureParameter param, Integer defaultValue)`：读取可选整数参数，先 `validateParamType(param, DataTypes.IntegerType)` 校验类型，再通过 `ordinal(param)` 取列序号，`args.isNullAt(ordinal)` 时返回默认值，否则 `(Integer) args.getInt(ordinal)` 取值。注意 Spark 的 `InternalRow.getInt` 返回 `int`，这里装箱为 `Integer`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`

**修改目的**：在 `SparkTableUtil` 的 import 系列方法中透传 `parallelism`，并区分"分区发现并行度"与"文件读取并行度"两个概念。

**工作逻辑**：
- 新增带 `int parallelism` 末尾参数的 `importSparkTable(...)` / `importSparkPartitions(...)` 重载，并保留原无 parallelism 的重载（内部以 `parallelism=1` 调用新重载，保持向后兼容）。
- `listPartition(SparkPartition partition, ..., NameMapping mapping)` 新增 `int parallelism` 参数，透传给 `TableMigrationUtil.listPartition(..., parallelism)`。
- 在 `importSparkPartitions` 中将原局部变量 `parallelism`（用于 `sparkContext.parallelize(partitions, parallelism)` 控制分布式分区发现并行度）重命名为 `listingParallelism`，避免与新参数 `parallelism` 命名冲突——这处重命名是本提交易被忽视但关键的细节。
- FlatMap 闭包中调用 `listPartition(...)` 时新增 `parallelism` 参数，使每个 Spark 任务在读取文件元数据时使用指定线程数并发。

### `data/src/main/java/org/apache/iceberg/data/TableMigrationUtil.java`

**修改目的**：将 `migrationService` 的线程池规模参数语义对齐 `parallelism`。

**工作逻辑**：`migrationService(int concurrentDeletes)` 方法重命名为 `migrationService(int parallelism)`，参数名从 `concurrentDeletes` 改为 `parallelism`，但语义未变——仍是 `Executors.newFixedThreadPool(parallelism, ...)` 创建名为 `table-migration-%d` 的固定线程池。这是命名上的澄清：该线程池不仅服务于删除，更服务于文件读取并发。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAddFilesProcedure.java`

**修改目的**：新增测试验证 `parallelism` 参数功能。

**工作逻辑**：新增 `testAddFilesWithParallelism` 测试方法。创建非分区 Hive 表并写入数据；创建同名 Iceberg 表；调用 `CALL %s.system.add_files(table => '%s', source_table => '%s', parallelism => 2)`；断言过程输出为 `ImmutableList.of(row(2L, 1L))`（2 个文件、1 个快照），并断言导入后 Iceberg 表数据与源表数据一致（按 id 排序后比对）。

### `docs/spark-procedures.md`

**修改目的**：在 `add_files` 过程的参数表中补充 `parallelism` 参数说明。

**工作逻辑**：在参数表新增一行 `| parallelism | | int | number of threads to use for file reading (defaults to 1) |`，未标记必填（无 ✔️），类型 int，默认 1。

## 小结

本提交为 `add_files` 过程增加了用户可控的文件读取线程级并行能力，是数据迁移性能优化的实用增强。实现上从过程入口 `AddFilesProcedure` 到底层 `TableMigrationUtil` 全链路贯通 `parallelism` 参数，并通过将原 `parallelism` 局部变量重命名为 `listingParallelism` 清晰区分了"Spark 分布式分区发现并行度"与"节点内文件读取线程池并行度"两个不同维度。默认值 1 保持向后兼容，用户可按需调高以加速大批量文件导入。本次净增 130 行（156 增 / 26 删），属于功能增强，无破坏性改动，配套测试与文档齐全。
