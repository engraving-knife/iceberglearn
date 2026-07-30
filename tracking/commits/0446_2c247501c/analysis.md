# 提交 0446：Flink: backport #9381 to 1.17 and 1.16 for Migrate subclasses of FlinkCatalogTestBase to JUnit5 (#9598)

## 提交信息

- **序号**：0446
- **完整哈希**：2c247501c4700ebd3c3e1349aca2ff8c9ee840c9
- **短哈希**：2c247501c
- **日期**：2024-02-02 09:39:37 +0100
- **作者**：Rodrigo <rmenesespinillos@apple.com>
- **提交说明**：Flink: backport #9381 to 1.17 and 1.16 for Migrate subclasses of FlinkCatalogTestBase to JUnit5 (#9598)
- **关联 PR**：#9598（本提交）、#9381（被回 port 的原始 PR）

## 总体目的

本提交是 Iceberg 主分支上 PR #9381 的回 port，目的是把 Flink 1.17 和 1.16 两个版本目录下的 FlinkCatalog 测试子类从 JUnit 4 迁移到 JUnit 5。原始 PR #9381 已经把公共的 JUnit 5 基类 `CatalogTestBase` 引入仓库（它在 `flink/v1.17` 与 `flink/v1.16` 下早已存在，使用 `@ExtendWith(ParameterizedTestExtension.class)` 和 Iceberg 自定义的 `@Parameter`/`@Parameters`/`ParameterizedTestExtension` 体系），并把旧的 JUnit 4 基类 `FlinkCatalogTestBase` 标记为待替换。本提交完成最后一步：删除 `FlinkCatalogTestBase.java`，把所有派生测试类改为继承 `CatalogTestBase`，并替换掉 JUnit 4 风格的注解、断言和参数化机制。

完成迁移后，Flink 模块在 1.16、1.17、1.18 三个版本的测试栈统一运行在 JUnit 5 之上，可以移除对 JUnit 4 `junit-vintage-engine` 的依赖，避免新旧两套生命周期注解混用导致的初始化顺序、参数注入、规则（Rule）与扩展（Extension）相互冲突等隐患。迁移还顺带把断言从 `org.junit.Assert.*` 系列改为 AssertJ 流式断言（`assertThat(...).isEqualTo(...)`），把 `Assume.assumeFalse` 改为 AssertJ 的 `assumeThat(...).isFalse()`，使错误信息和可读性更好。

此外，本提交修改了 `gradle.properties` 中的 `systemProp.defaultFlinkVersions`，从 `1.18` 扩展为 `1.16,1.17,1.18`。这样在默认构建（不显式指定 `-PflinkVersions=...`）时，会同时为三个 Flink 版本运行测试，便于在迁移完成后立刻验证三个版本目录的行为是否一致，避免只在 1.18 上验证而遗漏 1.16/1.17 的回归。

## 如何达成设计目的

实现路径是"替换基类 + 批量改写测试代码"。第一步，删除 `FlinkCatalogTestBase.java`（1.17 与 1.16 各一份，共 155 行），它的全部职责（warehouse 创建/销毁、`@Before` 建 catalog、`@After` 删 catalog、`parameters()`、`getURI()`、`toWithClause()` 等）都已被 `CatalogTestBase` 以 JUnit 5 方式重新实现。第二步，逐个把 `extends FlinkCatalogTestBase` 改为 `extends CatalogTestBase`，并把 JUnit 4 的参数化写法（构造函数 + `@Parameterized.Parameters` + `@RunWith(Parameterized.class)`）改为 Iceberg 自定义的 `@Parameter(index=N)` 字段注入 + `@Parameters` 工厂方法 + `@TestTemplate`。第三步，把生命周期注解 `@Before/@After/@Test` 改为 `@BeforeEach/@AfterEach/@TestTemplate`，把 `Assert.*` 与 `Assume.*` 改为 AssertJ 断言，把 `@ClassRule MiniClusterWithClientResource`/`TemporaryFolder` 删掉（改由 `TestBase` 中的 `@RegisterExtension MiniClusterExtension` 与 `@TempDir` 统一提供）。最后调整 `gradle.properties` 让三个版本默认都跑测试。

## 修改详情

