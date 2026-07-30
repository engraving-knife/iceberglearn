# 提交 0471：Spark: Migrate tests to JUnit5 (#9624)

## 提交信息

| 字段 | 内容 |
| --- | --- |
| 序号 | 0471 |
| 完整哈希 | 4835549ac39165fc7e44337b03a081d390e64711 |
| 短哈希 | 4835549ac |
| 日期 | 2024-02-06（Tue Feb 6 19:40:08 2024 +0900） |
| 作者 | Tom Tanaka <43331405+tomtongue@users.noreply.github.com> |
| 说明 | Spark: Migrate tests to JUnit5 (#9624) |
| PR | #9624 |

提交统计：7 个文件修改，449 行新增，460 行删除。

涉及文件（均在 `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/` 下）：

1. `TestAlterTablePartitionFields.java`（224 行变更）
2. `TestAlterTableSchema.java`（103 行变更）
3. `TestBranchDDL.java`（173 行变更）
4. `TestReplaceBranch.java`（92 行变更）
5. `TestRequiredDistributionAndOrdering.java`（39 行变更）
6. `TestSetWriteDistributionAndOrdering.java`（126 行变更）
7. `TestTagDDL.java`（152 行变更）

## 总体目的

本提交是 Iceberg 测试基础设施现代化工作的一部分，将 Spark v3.5 扩展模块（`spark-extensions`）下的一批测试类从 JUnit 4 迁移到 JUnit 5（Jupiter）。Iceberg 主干（main 分支）已先期引入了自定义的 JUnit 5 测试支撑类——`org.apache.iceberg.ParameterizedTestExtension`、`org.apache.iceberg.Parameters`、`org.apache.iceberg.Parameter`，以及新的测试基类 `ExtensionsTestBase`，本提交则是把这一批 Spark 扩展测试切换到新的支撑体系上，使它们与主干其余测试保持一致的 JUnit 5 风格。

迁移的核心价值在于：JUnit 5 的扩展模型（`@ExtendWith` + `Extension`）相比 JUnit 4 的 Runner/Rule 模型更灵活、可组合；`@TestTemplate` 配合自定义扩展可以实现参数化测试，而不再依赖 JUnit 4 中较为僵化的 `@Parameterized` Runner（一个类只能用一个 Runner）。同时，断言统一收敛到 AssertJ 的流式 API（`assertThat(...).isEqualTo(...)`、`.isTrue()`、`.as(...)` 描述），既提升了失败信息的可读性，也消除了 JUnit 4 `Assert.assertEquals(message, expected, actual)` 中“消息在前、值在后”这种容易写反的 API 陷阱。

本提交专门针对 Spark 3.5 模块下涉及表结构变更（分区字段、Schema、分支/标签 DDL、写入分布与排序）的 7 个测试类做迁移，是“分批迁移测试到 JUnit 5”这一长期工作的一个切片。由于 1.4.x 分支尚未合入主干上引入新测试支撑类的提交，因此本提交在 1.4.x 上单独看会缺少 `ParameterizedTestExtension`、`ExtensionsTestBase` 等依赖，需要连同主干上相关基础设施提交一起评估回迁可行性。

## 如何达成设计目的

实现路径是逐文件进行机械但成体系的替换：导入语句从 `org.junit.*`（JUnit 4）切换到 `org.junit.jupiter.api.*`（JUnit 5）；类声明上加 `@ExtendWith(ParameterizedTestExtension.class)`，并把父类由 `SparkExtensionsTestBase` 改为 `ExtensionsTestBase`；测试方法注解 `@Test` 改为 `@TestTemplate`，生命周期方法 `@Before`/`@After` 改为 `@BeforeEach`/`@AfterEach`；参数化入口 `@Parameterized.Parameters` 改为自定义的 `@Parameters`，构造函数注入改为 `@Parameter(index = N)` 字段注入（移除显式构造函数）；断言从 `Assert.assertEquals/assertTrue/assertNotNull` 改写为 AssertJ 的 `assertThat(...)` 链式调用，并将原 JUnit 4 风格的 `Assertions.assertThat(...)` 引用改为静态导入 `assertThat`，使整段断言更紧凑。

## 修改详情

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAlterTablePartitionFields.java`

修改目的：把分区字段增删改测试迁移到 JUnit 5 参数化模板测试。

工作逻辑：

- 新增静态导入 `assertThat`，并导入 `Parameter`、`ParameterizedTestExtension`、`Parameters`；移除 `org.assertj.core.api.Assertions`、`org.junit.After`、`org.junit.Assert`、`org.junit.Test`、`org.junit.runners.Parameterized`。
- 类上加 `@ExtendWith(ParameterizedTestExtension.class)`，父类由 `SparkExtensionsTestBase` 改为 `ExtensionsTestBase`。
- 参数化方法注解由 `@Parameterized.Parameters(name = "catalogConfig = {0}, formatVersion = {1}")` 改为 `@Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}, formatVersion = {3}")`。对应地，原参数二维数组只传 `{SparkCatalogConfig.HIVE, 1}` 两元组，现在展开为完整的四元组 `(catalogName, implementation, properties, formatVersion)`，使参数集与 `ExtensionsTestBase` 期望的 `(catalogName, implementation, config)` 三元组对齐，并额外携带 `formatVersion`。
- 删除原构造函数 `TestAlterTablePartitionFields(SparkCatalogConfig, int)`，改为用 `@Parameter(index = 3) private int formatVersion;` 字段注入，前三列参数由 `ExtensionsTestBase` 通过其自身字段注入消费。
- `@After removeTable()` 改为 `@AfterEach`；每个 `@Test` 方法改为 `@TestTemplate`。
- 断言改写：`Assert.assertTrue("...", cond)` → `assertThat(cond).as("...").isTrue()`；`Assert.assertEquals("...", expected, actual)` → `assertThat(actual).as("...").isEqualTo(expected)`；原本已是 AssertJ 但写成 `Assertions.assertThat(...).as(...).isTrue()` 的（如 `testAddYearPartition`、`testAddMonthPartition`）改为静态导入的 `assertThat(...)`，使风格统一。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAlterTableSchema.java`

修改目的：把 Schema 变更（设置/删除标识字段、加列、改列等）测试迁移到 JUnit 5。

工作逻辑：

- 导入切换为 JUnit 5（`AfterEach`、`TestTemplate`、`ExtendWith`），新增静态导入 `assertThat`，引入 `ParameterizedTestExtension`。
- 类上加 `@ExtendWith(ParameterizedTestExtension.class)`，父类改为 `ExtensionsTestBase`，删除原 `(catalogName, implementation, config)` 构造函数（参数注入由基类接管）。
- `@After` → `@AfterEach`，`@Test` → `@TestTemplate`。
- 断言统一改写为 AssertJ：例如 `Assert.assertTrue("Table should start without identifier", table.schema().identifierFieldIds().isEmpty())` 改为 `assertThat(table.schema().identifierFieldIds()).as("Table should start without identifier").isEmpty()`；`Assert.assertEquals("Should have new identifier field", expected, actual)` 改为 `assertThat(actual).as("Should have new identifier field").isEqualTo(expected)`。注意原文件仍保留了一处 `import org.junit.Assert;`（与 `assertThat` 静态导入并存），这是过渡期残留，未在本提交中清理。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestBranchDDL.java`

