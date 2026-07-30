# 提交 2004：Spark 3.4: Migrate ExtensionsTestBase-related tests for Snapshot manipulation, ChangeLogView and Distribution/Ordering

## 提交信息

- **序号**：2004 / 4088
- **哈希**：68d833e9adbe8fa8eb68714c2aa85b81a238688f
- **短哈希**：68d833e9a
- **日期**：2025-04-16 10:58:56 +0200
- **作者**：Tom Tanaka
- **提交说明**：Spark 3.4: Migrate ExtensionsTestBase-related tests for Snapshot manipulation, ChangeLogView and Distribution/Ordering (#12807)
- **PR/Issue**：#12807

## 总体目的

本提交是 Iceberg 项目 JUnit 4 到 JUnit 5 迁移工作的继续，处理 Spark 3.4 模块中与快照操作（Snapshot manipulation）、变更日志视图（ChangeLogView）和分布/排序（Distribution/Ordering）相关的测试类。

除了测试迁移外，本提交还包含一个重要的生产代码修复：将写入分布模式（distribution mode）的类型从 `DistributionMode` 改为 `Option[DistributionMode]`，以正确处理 `LOCALLY` 排序模式。此前，当用户指定 `LOCALLY` 排序时，分布模式会被设置为 `DistributionMode.NONE`，但这并不正确——`LOCALLY` 意味着不应设置任何分布模式（保持引擎默认行为），而非强制设置为 NONE 模式。通过使用 `Option[DistributionMode]`，`LOCALLY` 场景返回 `None`，表示不修改分布模式属性。

## 如何达成设计目的

1. **生产代码修复**：修改三个 Scala 文件，将 `distributionMode` 的类型从 `DistributionMode` 改为 `Option[DistributionMode]`，在 `LOCALLY` 排序时返回 `None`，在执行器中仅当模式为 `Some` 时才设置属性。

2. **测试迁移**：将 16 个 Spark 3.4 测试类从 JUnit 4 迁移到 JUnit 5，采用与之前提交相同的迁移模式（注解替换、基类替换、断言迁移）。

3. **Spark 3.5 测试同步**：对 Spark 3.5 中对应测试进行小幅调整以保持一致性。

## 修改详情

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/parser/extensions/IcebergSqlExtensionsAstBuilder.scala` (修改, +10/-7 lines)

**修改目的**：修复 LOCALLY 排序模式下的分布模式处理逻辑。

**工作逻辑**：
原代码将分布模式分为三种情况：
- 有 distributionSpec → `DistributionMode.HASH`
- UNORDERED 或 LOCALLY → `DistributionMode.NONE`
- 其他 → `DistributionMode.RANGE`

新代码将 UNORDERED 和 LOCALLY 分开处理：
- 有 distributionSpec → `Some(DistributionMode.HASH)`
- UNORDERED → `Some(DistributionMode.NONE)`
- LOCALLY → `None`（不设置分布模式）
- 其他 → `Some(DistributionMode.RANGE)`

使用 `Option` 包装，`LOCALLY` 返回 `None` 表示不修改分布模式属性。

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/SetWriteDistributionAndOrderingExec.scala` (修改, +10/-5 lines)

**修改目的**：适配 distributionMode 类型变更，仅在模式存在时设置属性。

**工作逻辑**：
- 将 `distributionMode` 字段类型从 `DistributionMode` 改为 `Option[DistributionMode]`
- 将属性设置逻辑从直接调用改为 `distributionMode.foreach { mode => ... }`，仅当模式为 `Some` 时才执行 `updateProperties().set(WRITE_DISTRIBUTION_MODE, mode.modeName()).commit()`

### `spark/v3.4/spark/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/SetWriteDistributionAndOrdering.scala` (修改, +2/-1 lines)

**修改目的**：适配 distributionMode 类型变更。

**工作逻辑**：将 `distributionMode` 字段类型从 `DistributionMode` 改为 `Option[DistributionMode]`。

### Spark 3.4 测试文件（16个，修改）

**修改目的**：将以下测试类从 JUnit 4 迁移到 JUnit 5：
- `TestAncestorsOfProcedure.java`
- `TestCallStatementParser.java`
- `TestChangelogTable.java`
- `TestCherrypickSnapshotProcedure.java`
- `TestConflictValidation.java`
- `TestCreateChangelogViewProcedure.java`
- `TestMetaColumnProjectionWithStageScan.java`
- `TestRequiredDistributionAndOrdering.java`
- `TestRollbackToSnapshotProcedure.java`
- `TestRollbackToTimestampProcedure.java`
- `TestSetCurrentSnapshotProcedure.java`
- `TestSetWriteDistributionAndOrdering.java`
- `TestStoragePartitionedJoinsInRowLevelOperations.java`
- `TestSystemFunctionPushDownDQL.java`
- `TestSystemFunctionPushDownInRowLevelOperations.java`
- `TestWriteAborts.java`

**工作逻辑**：采用标准 JUnit 5 迁移模式：
- `extends SparkExtensionsTestBase` → `@ExtendWith(ParameterizedTestExtension.class) extends ExtensionsTestBase`
- `@Test` → `@TestTemplate`，`@Before` → `@BeforeEach`，`@After` → `@AfterEach`
- 构造函数注入 → `@Parameter` 字段注入
- `@Parameterized.Parameters` → `@Parameters`
- 移除构造函数，添加 `super.before()` 调用（如需要）

### Spark 3.5 测试文件（7个，修改）

**修改目的**：对 Spark 3.5 对应测试进行小幅调整以保持一致性。

**工作逻辑**：主要包括添加缺失的注解、调整 import、移除冗余代码等小幅清理。

## 总结

本提交完成了 Spark 3.4 模块中快照操作、变更日志视图和分布/排序相关测试的 JUnit 5 迁移（16个测试类）。同时修复了一个生产代码 bug：将分布模式类型改为 `Option[DistributionMode]`，使 `LOCALLY` 排序模式不再错误地设置分布模式为 NONE，而是保持不修改分布属性。这是 Iceberg JUnit 5 迁移工作的重要组成部分。
