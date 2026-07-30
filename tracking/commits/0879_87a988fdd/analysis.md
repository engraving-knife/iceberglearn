# 提交 0879：Spark 3.5: Parallelize reading files in snapshot and migrate procedures (#10037)

## 提交信息

- **序号**：0879 / 4088
- **哈希**：87a988fdda5fc68fd3b04947e000ef190fa54f61
- **短哈希**：87a988fdd
- **日期**：2024-06-26 18:28:00 +0200（Thu Jun 27 00:28:00 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 3.5: Parallelize reading files in snapshot and migrate procedures (#10037)
- **PR/Issue**：#10037

## 总体目的

Iceberg 的 Spark 3.5 集成提供了两个常用的存储过程（procedure）：

- `system.snapshot`：把一个外部 Spark 表（如 Parquet/ORC 表）"快照"为一个 Iceberg 表，原表保留。底层由 `SnapshotTableSparkAction` 实现。
- `system.migrate`：把一个外部 Spark 表"迁移"为一个 Iceberg 表，原表会被替换（可备份）。底层由 `MigrateTableSparkAction` 实现。

这两个 action 都会调用 `SparkTableUtil.importSparkTable(...)` 来扫描源表的分区、列出每个分区下的所有数据文件，并为每个文件读取 footer 中的 metrics（行数、列统计等），构造 Iceberg `DataFile` 对象，最终写入 manifest 文件提交到目标 Iceberg 表。文件读取（特别是 Parquet/ORC footer 中的 metrics 解析）是 CPU 密集型操作，当源表文件数量很大时会很慢。

此前 `snapshot` 和 `migrate` 这两个存储过程都不接受任何"并行度"参数，文件读取只能串行进行——每次只能用一个线程读取一个文件。而底层 `SparkTableUtil.importSparkTable(...)` 和 `TableMigrationUtil.listPartition(...)` 本身已经支持 `parallelism` 参数（如 `add_files` 存储过程就用了），但 snapshot/migrate 没有把这一参数暴露给用户。

本提交的目的就是为 `snapshot` 和 `migrate` 这两个存储过程新增 `parallelism` 参数，让用户可以指定用多少线程并行读取源表文件，从而加速大表的快照/迁移过程。同时在 API 层（`SnapshotTable`、`MigrateTable` 接口）增加 `executeWith(ExecutorService)` 方法，让 Java API 调用方也能传入自定义的 executor service。

## 如何达成设计目的

整体设计分四层，逐层向上暴露并行能力：

1. **底层 `TableMigrationUtil.listPartition` 重构**：原来只有一个签名 `listPartition(..., int parallelism)`，内部根据 `parallelism > 1` 决定是否创建 executor service。重构后拆成两个签名：
   - `listPartition(..., int parallelism)`：保持向后兼容，内部调用 `migrationService(parallelism)` 转换为 executor service 后委托给新方法。
   - `listPartition(..., ExecutorService service)`：新方法，直接接收外部传入的 executor service；内部改为 `if (service != null) task.executeWith(service)`。
   同时把 `migrationService(int parallelism)` 从 `private` 改为 `public`，并把实现从原来直接 `Executors.newFixedThreadPool + MoreExecutors.getExitingExecutorService` 改为调用 `ThreadPools.newWorkerPool("table-migration", parallelism)`，并在 `parallelism == 1` 时返回 `null`（避免无谓的线程池创建）。

2. **`SparkTableUtil` 新增接收 `ExecutorService` 的重载方法**：为 `importSparkTable` 和 `importSparkPartitions` 各增加一个接收 `ExecutorService service` 的重载，并把内部对 `listPartition`、`importUnpartitionedSparkTable` 的调用改为传递 `service` 而非 `parallelism`。原有的接收 `int parallelism` 的重载保留，内部委托给新方法（先 `TableMigrationUtil.migrationService(parallelism)` 转换）。

3. **API 层 `actions/MigrateTable.java`、`actions/SnapshotTable.java`**：新增 `default MigrateTable executeWith(ExecutorService service)` / `default SnapshotTable executeWith(ExecutorService service)` 方法，默认抛 `UnsupportedOperationException`，供实现类覆盖。

4. **Spark 实现 `MigrateTableSparkAction`、`SnapshotTableSparkAction`**：新增 `executorService` 字段、覆盖 `executeWith(...)` 把 service 保存到字段，并在 `execute()` 中调用 `SparkTableUtil.importSparkTable(..., executorService)` 透传 service。

5. **存储过程 `MigrateTableProcedure`、`SnapshotTableProcedure`**：在 `ProcedureParameter` 列表中新增 `optional("parallelism", DataTypes.IntegerType)`；在调用 action 时如果 `parallelism` 非空，先 `Preconditions.checkArgument(parallelism > 0, "Parallelism should be larger than 0")` 校验，然后调用 `executeWith(executorService(parallelism, "table-migration"/"table-snapshot"))`（`executorService` 是 `BaseProcedure` 提供的辅助方法，会创建并保存线程池，action 执行完毕后 `closeService` 会关闭）。

6. **文档与测试**：在 `docs/docs/spark-procedures.md` 中为 `snapshot` 和 `migrate` 的参数表新增 `parallelism` 行；顺手把 `add_files` 那一行 `parallelism` 的描述从 "number of threads..." 改为 "Number of threads..."（首字母大写统一）。新增 4 个测试：
   - `TestMigrateTableProcedure.testMigrateWithParallelism` / `testMigrateWithInvalidParallelism`（校验正常并行 + 校验 `parallelism = -1` 抛错）。
   - `TestSnapshotTableProcedure.testSnapshotWithParallelism` / `testSnapshotWithInvalidParallelism`。
   - `TestMigrateTableAction.testMigrateWithParallelTasks` / `TestSnapshotTableAction.testSnapshotWithParallelTasks`（在 Java API 层验证 `executeWith` 真的把任务分发到了多线程，通过 `AtomicInteger` 计数线程数断言 `migrationThreadsIndex.get() == 2`）。

整体设计在保持向后兼容的前提下，把已有的并行能力从底层逐步暴露到 procedure 调用面，并提供了 Java API 直接传入 executor service 的能力。

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/MigrateTable.java`

**修改目的**：在 `MigrateTable` 接口上声明 `executeWith(ExecutorService)` 默认方法。

**工作逻辑**：
- 新增 import `java.util.concurrent.ExecutorService`。
- 在接口中新增 default 方法：
  ```java
  default MigrateTable executeWith(ExecutorService service) {
    throw new UnsupportedOperationException("Setting executor service is not supported");
  }
  ```
  默认抛 `UnsupportedOperationException`，让旧实现（如果存在）保持二进制兼容；具体实现类可以覆盖。

### `api/src/main/java/org/apache/iceberg/actions/SnapshotTable.java`

**修改目的**：与 `MigrateTable` 对称，在 `SnapshotTable` 接口上声明 `executeWith(ExecutorService)` 默认方法。

**工作逻辑**：与 `MigrateTable` 完全一致的 import 与 default 方法。

### `data/src/main/java/org/apache/iceberg/data/TableMigrationUtil.java`

**修改目的**：把 `listPartition` 重构为接收 `ExecutorService` 的新签名，并把 `migrationService` 暴露为 `public`，统一底层线程池创建逻辑。

**工作逻辑**：
- 移除 import `java.util.concurrent.Executors`、`java.util.concurrent.ThreadPoolExecutor`、`MoreExecutors`、`ThreadFactoryBuilder`；新增 import `org.apache.iceberg.util.ThreadPools`。
- 原 `listPartition(..., int parallelism)` 改为委托给新方法：
  ```java
  return listPartition(..., migrationService(parallelism));
  ```
- 新增 `listPartition(..., ExecutorService service)` 方法，原方法体中的
  ```java
  if (parallelism > 1) { service = migrationService(parallelism); task.executeWith(service); }
  ```
  改为
  ```java
  if (service != null) { task.executeWith(service); }
  ```
  其余文件枚举、metrics 读取逻辑不变。
- `migrationService(int parallelism)` 从 `private` 改为 `public`，实现改为：
  ```java
  return parallelism == 1 ? null : ThreadPools.newWorkerPool("table-migration", parallelism);
  ```
  当 parallelism 为 1 时返回 null，避免创建无谓线程池。

### `docs/docs/spark-procedures.md`

**修改目的**：为 `snapshot` 和 `migrate` 存储过程的参数表新增 `parallelism` 参数说明，并统一 `add_files` 中 `parallelism` 描述的首字母大小写。

**工作逻辑**：
- 在 `snapshot` 参数表新增一行：`| parallelism | | int | Number of threads to use for file reading (defaults to 1) |`
- 在 `migrate` 参数表新增同样一行。
- 把 `add_files` 中 `parallelism` 描述的 "number of threads..." 改为 "Number of threads..."（统一首字母大写）。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMigrateTableProcedure.java`

**修改目的**：覆盖 `migrate` 存储过程的 `parallelism` 参数（正常 + 非法值）。

**工作逻辑**：
- 新增 import `java.util.List`。
- `testMigrateWithParallelism`：建 Parquet 表，插两行，调用 `CALL ...system.migrate(table => ..., parallelism => 2)`，断言返回 `(2L)`（迁移了 2 个文件），并断言 SELECT 结果正确。
- `testMigrateWithInvalidParallelism`：建表插数据后调用 `migrate(..., parallelism => -1)`，断言抛 `IllegalArgumentException("Parallelism should be larger than 0")`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSnapshotTableProcedure.java`

**修改目的**：覆盖 `snapshot` 存储过程的 `parallelism` 参数（正常 + 非法值）。

**工作逻辑**：与 `TestMigrateTableProcedure` 对称：
- `testSnapshotWithParallelism`：建 Parquet source 表，插两行，调用 `CALL ...system.snapshot(source_table => ..., table => ..., parallelism => 2)`，断言返回 `(2L)`，SELECT 目标表数据正确。
- `testSnapshotWithInvalidParallelism`：调用 `snapshot(..., parallelism => -1)`，断言抛 `IllegalArgumentException("Parallelism should be larger than 0")`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`

**修改目的**：为 `importSparkTable` 和 `importSparkPartitions` 新增接收 `ExecutorService` 的重载，并在内部把 `parallelism` 改为传递 `service`。

**工作逻辑**：
- 新增 import `java.util.concurrent.ExecutorService`。
- 新增私有重载 `listPartition(SparkPartition, PartitionSpec, SerializableConfiguration, MetricsConfig, NameMapping, ExecutorService service)`，直接调用 `TableMigrationUtil.listPartition(..., service)`，避免在 `SparkTableUtil` 内重复实现文件枚举逻辑。
- `importSparkTable(...)` 新增两个重载：
  - `importSparkTable(..., int parallelism)`：委托给 `importSparkTable(..., TableMigrationUtil.migrationService(parallelism))`，保持向后兼容。
  - `importSparkTable(..., ExecutorService service)`：委托给完整的 `importSparkTable(..., Collections.emptyMap(), false, service)`。
  - 原完整的 `importSparkTable(..., Map<String,String> partitionFilter, boolean checkDuplicateFiles, int parallelism)` 末尾委托给新方法 `importSparkTable(..., ExecutorService service)`，把 `parallelism` 转换为 `TableMigrationUtil.migrationService(parallelism)`。
  - 内部对 `importUnpartitionedSparkTable` 和 `importSparkPartitions` 的调用都从传 `parallelism` 改为传 `service`。
- `importUnpartitionedSparkTable` 的参数从 `int parallelism` 改为 `ExecutorService service`，内部对 `TableMigrationUtil.listPartition` 的调用从传 `parallelism` 改为传 `service`。
- `importSparkPartitions` 新增 `importSparkPartitions(..., ExecutorService service)` 重载，原 `int parallelism` 版本委托给它。内部 list partition 时传 `service` 替代 `parallelism`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/MigrateTableSparkAction.java`

**修改目的**：实现 `MigrateTable.executeWith`，并把 executor service 透传给 `SparkTableUtil.importSparkTable`。

**工作逻辑**：
- 新增 import `java.util.concurrent.ExecutorService`。
- 新增字段 `private ExecutorService executorService;`。
- 覆盖 `executeWith(ExecutorService service)`：保存到字段，返回 `this`。
- 在 `execute()` 中把
  ```java
  SparkTableUtil.importSparkTable(spark(), v1BackupIdent, icebergTable, stagingLocation);
  ```
  改为
  ```java
  SparkTableUtil.importSparkTable(
      spark(), v1BackupIdent, icebergTable, stagingLocation, executorService);
  ```
  （`importSparkTable` 已有接收 `ExecutorService` 的重载）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/SnapshotTableSparkAction.java`

**修改目的**：与 `MigrateTableSparkAction` 对称，实现 `SnapshotTable.executeWith` 并透传 executor service。

**工作逻辑**：完全对称地新增 import、字段、`executeWith` 覆盖，并把 `execute()` 中的 `importSparkTable` 调用改为传 `executorService`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/MigrateTableProcedure.java`

**修改目的**：在 `migrate` 存储过程中新增 `parallelism` 参数并校验、调用 `executeWith`。

**工作逻辑**：
- `ProcedureParameter` 列表末尾新增 `ProcedureParameter.optional("parallelism", DataTypes.IntegerType)`（位于 `backup_table_name` 之后，索引 4）。
- 在构建 `migrateTableSparkAction` 后，新增处理：
  ```java
  if (!args.isNullAt(4)) {
    int parallelism = args.getInt(4);
    Preconditions.checkArgument(parallelism > 0, "Parallelism should be larger than 0");
    migrateTableSparkAction =
        migrateTableSparkAction.executeWith(executorService(parallelism, "table-migration"));
  }
  ```
  其中 `executorService(int threadPoolSize, String nameFormat)` 是 `BaseProcedure` 提供的辅助方法，会创建并保存线程池（同一次 procedure 调用内复用），并在 procedure 执行完毕后由 `closeService()` 关闭。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/SnapshotTableProcedure.java`

**修改目的**：在 `snapshot` 存储过程中新增 `parallelism` 参数并校验、调用 `executeWith`。

**工作逻辑**：
- `ProcedureParameter` 列表末尾新增 `ProcedureParameter.optional("parallelism", DataTypes.IntegerType)`（位于 `properties` 之后，索引 4）。
- 在设置完 `action.tableLocation(...)` 后新增：
  ```java
  if (!args.isNullAt(4)) {
    int parallelism = args.getInt(4);
    Preconditions.checkArgument(parallelism > 0, "Parallelism should be larger than 0");
    action = action.executeWith(executorService(parallelism, "table-snapshot"));
  }
  ```
  线程池名称用 `"table-snapshot"` 以区别于 `migrate`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestMigrateTableAction.java`（新增）

**修改目的**：在 Java API 层验证 `MigrateTableSparkAction.executeWith` 确实把任务分发到多线程。

**工作逻辑**：
- 68 行测试，继承 `CatalogTestBase`。
- `testMigrateWithParallelTasks`：建 Parquet 表插两行数据。构造一个 4 线程的 `Executors.newFixedThreadPool`，但通过自定义 `ThreadFactory` 让每个线程都从 `AtomicInteger migrationThreadsIndex` 取号并命名（`table-migration-N`）。调用 `SparkActions.get().migrateTable(tableName).executeWith(...).execute()`。断言 `migrationThreadsIndex.get() == 2`——即实际真正并发执行文件读取的线程数为 2（与文件数 2 相符），证明 executor service 被正确传递并使用。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestSnapshotTableAction.java`（新增）

**修改目的**：与 `TestMigrateTableAction` 对称，验证 `SnapshotTableSparkAction.executeWith` 真的并行执行。

**工作逻辑**：68 行测试，结构与 `TestMigrateTableAction` 完全一致，仅将 source 名改为 `spark_catalog.default.source`、线程名前缀改为 `table-snapshot-`，同样断言 `snapshotThreadsIndex.get() == 2`。

## 小结

- **成效**：为 Spark 3.5 的 `system.snapshot` 和 `system.migrate` 存储过程新增 `parallelism` 参数，用户可通过 `CALL ...system.snapshot(source_table => ..., table => ..., parallelism => N)` 指定并行度，加速大表快照/迁移过程中的文件读取与 metrics 解析。同时在 `MigrateTable`/`SnapshotTable` API 上新增 `executeWith(ExecutorService)` 方法，供 Java API 调用方传入自定义线程池。底层重构把 `TableMigrationUtil.listPartition` 改为接收 `ExecutorService`，并把 `migrationService` 暴露为 `public`、统一通过 `ThreadPools.newWorkerPool` 创建线程池（`parallelism == 1` 时返回 null）。
- **影响范围**：仅 Spark 3.5 模块 + `api` + `data` 模块，共 13 个文件、459 行新增、23 行删除。涉及 API 接口（`actions/MigrateTable`、`actions/SnapshotTable`）、共享数据模块（`TableMigrationUtil`）、Spark 主代码（`SparkTableUtil`、两个 action、两个 procedure）、文档（`spark-procedures.md`）和测试（4 个测试类、2 个新增 2 个扩展）。不波及 Spark 3.3/3.4、Flink、Core 等其它模块。
- **回迁到 1.4.x 的注意事项**：这是一个功能增强（为已有 procedure 暴露并行能力），**对 1.4.x 回迁需要谨慎评估**。注意点：
  1. 需要确认 1.4.x 的 Spark 3.5 模块是否已经具备同样的能力。如果 1.4.x 中 `migrate` / `snapshot` 存储过程尚无 `parallelism` 参数，则回迁本提交能直接给用户带来大表加速收益。
  2. 回迁必须同时带上：API 接口的 `executeWith` 默认方法（`MigrateTable`、`SnapshotTable`）、`TableMigrationUtil` 的重构（包括 `migrationService` 改 public 与 `ThreadPools.newWorkerPool` 调用）、`SparkTableUtil` 的重载方法、两个 action 的实现、两个 procedure 的参数与校验、文档与测试。任何一个缺失会导致编译错误或功能不完整。
  3. `TableMigrationUtil` 位于 `data` 模块，是 Spark/Flink 等多模块共享的代码。回迁时需要检查 1.4.x 的 `data` 模块是否已有 `ThreadPools.newWorkerPool` 工具方法；如果没有，需要一并回迁该工具方法（或使用替代实现），否则 `migrationService` 会编译失败。
  4. `BaseProcedure.executorService(int, String)` 与 `closeService()` 是 Spark 3.5 procedure 基类已有的能力（本提交未改动），所以 procedure 侧不需要额外回迁基类。但要确认 1.4.x 的 `BaseProcedure` 已经提供这两个方法。
  5. 本提交只覆盖 Spark 3.5；如果 1.4.x 还维护 Spark 3.3 / 3.4 分支并希望它们也获得同样能力，需要单独再回迁一次（本提交不包含）。
  6. `parallelism` 参数的索引位置（在 procedure 的 args 中为索引 4）必须严格匹配，否则会读到错误的参数值。
