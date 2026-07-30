# 提交 0966：Flink 1.17, 1.18: Migrate remaining tests to JUnit5 (#10749)

## 提交信息

- **序号**：0966 / 4088
- **哈希**：f7b3906003cf081c0ec29942582e21d014d8401c
- **短哈希**：f7b390600
- **日期**：2024-07-23 14:35:22 +0200
- **作者**：Tom Tanaka
- **提交说明**：Flink 1.17, 1.18: Migrate remaining tests to JUnit5 (#10749)
- **PR/Issue**：#10749

## 总体目的

Iceberg 的 Flink 集成模块按 Flink 版本分目录维护（`flink/v1.17`、`flink/v1.18`、`flink/v1.19`）。Flink 自 1.17 起官方推荐使用 JUnit5，Iceberg 在 1.19 上已完成大部分测试的 JUnit5 迁移，但 1.17 与 1.18 目录中还遗留了一批 JUnit4 测试，包括 sink/source/shuffle/data 等子模块。这种"1.19 用 JUnit5、1.17/1.18 用 JUnit4"的分裂状态带来了几个问题：

1. 同一份测试逻辑在三套目录里写法不同，跨版本同步改动时容易引入差异；
2. JUnit4 与 JUnit5 混用会引入两套测试依赖，增加构建复杂度；
3. AssertJ 在 JUnit5 下更顺手，而 JUnit4 风格的 `org.junit.Assert.assertEquals` 写法在错误信息上不如 AssertJ 友好。

本提交把 1.17 和 1.18 目录中剩余的 JUnit4 测试统一迁移到 JUnit5，同时把 1.17/1.18 自有的两个 JUnit4 风格测试资源类（`HadoopCatalogResource`、`HadoopTableResource`）替换为对应的 JUnit5 扩展（`HadoopCatalogExtension`、`HadoopTableExtension`，二者此前已在 1.19 引入）。迁移完成后，三套 Flink 目录的测试风格保持一致，可以彻底移除 JUnit4 依赖。

## 如何达成设计目的

整体思路是机械但系统地替换 JUnit4 的 API 与生命周期到 JUnit5 等价物，并把 AssertJ 的 `assertThat` 流式断言替换 `org.junit.Assert.*` 静态调用。常见的替换模式如下：

- `@Rule public TemporaryFolder tempFolder = new TemporaryFolder();` → `@TempDir protected Path temporaryFolder;`
- `@ClassRule public static final TemporaryFolder TEMPORARY_FOLDER` → 实例字段 `@TempDir Path temporaryFolder`
- `@Before` → `@BeforeEach`
- `@Test` 在普通测试中保留为 JUnit5 的 `@Test`；在参数化测试中改为 `@TestTemplate` 并配合 Iceberg 自带的 `@ExtendWith(ParameterizedTestExtension.class)` 与 `@Parameters`、`@Parameter`
- JUnit4 `@RunWith(Parameterized.class)` 与构造注入 → `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameter(index = 0)` 字段注入
- `@Rule public MiniClusterWithClientResource miniClusterResource` / `@ClassRule MiniClusterWithClientResource` → `@RegisterExtension static MiniClusterExtension`（Flink 的 JUnit5 扩展）
- `org.junit.Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)` 或更语义化的 `containsEntry`、`hasSameSizeAs`、`containsExactlyInAnyOrderElementsOf` 等
- `@Rule public Timeout` / `@ClassRule Timeout` → JUnit5 `@Timeout`
- JUnit4 的 `ExternalResource` 子类 `HadoopCatalogResource`/`HadoopTableResource` → JUnit5 的 `HadoopCatalogExtension`/`HadoopTableExtension`

对于 failover 测试这种需要"每个测试方法用独立 mini cluster"的场景，新增了 `runTestWithNewMiniCluster` 工具方法，在 try-finally 中显式调用 `MiniClusterWithClientResource.before()/after()` 创建临时集群，确保测试间状态隔离。

由于改动横跨 47 个文件，下面按文件类别归类说明。

## 修改详情

### `flink/v1.17` 与 `flink/v1.18` 下 `HadoopCatalogResource.java` 与 `HadoopTableResource.java`（删除）

**修改目的**：移除 JUnit4 风格的 `ExternalResource` 测试资源类，改用 1.19 中已有的 JUnit5 扩展。

**工作逻辑**：原 `HadoopCatalogResource extends ExternalResource` 通过 `before()` 创建临时 warehouse 与 catalog、`after()` 关闭 catalog 与 tableLoader；`HadoopTableResource extends HadoopCatalogResource` 在 `before()` 中额外调用 `catalog.createTable`。这两个类只能通过 `@Rule` 在 JUnit4 下使用，JUnit5 不兼容，因此删除，由同 PR 引入/复用的 `HadoopCatalogExtension`、`HadoopTableExtension`（已存在于仓库其他位置）取代。

### `flink/v1.17` 与 `flink/v1.18` 下 `MiniFlinkClusterExtension.java`

**修改目的**：补全 JUnit5 风格的 mini cluster 扩展工具方法。

**工作逻辑**：新增静态方法 `createWithClassloaderCheckDisabled(InMemoryReporter inMemoryReporter)`，把 reporter 添加到 `Configuration`，再以禁用 classloader check 的配置构建 `MiniClusterExtension`，便于 failover/continuous 测试以 `@RegisterExtension` 方式使用。

### `flink/v1.17` 与 `flink/v1.18` 下 `TestFixtures.java`

**修改目的**：补全 sink 测试需要的常量。

**工作逻辑**：新增 `SINK_TABLE_IDENTIFIER = TableIdentifier.of(DATABASE, SINK_TABLE)`，供 source 测试中向 sink 表写入结果时使用。

### `flink/v1.17` 与 `flink/v1.18` 下 `SimpleDataUtil.java`

**修改目的**：把断言改为 AssertJ 风格，与 1.19 保持一致。

**工作逻辑**：`assertRecordsEquals` 与 `assertRecords` 中 `Assert.assertEquals` → `assertThat(...).hasSameSizeAs / containsExactlyInAnyOrderElementsOf / isEmpty`。语义不变，错误信息更友好。

### `flink/v1.17` 与 `flink/v1.18` 下 `data/TestRowProjection.java` 与 `data/TestStructRowData.java`

**修改目的**：清理 JUnit4 残留并完成 AssertJ 迁移。

**工作逻辑**：`TestRowProjection` 删除未使用的 `Comparators` import 与冗余的 `int cmp = ...` 调用，断言改为 `assertThat(projected.getString(0)).asString().isEqualTo("test")`。`TestStructRowData` 微调。

### `flink/v1.17` 与 `flink/v1.18` 下 `sink/TestCompressionSettings.java`

**修改目的**：参数化测试迁移到 Iceberg 的 JUnit5 扩展。

**工作逻辑**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；构造注入 → `@Parameter(index = 0) private Map<String, String> initProperties;`；`@Parameterized.Parameters` → `@Parameters`，返回值由 `Object[]` 改为 `Object[][]`；`@Rule TemporaryFolder` → `@TempDir Path temporaryFolder`；`@Before` → `@BeforeEach`，临时目录由 `tempFolder.newFolder()` 改为 `Files.createTempDirectory(temporaryFolder, "junit").toFile()`；3 个 `@Test` → `@TestTemplate`；所有 `Assert.assertEquals` 改为 `assertThat(resultProperties).containsEntry(...).doesNotContainKey(...)`，原本隐式断言"key 不存在"的场景显式表达为 `doesNotContainKey`，更准确。

### `flink/v1.17` 与 `flink/v1.18` 下 `sink/TestFlinkManifest.java`、`sink/TestIcebergStreamWriter.java`、`sink/TestRowDataPartitionKey.java`、`sink/TestTaskWriters.java`

**修改目的**：把这一组 sink 测试从 JUnit4 迁到 JUnit5。

**工作逻辑**：统一替换注解（`@Rule TemporaryFolder` → `@TempDir Path`、`@Before` → `@BeforeEach`、`@Test` 保留），`Assert.*` → AssertJ `assertThat`，构造临时目录/文件的写法相应调整（如 `tempFolder.newFolder()` → `Files.createTempDirectory(temporaryFolder, "junit").toFile()`、`tempFolder.newFile()` → `Files.createTempFile(...)` 等）。

### `flink/v1.17` 与 `flink/v1.18` 下 `sink/shuffle/TestAggregatedStatistics.java`、`TestAggregatedStatisticsTracker.java`、`TestDataStatisticsCoordinator.java`、`TestDataStatisticsCoordinatorProvider.java`、`TestDataStatisticsOperator.java`

**修改目的**：把 sink shuffle 子模块的测试同步到 JUnit5（与 1.19 一致）。

**工作逻辑**：主要为注解与 import 的同步替换，包括 `@Before` → `@BeforeEach`、`@Test` 保留、`Assert.*` → `assertThat`、`TemporaryFolder` → `@TempDir`。这些测试此前在 0964 提交（统计重构）中已被改动，本提交把剩余的 JUnit4 痕迹清理干净。

### `flink/v1.17` 与 `flink/v1.18` 下 `source/TestIcebergSourceBoundedGenericRecord.java`、`TestIcebergSourceContinuous.java`、`TestIcebergSourceFailover.java`、`TestIcebergSourceFailoverWithWatermarkExtractor.java`、`TestIcebergSourceWithWatermarkExtractor.java`、`TestProjectMetaColumn.java`、`TestSourceUtil.java`

**修改目的**：把 source 子模块所有剩余测试迁到 JUnit5。

**工作逻辑**：
- `@ClassRule MiniClusterWithClientResource` / `@ClassRule TemporaryFolder` → `@RegisterExtension static MiniClusterExtension`（通过 `MiniFlinkClusterExtension.createWithClassloaderCheckDisabled(METRIC_REPORTER)`）+ `@TempDir Path temporaryFolder`；
- `@Rule HadoopTableResource tableResource` → `@RegisterExtension static HadoopTableExtension TABLE_EXTENSION`，访问从 `tableResource.table()` 改为 `TABLE_EXTENSION.table()`；
- `@Rule Timeout` → 类级 `@Timeout(value = 120)`；
- 注入 `ClusterClient` 用 Flink 5 的 `@InjectClusterClient`；
- `Assert.*` → AssertJ；
- `TestIcebergSourceFailover` 新增 `runTestWithNewMiniCluster(ThrowingConsumer<MiniCluster, Exception> testMethod)` 工具方法，在每个测试方法中临时创建并销毁 `MiniClusterWithClientResource`，把 `miniCluster` 作为参数传给内部测试逻辑，保证 failover 测试间集群状态完全隔离；所有 `triggerFailover(..., miniClusterResource.getMiniCluster())` 调用改为传入新建的 `miniCluster`，`sourceTableResource.table()` 改为 `sourceTable`、`sinkTableResource.table()` 改为 `sinkTable`、`sinkTableResource.tableLoader()` 改为 `SINK_CATALOG_EXTENSION.tableLoader()`。

## 小结

- **成效**：Flink 1.17 与 1.18 目录下所有剩余的 JUnit4 测试（47 个文件）已迁移到 JUnit5，并与 1.19 风格保持一致；删除了两个 JUnit4 风格的 `HadoopCatalogResource`/`HadoopTableResource` 资源类，统一使用 JUnit5 扩展；新增 `MiniFlinkClusterExtension.createWithClassloaderCheckDisabled(InMemoryReporter)` 工具方法与 `TestFixtures.SINK_TABLE_IDENTIFIER` 常量；failover 测试通过 `runTestWithNewMiniCluster` 实现每用例独立集群。
- **影响范围**：仅测试代码，覆盖 Flink 1.17 与 1.18 两套目录的 47 个文件（每套约 23 个），包含 sink/source/shuffle/data 各子模块以及公共测试工具（`SimpleDataUtil`、`TestFixtures`、`MiniFlinkClusterExtension`），无生产代码改动。
- **回迁到 1.4.x 的注意事项**：本提交属于测试基础设施统一，**原则上可回迁到 1.4.x**，但前提是 1.4.x 分支上已经存在对应的 JUnit5 扩展类（`HadoopCatalogExtension`、`HadoopTableExtension`、`MiniFlinkClusterExtension`、Iceberg 自带的 `ParameterizedTestExtension` 等），否则会编译失败。由于改动量大且均为机械替换，cherry-pick 时大概率出现上下文冲突，需要逐文件手工解决。若 1.4.x 仍依赖 JUnit4，建议先评估是否值得投入做整批迁移，否则保持现状即可；如确实要回迁，应整体搬过去以保证分支内部一致性。注意 `TestIcebergSourceFailover` 的 `runTestWithNewMiniCluster` 改造影响测试行为（每用例新集群），需要重新验证测试稳定性与耗时。
