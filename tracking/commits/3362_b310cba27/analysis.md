# 提交 3362：Flink: Add the possibility to use Coordinator Lock when using Flink SQL (#15459)

## 提交信息

- **序号**：3362 / 4088
- **哈希**：b310cba27cefdc9c3ac0bd620dc36d1d1879ecb9
- **短哈希**：b310cba27
- **日期**：2026-03-09
- **作者**：GuoYu
- **提交说明**：Flink: Add the possibility to use Coordinator Lock when using Flink SQL (#15459)
- **PR/Issue**：#15459

## 总体目的

该提交为 Flink Iceberg Sink 的自动维护（maintenance）功能增加了一种无需外部锁服务（如 JDBC、ZooKeeper）的"Coordinator Lock"（协调器锁）模式。在本次改动之前，Flink Iceberg Sink 在启用自动压缩（compaction）等维护操作时，必须配置外部 `TriggerLockFactory`（如 `JdbcLockFactory` 或 `ZkLockFactory`）来保证同一张表不会有多个维护作业并发执行。这一要求对于通过 Flink SQL 使用 Iceberg 的用户来说是一个显著的部署障碍——用户需要额外搭建并维护 JDBC 数据库或 ZooKeeper 集群仅用于锁管理。

本次改动引入了一种轻量级替代方案：当不配置外部锁类型（`flink-maintenance.lock.type` 为空或不设置）时，维护作业将使用 Flink 自身的 Operator Coordinator 机制在作业内部管理锁，无需任何外部依赖。其前提条件是同一张表没有并行的多个维护作业（即只有一个 Flink 作业负责该表的维护）。这大大降低了 Flink SQL 场景下启用 Iceberg 自动维护的门槛。

此外，提交还修复了 `LockRemoverOperator` 在处理 `MAX_WATERMARK` 时会向协调器发送多余锁释放事件的问题——在流作业结束时 Flink 会发送 `Watermark.MAX_WATERMARK`，此时发送锁释放事件没有意义且可能引发异常。

## 如何达成设计目的

整体设计思路分为三部分：

1. **Sink 端条件化构建锁工厂**：在 `IcebergSink` 中，根据 `LockConfig.lockType()` 是否非空来决定是否构建 `TriggerLockFactory`。若锁类型为空，则调用 `TableMaintenance.forChangeStream` 的无锁工厂重载版本，由 Flink 协调器内部管理锁。
2. **LockRemoverOperator 跳过 MAX_WATERMARK**：在 `processWatermark` 中增加判断，当 watermark 为 `MAX_WATERMARK` 时不发送 `LockReleaseEvent`，避免作业结束时的无效事件。
3. **测试与文档**：为新的无锁模式添加参数化测试覆盖，并更新 Flink 维护文档说明 Coordinator Lock 选项。

涉及 Flink v2.1 模块下的主代码 2 个文件、测试 3 个文件以及文档 1 个文件。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+11/-5 lines)

**修改目的**：根据是否配置锁类型，条件化地选择带外部锁工厂或不带锁工厂的维护流程。

