# 提交 3367：Flink: Backport Add the possibility to use Coordinator Lock when using Flink SQL to 2.0 and 1.20 (#15562)

## 提交信息

- **序号**：3367 / 4088
- **哈希**：afb58aa341d055a6bef81a54d1e9e20d07b7e075
- **短哈希**：afb58aa34
- **日期**：2026-03-09
- **作者**：GuoYu
- **提交说明**：Flink: Backport Add the possibility to use Coordinator Lock when using Flink SQL to 2.0 and 1.20 (#15562)
- **PR/Issue**：#15562（回移自 #15459）

## 总体目的

这是一个回移（backport）提交，将 PR #15459 的功能回移到 Flink 1.20 和 2.0 两个版本分支。原 PR 的目标是让 Flink SQL Sink 在使用 Iceberg 表维护（maintenance）功能时，可以选择不使用外部锁（如 JDBC 锁），而改用 Flink 的 Coordinator Lock 机制来协调压缩任务。

在此之前，Iceberg Flink Sink 在启用压缩（compaction）时，必须配置一个 `TriggerLockFactory`（通常是 JDBC 锁），这要求用户额外搭建和维护一个 JDBC 数据库来作为锁服务。对于很多用户来说这是一个较重的依赖。本次改动使得当 `lockType` 为空（未配置锁类型）时，`IcebergSink` 不再构建 `TriggerLockFactory`，而是通过无锁的方式（依赖 Coordinator Lock）来运行表维护流程，从而简化部署。

此外，本次提交还修复了 `LockRemoverOperator` 在处理 `MAX_WATERMARK` 时的问题：之前无论收到什么 watermark 都会向 Coordinator 发送锁释放事件，但 `MAX_WATERMARK` 表示流已结束，此时不应再发送锁释放事件。

## 如何达成设计目的

改动覆盖 Flink 1.20 和 2.0 两个模块，每个模块都修改了 `LockRemoverOperator` 和 `IcebergSink` 两个核心文件，以及三个测试文件。核心设计思路：在 `IcebergSink` 中根据 `lockConfig.lockType()` 是否为空来决定构建 `TableMaintenance` 时是否传入 `TriggerLockFactory`；在 `LockRemoverOperator` 中增加对 `MAX_WATERMARK` 的判断，避免在流结束时发送多余的锁释放事件。测试侧将原有固定使用 JDBC 锁的测试改造为参数化测试，同时覆盖 JDBC 锁和无锁两种场景。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemoverOperator.java` (+7/-2 lines) 和 `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemoverOperator.java` (+7/-2 lines)

**修改目的**：避免在 `MAX_WATERMARK` 时向 Coordinator 发送锁释放事件。

**工作逻辑**：
`processWatermark` 方法原先直接调用 `operatorEventGateway.sendEventToCoordinator(new LockReleaseEvent(tableName, mark.getTimestamp()))`。修改后增加条件判断：`if (Watermark.MAX_WATERMARK.getTimestamp() != mark.getTimestamp())`，只有当 watermark 不是最大 watermark 时才发送锁释放事件。`MAX_WATERMARK` 通常在流结束时发送，此时再释放锁没有意义且可能导致异常。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+16/-5 lines) 和 `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+16/-5 lines)

**修改目的**：支持在未配置锁类型时不构建 `TriggerLockFactory`，改用 Coordinator Lock。

**工作逻辑**：
原先代码无条件构建 `TriggerLockFactory`：
```java
TriggerLockFactory triggerLockFactory = LockFactoryBuilder.build(lockConfig, table.name());
TableMaintenance.Builder builder =
    TableMaintenance.forChangeStream(tableChangeStream, tableLoader, triggerLockFactory)
        .uidSuffix(tableMaintenanceUid).add(rewriteBuilder);
```
修改后使用 `StringUtils.isNotEmpty(lockConfig.lockType())` 判断锁类型是否非空。若非空，则按原逻辑构建 `TriggerLockFactory` 并传入 `TableMaintenance.forChangeStream`；若为空，则调用 `TableMaintenance.forChangeStream` 的无锁重载版本（不传 `triggerLockFactory`）。同时移除了对 `TriggerLockFactory` 的 import，新增了 `org.apache.commons.lang3.StringUtils` 的 import。这使 `TableMaintenance` 提供了两个构造路径，分别对应有锁和无锁场景。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSinkCompaction.java` (+46/-8 lines) 和 `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSinkCompaction.java` (+46/-8 lines)

**修改目的**：将测试参数化以同时覆盖 JDBC 锁和 Coordinator Lock（无锁）两种场景。

**工作逻辑**：
类上新增 `@ExtendWith(ParameterizedTestExtension.class)` 注解。新增 `@Parameter(index = 3)` 的 `lockType` 字段，`parameters()` 方法从原来的 2 组参数（`userSqlHint` 为 true/false）扩展为 4 组：分别对 JDBC 锁和空锁各做 true/false 组合。新增 `TABLE_PROPERTIES_COORDINATOR` 常量（不含锁配置，仅含 maintenance 相关属性）。建表和 INSERT 时根据 `lockType` 选择使用 `TABLE_PROPERTIES`（JDBC）还是 `TABLE_PROPERTIES_COORDINATOR`（无锁）。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestLockRemoverOperation.java` (+21/-0 lines) 和 `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestLockRemoverOperation.java` (+21/-0 lines)

**修改目的**：验证 `LockRemoverOperator` 在 `MAX_WATERMARK` 时不发送锁释放事件。

**工作逻辑**：
新增 `testProcessMaxWaterMark` 测试：创建 `LockRemoverOperator`，先发送一条普通 `TaskResult`，断言无事件发出；再发送普通 watermark，断言发出 1 个事件；最后发送 `Watermark.MAX_WATERMARK`，断言事件数仍为 1（即未新增），验证了 `MAX_WATERMARK` 被正确忽略。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSinkCompact.java` (+36/-14 lines) 和 `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSinkCompact.java` (+36/-14 lines)

**修改目的**：将压缩测试参数化以覆盖 JDBC 锁和空锁两种锁类型。

**工作逻辑**：
新增 `LOCK_TYPES` 常量 `new String[] {LockConfig.JdbcLockConfig.JDBC, ""}`。`before()` 方法中移除了固定的 JDBC 锁配置，仅保留通用的压缩配置。原 `@Test` 方法改为 `@ParameterizedTest` + `@FieldSource("LOCK_TYPES")`，方法参数为 `String lockType`。新增 `setupLockConfig(String lockType)` 辅助方法：若 `lockType` 为 JDBC 则配置 JDBC 锁相关参数（URI、lockId、init table），否则将 `LOCK_TYPE_OPTION` 设为空字符串。两个测试方法 `testCompactFileE2e` 和 `testTableMaintenanceOperatorAdded` 都在开头调用 `setupLockConfig(lockType)`。

## 总结

本次回移提交使 Flink 1.20 和 2.0 的 Iceberg Sink 支持在不配置 JDBC 锁的情况下使用 Coordinator Lock 进行表维护，降低了部署复杂度。同时修复了 `LockRemoverOperator` 在 `MAX_WATERMARK` 时误发锁释放事件的问题。测试全面参数化以覆盖两种锁场景，保证了功能的正确性和向后兼容性。