修改目的：把分支 DDL（CREATE/REPLACE/DROP BRANCH 等）测试迁移到 JUnit 5。

工作逻辑：

- 导入：移除 `java.util.Map`、`org.junit.After/Before/Assert/Test`、`org.junit.runners.Parameterized`；新增 `ParameterizedTestExtension`、`Parameters`、`BeforeEach`、`AfterEach`、`TestTemplate`、`ExtendWith`。
- 类上加 `@ExtendWith(ParameterizedTestExtension.class)`，父类改为 `ExtensionsTestBase`；删除原 `(String catalog, String implementation, Map properties)` 构造函数。
- `@Before before()` → `@BeforeEach createTable()`（方法名也同步改为更具语义的 `createTable`）；`@After` → `@AfterEach`；`@Test` → `@TestTemplate`。
- 参数化注解 `@Parameterized.Parameters` → `@Parameters`，参数格式字符串保持 `"catalogName = {0}, implementation = {1}, config = {2}"`。
- 断言改写：`Assert.assertEquals(expected, actual)`（注意原 JUnit 4 此处已是无消息重载，参数顺序 expected 在前）改为 `assertThat(actual).isEqualTo(expected)`，例如 `assertThat(ref.snapshotId()).isEqualTo(table.currentSnapshot().snapshotId())`、`assertThat(ref.maxSnapshotAgeMs().longValue()).isEqualTo(TimeUnit.DAYS.toMillis(maxSnapshotAge))`。原 `Assertions.assertThat(...)` 形式（如 `Assertions.assertThat(mainRef).isNull()`）统一改为静态导入的 `assertThat(...)`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestReplaceBranch.java`

修改目的：把分支替换（replace branch）测试迁移到 JUnit 5。

工作逻辑：

- 导入：移除 `java.util.Map`、`org.junit.After/Assert/Test`、`org.junit.runners.Parameterized`；新增静态导入 `assertThat`，引入 `ParameterizedTestExtension`、`Parameters`、`AfterEach`、`TestTemplate`、`ExtendWith`。
- 类上加 `@ExtendWith(ParameterizedTestExtension.class)`，父类改为 `ExtensionsTestBase`，删除原 `(catalogName, implementation, config)` 构造函数。
- `@After removeTable()` → `@AfterEach`，`@Test` → `@TestTemplate`。
- 参数化注解 `@Parameterized.Parameters` → `@Parameters`，名称模板不变。
- 断言改写：`Assert.assertNotNull(ref)` → `assertThat(ref).isNotNull()`；`Assert.assertEquals(expectedMinSnapshotsToKeep, ref.minSnapshotsToKeep().intValue())` → `assertThat(ref.minSnapshotsToKeep().intValue()).isEqualTo(expectedMinSnapshotsToKeep)`；`Assert.assertEquals(expectedMaxSnapshotAgeMs, ref.maxSnapshotAgeMs().longValue())` → `assertThat(ref.maxSnapshotAgeMs().longValue()).isEqualTo(expectedMaxSnapshotAgeMs)`，统一为 actual-在左、expected-在右的 AssertJ 惯用法。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRequiredDistributionAndOrdering.java`

