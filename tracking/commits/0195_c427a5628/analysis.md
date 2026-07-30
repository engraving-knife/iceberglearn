# 提交 0195：Flink: Create JUnit5 version of FlinkTestBase (#9120)

## 提交信息

- **序号**：0195 / 4088
- **哈希**：c427a5628be6f1b5f2c4783c36c3cf7783a1916b
- **短哈希**：c427a5628
- **日期**：2023-11-24 07:55:52 +0100
- **作者**：CG
- **提交说明**：Flink: Create JUnit5 version of FlinkTestBase (#9120)
- **PR/Issue**：#9120

## 总体目的

这个提交为 Iceberg 的 Flink 集成测试引入了 JUnit 5 版本的测试基类 `TestBase` 与 `MiniFlinkClusterExtension`，作为现有 JUnit 4 版本 `FlinkTestBase`（基于 `@ClassRule` + `MiniClusterWithClientResource`）的对应物。背景是 Iceberg Flink 测试套件历史上基于 JUnit 4，而 Flink 社区与 broader Java 生态正在向 JUnit 5（Jupiter）迁移：JUnit 5 提供更强大的扩展模型（`@RegisterExtension`、`@TempDir`）、更好的生命周期注解（`@BeforeAll`/`@AfterAll` 在 Jupiter 中语义更清晰）、以及对参数化测试和嵌套测试的原生支持。

本提交并不删除旧的 `FlinkTestBase`，而是新建一套并行的 JUnit 5 基础设施，让新写或迁移的测试可以直接基于 JUnit 5。同时把一个已有测试 `TestCatalogTableLoader` 从 JUnit 4（`FlinkTestBase`）迁移到 JUnit 5（新 `TestBase`），作为首个迁移样例与验证。这对 Iceberg Flink 测试体系逐步向 JUnit 5 演进是奠基性的一步，与 Flink 自身测试栈升级方向一致。

## 如何达成设计目的

设计思路是"平行新建 + 逐个迁移"。新建 `MiniFlinkClusterExtension`，封装 Flink 的 `MiniClusterExtension`（JUnit 5 扩展），提供与旧 `MiniClusterResource` 等价的"启动 MiniCluster + 关闭 classloader 泄漏检查"能力。新建抽象类 `TestBase`，对齐旧 `FlinkTestBase` 的能力：启动/停止内嵌 Hive Metastore、加载 HiveCatalog、提供 `TableEnvironment`、`exec`/`sql` 工具方法、`assertSameElements` 断言、`dropCatalog` 辅助。差异点：用 `@RegisterExtension` 替代 `@ClassRule`、用 `@TempDir` 替代 `TemporaryFolder`、用 Jupiter 的 `@BeforeAll`/`@AfterAll` 替代 JUnit 4 同名注解、用 AssertJ 替代 JUnit 4 `Assert`。然后把 `TestCatalogTableLoader` 改为继承新 `TestBase` 并切换全部注解与断言。

## 修改详情

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/MiniFlinkClusterExtension.java`（新增）

**修改目的**：提供 JUnit 5 版本的 MiniCluster 启动扩展，对应旧 `MiniClusterResource`。

**工作逻辑**：
- `DEFAULT_TM_NUM = 1`、`DEFAULT_PARALLELISM = 4`。
- `DISABLE_CLASSLOADER_CHECK_CONFIG`：`Configuration` 中 `set(CoreOptions.CHECK_LEAKED_CLASSLOADER, false)`。注释说明：Iceberg 集成测试在 job 结束后会断言结果，可能访问已被 TM 关闭的 classloader，因此禁用 classloader 泄漏检查以免误报。
- `createWithClassloaderCheckDisabled()`：返回 `new MiniClusterExtension(new MiniClusterResourceConfiguration.Builder().setNumberTaskManagers(DEFAULT_TM_NUM).setNumberSlotsPerTaskManager(DEFAULT_PARALLELISM).setConfiguration(DISABLE_CLASSLOADER_CHECK_CONFIG).build())`。注意这里用的是 Flink JUnit 5 的 `org.apache.flink.test.junit5.MiniClusterExtension`，而非旧的 `org.apache.flink.test.util.MiniClusterWithClientResource`。
- 构造函数私有，仅作为静态工厂。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestBase.java`（新增）

**修改目的**：JUnit 5 版本的 Flink 测试基类，对齐旧 `FlinkTestBase` 能力。

**工作逻辑**：
- `abstract class TestBase extends TestBaseUtils`（仍复用 Flink 的 `TestBaseUtils`）。
- `@RegisterExtension public static MiniClusterExtension miniClusterResource = MiniFlinkClusterExtension.createWithClassloaderCheckDisabled();`——JUnit 5 扩展注册方式，对应旧 `@ClassRule`。
- `@TempDir Path temporaryDirectory;`——JUnit 5 内建临时目录扩展，对应旧 `@ClassRule TemporaryFolder`。
- `@BeforeAll static startMetastore()`：启动 `TestHiveMetastore`，设置 `hiveConf`，通过 `CatalogUtil.loadCatalog` 加载 `HiveCatalog`。与旧 `FlinkTestBase` 等价。
- `@AfterAll static stopMetastore()`：停止 metastore、置空 catalog。
- `getTableEnv()`：双重检查锁懒加载 `TableEnvironment`，batch 模式，关闭 `TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM`。
- `exec(env, query, args...)` / `exec(query, args...)`：执行 SQL。
- `sql(query, args...)`：执行 SQL 并 `collect()` 收集为 `List<Row>`。
- `assertSameElements(expected, actual)` / `assertSameElements(message, expected, actual)`：基于 AssertJ `containsExactlyInAnyOrderElementsOf`。
- `dropCatalog(catalogName, ifExists)`：先 `USE CATALOG default_catalog` 再 `DROP CATALOG`——注释解释 FLINK-29677 后不能 drop 当前在用 catalog，故先切回默认。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestCatalogTableLoader.java`

**修改目的**：作为首个从 JUnit 4 迁移到 JUnit 5 的测试样例，验证新基类可用。

**工作逻辑**：
- 父类由 `FlinkTestBase` 改为 `TestBase`。
- import 切换：`org.junit.AfterClass`/`BeforeClass`/`Test`/`Assert` → `org.junit.jupiter.api.AfterAll`/`BeforeAll`/`Test` + `org.assertj.core.api.Assertions`。
- `@BeforeClass` → `@BeforeAll`，`@AfterClass` → `@AfterAll`。
- 断言切换：
  - `Assert.assertTrue(warehouse.delete())` → `Assertions.assertThat(warehouse.delete()).isTrue()`。
  - `Assert.assertTrue("Failed to delete " + warehousePath, fs.delete(...))` → `Assertions.assertThat(fs.delete(...)).as("Failed to delete " + warehousePath).isTrue()`。
  - `Assert.assertEquals("my_value", hadoopIO.conf().get("my_key"))` → `Assertions.assertThat(hadoopIO.conf().get("my_key")).isEqualTo("my_value")`。
- 业务逻辑（创建 warehouse、加载 TableLoader、校验 FileIO 类型与配置）不变。

## 小结

新建 JUnit 5 版本的 Flink 测试基类（`TestBase` + `MiniFlinkClusterExtension`）并完成首个测试 `TestCatalogTableLoader` 的迁移，为 Iceberg Flink 测试套件向 JUnit 5 平稳演进奠定基础。