**工作逻辑**：
- 新增 import `org.apache.commons.lang3.StringUtils`，移除 `TriggerLockFactory` import。
- 在构建 `TableMaintenance` 时，原代码无条件构建 `TriggerLockFactory`：
  ```java
  TriggerLockFactory triggerLockFactory = LockFactoryBuilder.build(lockConfig, table.name());
  TableMaintenance.forChangeStream(tableChangeStream, tableLoader, triggerLockFactory)
  ```
  改为条件分支：
  ```java
  StringUtils.isNotEmpty(lockConfig.lockType())
      ? TableMaintenance.forChangeStream(tableChangeStream, tableLoader,
              LockFactoryBuilder.build(lockConfig, table.name()))
          .uidSuffix(tableMaintenanceUid).add(rewriteBuilder)
      : TableMaintenance.forChangeStream(tableChangeStream, tableLoader)
          .uidSuffix(tableMaintenanceUid).add(rewriteBuilder);
  ```
  当 `lockType` 非空时仍走外部锁工厂路径；为空时走无锁工厂路径（由 `TableMaintenance.forChangeStream` 的无 `TriggerLockFactory` 参数重载处理，内部使用 Coordinator Lock）。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemoverOperator.java` (+5/-2 lines)

**修改目的**：避免在 `MAX_WATERMARK` 时向协调器发送无效的锁释放事件。

**工作逻辑**：
`processWatermark` 原来无条件发送事件：
```java
operatorEventGateway.sendEventToCoordinator(new LockReleaseEvent(tableName, mark.getTimestamp()));
super.processWatermark(mark);
```
改为：
```java
if (Watermark.MAX_WATERMARK.getTimestamp() != mark.getTimestamp()) {
  operatorEventGateway.sendEventToCoordinator(new LockReleaseEvent(tableName, mark.getTimestamp()));
}
super.processWatermark(mark);
```
当收到 `MAX_WATERMARK`（流结束标志）时跳过发送，因为此时协调器即将关闭，发送锁释放事件无意义。

### `docs/docs/flink-maintenance.md` (+9/-1 lines)

**修改目的**：文档新增 Flink-managed lock（Coordinator Lock）说明。

**工作逻辑**：
- 新增"Flink-maintained lock"小节，说明该模式在 Flink 内部维护锁，无需配置外部系统，前提是没有并行的表维护作业。
- 在 Quick Start 示例中增加 Option 1（外部锁工厂，计划 1.12 弃用）和 Option 2（Flink 管理锁，无需外部锁）两种用法。
- 在配置项表格中新增 `COORDINATOR LOCK` 分类，说明 `flink-maintenance.lock.type` 设置为空或不设置即启用 Coordinator Lock。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestLockRemoverOperation.java` (+18/-0 lines)

**修改目的**：测试 `LockRemoverOperator` 对 `MAX_WATERMARK` 的处理。

**工作逻辑**：
新增 `testProcessMaxWaterMark` 测试：先发送一个普通 `TaskResult`，验证网关无事件；再发送普通 watermark，验证发出 1 个事件；最后发送 `Watermark.MAX_WATERMARK`，验证事件数仍为 1（即 MAX_WATERMARK 未触发新事件）。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSinkCompaction.java` (+33/-10 lines)

**修改目的**：为 SQL 场景增加 Coordinator Lock 模式的参数化测试。

**工作逻辑**：
- 添加 `@ExtendWith(ParameterizedTestExtension.class)`。
- 新增参数 `lockType`（第 3 个参数），参数集扩展为 4 组：`userSqlHint=true/false` × `lockType=JDBC/""`。
- 新增 `TABLE_PROPERTIES_COORDINATOR` 常量（不含锁配置，仅含压缩相关属性）。
- 建表和 INSERT 语句根据 `lockType` 选择使用带 JDBC 锁配置还是 Coordinator 锁配置的属性。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSinkCompact.java` (+25/-11 lines)

**修改目的**：为 Sink API 场景增加 Coordinator Lock 模式的参数化测试。

**工作逻辑**：
- 将测试从 `@Test` 改为 `@ParameterizedTest` + `@FieldSource("LOCK_TYPES")`，`LOCK_TYPES` 包含 `JDBC` 和 `""` 两种。
- 将原本在 `before()` 中硬编码的 JDBC 锁配置抽取到 `setupLockConfig(String lockType)` 方法中，根据锁类型动态设置：JDBC 模式设置完整的 JDBC 锁参数，Coordinator 模式将 `LOCK_TYPE_OPTION` 设为空字符串。
- `testCompactFileE2e` 和 `testTableMaintenanceOperatorAdded` 两个测试方法均参数化，覆盖两种锁模式。

## 总结

本次提交为 Flink Iceberg Sink 的自动维护功能引入了 Coordinator Lock 模式，使用户在不配置外部锁服务（JDBC/ZooKeeper）的情况下也能通过 Flink SQL 启用表压缩等维护操作，显著降低了使用门槛。同时修复了 `LockRemoverOperator` 在 `MAX_WATERMARK` 时发送无效事件的问题，并配套更新了文档与参数化测试。