修改目的：把写入分布与排序（required distribution and ordering）测试迁移到 JUnit 5。

工作逻辑：

- 导入：移除 `java.util.Map`、`org.junit.After`、`org.junit.Test`；新增 `ParameterizedTestExtension`、`AfterEach`、`TestTemplate`、`ExtendWith`。本类未使用 `@Parameters`（不自行声明参数集），仅通过基类参数化。
- 类上加 `@ExtendWith(ParameterizedTestExtension.class)`，父类改为 `ExtensionsTestBase`，删除原 `(catalogName, implementation, config)` 构造函数。
- `@After dropTestTable()` → `@AfterEach`，全部 `@Test` → `@TestTemplate`。
- 本类内断言主要使用继承自基类的 `assertEquals(message, expected, actual)` 工具方法（未改动），因此本文件 diff 体量最小，仅做注解与父类/构造函数层面的迁移。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSetWriteDistributionAndOrdering.java`

修改目的：把设置写入分布与排序（`WRITE ORDERED BY` / `WRITE DISTRIBUTED BY PARTITION` 等）测试迁移到 JUnit 5。

工作逻辑：

- 导入：移除 `java.util.Map`、`org.junit.After/Assert/Test`；新增静态导入 `assertThat`，引入 `ParameterizedTestExtension`、`AfterEach`、`TestTemplate`、`ExtendWith`。
- 类上加 `@ExtendWith(ParameterizedTestExtension.class)`，父类改为 `ExtensionsTestBase`，删除原 `(catalogName, implementation, config)` 构造函数。
- `@After removeTable()` → `@AfterEach`，全部 `@Test` → `@TestTemplate`。
- 断言改写示例：`Assert.assertTrue("Table should start unsorted", table.sortOrder().isUnsorted())` → `assertThat(table.sortOrder().isUnsorted()).as("Table should start unsorted").isTrue()`；`Assert.assertEquals("Distribution mode must match", "range", distributionMode)` → `assertThat(distributionMode).as("Distribution mode must match").isEqualTo("range")`；`Assert.assertEquals("Sort order must match", expected, table.sortOrder())` → `assertThat(table.sortOrder()).as("Sort order must match").isEqualTo(expected)`。注意原 JUnit 4 的 `Assert.assertEquals(message, expected, actual)` 与 AssertJ `assertThat(actual).as(message).isEqualTo(expected)` 在参数顺序上相反，迁移时统一调换为 actual 在前。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestTagDDL.java`

