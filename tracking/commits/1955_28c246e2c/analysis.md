# 提交 1955：Spark 3.4: Fix NotSerializableException when migrating Spark tables (#12705)

## 提交信息

- **序号**：1955 / 4088
- **哈希**：28c246e2c020a1f849effcfc7d5d1852f1f59b02
- **短哈希**：28c246e2c
- **日期**：2025-04-02 16:12:18 +0200
- **作者**：Manu Zhang
- **提交说明**：Spark 3.4: Fix NotSerializableException when migrating Spark tables (#12705)
  - backports #11157 to Spark 3.4
- **PR/Issue**：#12705（回填 #11157）

## 总体目的

在 Spark 3.4 中执行表迁移/快照过程（`migrate` / `snapshot` / `add_files`）时，如果指定 `parallelism > 1`，会使用一个线程池并行读取文件。原先 `MigrateTableProcedure` 和 `SnapshotTableProcedure` 通过 `BaseProcedure.executorService(parallelism, ...)` 创建一个真实的 `ExecutorService`（ThreadPoolExecutor），该对象不可序列化。由于 Spark 的过程（procedure）执行链路可能触发闭包序列化（把包含 executor 的对象序列化分发到任务），这会导致 `NotSerializableException`，使迁移失败。

本提交（回填自主分支 #11157）的目的是用一个新的可序列化的 `LazyExecutorService` 包装类替代直接创建的真实线程池：它在序列化时只保存 `parallelism` 这个 int，在被实际使用（调用任意 ExecutorService 方法）时才懒加载创建真实的线程池。这样既保留了并行能力，又避免了把不可序列化的线程池对象放进需要序列化的闭包中。

## 如何达成设计目的

设计思路是引入一个 `LazyExecutorService`，它同时实现 `ExecutorService` 和 `Serializable`：

1. **`SparkTableUtil.migrationService(int parallelism)`**：新的工厂方法。当 `parallelism == 1` 时返回 `null`（表示不需要并行，调用方按单线程处理）；否则返回一个 `LazyExecutorService` 实例。
2. **`LazyExecutorService`**：内部持有 `parallelism`（可序列化的 int）和 `volatile ExecutorService service`（transient 的真实线程池，不参与序列化）。所有 `ExecutorService` 接口方法都委托给 `getService()`，后者在首次访问时通过双重检查锁懒加载调用 `TableMigrationUtil.migrationService(parallelism)` 创建真实线程池。
3. **替换调用点**：`SparkTableUtil` 内部两处原本调用 `TableMigrationUtil.migrationService(parallelism)` 的地方改为调用新的 `migrationService(parallelism)`；`MigrateTableProcedure` 和 `SnapshotTableProcedure` 原本用 `executorService(parallelism, "...")` 创建线程池，改为用 `SparkTableUtil.migrationService(parallelism)`。

这样，当对象被序列化时，只有 `parallelism` 被序列化，真实线程池不会被序列化，从而避免 `NotSerializableException`；反序列化后首次使用时再重建线程池。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (修改, +117/-2 lines)

**修改目的**：引入可序列化的懒加载线程池工厂与实现。

**工作逻辑**：
- 新增静态工厂 `migrationService(int parallelism)`：`parallelism == 1` 返回 `null`，否则返回 `new LazyExecutorService(parallelism)`。
- 新增私有静态内部类 `LazyExecutorService implements ExecutorService, Serializable`：
  - 字段 `int parallelism`（可序列化）、`volatile ExecutorService service`（运行时懒加载）。
  - 所有 ExecutorService 方法（shutdown/shutdownNow/isShutdown/isTerminated/awaitTermination/submit/invokeAll/invokeAny/execute 等）都委托给 `getService()`。
  - `getService()` 使用双重检查锁（synchronized + volatile）在首次调用时通过 `TableMigrationUtil.migrationService(parallelism)` 创建真实线程池。
- 将类内两处原本 `TableMigrationUtil.migrationService(parallelism)` 调用改为 `migrationService(parallelism)`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/MigrateTableProcedure.java` (修改, +2/-1 lines)

**修改目的**：使用可序列化的迁移线程池。

**工作逻辑**：将 `migrateTableSparkAction.executeWith(executorService(parallelism, "table-migration"))` 改为 `migrateTableSparkAction.executeWith(SparkTableUtil.migrationService(parallelism))`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/SnapshotTableProcedure.java` (修改, +2/-1 lines)

**修改目的**：使用可序列化的快照线程池。

**工作逻辑**：将 `action.executeWith(executorService(parallelism, "table-snapshot"))` 改为 `action.executeWith(SparkTableUtil.migrationService(parallelism))`。

### 测试文件 (修改, +60/-0 lines)

**修改目的**：为迁移/快照/添加文件过程补充 `parallelism > 1` 的回归测试。

涉及文件：`spark/v3.4/spark/src/test/.../TestAddFilesProcedure.java`、`TestMigrateTableProcedure.java`、`TestSnapshotTableProcedure.java`，各新增 20 行测试，验证在指定 parallelism 大于 1 时过程能正常执行而不抛 NotSerializableException。

## 总结

本提交（回填 #11157 至 Spark 3.4）修复了表迁移/快照过程在 `parallelism > 1` 时因 `ExecutorService` 不可序列化导致的 `NotSerializableException`。核心是引入实现 `Serializable` 的 `LazyExecutorService`，序列化时只保存 `parallelism`，运行时懒加载创建真实线程池，并替换 `MigrateTableProcedure`/`SnapshotTableProcedure`/`SparkTableUtil` 中的线程池创建方式。
