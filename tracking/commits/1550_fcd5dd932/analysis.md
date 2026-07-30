# 提交 1550 fcd5dd932 分析

## 提交信息
- 哈希：fcd5dd932a21066d6127c94c50f3de43e8c2d80c
- 日期：2025-01-05（Sun Jan 5 01:25:10 2025 +0700）
- 作者：Vova Kolmakov <wombatukun@gmail.com>
- 消息：Kafka-connect-runtime: remove code duplications in integration tests (#11883)

## 总体目的

重构 Kafka Connect 运行时模块的集成测试，消除三个测试类（`IntegrationTest`、`IntegrationMultiTableTest`、`IntegrationDynamicTableTest`）中大量重复的测试基础设施代码，将其提取到基类 `IntegrationTestBase` 中。

这三个集成测试类在重构前各自维护了几乎相同的测试设置和清理逻辑，包括：Kafka topic 的创建与删除、Hive 命名空间的创建与删除、Kafka Connect connector 的启动与停止、connector 配置的构建（大部分配置项相同）、事件发送后的快照验证逻辑（使用 Awaitility 轮询检查快照是否生成）。这些重复代码不仅增加了维护成本，还容易导致不一致——如果某个测试类遗漏了某项清理步骤，可能影响其他测试的隔离性。

重构后，`IntegrationTestBase` 成为抽象基类，统一管理测试生命周期（setup/teardown）、通用 connector 配置构建、测试执行流程编排和快照验证。各子类只需实现三个抽象方法来提供测试特定的逻辑：connector 配置的差异部分、事件数据的构造、以及表清理逻辑。这使测试代码更简洁、更易维护，新增测试类型时也只需关注差异部分。

## 如何达成设计目的

将 `IntegrationTestBase` 改为抽象类并提升其职责，引入三个抽象方法（`createConfig`、`sendEvents`、`dropTables`）和若干模板方法（`createCommonConfig`、`runTest`、`assertSnapshotAdded`），将通用的 setup/teardown 逻辑集中到基类。各子类移除重复代码，仅保留测试特定的实现。

### 修改详情

#### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/IntegrationTestBase.java`

**修改目的**：从普通基类升级为抽象基类，集中管理测试基础设施。

**工作逻辑**：

1. **类声明**：从 `public class IntegrationTestBase` 改为 `public abstract class IntegrationTestBase`。

2. **新增常量和抽象方法**：
   - `protected static final String TEST_DB = "test"`（从子类上移）
   - `abstract KafkaConnectUtils.Config createConfig(boolean useSchema)` — 子类提供 connector 的差异配置
   - `abstract void sendEvents(boolean useSchema)` — 子类提供事件构造和发送逻辑
   - `abstract void dropTables()` — 子类提供表清理逻辑

3. **`@BeforeEach` 增强**：在原有初始化基础上新增 `createTopic(testTopic(), TEST_TOPIC_PARTITIONS)` 和 `((SupportsNamespaces) catalog()).createNamespace(Namespace.of(TEST_DB))`，将 topic 和 namespace 创建从子类上移。

4. **`@AfterEach` 增强**：新增 `context().stopConnector(connectorName())`、`deleteTopic(testTopic())`、`dropTables()`（调用子类实现）、`((SupportsNamespaces) catalog()).dropNamespace(Namespace.of(TEST_DB))`，将清理逻辑从子类上移。原有的 catalog close 逻辑保留。

5. **新增 `createCommonConfig(boolean useSchema)`**：构建所有测试共享的 connector 基础配置，包括 topics、connector.class、tasks.max、consumer override、converters、commit interval/timeout、kafka offset reset 等。

6. **新增 `runTest(String branch, boolean useSchema, Map<String, String> extraConfig, List<TableIdentifier> tableIdentifiers)`**：编排测试执行流程——调用 `createConfig` 获取配置、追加 catalog 属性和 branch 配置和额外配置、启动 connector、调用 `sendEvents` 发送事件、flush、使用 Awaitility 轮询验证快照生成。

7. **新增 `assertSnapshotAdded(List<TableIdentifier> tableIdentifiers)`**：遍历所有表标识符，加载每个表并断言其恰好有 1 个快照。如果表不存在则 fail。

#### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/IntegrationTest.java`

**修改目的**：移除重复代码，改为实现基类抽象方法。

**工作逻辑**：
- 移除 `TEST_DB` 常量、`@BeforeEach`、`@AfterEach`、`runTest`、`assertSnapshotAdded` 方法。
- 实现抽象方法 `createConfig`：调用 `createCommonConfig(useSchema)` 并追加 `"iceberg.tables"` 配置。
- 实现抽象方法 `sendEvents`：构造两个 TestEvent（一个当前时间，一个三天前）并发送。
- 实现抽象方法 `dropTables`：删除 `TABLE_IDENTIFIER`。
- 各测试方法中将 `runTest(branch, useSchema, extraConfig)` 调用改为 `runTest(branch, useSchema, extraConfig, List.of(TABLE_IDENTIFIER))`。

#### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/IntegrationMultiTableTest.java`

**修改目的**：同 IntegrationTest，移除重复代码并实现抽象方法。

**工作逻辑**：
- 移除与 IntegrationTest 相同的重复代码。
- 实现 `createConfig`：调用 `createCommonConfig` 并追加 `"iceberg.tables"`（逗号分隔的两表）、`"iceberg.tables.route-field"` 和每张表的 `route-regex`。
- 实现 `sendEvents`：构造三个 TestEvent（type1、type2 各路由到对应表，type3 被忽略）并发送。
- 实现 `dropTables`：删除两张表的标识符。
- 测试方法调用改为传入 `List.of(TABLE_IDENTIFIER1, TABLE_IDENTIFIER2)`。

#### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/IntegrationDynamicTableTest.java`

**修改目的**：同上，移除重复代码并实现抽象方法。

**工作逻辑**：
- 移除重复代码。
- 实现 `createConfig`：调用 `createCommonConfig` 并追加 `"iceberg.tables.dynamic-enabled"` 和 `"iceberg.tables.route-field"`。
- 实现 `sendEvents`：构造三个 TestEvent（分别路由到 tbl1、tbl2、tbl3，其中 tbl3 不存在用于测试动态建表）并发送。
- 实现 `dropTables`：删除 tbl1 和 tbl2（tbl3 由动态建表创建，测试后不保留）。
- 测试方法调用改为传入 `List.of(TABLE_IDENTIFIER1, TABLE_IDENTIFIER2)`。

## 小结

- **成效**：消除了三个集成测试类中约 200 行重复代码，将公共的测试基础设施（setup/teardown、connector 配置构建、测试执行流程、快照验证）集中到 `IntegrationTestBase` 抽象基类。子类通过实现三个简洁的抽象方法（`createConfig`、`sendEvents`、`dropTables`）提供测试特定的逻辑，代码更清晰、更易维护，新增测试类型时只需关注差异部分。
- **影响范围**：涉及 4 个文件（1 个基类 + 3 个子类），净减少 77 行代码（123 新增 / 200 删除）。纯测试代码重构，不影响产品功能。
- **回迁到 1.4.x 的注意事项**：测试代码重构与产品功能无关，**可选回迁**。如果 1.4.x 的 Kafka Connect 测试存在同样的重复问题，回迁可改善测试可维护性；不回迁也不影响功能。但需注意 1.4.x 的测试逻辑是否与 main 分支一致，避免回迁后测试行为变化。
