# 提交 0891：Flink 1.17, 1.18: Migrate HadoopCatalog related tests (#10620)

## 提交信息

- **序号**：0891 / 4088
- **哈希**：0eab91aad705205ebbf675eeb9ea49d3a07d4755
- **短哈希**：0eab91aad
- **日期**：2024-07-02（Wed Jul 3 01:26:47 2024 +0900）
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Flink 1.17, 1.18: Migrate HadoopCatalog related tests (#10620)
- **PR/Issue**：#10620

## 总体目的

本提交是提交 0886（`Flink: Migrate HadoopCatalog related tests`）的后续，将相同的 JUnit 4 → JUnit 5 迁移工作应用到 Flink v1.17 与 v1.18 模块。提交 0886 已完成 v1.19 模块的迁移，本提交补齐 v1.17/v1.18 两个版本的对应测试类，使三个 Flink 版本的测试基础设施保持一致。

Iceberg 为 Flink 1.17、1.18、1.19 各维护一套独立的源码目录（`flink/v1.17/`、`flink/v1.18/`、`flink/v1.19/`），各自有独立的测试代码副本。v1.17/v1.18 此前已具备 JUnit 5 基础设施（`HadoopCatalogExtension` 等扩展类已存在），但仍有部分使用 `HadoopCatalogResource`/`HadoopTableResource`（JUnit 4 `@Rule`）的测试类未迁移。本提交将这些遗留测试统一迁移到 JUnit 5 扩展模型。

## 如何达成设计目的

迁移模式与提交 0886 完全一致，对 v1.17 和 v1.18 各自的 11 个测试文件执行相同的系统性改写：

1. **参数化测试**：`@RunWith(Parameterized.class)` + 构造器注入 → `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameter` 字段注入 + `@Parameters`；`@Test` → `@TestTemplate`。
2. **MiniCluster**：`@ClassRule MiniClusterWithClientResource` → `@RegisterExtension MiniClusterExtension`（经 `MiniFlinkClusterExtension` 创建）。
3. **HadoopCatalog 夹具**：`@Rule HadoopCatalogResource` → `@RegisterExtension HadoopCatalogExtension`。
4. **临时目录**：`@ClassRule TemporaryFolder`（`java.io.File`）→ `@TempDir Path`（`java.nio.file.Path`）。
5. **生命周期**：`@Before` → `@BeforeEach`，`@BeforeClass` → `@BeforeAll`。
6. **断言**：JUnit 4 `Assert.*` → AssertJ `assertThat`。
7. **假设**：`Assume.assumeTrue` → AssertJ `assumeThat`。
8. **参数枚举化**：参数表字符串 `"avro"` → `FileFormat.AVRO` 枚举。

此外，v1.17 与 v1.18 各新增 `HadoopTableExtension`（内容与 v1.19 版本完全一致，blob hash 相同），并在 `ReaderUtil` 中新增接收 `Path` 的 `createCombinedScanTask` 重载。

## 修改详情

以下按文件说明。v1.17 与 v1.18 各有一份内容相同的副本，改动逻辑完全一致。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/HadoopTableExtension.java`（新增）
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/HadoopTableExtension.java`（新增）

**修改目的**：提供 JUnit 5 扩展，在 `HadoopCatalogExtension` 基础上自动建表。

**工作逻辑**：继承 `HadoopCatalogExtension`，构造时接收 `database`、`tableName`、`schema`（可选 `partitionSpec`）。`beforeEach` 先调 `super.beforeEach`，再调用 `catalog.createTable(...)` 建表并 `tableLoader.open()`。`table()` 返回已建好的 `Table`。内容与 v1.19 版本（提交 0886 新增）完全相同。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/reader/ReaderUtil.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/reader/ReaderUtil.java`

**修改目的**：新增接收 `java.nio.file.Path` 的 `createCombinedScanTask` 重载，支持 JUnit 5 `@TempDir`。

**工作逻辑**：新重载逻辑与旧 `TemporaryFolder` 版本一致，临时文件创建改用 `File.createTempFile("junit", null, temporaryFolder.toFile())`。旧重载保留并标注"Only for JUnit4 tests"。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSink.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSink.java`

**修改目的**：将参数化 sink 测试从 JUnit 4 迁移到 JUnit 5。

**工作逻辑**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`，构造器注入改 `@Parameter` 字段注入，规则改扩展，`@Before` → `@BeforeEach`，`@Test` → `@TestTemplate`，参数表字符串改 `FileFormat` 枚举，`Assert` → `assertThat`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkBranch.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkBranch.java`

**修改目的**：同上，迁移到 JUnit 5。

**工作逻辑**：与 `TestFlinkIcebergSink` 相同的迁移模式。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2.java`

**修改目的**：迁移 V2 sink 测试到 JUnit 5。

**工作逻辑**：同样的迁移模式，`@RunWith` → `@ExtendWith`，规则改扩展，`TemporaryFolder` → `@TempDir Path`，`@Test` → `@TestTemplate`，`Assert` → `assertThat`，`@Timeout` 改 JUnit 5 版本。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2Base.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2Base.java`

**修改目的**：将基类参数化字段改为 `@Parameter` 注入，新增 `@Parameters` 工厂方法，断言迁移到 AssertJ。

**工作逻辑**：`format`、`parallelism`、`partitioned`、`writeDistributionMode` 改为 `@Parameter(index=0..3)` 标注，基类提供 `@Parameters` 静态方法（12 组合）。`Assert.assertEquals` → `assertThat(...).hasSize` / `.isEqualTo`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2Branch.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2Branch.java`

**修改目的**：迁移到 JUnit 5。

**工作逻辑**：与其它 sink 测试相同的迁移模式。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSourceSql.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSourceSql.java`

**修改目的**：迁移到 JUnit 5，使用基类新 `temporaryFolder` 字段。

**工作逻辑**：`import org.junit.Test` → `import org.junit.jupiter.api.Test`；`TEMPORARY_FOLDER` → 基类 `temporaryFolder`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceSql.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceSql.java`

**修改目的**：同上。

**工作逻辑**：`@Test` 改 JUnit Jupiter，`TEMPORARY_FOLDER` → `temporaryFolder`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestSqlBase.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestSqlBase.java`

**修改目的**：将抽象基类迁移到 JUnit 5 扩展模型。

**工作逻辑**：`MiniClusterWithClientResource` `@ClassRule` → `MiniClusterExtension` `@RegisterExtension`，`HadoopCatalogResource` `@Rule` → `HadoopCatalogExtension` `@RegisterExtension`，`TemporaryFolder` → `@TempDir Path`，`@Before` → `@BeforeEach`，`Assert.assertFalse` → `assertThat(...).isFalse()`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestColumnStatsWatermarkExtractor.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestColumnStatsWatermarkExtractor.java`

**修改目的**：迁移参数化 watermark 提取器测试到 JUnit 5，使用 `HadoopTableExtension` 自动建表。

**工作逻辑**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`，`HadoopTableResource` `@Rule` → `HadoopTableExtension` `@RegisterExtension`，`TemporaryFolder` → `@TempDir Path`，`@BeforeClass` → `@BeforeAll`，`@Test` → `@TestTemplate`，`Assert.assertEquals` → `assertThat(...).isEqualTo`，`Assume.assumeTrue` → `assumeThat`，`createCombinedScanTask` 调用改用 `Path` 重载。

## 小结

- **成效**：完成 Flink v1.17 与 v1.18 模块下各 11 个测试文件（含 1 个新增辅助类 `HadoopTableExtension`、1 个工具类 `ReaderUtil` 增强）从 JUnit 4 到 JUnit 5 的系统迁移，使三个 Flink 版本（1.17/1.18/1.19）的 HadoopCatalog 相关测试基础设施完全一致，均使用 JUnit 5 扩展模型、AssertJ 断言与 `@TempDir` 临时目录。
- **影响范围**：仅 `flink/v1.17/flink/src/test/` 与 `flink/v1.18/flink/src/test/` 下测试代码（共 22 个文件），无生产代码改动。
- **回迁到 1.4.x 的注意事项**：本提交属于测试框架现代化，**不建议回迁到 1.4.x**。1.4.x 分支维护各自的 Flink 版本测试代码，且依赖的 JUnit 5 扩展类（`HadoopCatalogExtension`、`MiniFlinkClusterExtension`、`ParameterizedTestExtension` 等）需要确认在 1.4.x 中已就绪。若 1.4.x 仍在使用 JUnit 4 的 `HadoopCatalogResource`，应先完成基础扩展类的迁移再考虑回迁本提交。通常 1.4.x 维护分支以修复 bug 为主，不建议引入大规模测试框架迁移。