### `gradle.properties`
**修改目的**：让默认构建同时覆盖三个 Flink 版本，确保回 port 后 1.16/1.17/1.18 都被验证。
**工作逻辑**：把 `systemProp.defaultFlinkVersions` 由 `1.18` 改为 `1.16,1.17,1.18`，`knownFlinkVersions=1.16,1.17,1.18` 保持不变。这是迁移期间的临时设置，目的是在合并前一次性回归三个版本目录。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/FlinkCatalogTestBase.java`（删除）
**修改目的**：移除 JUnit 4 时代的公共参数化测试基类。
**工作逻辑**：该类原本用 `@RunWith(Parameterized.class)` + 构造函数接收 `catalogName`/`baseNamespace`，提供 `@BeforeClass/@AfterClass` 管理 warehouse、`@Before/@After` 建/删 catalog，以及 `parameters()`、`getURI(HiveConf)`、`toWithClause(Map)`、`warehouseRoot()`、`getFullQualifiedTableName(String)` 等静态/实例辅助方法。这些职责全部已由 `CatalogTestBase`（JUnit 5 版）承担，因此整个文件被删除。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestBase.java`
**修改目的**：放宽 `@TempDir` 字段的可见性，让子类（如 `TestFlinkMetaDataTable`）能直接使用临时目录。
**工作逻辑**：把 `@TempDir Path temporaryDirectory;` 改为 `@TempDir protected Path temporaryDirectory;`。迁移后部分测试不再使用自己的 `TemporaryFolder` `@ClassRule`，而是复用基类的 `@TempDir`，因此需要把可见性从包级提升到 `protected`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java`
**修改目的**：把 Catalog 表级测试迁到 `CatalogTestBase` + JUnit 5。
**工作逻辑**：去掉构造函数和 `import org.apache.iceberg.catalog.Namespace`，改为 `extends CatalogTestBase`。`@Before/@After/@Test` 分别改为 `@BeforeEach/@AfterEach/@TestTemplate`。所有 `Assert.assertEquals(msg, expected, actual)` 改写为 AssertJ `assertThat(actual).as(msg).isEqualTo(expected)`，`Assume.assumeFalse(msg, cond)` 改为 `assumeThat(cond).as(msg).isFalse()`。新增 `assertThatThrownBy`、`assertThat`、`assumeThat` 的静态导入。测试逻辑（建库、建表、改名、增删列、format-version 升降级、分区等）保持不变，仅断言风格与生命周期注解改变。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTablePartitions.java`
**修改目的**：跟随基类迁移，更新静态工厂方法引用。
**工作逻辑**：把 `FlinkCatalogTestBase.parameters()` 调用改为 `CatalogTestBase.parameters()`，因为旧基类已被删除。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestFlinkHiveCatalog.java`
**修改目的**：把对旧基类静态方法的引用替换为新基类。
**工作逻辑**：两处 `FlinkCatalogTestBase.getURI(hiveConf)` 改为 `CatalogTestBase.getURI(hiveConf)`；一处 `FlinkCatalogTestBase.toWithClause(catalogProperties)` 改为 `CatalogTestBase.toWithClause(catalogProperties)`，并顺手把 `sql("CREATE CATALOG ...")` 的多行写法合并成一行。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestIcebergConnector.java`
**修改目的**：更新对旧基类静态方法的引用。
**工作逻辑**：三处 `FlinkCatalogTestBase.getURI(hiveConf)` 改为 `CatalogTestBase.getURI(hiveConf)`；私有 `toWithClause` 内部委托由 `FlinkCatalogTestBase.toWithClause` 改为 `CatalogTestBase.toWithClause`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSink.java`
**修改目的**：把 Sink 测试迁移到 JUnit 5 字段注入式参数化。
**工作逻辑**：删除 `@RunWith(Parameterized.class)`、`@ClassRule MiniClusterWithClientResource`、`@ClassRule TemporaryFolder`，以及接收 `catalogName/baseNamespace/format/isStreamingJob` 的构造函数。改用 `@Parameter(index=2) FileFormat format` 与 `@Parameter(index=3) boolean isStreamingJob` 字段注入（前两个 index 由 `CatalogTestBase` 接收）。`@Parameterized.Parameters` 改为 `@Parameters` 并返回 `List<Object[]>`；内层 `FlinkCatalogTestBase.parameters()` 改为 `CatalogTestBase.parameters()`。生命周期与断言迁移同上，`Assume.assumeFalse` 改 `assumeThat(...).isFalse()`，`Assert.assertEquals` 改 AssertJ，`Sets.newHashSet` 比较改为 `containsExactlyInAnyOrderElementsOf`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestFlinkUpsert.java`
**修改目的**：把 Upsert 测试迁移到 JUnit 5 字段注入式参数化。
**工作逻辑**：删除 `@RunWith`、两个 `@ClassRule` 与构造函数；`format`/`isStreamingJob` 改为 `@Parameter(index=2/3)` 字段。`@Parameterized.Parameters` 改为 `@Parameters`，`FlinkCatalogTestBase.parameters()` 改为 `CatalogTestBase.parameters()`。`@Before/@After/@Test` 改为 `@BeforeEach/@AfterEach/@TestTemplate`。原构造函数中初始化 `tableUpsertProps` 的逻辑需要迁移到 `before()` 中（字段注入在构造函数前不可用），这是迁移的典型坑点之一。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestIcebergConnector.java`
（已在上面单独说明，主要替换静态方法引用。）

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/actions/TestRewriteDataFilesAction.java`
**修改目的**：把 RewriteDataFiles Action 测试迁移到 JUnit 5。
**工作逻辑**：`extends FlinkCatalogTestBase` 改为 `extends CatalogTestBase`，import 由 `org.apache.iceberg.flink.FlinkCatalogTestBase` 改为 `CatalogTestBase`。删除构造函数，`format` 改为 `@Parameter(index=2) FileFormat format`。`@Parameterized.Parameters` 改为 `@Parameters` 返回 `List<Object[]>`，内层 `FlinkCatalogTestBase.parameters()` 改为 `CatalogTestBase.parameters()`。`@Before/@After/@Test` 改为 `@BeforeEach/@AfterEach/@TestTemplate`；`@Rule TemporaryFolder` 改为 `@TempDir Path`/`File`；`Assert.*` 改为 AssertJ。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkMetaDataTable.java`
**修改目的**：把元数据表测试迁移到 JUnit 5，文件改动量最大（454 行变动）。
**工作逻辑**：`extends FlinkCatalogTestBase` 改为 `extends CatalogTestBase`；删除构造函数，`isPartition` 改为 `@Parameter(index=2) Boolean isPartition`；`@Parameterized.Parameters` 改为 `@Parameters`（注意此处把可见性从 `public` 调整为 `protected`，因为 `TestFlinkMetaDataTable` 与其子类共享参数工厂）。`static final TemporaryFolder TEMP` 改为 `@TempDir Path temp`。`@Before/@After/@Test` 改为 `@BeforeEach/@AfterEach/@TestTemplate`；大量 `Assert.assertEquals/Assert.assertTrue/Assume.assumeFalse` 改写为 AssertJ 断言（`assertThat(...).isEqualTo(...)`、`assumeThat(...).isFalse()`）。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java`
**修改目的**：把流式扫描 SQL 测试迁移到 JUnit 5。
**工作逻辑**：基类与参数化迁移模式与上述文件一致；`@Test` 改为 `@TestTemplate`，断言与生命周期注解按统一规则替换。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/*`（1.16 版本目录下相同一组文件）
**修改目的**：对 Flink 1.16 版本目录做完全相同的迁移，保证 1.16 与 1.17 行为一致。
**工作逻辑**：1.16 目录下的 `FlinkCatalogTestBase.java`（删除）、`TestBase.java`、`TestFlinkCatalogTable.java`、`TestFlinkCatalogTablePartitions.java`、`TestFlinkHiveCatalog.java`、`TestFlinkTableSink.java`、`TestFlinkUpsert.java`、`TestIcebergConnector.java`、`actions/TestRewriteDataFilesAction.java`、`source/TestFlinkMetaDataTable.java`、`source/TestStreamScanSql.java` 与 1.17 的改动一一对应，仅文件路径前缀不同（`flink/v1.16/...`）。这也是 `git show --stat` 中每个文件出现两次、改动量翻倍的原因。

## 小结

本提交是 Iceberg Flink 模块测试基础设施的一次大范围 JUnit 4 → JUnit 5 迁移收尾，覆盖 1.16 与 1.17 两个版本目录共 22 个文件、1105 行新增、1622 行删除。核心动作是删除旧基类 `FlinkCatalogTestBase`，让所有 catalog 派生测试改为继承已经 JUnit 5 化的 `CatalogTestBase`，并把参数化机制从 JUnit 4 构造函数注入改为 Iceberg 自定义的 `@Parameter` 字段注入 + `@TestTemplate`。同时统一了断言库（AssertJ）、临时目录机制（`@TempDir`）与默认构建覆盖的 Flink 版本（1.16/1.17/1.18）。该迁移为后续彻底移除 JUnit 4 依赖、统一测试栈奠定了基础，是 Iceberg 测试体系现代化的重要一步。