修改目的：把标签 DDL（CREATE/DROP TAG、retain、time unit 校验等）测试迁移到 JUnit 5。

工作逻辑：

- 导入：移除 `java.util.Map`、`org.junit.After/Before/Assert/Test`、`org.junit.runners.Parameterized`；新增 `ParameterizedTestExtension`、`Parameters`、`BeforeEach`、`AfterEach`、`TestTemplate`、`ExtendWith`。
- 类上加 `@ExtendWith(ParameterizedTestExtension.class)`，父类改为 `ExtensionsTestBase`，删除原 `(catalogName, implementation, config)` 构造函数。
- `@Before before()` → `@BeforeEach createTable()`；`@After` → `@AfterEach`；`@Test` → `@TestTemplate`。
- 参数化注解 `@Parameterized.Parameters` → `@Parameters`，名称模板不变。
- 断言改写示例：原 `Assert.assertEquals("The tag needs to point to a specific snapshot id.", firstSnapshotId, ref.snapshotId())` 拆成两行 `assertThat(ref.snapshotId()).as("The tag needs to point to a specific snapshot id.").isEqualTo(firstSnapshotId)`，把描述信息通过 `.as(...)` 附加；时间单位相关断言 `Assert.assertEquals("The tag needs to have the correct max ref age.", TimeUnit.valueOf(...).toMillis(maxRefAge), ref.maxRefAgeMs().longValue())` 改为 `assertThat(ref.maxRefAgeMs().longValue()).as("The tag needs to have the correct max ref age.").isEqualTo(TimeUnit.valueOf(...).toMillis(maxRefAge))`，actual 与 expected 顺序调换。

## 小结

本提交是 Iceberg 测试体系从 JUnit 4 向 JUnit 5 迁移的延续，针对 Spark v3.5 扩展模块的 7 个测试类完成切换：统一使用 `@ExtendWith(ParameterizedTestExtension.class)` + `@TestTemplate` 的自定义扩展模型替代 JUnit 4 的 `@Parameterized` Runner，用 `@Parameter` 字段注入替代构造函数注入，用 `@BeforeEach`/`@AfterEach` 替代 `@Before`/`@After`，并把断言整体收敛到 AssertJ 静态导入 `assertThat` 的流式写法（含 `.as(...)` 描述、actual 在前 expected 在后的约定）。迁移后测试与主干已迁移的其余测试风格一致，参数化能力更灵活，失败信息更友好。需要注意的是，本提交依赖主干上先期引入的 `ParameterizedTestExtension`、`Parameter`、`Parameters` 与 `ExtensionsTestBase` 等支撑类，在 1.4.x 分支上单独回迁本提交需要先确认这些基础设施是否已具备。
