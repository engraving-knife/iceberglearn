# 提交 0961：Flink: Migrate remaining classes to JUnit5 (#10684)

## 提交信息

- **序号**：0961 / 4088
- **哈希**：fae8f6e733e475099f54675e7d73eac3718cbc2f
- **短哈希**：fae8f6e73
- **日期**：2024-07-22（Mon Jul 22 17:25:00 2024 +0900）
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Flink: Migrate remaining classes to JUnit5 (#10684)
- **PR/Issue**：#10684

## 总体目的

Iceberg 的 Flink v1.19 测试模块此前正在逐步从 JUnit 4 迁移到 JUnit 5。本提交标题中的"remaining classes"表明此前已有若干测试类完成迁移（并已引入 JUnit 5 版本的 `HadoopCatalogExtension`、`HadoopTableExtension`、`MiniFlinkClusterExtension` 等扩展类），本提交负责将 Flink v1.19 模块中**剩余的**仍基于 JUnit 4 的测试类统一迁移到 JUnit 5，从而完成该模块的 JUnit 5 化，消除 JUnit 4 与 JUnit 5 混用带来的依赖混乱、测试生命周期不一致以及规则（Rule）与扩展（Extension）机制无法互通等问题。

迁移的动机包括：

1. JUnit 4 已停止演进，JUnit 5 是社区主流方向，Flink 上游自身也在推进 JUnit 5 化，Iceberg 跟进可减少与 Flink 测试基础设施的差异；
2. JUnit 4 的 `@Rule`/`@ClassRule`（基于 `ExternalResource`）与 JUnit 5 的 `@RegisterExtension`/`@ExtendWith`（基于 `Extension` 接口）机制不互通，混用会导致测试资源初始化顺序难以预测；
3. JUnit 5 的参数化测试（`@TestTemplate` + 自定义 `Extension`）比 JUnit 4 的 `@RunWith(Parameterized.class)` + 构造器注入更灵活，可在同一类中组合多个扩展。

本提交还顺带删除了 JUnit 4 时代遗留的 `HadoopCatalogResource`、`HadoopTableResource` 两个 `ExternalResource` 实现（其 JUnit 5 对应物 `HadoopCatalogExtension`、`HadoopTableExtension` 已在先前提交中引入），并在 `MiniFlinkClusterExtension` 中新增了一个支持 `InMemoryReporter` 的工厂方法，以替代旧的 `MiniClusterResource.createWithClassloaderCheckDisabled`。

## 如何达成设计目的

整体设计思路是机械式地、按一套固定映射规则将每个 JUnit 4 测试类改写为 JUnit 5 等价形式，同时将断言从 JUnit 4 的 `org.junit.Assert.*` 切换到 AssertJ 的 `assertThat(...)` 流式断言（Iceberg 测试统一采用 AssertJ）。核心映射规则如下：

- `@RunWith(Parameterized.class)` + 构造器注入参数 → `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameter` 字段注入 + `@Parameters` 提供 `Object[][]`（注意从 `Object[]` 改为 `Object[][]`，且每个元素用 `new Object[]{...}` 包裹）；测试方法 `@Test` → `@TestTemplate`；
- `@Rule public TemporaryFolder` → `@TempDir java.nio.file.Path`，临时目录创建从 `tempFolder.newFolder()` 改为 `Files.createTempDirectory(temporaryFolder, "junit").toFile()`；
- `@Rule/@ClassRule` 基于 `ExternalResource` 的资源 → `@RegisterExtension` 基于 JUnit 5 `Extension` 的扩展（`HadoopCatalogResource` → `HadoopCatalogExtension`，`HadoopTableResource` → `HadoopTableExtension`，`MiniClusterWithClientResource` → `MiniClusterExtension`）；
- `@Before` → `@BeforeEach`；
- `@Rule public Timeout` → `@Timeout`（类级别）；
- `org.junit.Assert.assertEquals/assertNull/assertNotNull/assertArrayEquals/assertTrue` → AssertJ `assertThat(...).isEqualTo/hasSize/isEmpty/isNull/isNotNull/containsExactly/containsExactlyInAnyOrderElementsOf/isTrue()` 等；
- 参数化测试的参数从字符串字面量改为直接使用枚举/类型常量（如 `"avro"` → `FileFormat.AVRO`），避免在构造器中做字符串到枚举的转换；
- 对于 failover 类测试，原先通过 `@Rule MiniClusterWithClientResource` 复用同一集群；迁移后由于 failover 测试需要每个测试方法独立的新集群（避免状态污染），引入 `runTestWithNewMiniCluster` 工具方法手动创建/销毁 `MiniClusterWithClientResource`，并将 `MiniCluster` 实例作为参数传入测试逻辑；
- 对于需要 `ClusterClient` 的测试方法（如 savepoint 测试），使用 Flink JUnit 5 提供的 `@InjectClusterClient` 参数注入；对于需要 `MiniCluster` 的方法，使用 `@InjectMiniCluster` 注入。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/MiniFlinkClusterExtension.java`

**修改目的**：新增一个支持 `InMemoryReporter` 的 `MiniClusterExtension` 工厂方法，替代旧的 `MiniClusterResource.createWithClassloaderCheckDisabled(InMemoryReporter)`。

**工作逻辑**：新增静态方法 `createWithClassloaderCheckDisabled(InMemoryReporter inMemoryReporter)`，该方法基于已有的 `DISABLE_CLASSLOADER_CHECK_CONFIG` 配置创建一个新的 `Configuration`，通过 `inMemoryReporter.addToConfiguration(configuration)` 将指标 reporter 注入配置，再据此构建 `MiniClusterResourceConfiguration` 并返回新的 `MiniClusterExtension`。这样 `TestIcebergSourceContinuous` 等需要指标断言的测试可在 JUnit 5 下复用该工厂方法。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/HadoopCatalogResource.java`（删除）

**修改目的**：删除 JUnit 4 版本的 `HadoopCatalogResource`（继承 `ExternalResource`），其功能已由先前提交引入的 JUnit 5 版 `HadoopCatalogExtension` 取代。

**工作逻辑**：该类原通过 `before()`/`after()` 管理临时 warehouse、`CatalogLoader`、`TableLoader` 与 `Catalog` 的创建与关闭。删除后，所有引用方改用 `HadoopCatalogExtension`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/HadoopTableResource.java`（删除）

**修改目的**：删除 JUnit 4 版本的 `HadoopTableResource`（继承 `HadoopCatalogResource`），其功能已由 JUnit 5 版 `HadoopTableExtension` 取代。

**工作逻辑**：该类原在 `before()` 中调用父类创建 catalog 后再 `catalog.createTable(...)` 创建表并打开 `tableLoader`。删除后引用方改用 `HadoopTableExtension`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/SimpleDataUtil.java`

**修改目的**：将工具类中的 JUnit 4 `Assert.*` 断言切换为 AssertJ `assertThat(...)`。

**工作逻辑**：`assertRecordsEqual` 中 `Assert.assertEquals(expected.size(), actual.size())` → `assertThat(actual).hasSameSizeAs(expected)`，`Assert.assertEquals(expectedSet, actualSet)` → `assertThat(actualSet).containsExactlyInAnyOrderElementsOf(expectedSet)`；`assertTableRecords` 中空列表断言改为 `assertThat(expected).isEmpty()`，记录比较改为 `containsExactlyInAnyOrderElementsOf`。这些断言语义与原逻辑等价，但 AssertJ 的可读性与失败信息更友好。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFixtures.java`

**修改目的**：新增 `SINK_TABLE_IDENTIFIER` 常量，供 failover 测试在 `setupTable` 中创建 sink 表使用。

**工作逻辑**：新增 `public static final TableIdentifier SINK_TABLE_IDENTIFIER = TableIdentifier.of(DATABASE, SINK_TABLE);`，避免各测试类重复构造。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestCompressionSettings.java`

**修改目的**：将参数化测试从 JUnit 4 `@RunWith(Parameterized.class)` 迁移到 JUnit 5 `@ExtendWith(ParameterizedTestExtension.class)` + `@TestTemplate`，断言切换为 AssertJ，临时目录改为 `@TempDir`。

**工作逻辑**：参数从构造器注入改为 `@Parameter(index=0)` 字段注入；`@Parameters` 返回类型从 `Object[]` 改为 `Object[][]`，每个参数用 `new Object[]{...}` 包裹；`@Before` → `@BeforeEach`；`@Test` → `@TestTemplate`；`tempFolder.newFolder()` → `Files.createTempDirectory(temporaryFolder, "junit").toFile()`；所有 `Assert.assertEquals` 改为 `assertThat(resultProperties).containsEntry(...).doesNotContainKey(...)` 链式断言。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkManifest.java`

**修改目的**：将普通（非参数化）测试类从 JUnit 4 迁移到 JUnit 5，断言切换为 AssertJ。

**工作逻辑**：`@Rule public TemporaryFolder` → `@TempDir Path`；`@Before` → `@BeforeEach`；`@Test` 保留（JUnit 5 的 `org.junit.jupiter.api.Test`）；`Assert.assertTrue/assertEquals/assertNotNull/assertNull/assertArrayEquals` 全部改为 AssertJ 等价形式，如 `assertThat(new File(tablePath).mkdir()).isTrue()`、`assertThat(result.deleteFiles()).hasSize(10)`、`assertThat(delta.deleteManifest()).isNull()`、`assertThat(versionedSerializeData2).containsExactly(versionedSerializeData)` 等。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergStreamWriter.java`

**修改目的**：将参数化测试迁移到 JUnit 5，参数从字符串改为 `FileFormat` 枚举直接注入。

**工作逻辑**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；构造器注入改为 `@Parameter(index=0)` 与 `@Parameter(index=1)` 字段注入；`@Parameters` 中参数从 `{"avro", true}` 等字符串改为 `{FileFormat.AVRO, true}` 等枚举，移除构造器中 `FileFormat.fromString(format)` 转换；`@Before` → `@BeforeEach`，`@Test` → `@TestTemplate`；`tempFolder.newFolder()` → `Files.createTempDirectory(temporaryFolder, "junit").toFile()`；断言切换 AssertJ。同时将 `long expectedDataFiles` 改为 `int` 以匹配 `result.dataFiles().length` 的 `int` 类型。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestRowDataPartitionKey.java`

**修改目的**：将普通测试类迁移到 JUnit 5，断言切换 AssertJ。

**工作逻辑**：`org.junit.Test` → `org.junit.jupiter.api.Test`；移除 `org.junit.Assert` 导入，新增 `assertThat` 静态导入；多处 `Assert.assertEquals` 改为 `assertThat(...).isEqualTo(...)`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestTaskWriters.java`

**修改目的**：将参数化测试迁移到 JUnit 5，断言切换 AssertJ。

**工作逻辑**：与 `TestIcebergStreamWriter` 相同的迁移模式：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`，构造器注入改为 `@Parameter` 字段注入，`@Before`→`@BeforeEach`，`@Test`→`@TestTemplate`，`@Rule TemporaryFolder`→`@TempDir Path`，断言切换 AssertJ。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestAggregatedStatisticsTracker.java`

**修改目的**：将 `org.junit.Test` 导入替换为 `org.junit.jupiter.api.Test`，使该类与 JUnit 5 一致。

**工作逻辑**：该类已使用 JUnit 5 的 `@ParameterizedTest` + `@EnumSource`，仅保留了一个旧的 `org.junit.Test` 导入。本提交将该导入改为 `org.junit.jupiter.api.Test`，完成纯导入修正。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBoundedGenericRecord.java`

**修改目的**：将参数化测试迁移到 JUnit 5，使用 `HadoopCatalogExtension` 替代 `HadoopCatalogResource`，使用 `MiniFlinkClusterExtension` 替代 `MiniClusterResource`。

**工作逻辑**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；`@ClassRule MiniClusterWithClientResource` → 移除（该测试实际不需要 mini cluster 扩展，仅用 `@TempDir` + `@RegisterExtension HadoopCatalogExtension`）；`@Parameter` 字段注入替代构造器；参数从字符串改为 `FileFormat` 枚举；`@Test` → `@TestTemplate`；`TEMPORARY_FOLDER` → `temporaryFolder`（`@TempDir Path`）；`catalogResource` → `CATALOG_EXTENSION`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceContinuous.java`

**修改目的**：迁移到 JUnit 5，使用 `MiniFlinkClusterExtension.createWithClassloaderCheckDisabled(InMemoryReporter)` 工厂方法替代旧的 `MiniClusterResource.createWithClassloaderCheckDisabled`，使用 `HadoopTableExtension` 替代 `HadoopTableResource`。

**工作逻辑**：`@ClassRule MiniClusterWithClientResource` → `@RegisterExtension MiniClusterExtension`（通过新工厂方法创建）；`@ClassRule TemporaryFolder` → `@TempDir Path`；`@Rule HadoopTableResource` → `@RegisterExtension HadoopTableExtension`；`tableResource.table()` → `TABLE_EXTENSION.table()`；`Assert.assertEquals` → `assertThat(...).hasSize/isEqualTo`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java`

**修改目的**：迁移到 JUnit 5，重构 failover 测试使每个测试方法使用独立的 mini cluster，引入 `runTestWithNewMiniCluster` 工具方法。

**工作逻辑**：这是本提交中改动最大的文件。关键改动：

- `@ClassRule TemporaryFolder` → `@TempDir Path`；
- `@Rule MiniClusterWithClientResource` → `@RegisterExtension static MiniClusterExtension MINI_CLUSTER_EXTENSION`（作为共享集群，用于 savepoint 测试）；
- `@Rule HadoopTableResource`（source/sink 各一个）→ `@RegisterExtension static HadoopCatalogExtension`（SOURCE_CATALOG_EXTENSION / SINK_CATALOG_EXTENSION）；
- 新增 `@BeforeEach setupTable()` 方法，在 `@BeforeEach` 中通过 catalog 扩展创建 source/sink 表（原先由 `HadoopTableResource.before()` 自动完成）；
- `@Rule Timeout` → 类级 `@Timeout(value = 120)`；
- `testBoundedWithSavepoint` 方法增加 `@InjectClusterClient ClusterClient<?>` 参数注入，替代 `miniClusterResource.getClusterClient()`；savepoint 路径从 `TEMPORARY_FOLDER.newFolder().toPath().toString()` 改为 `temporaryFolder.toString()`；
- failover 测试方法（`testBoundedWithTaskManagerFailover` 等）改为通过 `runTestWithNewMiniCluster(miniCluster -> testBoundedIcebergSource(FailoverType.TM, miniCluster))` 调用，使每个测试获得独立集群；
- `testBoundedIcebergSource`/`testContinuousIcebergSource` 增加 `MiniCluster miniCluster` 参数，替代从 `miniClusterResource.getMiniCluster()` 获取；
- 新增私有静态方法 `runTestWithNewMiniCluster(ThrowingConsumer<MiniCluster, Exception> testMethod)`，手动 `new MiniClusterWithClientResource(MINI_CLUSTER_RESOURCE_CONFIG)`、调用 `before()`/`after()` 管理生命周期，并在 finally 中清理。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailoverWithWatermarkExtractor.java`

**修改目的**：该类继承 `TestIcebergSourceFailover`，需覆写 `setupTable()` 以使用 `TS_SCHEMA` 创建表，并更新 `sourceBuilder()` 中对 `sourceTableResource` 的引用。

**工作逻辑**：新增 `@Override @BeforeEach setupTable()`，使用 `SOURCE_CATALOG_EXTENSION.catalog().createTable(TestFixtures.TABLE_IDENTIFIER, TestFixtures.TS_SCHEMA)` 创建带时间戳 schema 的表；`sourceBuilder()` 中 `sourceTableResource.tableLoader()` → `SOURCE_CATALOG_EXTENSION.tableLoader()`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java`

**修改目的**：迁移到 JUnit 5，使用 `@InjectMiniCluster` 注入 `MiniCluster`，使用 `HadoopTableExtension` 替代 `HadoopTableResource`。

**工作逻辑**：`@ClassRule MiniClusterWithClientResource` → `@RegisterExtension MiniClusterExtension`；`@Rule HadoopTableResource` → `@RegisterExtension HadoopTableExtension`；`@ClassRule TemporaryFolder` → `@TempDir Path`；需要 `MiniCluster` 的测试方法增加 `@InjectMiniCluster MiniCluster miniCluster` 参数注入；`tableResource` → `TABLE_EXTENSION`；`Assert.assertEquals` → AssertJ。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestProjectMetaColumn.java`

**修改目的**：将参数化测试迁移到 JUnit 5，断言切换 AssertJ。

**工作逻辑**：与其它参数化测试相同的迁移模式：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`，构造器注入 → `@Parameter` 字段注入，`@Test` → `@TestTemplate`，`@Rule TemporaryFolder` → `@TempDir Path`，断言切换 AssertJ。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestSourceUtil.java`

**修改目的**：将普通测试类迁移到 JUnit 5，断言切换 AssertJ。

**工作逻辑**：`org.junit.Test` → `org.junit.jupiter.api.Test`；`Assert.assertEquals` → `assertThat(parallelism).isEqualTo(...)`。

## 小结

- **成效**：完成 Flink v1.19 模块剩余测试类从 JUnit 4 到 JUnit 5 的迁移，消除了 JUnit 4/5 混用，统一了测试扩展机制（`@RegisterExtension`/`@ExtendWith`）、断言库（AssertJ）、临时目录管理（`@TempDir`）和参数化测试方式（`@TestTemplate` + `ParameterizedTestExtension`）。同时清理了不再需要的 JUnit 4 `HadoopCatalogResource`/`HadoopTableResource`，并在 `MiniFlinkClusterExtension` 中补充了支持 `InMemoryReporter` 的工厂方法。共涉及 18 个文件，+569/-658 行。
- **影响范围**：仅影响 `flink/v1.19/flink/src/test/` 下的测试代码，不涉及任何生产代码。具体包括：2 个测试基础设施类（`MiniFlinkClusterExtension` 新增方法、`TestFixtures` 新增常量）、1 个测试工具类（`SimpleDataUtil` 断言切换）、2 个删除的 JUnit 4 资源类、13 个测试类的迁移。
- **回迁到 1.4.x 的注意事项**：**需谨慎评估是否回迁**。本提交是 Flink v1.19 模块专属的测试迁移，前提是该模块已存在 JUnit 5 版本的扩展类（`HadoopCatalogExtension`、`HadoopTableExtension`）以及 `ParameterizedTestExtension`/`Parameter`/`Parameters` 等基础设施。回迁到 1.4.x 前需确认：1.4.x 的 Flink v1.19 模块是否已引入这些 JUnit 5 基础设施（若未引入，需先回迁引入这些基础设施的先前提交，否则本提交无法编译）；Flink v1.19 依赖的 `flink-test-utils-junit5`（提供 `MiniClusterExtension`、`@InjectClusterClient`、`@InjectMiniCluster`）版本是否在 1.4.x 中可用；1.4.x 是否维护 Flink v1.19 模块（若 1.4.x 不包含该模块，本提交无需回迁）。由于纯测试改动，回迁风险主要在编译期与测试期兼容性，不影响生产功能。建议仅当 1.4.x 的 Flink v1.19 测试模块确实需要统一到 JUnit 5 时才回迁，并连同先前引入 JUnit 5 基础设施的提交一起评估。
