# 提交 0546：Spark: Migrate tests to JUnit5

## 提交信息

- **序号**：0546 / 4088
- **哈希**：22401546e95b4da1ee11457d42d80050572c6c37
- **短哈希**：22401546e
- **日期**：2024-02-27 23:19:02 +0900
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Spark: Migrate tests to JUnit5 (#9790)
- **PR/Issue**：#9790

## 总体目的

本提交是 Iceberg Spark 3.5 模块从 JUnit4 迁移到 JUnit5 的延续工作。Iceberg 社区在 2023 年下半年起陆续将各模块（Flink、Spark 3.4、Spark 3.5 等）测试代码迁移到 JUnit5，并改用 AssertJ 流式断言风格，以统一测试栈、改善失败诊断信息、避免 JUnit4 与 JUnit5 共存带来的依赖混乱。

本提交针对 Spark 3.5 的 6 个尚未迁移的测试类完成 JUnit5 化：

- `TestAlterTableSchema`
- `TestMetaColumnProjectionWithStageScan`
- `TestMetadataTables`
- `TestStoragePartitionedJoinsInRowLevelOperations`
- `TestSystemFunctionPushDownDQL`
- `TestViews`
- 以及 spark-runtime 集成测试目录下的 `SmokeTest`

同时调整 `spark/v3.5/build.gradle`，在 integrationTest 任务上启用 `useJUnitPlatform()`，使 runtime 集成测试也能在 JUnit5 平台执行；并新增 `libs.junit.jupiter` 依赖，配合已存在的 `libs.junit.vintage.engine`（后者兼容存量 JUnit4 测试）。这一基础设施改造使 SmokeTest 改为 JUnit5 后能被 Gradle 正确识别执行。

## 如何达成设计目的

整体设计思路是：复用社区此前已经引入的 JUnit5 测试基础设施（`ExtensionsTestBase`、`ParameterizedTestExtension`、`@Parameters`、`CatalogTestBase/TestBase` 等），把 6 个 JUnit4 测试类按统一的模板做机械式改写。改写模板包括以下要点：

1. **取消构造器参数化**：JUnit4 时代 `SparkExtensionsTestBase` 通过带 `(catalogName, implementation, config)` 的构造器接收参数化输入，子类必须复制该构造器。JUnit5 不再支持通过构造器注入参数化数据，改用 `@ExtendWith(ParameterizedTestExtension.class)` 装饰类，由扩展通过反射把 `@Parameters` 方法返回的二维数组逐组注入到测试实例字段中。因此子类不再需要写构造器，去掉冗余的 `Map<String,String> config` 字段，并把基类从 `SparkExtensionsTestBase` 改为新的 `ExtensionsTestBase`。
2. **注解替换**：
   - `@Test` → `@TestTemplate`（因参数化扩展要求模板化测试方法）
   - `@Before` → `@BeforeEach`，`@After` → `@AfterEach`
   - `@Parameterized.Parameters` → `org.apache.iceberg.Parameters`（Iceberg 自带的 JUnit5 参数化注解，与 Flink 借鉴实现一致）
3. **断言风格迁移**：将 `org.junit.Assert.assertEquals/assertTrue` 等迁移到 AssertJ 的 `assertThat(...).isEqualTo(...)` / `.hasSize(...)` / `.isEmpty()` / `.isTrue()` 流式断言；将 `org.assertj.core.api.Assertions.assertThatThrownBy` 改为静态导入的 `assertThatThrownBy`，并把 `Assertions.assertThat(actual.size()).isEqualTo(n)` 这种"先取 size 再断言"的反模式改写为 `assertThat(actual).hasSize(n)`。
4. **测试方法签名调整**：部分 JUnit5 迁移中会顺手清理多余的 `throws NoSuchTableException` 等不再需要的受检异常声明（如 SmokeTest.testAlterTable 直接去掉 throws）。
5. **SmokeTest 特殊处理**：由于 SmokeTest 在 spark-runtime 集成测试目录下运行，此前依赖 `temp.newFolder()` 创建临时目录。JUnit5 的 `@TempDir` 机制下需改为 `Files.createTempDirectory(temp, "junit")`，这是因为新基类的 `temp` 字段类型/行为与旧 JUnit4 Rule 不完全一致，需要显式创建子目录以避免冲突。
6. **build.gradle 启用 JUnit Platform**：integrationTest 任务调用 `useJUnitPlatform()`，并新增 `integrationImplementation libs.junit.jupiter` 依赖。这样 Gradle 用 JUnit Platform 启动器运行测试，JUnit5 原生测试由 jupiter 引擎执行，存量 JUnit4 测试由 vintage 引擎兜底执行。

## 修改详情

### `spark/v3.5/build.gradle`

**修改目的**：为 spark-runtime 的 integrationTest 任务提供 JUnit5 运行平台。

**工作逻辑**：
- 在 `:iceberg-spark:iceberg-spark-runtime-...` 项目依赖块中新增 `integrationImplementation libs.junit.jupiter`，引入 JUnit Jupiter API + 引擎，使 SmokeTest 等 JUnit5 集成测试可被编译和运行。
- 在 `task integrationTest(type: Test)` 内新增 `useJUnitPlatform()`，指示 Gradle 用 JUnit Platform 启动器执行该任务下所有测试。此前仅依赖 `junit.vintage.engine`，无法识别 `@TestTemplate`/`@ExtendWith` 等 JUnit5 注解。
- 此前已有的 `libs.junit.vintage.engine` 仍然保留，用于兼容尚未迁移的 JUnit4 集成测试，保证迁移渐进式推进不破坏现有用例。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAlterTableSchema.java`

**修改目的**：迁移该测试类中残留的 JUnit4 `Assert.assertTrue` 调用。

**工作逻辑**：该类大部分注解已在前期提交迁移到 JUnit5（已是 `@TestTemplate`、`ExtensionsTestBase`），仅 `testSetInvalidIdentifierFields` 中仍使用 `org.junit.Assert.assertTrue`。本次提交将其改为 `assertThat(table.schema().identifierFieldIds()).as("Table should start without identifier").isEmpty()`，并删除 `import org.junit.Assert;`。改动小但彻底消除该类对 JUnit4 API 的依赖。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetaColumnProjectionWithStageScan.java`

**修改目的**：完成该类从 JUnit4 参数化到 JUnit5 参数化的完整迁移。

**工作逻辑**：
- 删除构造器 `public TestMetaColumnProjectionWithStageScan(String catalogName, String implementation, Map<String,String> config)`，因 JUnit5 不再需要构造器注入。
- 类声明改为 `@ExtendWith(ParameterizedTestExtension.class) public class TestMetaColumnProjectionWithStageScan extends ExtensionsTestBase`。
- `@Parameterized.Parameters` → `@Parameters`（来自 `org.apache.iceberg.Parameters`），`@After` → `@AfterEach`，`@Test` → `@TestTemplate`。
- 新增 `import static org.assertj.core.api.Assertions.assertThat;`，删除 `import org.assertj.core.api.Assertions;`。
- `Assertions.assertThat(scanDF2.columns().length).isEqualTo(2)` 改写为 `assertThat(scanDF2.columns()).hasSize(2)`，更符合 AssertJ 推荐用法（断言集合本身而非其 size）。
- 移除未使用的 `import java.util.Map;`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetadataTables.java`

**修改目的**：将该大型测试类（含约 10 个 `@Test` 方法）从 JUnit4 迁移到 JUnit5，并统一断言风格。

**工作逻辑**：
- 类层面：删除构造器、`@ExtendWith(ParameterizedTestExtension.class)` 装饰、基类改为 `ExtensionsTestBase`、`@After` → `@AfterEach`、所有 `@Test` → `@TestTemplate`、`@Parameterized.Parameters` → `@Parameters`。
- 断言层面：把分散在各测试方法中的 `Assert.assertEquals(message, expected, actual)` 系统性替换为 `assertThat(actual).as(message).hasSize(expected)` 或 `.isEqualTo(expected)`、`.isEmpty()`。例如：
  - `Assert.assertEquals("Should have 1 data manifest", 1, expectedDataManifests.size())` → `assertThat(expectedDataManifests).as("Should have 1 data manifest").hasSize(1)`
  - `Assert.assertEquals("Table should be cleared", 0, results.size())` → `assertThat(results).as("Table should be cleared").isEmpty()`
- `testSnapshotReferencesMetatable` 方法中原本对 `mainBranch.get(0).getAs("name")` 等字段逐一断言，迁移时重构为更紧凑的 `assertThat(mainBranch).hasSize(1).containsExactly(RowFactory.create("main", "BRANCH", currentSnapshotId, null, null, null))`，并新增对 `mainBranch.get(0).schema().fieldNames()` 的 `containsExactly(...)` 断言，使列顺序也被覆盖。
- 因迁移后字段顺序更严格，`testBranchProjection` 中原 SQL `SELECT type, name, max_reference_age_in_ms, snapshot_id FROM ...` 被改为 `SELECT name, type, snapshot_id, max_reference_age_in_ms FROM ...`，与 `containsExactly(RowFactory.create("testBranch", "BRANCH", currentSnapshotId, 10L))` 顺序一致。这是为了让 AssertJ 的 `containsExactly`（严格顺序）能通过——属于迁移过程中顺带的断言强化。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestStoragePartitionedJoinsInRowLevelOperations.java`

**修改目的**：将该类从 JUnit4 迁移到 JUnit5。

**工作逻辑**：
- 与上述类相同的注解/构造器/基类迁移模式。
- 该类有 4 个 `checkDelete/checkUpdate/checkMerge` 系列 `@Test` 方法，全部改为 `@TestTemplate`。
- `Assert.assertEquals("Should be 1 shuffle with SPJ", 1, actualNumShuffles)` 改为 `assertThat(actualNumShuffles).as("Should be 1 shuffle with SPJ").isEqualTo(1)`；`Assertions.assertThat(planAsString).contains("Exchange hashpartitioning(_file")` 改为静态导入的 `assertThat(planAsString).contains(...)`。
- 删除 `import org.junit.After; import org.junit.Assert; import org.junit.Test; import org.junit.runners.Parameterized;` 等所有 JUnit4 import，新增 JUnit5 + Iceberg Parameters + AssertJ 静态导入。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSystemFunctionPushDownDQL.java`

**修改目的**：迁移该类到 JUnit5，并修正 `@Before` 的继承链调用问题。

**工作逻辑**：
- 注解、构造器、基类迁移模式同上。
- **关键修正**：JUnit4 时代 `@Before public void before()` 仅调用 `sql("USE %s", catalogName)`，而 JUnit5 迁移后新增 `super.before()` 调用。这是因为 JUnit5 中基类 `CatalogTestBase.before()` 承担了 catalog 初始化等关键设置，子类重写 `before()` 必须显式调用 super，否则参数化字段（catalogName 等）未被正确初始化就会执行 `sql("USE %s", catalogName)` 导致 NPE。这是 JUnit4→5 迁移中容易遗漏的点，本提交做了正确处理。
- 断言迁移：`Assertions.assertThat(actual.size()).isEqualTo(5)` → `assertThat(actual).hasSize(5)`；`Assertions.assertThat(staticInvokes).isEmpty()` → `assertThat(staticInvokes).isEmpty()`；`Assertions.assertThat(ExpressionUtil.equivalent(expected, actual, STRUCT, true)).as("Pushed filter should match").isTrue()` → `assertThat(ExpressionUtil.equivalent(expected, actual, STRUCT, true)).as("Pushed filter should match").isTrue()`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：将 Iceberg Spark 3.5 中规模最大的视图测试类（约 50 个 `@Test` 方法）迁移到 JUnit5。

**工作逻辑**：
- 注解/构造器/基类迁移模式同上，所有 `@Test` → `@TestTemplate`、`@Before` → `@BeforeEach`、`@After` → `@AfterEach`、`@Parameterized.Parameters` → `@Parameters`。
- 同样在 `@BeforeEach before()` 中新增 `super.before()` 调用，保证基类初始化逻辑执行。
- 该类原有断言已大量使用 AssertJ 风格（`assertThatThrownBy`、`assertThat(...).containsExactly`），迁移主要工作是注解替换与 import 整理，断言主体改动较少。

### `spark/v3.5/spark-runtime/src/integration/java/org/apache/iceberg/spark/SmokeTest.java`

**修改目的**：将 spark-runtime 集成测试 SmokeTest 迁移到 JUnit5，使整个 spark-runtime 模块的集成测试可在 JUnit Platform 上运行。

**工作逻辑**：
- 类声明：`public class SmokeTest extends SparkExtensionsTestBase` → `@ExtendWith(ParameterizedTestExtension.class) public class SmokeTest extends ExtensionsTestBase`，删除构造器。
- `@Before` → `@AfterEach`（注意原 `@Before` 名为 `dropTable` 实为清理逻辑，迁移时直接纠正为 `@AfterEach` 更符合语义）；`@Test` → `@TestTemplate`。
- `testGettingStarted` 中两处 `temp.newFolder()` 改为 `Files.createTempDirectory(temp, "junit")`。这是因为新基类 `TestBase.temp` 是 `File` 类型而非 JUnit4 `TemporaryFolder` Rule，没有 `newFolder()` 方法；改用 `Files.createTempDirectory` 在 `temp` 目录下创建子目录，行为等价。
- 删除 `throws NoSuchTableException`（testAlterTable 方法），因为该方法实际未抛出该异常，迁移时清理冗余声明。
- 大量 `Assert.assertEquals(message, expected, actual)` 改为 `Assertions.assertThat(actual).as(message).isEqualTo(expected)`；`Assert.assertTrue(message, actual)` 改为 `Assertions.assertThat(actual).as(message).isTrue()`。注意此处 SmokeTest 用的是 `org.assertj.core.api.Assertions`（非静态导入），与其他测试类用静态导入风格略有不同，但同样符合 JUnit5 + AssertJ 体系。
- 新增 `import java.nio.file.Files;`，删除 `import java.util.Map;`、`import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;`、`import org.junit.Assert; import org.junit.Before; import org.junit.Test;`。

## 小结

本提交是 Spark 3.5 模块 JUnit5 迁移工作的收尾之一，将 6 个仍停留在 JUnit4 的测试类（含 1 个 spark-runtime 集成测试）统一迁移到 JUnit5 平台，并配套调整 build.gradle 使 integrationTest 任务支持 JUnit Platform。改动以机械式注解替换和断言风格统一为主，但其中两处需要特别关注：

1. **`super.before()` 显式调用**：JUnit5 参数化扩展通过字段注入而非构造器注入，子类 `@BeforeEach` 方法若重写基类 before 必须显式调用 super，否则基类初始化逻辑被跳过。TestSystemFunctionPushDownDQL 和 TestViews 都做了此修正。
2. **`temp.newFolder()` → `Files.createTempDirectory(temp, "junit")`**：SmokeTest 因基类 temp 字段类型变化需调整临时目录创建方式。

**回迁到 1.4.x 的注意事项**：
- 1.4.x 分支若仍使用 JUnit4 主导的测试栈，回迁本提交需要先确认 1.4.x 是否已存在 `ExtensionsTestBase`、`ParameterizedTestExtension`、`Parameters`、`CatalogTestBase/TestBase` 等 JUnit5 基础设施。这些类是在更早的多个 PR 中分批引入的（如 #9341、#9367、#9401、#9417、#9380 等针对 Spark 3.5 的迁移），1.4.x 若未合入这些前置 PR，则无法直接应用本提交。
- build.gradle 的 `useJUnitPlatform()` 与 `libs.junit.jupiter` 依赖需一并回迁，否则 SmokeTest 改为 JUnit5 后无法被 Gradle 执行。
- 若 1.4.x 决定保留 JUnit4 测试栈，则本提交不宜直接回迁；可考虑仅回迁其中与 JUnit5 无关的业务逻辑修正（如 testBranchProjection 中 SQL 字段顺序调整），但这些与迁移耦合较深，单独剥离成本较高。
- 本提交不涉及产品代码逻辑变更，纯测试基础设施改造，对运行时行为无影响，回迁风险主要在测试编译与执行层面。
