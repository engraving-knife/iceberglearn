# 提交 1404：Spark 3.5: Fix NotSerializableException when migrating Spark tables (#11157)

## 提交信息

- **序号**：1404 / 4088
- **哈希**：799925a4ef41e7b4231930377b83bd686001c2c0
- **短哈希**：799925a4e
- **日期**：2024-11-21（Thu Nov 21 00:39:41 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 3.5: Fix NotSerializableException when migrating Spark tables (#11157)
- **PR/Issue**：#11157

## 总体目的

在 Spark 3.5 模块中，调用 `system.migrate` / `system.snapshot` / `system.add_files` 存储过程并指定 `parallelism > 1` 时，存储过程会通过 `BaseProcedure#executorService(int, String)` 创建一个真实的 `ThreadPoolExecutor`（经 `MoreExecutors.getExitingExecutorService` 包装），并通过 `executeWith(...)` 传给 `MigrateTableSparkAction` / `SnapshotTableSparkAction`。

由于 Spark action 在执行时会跨 JVM 序列化闭包与任务到 executor 端，而这个真实的 `ThreadPoolExecutor` 不可序列化，导致抛出 `NotSerializableException`，使得带 `parallelism` 参数的迁移/快照调用直接失败。

本提交的目的是：用一个可序列化的「延迟初始化」`ExecutorService` 包装替代直接的线程池实例，使其在 driver 端只携带可序列化的元信息（`parallelism`），到 executor 端真正要使用线程池时再懒构造真实实例，从而规避序列化失败。

## 如何达成设计目的

在 `SparkTableUtil` 中新增静态方法 `migrationService(int parallelism)`：

- 当 `parallelism == 1` 时返回 `null`（与 `TableMigrationUtil.migrationService` 的语义一致：单线程无需线程池，直接在调用线程执行）；
- 否则返回一个新的 `LazyExecutorService(parallelism)`。

`LazyExecutorService` 同时实现 `ExecutorService` 与 `Serializable`：

- 字段只有 `int parallelism` 与 `volatile ExecutorService service`（懒构造产物，使用 `transient`-like 的运行期约定，序列列化时实际只把 `parallelism` 带过去，`service` 字段由于是 `volatile` 引用，序列化时为 null，到目标端再懒构造）；
- 所有 `ExecutorService` 接口方法都委托给 `getService()`，该方法用 double-checked locking 在首次访问时调用 `TableMigrationUtil.migrationService(parallelism)` 创建真实线程池；
- 同时使用 `@NotNull` / `@Nullable`（jetbrains annotations）标注方法签名，便于静态检查。

把 `MigrateTableProcedure` / `SnapshotTableProcedure` 中原本调用 `BaseProcedure#executorService(...)` 的代码改为调用 `SparkTableUtil.migrationService(parallelism)`。这样：

1. Driver 端持有的是可序列化的 `LazyExecutorService`（或 null）；
2. 闭包被 Spark 序列化到 executor 端时不再触发 `NotSerializableException`；
3. 在 executor 端首次调用 `submit`/`execute` 等方法时，才真正构造本地线程池执行任务。

同时把 `SparkTableUtil` 内部原本直接调用 `TableMigrationUtil.migrationService(parallelism)` 的两处（`importSparkTable` / `importSparkPartitions` 系列）也改为调用新的 `SparkTableUtil.migrationService(parallelism)`，让 Spark 3.5 模块内的入口统一走可序列化包装。注意：内部包装与原 `TableMigrationUtil.migrationService` 行为一致（`parallelism == 1` 返回 `null`，否则建线程池），只是包装了一层懒加载。此外 `TableMigrationUtil.migrationService` 增加了 `@Nullable` 注解以反映其可空返回值。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`

**修改目的**：提供可序列化的 `ExecutorService` 工厂方法，替代直接的 `TableMigrationUtil.migrationService`。

**工作逻辑**：

1. 新增 import：`Collection`、`Callable`、`ExecutionException`、`Future`、`TimeUnit`、`TimeoutException`，以及 jetbrains 的 `@NotNull` / `@Nullable`。
2. 在两个原有调用点把 `TableMigrationUtil.migrationService(parallelism)` 改为 `migrationService(parallelism)`（即调用本类新增的静态方法）：
   - `importSparkPartitions(...)` 流程；
   - `importSparkTable(...)` 流程。
3. 新增静态方法：

```java
@Nullable
public static ExecutorService migrationService(int parallelism) {
  return parallelism == 1 ? null : new LazyExecutorService(parallelism);
}
```

4. 新增私有静态内部类 `LazyExecutorService implements ExecutorService, Serializable`：
   - 字段：`int parallelism`、`volatile ExecutorService service`；
   - 所有 `ExecutorService` 方法（`shutdown` / `shutdownNow` / `isShutdown` / `isTerminated` / `awaitTermination` / `submit` × 3 / `invokeAll` × 2 / `invokeAny` × 2 / `execute`）一律委托 `getService()`；
   - `getService()` 使用 double-checked locking 在 `service == null` 时调用 `TableMigrationUtil.migrationService(parallelism)` 构造真实线程池。

由于 `LazyExecutorService` 实现了 `Serializable` 且仅有的非 `volatile` 字段是 `int parallelism`（基本类型可序列化），实际 Spark 序列化闭包时把 `service` 字段（运行期为 null）一并写入；目标端第一次使用时再懒构造，从而绕开真实 `ThreadPoolExecutor` 不可序列化的问题。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/MigrateTableProcedure.java`

**修改目的**：让 `migrate` 存储过程使用可序列化的 executor service。

**工作逻辑**：增加 `import org.apache.iceberg.spark.SparkTableUtil;`，并把

```java
migrateTableSparkAction.executeWith(executorService(parallelism, "table-migration"));
```

改为

```java
migrateTableSparkAction.executeWith(SparkTableUtil.migrationService(parallelism));
```

不再走 `BaseProcedure#executorService` 这条会创建真实线程池的路径。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/SnapshotTableProcedure.java`

**修改目的**：让 `snapshot` 存储过程使用可序列化的 executor service。

**工作逻辑**：同 `MigrateTableProcedure`，引入 `SparkTableUtil`，把

```java
action = action.executeWith(executorService(parallelism, "table-snapshot"));
```

改为

```java
action = action.executeWith(SparkTableUtil.migrationService(parallelism));
```

### `data/src/main/java/org/apache/iceberg/data/TableMigrationUtil.java`

**修改目的**：明确 `migrationService` 返回值可空。

**工作逻辑**：在 `migrationService(int parallelism)` 上增加 `@Nullable` 注解（`javax.annotation.Nullable`），引入相应 import。该方法在 `parallelism == 1` 时返回 `null`，注解把这一约定显式化，便于 IDE/静态分析。

### 测试：`TestAddFilesProcedure.java` / `TestMigrateTableProcedure.java` / `TestSnapshotTableProcedure.java`

**修改目的**：覆盖 `parallelism > 1` 场景下三个存储过程的可用性，回归保护序列化修复。

**工作逻辑**：

- `TestAddFilesProcedure#testAddFilesPartitionedWithParallelism`：创建分区 Hive 表，调用 `system.add_files(..., parallelism => 2)`，断言写入 8 个 data files / 4 个 manifest，并比对源表与 Iceberg 表数据一致。
- `TestMigrateTableProcedure#testMigratePartitionedWithParallelism`：仅 spark_catalog 上运行，建 parquet 分区表插入两行，调用 `system.migrate(..., parallelism => 2)`，断言返回 `[2]` 并校验数据。
- `TestSnapshotTableProcedure#testSnapshotPartitionedWithParallelism`：类似 migrate，但调用 `system.snapshot(..., parallelism => 2)`，校验返回行数与数据。

这三个用例都在 `parallelism => 2` 下触发原先会抛 `NotSerializableException` 的代码路径，从而保证修复后能够正常完成迁移/快照/AddFiles。

## 小结

- **成效**：Spark 3.5 的 `migrate` / `snapshot` / `add_files` 在指定 `parallelism > 1` 时不再抛 `NotSerializableException`，因为传入 action 的 `ExecutorService` 现在是可序列化的 `LazyExecutorService`，真实线程池在 executor 端懒构造。
- **影响范围**：Spark 3.5 模块下 `SparkTableUtil`（新增静态方法与内部类）、`MigrateTableProcedure` / `SnapshotTableProcedure` 各一处调用切换、`TableMigrationUtil` 仅加注解；data 模块仅一行注解。三个测试新增覆盖用例。
- **回迁到 1.4.x 的注意事项**：1.4.x（若维护 Spark 3.5 模块）若同样以 `parallelism > 1` 调用上述存储过程会撞上同样的序列化失败，建议回迁。回迁时注意：
  1. jetbrains annotations（`@NotNull` / `@Nullable`）需作为依赖可用；如不可用可省略注解，仅保留逻辑；
  2. `TableMigrationUtil.migrationService` 的 `@Nullable` 改用 1.4.x 已有的可空注解（如 `javax.annotation.Nullable`）；
  3. `BaseProcedure#executorService` 仍保留供其它 procedure 使用，不要误删；
  4. 验证 `MigrateTableSparkAction` / `SnapshotTableSparkAction` 在 1.4.x 上的 `executeWith` 是否接受 `null`（与 main 行为一致），以及懒加载包装的线程池在 action 结束后是否会被关闭（`MigrateTableSparkAction` 等会调用 `shutdown`，懒包装的 `shutdown` 会委托到真实池，行为正确）。
