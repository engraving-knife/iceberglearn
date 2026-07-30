# 提交 0887：Flink: Migrate HadoopCatalog related tests (#10358)

## 提交信息

- **序号**：0887 / 4088
- **哈希**：a975a955523239ca6423adf2ec26915ca8675f4f
- **短哈希**：a975a9555
- **日期**：2024-07-01（Mon Jul 1 22:24:02 2024 +0900）
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Flink: Migrate HadoopCatalog related tests (#10358)
- **PR/Issue**：#10358

## 总体目的

Iceberg Flink 模块的测试代码历史上基于 JUnit 4 编写，使用 `@RunWith(Parameterized.class)`、`@ClassRule`/`@Rule` 配合 `TemporaryFolder`、`MiniClusterWithClientResource`、`HadoopCatalogResource`/`HadoopTableResource` 等 JUnit 4 规则体系。随着项目整体向 JUnit 5（Jupiter）迁移，这些测试需要逐步改造为 JUnit 5 的扩展（Extension）模型。

本提交针对 Flink v1.19 模块下所有依赖 `HadoopCatalog` 的测试类，统一完成 JUnit 4 → JUnit 5 的迁移。迁移范围涵盖 sink 与 source 两类测试，涉及参数化测试框架、临时目录、MiniCluster、HadoopCatalog 测试夹具、断言与假设机制等多个维度的改写。这是 Iceberg Flink 测试基础设施现代化的一部分，目的是统一测试框架、利用 JUnit 5 更强的扩展能力，并与 Flink 官方测试工具链（提供 `MiniClusterExtension` 等 JUnit 5 扩展）对齐。

## 如何达成设计目的

迁移采用统一的模式，对每个测试类做以下系统性改写：

1. **参数化测试**：`@RunWith(Parameterized.class)` + 构造器注入 → `@ExtendWith(ParameterizedTestExtension.class)`（Iceberg 自定义扩展）+ `@Parameter` 字段注入 + `@Parameters` 标注静态工厂方法；测试方法由 `@Test` 改为 `@TestTemplate`。
2. **MiniCluster**：`@ClassRule MiniClusterWithClientResource` + `MiniClusterResource.createWithClassloaderCheckDisabled()` → `@RegisterExtension MiniClusterExtension` + `MiniFlinkClusterExtension.createWithClassloaderCheckDisabled()`（Flink 官方提供的 JUnit 5 扩展）。
3. **HadoopCatalog 夹具**：`@Rule HadoopCatalogResource` → `@RegisterExtension HadoopCatalogExtension`（JUnit 5 版本）。
4. **临时目录**：`@ClassRule TemporaryFolder`（JUnit 4，基于 `java.io.File`）→ `@TempDir Path`（JUnit 5，基于 `java.nio.file.Path`）。
5. **生命周期**：`@Before` → `@BeforeEach`，`@BeforeClass` → `@BeforeAll`。
6. **断言**：JUnit 4 `Assert.assertEquals/assertTrue/assertFalse/assertNull` → AssertJ `assertThat(...).isEqualTo/isGreaterThan/isFalse/doesNotContainKeys` 等。
7. **假设**：JUnit 4 `Assume.assumeTrue` → AssertJ `assumeThat(...).isEqualTo(...)`。
8. **参数枚举化**：参数表中字符串 `"avro"` → `FileFormat.AVRO` 枚举，避免运行时 `FileFormat.fromString` 转换。

此外新增两个辅助类/方法以支撑迁移：

- **`HadoopTableExtension`**：继承 `HadoopCatalogExtension`，在 `beforeEach` 中额外调用 `catalog.createTable(...)` 创建表并打开 `tableLoader`，提供 `table()` 访问器。用于那些需要"catalog + 自动建表"一步到位的测试（如 `TestColumnStatsWatermarkExtractor`）。
- **`ReaderUtil.createCombinedScanTask(...)` 新重载**：接收 `java.nio.file.Path` 临时目录（JUnit 5 风格），与原接收 `TemporaryFolder`（JUnit 4）的重载并存，注释标注旧方法仅供 JUnit4 测试迁移过渡期保留。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/HadoopTableExtension.java`（新增）

**修改目的**：提供 JUnit 5 扩展，在 `HadoopCatalogExtension` 基础上自动建表，简化需要表的测试的夹具装配。

**工作逻辑**：继承 `HadoopCatalogExtension`，构造时接收 `database`、`tableName`、`schema`（可选 `partitionSpec`）。`beforeEach` 先调 `super.beforeEach` 完成 catalog 与 tableLoader 初始化，再根据是否传入 `partitionSpec` 调用 `catalog.createTable(...)` 建表，并 `tableLoader.open()`。`table()` 方法返回已建好的 `Table` 实例。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/reader/ReaderUtil.java`

**修改目的**：新增接收 `java.nio.file.Path` 的 `createCombinedScanTask` 重载，支持 JUnit 5 `@TempDir` 风格的临时目录。

**工作逻辑**：新重载逻辑与旧 `TemporaryFolder` 版本一致——遍历 `recordBatchList`，对每批记录调用 `createFileTask` 生成 `FileScanTask`，最终组装为 `BaseCombinedScanTask`。区别仅在于临时文件创建改用 `File.createTempFile("junit", null, temporaryFolder.toFile())`（将 `Path` 转 `File`）。旧重载保留并加注释"Only for JUnit4 tests"。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSink.java`

**修改目的**：将参数化 sink 测试从 JUnit 4 迁移到 JUnit 5。

**工作逻辑**：
- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`，构造器注入改为 `@Parameter` 字段注入。
- `MiniClusterWithClientResource` `@ClassRule` → `MiniClusterExtension` `@RegisterExtension`（经 `MiniFlinkClusterExtension` 创建）。
- `HadoopCatalogResource` `@Rule` → `HadoopCatalogExtension` `@RegisterExtension`。
- `TemporaryFolder` 移除（本类不再直接使用临时目录）。
- `@Before` → `@BeforeEach`，`@Test` → `@TestTemplate`。
- 参数表 `"avro"` 等字符串改为 `FileFormat.AVRO` 枚举，移除构造器与 `FileFormat.fromString` 转换。
- `Assert.assertTrue/assertEquals/assertNull` → AssertJ `assertThat`，例如 `Assert.assertTrue("...", files > 3)` → `assertThat(files).isGreaterThan(3)`，`Assert.assertNull(...)` → `assertDoesNotContainKeys`，`Assert.assertEquals(...)` → `containsEntry`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkBranch.java`

**修改目的**：同上，迁移到 JUnit 5。

**工作逻辑**：与 `TestFlinkIcebergSink` 相同的迁移模式：`@RunWith` → `@ExtendWith(ParameterizedTestExtension.class)`，规则改扩展，`@Before` → `@BeforeEach`，`@Test` → `@TestTemplate`，`Assert` → `assertThat`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2.java`

**修改目的**：迁移 V2 sink 测试到 JUnit 5。

**工作逻辑**：同样的迁移模式。`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`，`MiniClusterWithClientResource` → `MiniClusterExtension`，`HadoopCatalogResource` → `HadoopCatalogExtension`，`TemporaryFolder` → `@TempDir Path`，`@Before` → `@BeforeEach`，`@Test` → `@TestTemplate`，`Assert` → `assertThat`，`@Timeout` 改用 JUnit 5 版本。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2Base.java`

**修改目的**：将基类参数化字段改为 `@Parameter` 注入，新增 `@Parameters` 工厂方法，断言迁移到 AssertJ。

**工作逻辑**：
- 原先 `format`、`parallelism`、`partitioned`、`writeDistributionMode` 为普通 protected 字段（由子类构造器设置），现改为 `@Parameter(index=0..3)` 标注，并由基类提供 `@Parameters` 静态方法生成参数矩阵（12 组合：3 种 FileFormat × 2 并行度 × 2 分区 × 对应分布模式）。
- `Assert.assertEquals` → `assertThat(...).hasSize` / `.as(...).isEqualTo(...)`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2Branch.java`

**修改目的**：迁移到 JUnit 5。

**工作逻辑**：与其它 sink 测试相同的迁移模式。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSourceSql.java`

**修改目的**：迁移到 JUnit 5，使用基类新 `temporaryFolder` 字段。

**工作逻辑**：`import org.junit.Test` → `import org.junit.jupiter.api.Test`；`TEMPORARY_FOLDER` → 基类的 `temporaryFolder`（`Path` 类型）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceSql.java`

**修改目的**：同上。

**工作逻辑**：`@Test` 改 JUnit Jupiter，`TEMPORARY_FOLDER` → `temporaryFolder`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestSqlBase.java`

**修改目的**：将抽象基类迁移到 JUnit 5 扩展模型，统一管理 MiniCluster、HadoopCatalog 与临时目录。

**工作逻辑**：
- `MiniClusterWithClientResource` `@ClassRule` → `MiniClusterExtension` `@RegisterExtension`。
- `HadoopCatalogResource` `@Rule` → `HadoopCatalogExtension` `@RegisterExtension`。
- `TemporaryFolder` `@ClassRule` → `@TempDir Path temporaryFolder`（protected 供子类用）。
- `@Before` → `@BeforeEach`。
- `Assert.assertFalse` → `assertThat(...).as(...).isFalse()`。
- `GenericAppenderHelper` 构造参数由 `TEMPORARY_FOLDER` 改为 `temporaryFolder`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestColumnStatsWatermarkExtractor.java`

**修改目的**：迁移参数化 watermark 提取器测试到 JUnit 5，并使用 `HadoopTableExtension` 自动建表。

**工作逻辑**：
- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`，构造器注入改 `@Parameter`。
- `HadoopTableResource` `@Rule` → `HadoopTableExtension` `@RegisterExtension`（用新增的 `HadoopTableExtension`，自动建表）。
- `TemporaryFolder` `@ClassRule` → `@TempDir Path`。
- `@BeforeClass` → `@BeforeAll`。
- `@Test` → `@TestTemplate`。
- `Assert.assertEquals` → `assertThat(...).isEqualTo(...)`。
- `Assume.assumeTrue` → `assumeThat(columnName).isEqualTo(...)`。
- `ReaderUtil.createCombinedScanTask` 调用改用接收 `Path` 的新重载。

## 小结

- **成效**：完成 Flink v1.19 模块下 11 个测试文件（含 1 个新增辅助类、1 个工具类增强）从 JUnit 4 到 JUnit 5 的系统迁移，统一使用 JUnit 5 扩展模型（`@RegisterExtension`/`@ExtendWith`）、AssertJ 断言、`@TempDir` 临时目录、`ParameterizedTestExtension` 参数化机制，并与 Flink 官方 JUnit 5 测试扩展对齐。
- **影响范围**：仅 `flink/v1.19/flink/src/test/` 下测试代码，无生产代码改动。新增 `HadoopTableExtension` 与 `ReaderUtil` 新重载为后续更多测试迁移提供基础设施。
- **回迁到 1.4.x 的注意事项**：本提交仅针对 v1.19，且属于测试框架现代化，**不建议回迁到 1.4.x**。1.4.x 分支通常维护各自的 Flink 版本（v1.17/v1.18/v1.19 等）测试代码，且依赖链（`HadoopCatalogExtension`、`MiniFlinkClusterExtension`、`ParameterizedTestExtension` 等 JUnit 5 基础设施）可能尚未在 1.4.x 中就绪。强行回迁可能因缺少支撑类而编译失败。若 1.4.x 确需迁移，应先确认这些 JUnit 5 扩展类已存在。
