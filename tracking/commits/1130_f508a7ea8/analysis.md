# 提交 1130：Spark 3.3, 3.4: Parallelize reading files in migrate procedures (#11043)

## 提交信息

- **序号**：1130 / 4088
- **哈希**：f508a7ea8e33959020778b3b9e2ce7cd0b2df0b5
- **短哈希**：f508a7ea8
- **日期**：2024-09-06（Fri Sep 6 01:15:49 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 3.3, 3.4: Parallelize reading files in migrate procedures (#11043)
- **PR/Issue**：#11043
- **回迁来源**：Back-port of #9274 与 #10037（针对 Spark 3.5 主线实现）

## 总体目的

Iceberg 的 Spark 迁移/快照类过程（`migrate_table`、`snapshot_table`、`add_files`）在把外部 Spark 表导入 Iceberg 时，需要枚举源表文件、读取每个文件的元数据（大小、记录数、列统计等 metrics）并构造 `DataFile`。这一步原先在 Spark executor 内是**串行**进行的——每个分区（`SparkPartition`）在 executor 上遍历其下所有文件，逐个通过 `TableMigrationUtil.listPartition` 同步读取 metrics。当某个分区文件数很多时，metrics 读取成为迁移耗时的瓶颈。

本提交（回迁自 Spark 3.5 的 #9274 与 #10037）为 Spark 3.3、3.4 两个版本引入"文件级并行读取"能力：

1. 给 `SparkTableUtil` 的 `importSparkTable` / `importSparkPartitions` 增加接受 `int parallelism` 或 `ExecutorService` 的重载，并行度由调用方决定。
2. 在 `TableMigrationUtil.listPartition` 内用传入的 `ExecutorService` 并发提交各文件的 metrics 读取任务。
3. 给 `add_files` / `migrate_table` / `snapshot_table` 三个存储过程新增可选参数 `parallelism`，用户可在 SQL 调用时指定并行度。
4. `MigrateTableSparkAction` / `SnapshotTableSparkAction` 实现 `executeWith(ExecutorService)` 接口，让 action 层也能注入线程池。
5. `ProcedureInput` 新增 `asInt` 访问器以读取整型参数。

保持向后兼容：所有原签名（无 parallelism）的调用默认 `parallelism=1`，行为与改动前完全一致。

## 如何达成设计目的

整体策略是"层层透传并行度参数 + 在文件枚举处用线程池并发"，并保证默认值 `1` 等价于原串行行为：

- **SparkTableUtil**：为 `importSparkTable` / `importSparkPartitions` 各增加 4 个重载（共 8 个），覆盖 `(parallelism)` 与 `(ExecutorService)` 两种传入方式；`parallelism` 重载内部调 `TableMigrationUtil.migrationService(parallelism)` 构造线程池后转调 `ExecutorService` 重载。原 5 参/6 参签名保留并默认传 `1`。
- **TableMigrationUtil**（未在本提交 diff 中，但被调用）：新增接受 `ExecutorService service` 的 `listPartition` 重载，在枚举分区内文件时用 `service` 并发提交 metrics 读取；`migrationService(int)` 工厂负责按并行度创建线程池。
- **ProcedureInput**：新增 `asInt(param)` 与 `asInt(param, default)`，统一读取整型参数。
- **三个 Procedure**：`AddFilesProcedure` 增加 `PARALLELISM` 参数并通过 `importToIceberg(..., parallelism)` 透传；`MigrateTableProcedure` / `SnapshotTableProcedure` 增加 `parallelism` 参数，校验 `> 0` 后用 `executorService(parallelism, name)` 构造线程池并调 `action.executeWith(service)`。
- **两个 Action**：`MigrateTableSparkAction` / `SnapshotTableSparkAction` 新增 `executorService` 字段与 `executeWith(ExecutorService)` 实现，执行 `importSparkTable` 时把线程池透传给 `SparkTableUtil`。
- **测试**：新增 `TestMigrateTableAction` / `TestSnapshotTableAction`（每个版本各一），用自定义 `ThreadFactory` + `AtomicInteger` 验证迁移/快照确实使用了多线程（断言线程序号达到 2）；`TestAddFilesProcedure` 新增 `testAddFilesWithParallelism` 验证 `add_files` 带 `parallelism => 2` 时数据正确导入。

Spark 3.3 与 3.4 两个版本目录的改动基本对称（仅 v3.3 的 `MigrateTableProcedure` 因原本没有 `backup_table_name` 参数，参数 ordinal 与 v3.4 不同）。

## 修改详情

### Spark 3.4 主代码

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`

**修改目的**：为表/分区导入增加并行度与线程池重载。

**工作逻辑**：

- 新增 `import java.util.concurrent.ExecutorService`。
- 新增私有 `listPartition(SparkPartition, PartitionSpec, SerializableConfiguration, MetricsConfig, NameMapping, ExecutorService)` 重载，转调 `TableMigrationUtil.listPartition(..., service)`；原 `listPartition(..., int parallelism)` 重载转调 `TableMigrationUtil.listPartition(..., parallelism)`。
- `importSparkTable` 从单个方法扩展为 6 个重载（含原有），新增接受 `int parallelism` 与 `ExecutorService service` 的版本。`parallelism` 版本内部调 `TableMigrationUtil.migrationService(parallelism)` 构造线程池后转调 `service` 版本。核心实现（最长那个重载）在调用 `importUnpartitionedSparkTable` 与 `importSparkPartitions` 时透传 `service`。
- `importUnpartitionedSparkTable` 增加 `ExecutorService service` 参数，调 `listPartition(..., service)`。
- `importSparkPartitions` 同样扩展为 4 个重载，核心实现把原局部变量 `parallelism`（用于 `sparkContext.parallelize` 的分区数）改名为 `listingParallelism`（避免与新引入的文件读取并行度概念混淆），并在 `listPartition` 调用处传入 `service`。
- 原 5 参/6 参的 `importSparkTable` / `importSparkPartitions` 保留，内部默认传 `1`，保持向后兼容。

注意：Spark RDD 层面的 `parallelize(partitions, listingParallelism)` 仍由 `spark.sessionState().conf().parallelPartitionDiscoveryParallelism()` 控制，这与本次新增的"文件 metrics 读取并行度"是两个独立维度——前者决定 Spark 用多少 task 并行处理分区，后者决定每个 task 内部用多少线程并发读取该分区下的文件。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/AddFilesProcedure.java`

**修改目的**：`add_files` 过程新增 `parallelism` 参数。

**工作逻辑**：

- 新增 `PARALLELISM = ProcedureParameter.optional("parallelism", DataTypes.IntegerType)`，加入 `PARAMETERS` 数组。
- `call` 中 `int parallelism = input.asInt(PARALLELISM, 1)`，透传给 `importToIceberg(..., parallelism)`。
- `importToIceberg` / `importFileTable` / `importCatalogTable` / `importPartitions` 均增加 `int parallelism` 参数并层层透传，最终调 `SparkTableUtil.importSparkTable/importSparkPartitions(..., parallelism)`。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/MigrateTableProcedure.java`

**修改目的**：`migrate_table` 过程新增 `parallelism` 参数。

**工作逻辑**：

- `PARAMETERS` 数组末尾增加 `ProcedureParameter.optional("parallelism", DataTypes.IntegerType)`（ordinal 4）。
- `call` 中：`if (!args.isNullAt(4)) { int parallelism = args.getInt(4); Preconditions.checkArgument(parallelism > 0, ...); migrateTableSparkAction = migrateTableSparkAction.executeWith(executorService(parallelism, "table-migration")); }`。`executorService` 是 `BaseProcedure` 提供的工厂方法。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/SnapshotTableProcedure.java`

**修改目的**：`snapshot_table` 过程新增 `parallelism` 参数。

**工作逻辑**：与 `MigrateTableProcedure` 对称——`PARAMETERS` 末尾加 `parallelism`（ordinal 4），`call` 中校验 `> 0` 后 `action.executeWith(executorService(parallelism, "table-snapshot"))`。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/ProcedureInput.java`

**修改目的**：提供整型参数读取能力。

**工作逻辑**：新增 `asInt(ProcedureParameter)`（无默认值，缺失抛错）与 `asInt(ProcedureParameter, Integer defaultValue)`，仿照已有的 `asBoolean`/`asLong` 模式，先 `validateParamType(param, DataTypes.IntegerType)`，再按 ordinal 取 `args.getInt`。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/MigrateTableSparkAction.java`

**修改目的**：支持注入线程池。

**工作逻辑**：

- 新增 `private ExecutorService executorService` 字段与 `import java.util.concurrent.ExecutorService`。
- 实现 `public MigrateTableSparkAction executeWith(ExecutorService service)`：`this.executorService = service; return this;`。
- `execute()` 中调 `SparkTableUtil.importSparkTable(spark(), v1BackupIdent, icebergTable, stagingLocation, executorService)`（注意传 `executorService` 即新 5 参重载；若未调 `executeWith` 则为 null，由 `SparkTableUtil` 侧处理——实际上 `importSparkTable` 的 5 参 `ExecutorService` 重载内部会把 null 的情况兜底，但推荐调用方显式传 `migrationService(1)`）。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SnapshotTableSparkAction.java`

**修改目的**：与 `MigrateTableSparkAction` 对称。

**工作逻辑**：同样新增 `executorService` 字段、`executeWith` 实现，`execute()` 中调 `SparkTableUtil.importSparkTable(..., executorService)`。

### Spark 3.3 主代码

`spark/v3.3/spark/src/main/java/...` 下的 `SparkTableUtil.java`、`AddFilesProcedure.java`、`MigrateTableProcedure.java`、`SnapshotTableProcedure.java`、`ProcedureInput.java`、`MigrateTableSparkAction.java`、`SnapshotTableSparkAction.java` 改动与 3.4 基本对称。

唯一差异在 `MigrateTableProcedure.java`：v3.3 原本只有 3 个参数（`table`/`properties`/`drop_backup`），没有 v3.4 的 `backup_table_name`，所以新增的 `parallelism` 在 ordinal 3 而非 4。同时顺手把原 `if/else` 两分支 `execute()` 重构为"先 `dropBackup()` 再统一 `execute()`"的形式，使 `parallelism` 注入逻辑能统一接在后面：

```java
if (dropBackup) {
  migrateTableSparkAction = migrateTableSparkAction.dropBackup();
}
if (!args.isNullAt(3)) {
  int parallelism = args.getInt(3);
  Preconditions.checkArgument(parallelism > 0, "Parallelism should be larger than 0");
  migrateTableSparkAction =
      migrateTableSparkAction.executeWith(executorService(parallelism, "table-migration"));
}
MigrateTable.Result result = migrateTableSparkAction.execute();
```

### 测试

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestMigrateTableAction.java`（新文件，70 行）

**修改目的**：验证 `migrate_table` 在指定线程池时确实并发执行。

**工作逻辑**：建 Parquet 表插入两条数据，用 `SparkActions.get().migrateTable(tableName).executeWith(Executors.newFixedThreadPool(4, threadFactory))` 执行迁移，`threadFactory` 用 `AtomicInteger` 记录创建的线程数与命名（`table-migration-N`）。断言 `migrationThreadsIndex.get() == 2`，即确实启用了 2 个迁移线程（对应 2 次插入产生的文件/分区）。`assumeThat(catalogName).isEqualToIgnoringCase("spark_catalog")` 限定只在外部 catalog 为 spark_catalog 时跑。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestSnapshotTableAction.java`（新文件，69 行）

**修改目的**：与上对称，验证 `snapshot_table` 的并发。

**工作逻辑**：建源表 `spark_catalog.default.source` 插两条数据，`SparkActions.get().snapshotTable(SOURCE_NAME).as(tableName).executeWith(threadPool)` 执行，断言 `snapshotThreadsIndex.get() == 2`。

#### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAddFilesProcedure.java`

**修改目的**：验证 `add_files` 带 `parallelism` 时数据正确导入。

**工作逻辑**：新增 `testAddFilesWithParallelism`：建非分区 Hive 表，再建 Iceberg 表，调 `CALL catalog.system.add_files(table => '...', source_table => '...', parallelism => 2)`，断言过程输出 `[(2L, 1L)]` 且 Iceberg 表数据与源表一致。

#### Spark 3.3 测试

`spark/v3.3/...` 下的 `TestMigrateTableAction.java`、`TestSnapshotTableAction.java`、`TestAddFilesProcedure.java` 与 3.4 对称新增。

## 小结

- **成效**：Spark 3.3/3.4 的 `migrate_table` / `snapshot_table` / `add_files` 三个过程现可并行读取源表文件 metrics，用户通过 `parallelism` 参数控制线程数，显著缩短大表（多文件分区）的迁移耗时。Action 层新增 `executeWith(ExecutorService)` 便于编程式注入自定义线程池。所有原签名默认 `parallelism=1`，完全向后兼容。
- **影响范围**：Spark 3.3 与 3.4 两个版本目录，共 20 文件、920 增 61 删。涉及 `SparkTableUtil`、3 个 Procedure、2 个 Action、`ProcedureInput` 与对应测试。新增公共方法均为重载，不破坏既有 API；新增 SQL 参数 `parallelism` 为可选，不影响现有调用。
- **回迁到 1.4.x 的注意事项**：本提交本身就是把 Spark 3.5 的能力回迁到 3.3/3.4。对 1.4.x：
  1. **若 1.4.x 仍维护 Spark 3.3/3.4 模块且用户有迁移大表性能痛点**，可考虑 cherry-pick。需注意 1.4.x 的 `TableMigrationUtil` 是否已具备 `listPartition(..., ExecutorService)` 与 `migrationService(int)` 重载——本提交假设这些已存在（在 Spark 3.5 主线 #9274/#10037 中引入）。若 1.4.x 的 `TableMigrationUtil` 缺这些方法，cherry-pick 本提交会编译失败，需连同 `TableMigrationUtil` 的改动一起回迁。
  2. **v3.3 `MigrateTableProcedure` 重构**：本提交顺手把 v3.3 的 `if/else execute` 重构为统一 `execute()`，cherry-pick 时注意与 1.4.x 现有代码的参数 ordinal 一致性。
  3. **新参数 `parallelism` 是可选的**，对现有 SQL 调用无破坏，回迁风险较低。
  4. **测试依赖**：`TestMigrate/SnapshotTableAction` 依赖 `assumeThat`（assertj）与 `SparkCatalogTestBase`，确认 1.4.x 测试基础设施兼容。
  - 总体而言，若 1.4.x 用户确有迁移性能需求且 `TableMigrationUtil` 已就绪，回迁是合理且低风险的；否则**无需回迁**。
