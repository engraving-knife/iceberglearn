# 提交 0311：Flink: Create CatalogTestBase for migration to JUnit5 (#9364)

## 提交信息

- **序号**：0311
- **哈希**：6c344dbbbe0fc1e373f588a1bbc4c64029cc7f8c
- **短哈希**：6c344dbbb
- **日期**：2023-12-26 11:46:47 +0100
- **作者**：vinitpatni
- **提交说明**：Flink: Create CatalogTestBase for migration to JUnit5 (#9364)
- **PR/Issue**：#9364

## 总体目的

本提交是 Iceberg Flink 1.18 模块 JUnit 4 → JUnit 5 系列迁移的关键一步，新建 `CatalogTestBase` 作为 Flink catalog 测试体系的 JUnit 5 基类，并将三个直接依赖旧基类 `FlinkCatalogTestBase` 的测试类（`TestFlinkCatalogDatabase`、`TestFlinkCatalogTablePartitions`、`TestMetadataTableReadableMetrics`）迁移到新基类之上。旧基类 `FlinkCatalogTestBase` 基于 JUnit 4 的 `@RunWith(Parameterized.class)` 运行器、构造器注入参数、`@Parameterized.Parameters` 提供 `Iterable<Object[]>` 参数源、`@Rule TemporaryFolder` 管理临时目录、`@Before/@After/@BeforeClass/@AfterClass` 生命周期注解，以及通过共享静态 `TemporaryFolder` 维护 warehouse 目录等机制；新基类 `CatalogTestBase` 全面切换到 JUnit 5：用 `@ExtendWith(ParameterizedTestExtension.class)` 注册 Iceberg 自定义的参数化扩展，用 `@Parameters` 注解的 `List<Object[]>` 方法提供参数，用 `@Parameter` 注解的字段（取代构造器参数）注入参数，用 `@TempDir File` 字段管理临时目录，用 `@BeforeEach/@AfterEach` 替换生命周期注解，并将原本由静态 `TemporaryFolder` 维护的 hive/hadoop warehouse 下放到实例字段（`@TempDir protected File hiveWarehouse` 和 `hadoopWarehouse`）。

这一改造对齐了 Spark 3.5 模块已完成（参见 #9342、#9367 等）的 `CatalogTestBase` 设计模式——Flink 与 Spark 两侧的 catalog 测试基础设施趋于一致，便于后续跨模块复用测试模式与统一迁移节奏。值得注意的是，本提交并非一次性迁移全部 Flink catalog 测试：`FlinkCatalogTestBase` 旧基类并未删除，剩余依赖它的测试类（`TestFlinkCatalog`、`TestFlinkCatalogTablePartitions` 之外的 catalog 子类等）将在后续提交中逐步迁移，本提交只完成基类创建与首批三个测试类的迁移作为模板与样板。迁移保持所有测试逻辑不变，仅替换测试基础设施 API（断言、注解、参数化机制、临时目录），属于纯测试基础设施现代化工作。

## 如何达成设计目的

设计上以新建 `CatalogTestBase` 为核心载体：它继承 Flink 模块已有的 `TestBase`（JUnit 5 化的 FlinkTestBase），持有三种 catalog 配置（testhive/testhadoop/testhadoop_basenamespace）作为参数化维度，在 `@BeforeEach before()` 中根据注入的 `catalogName`/`baseNamespace` 构建 `validationCatalog`（Hadoop 或 Hive）、`config`（含 type/uri/warehouse/base_namespace 等）、`flinkDatabase`、`icebergNamespace`，并通过 `sql("CREATE CATALOG %s WITH %s", ...)` 在 Flink TableEnvironment 中注册被测 catalog；在 `@AfterEach clean()` 中通过 `dropCatalog` 清理。子类测试方法因此必须从 `@Test` 改为 `@TestTemplate`，以便 `ParameterizedTestExtension` 针对每组 catalog 参数重复执行。其余迁移规则（注解替换、`TemporaryFolder` → `@TempDir`、断言静态导入化、`Assume` → `assumeThat`）遵循与 Spark 模块完全一致的模式。

## 修改详情

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/CatalogTestBase.java`（新增）

**修改目的**：作为 Flink catalog 测试的 JUnit 5 基类，替代 JUnit 4 风格的 `FlinkCatalogTestBase`。

**工作逻辑**：新文件 143 行。核心设计要点：
- 类级注解 `@ExtendWith(ParameterizedTestExtension.class)` 注册 Iceberg 自定义参数化扩展（`org.apache.iceberg.ParameterizedTestExtension`，非 JUnit 5 内置），并 `extends TestBase`（Flink 模块已 JUnit 5 化的基类，提供 spark/hive 环境）。
- 用 `@TempDir protected File hiveWarehouse` 与 `@TempDir protected File hadoopWarehouse` 两个字段管理 warehouse 目录，取代旧基类中 `private static TemporaryFolder` + `@BeforeClass createWarehouse()` 的静态共享模式——JUnit 5 推崇实例隔离，避免测试间状态泄漏。
- 参数化机制：`@Parameters(name = "catalogName={0}, baseNamespace={1}") protected static List<Object[]> parameters()` 返回三种 catalog 配置（testhive 空 namespace、testhadoop 空 namespace、testhadoop 带 `l0.l1` base namespace），用 `@Parameter(index = 0)` / `@Parameter(index = 1)` 注解字段 `catalogName`/`baseNamespace` 注入参数（取代旧基类在构造器中接收并赋值）。
- `@BeforeEach before()` 完成被测 catalog 的搭建：根据 `catalogName` 前缀判断 `isHadoopCatalog`；Hadoop catalog 用 `new HadoopCatalog(hiveConf, "file:" + hadoopWarehouse.getPath())`，Hive catalog 直接复用父类 `catalog`；填充 `config` map（type=iceberg、可选 base_namespace、ICEBERG_CATALOG_TYPE=hadoop/hive、URI、WAREHOUSE_LOCATION）；计算 `flinkDatabase = catalogName + ".db"` 与 `icebergNamespace`（baseNamespace levels 拼接 DATABASE）；最后 `sql("CREATE CATALOG %s WITH %s", ...)`。
- `@AfterEach clean()` 调用 `dropCatalog(catalogName, true)` 销毁 catalog。
- 辅助方法 `warehouseRoot()` 根据 isHadoopCatalog 返回对应 warehouse 路径；`getFullQualifiedTableName(tableName)` 拼接 `icebergNamespace.levels() + tableName` 形成全限定表名；静态 `getURI(HiveConf)` 读取 metastore URI；`toWithClause(Map)` 将 props map 序列化为 Flink SQL 的 WITH 子句字符串 `('k'='v','k2'='v2')`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogDatabase.java`

**修改目的**：将 Flink catalog 数据库操作测试迁移到新基类 `CatalogTestBase`，并完成 JUnit 5 化（断言、注解、临时目录）。

**工作逻辑**：基类由 `FlinkCatalogTestBase` 改为 `CatalogTestBase`；删除旧构造器 `public TestFlinkCatalogDatabase(String catalogName, Namespace baseNamespace)`（参数化注入由 `@Parameter` 字段完成）。所有测试方法 `@Test` → `@TestTemplate`（因父类已用 `ParameterizedTestExtension`）；`@After` → `@AfterEach`。新增静态导入 `assertThat`、`assumeThat`，移除 `org.junit.Assert/Assume/Test/After`。临时目录方面，原通过 `TEMPORARY_FOLDER.newFile()` 获取的 location 改为 `temporaryDirectory.getRoot()`（继承自父类 TestBase 的临时目录）。断言迁移要点：
- `Assert.assertFalse("msg", cond)` → `assertThat(cond).as("msg").isFalse()`，`assertTrue` → `.isTrue()`。
- `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`。
- `Assert.assertEquals("Should not list any tables", 0, sql("SHOW TABLES").size())` → `assertThat(sql("SHOW TABLES")).isEmpty()`（AssertJ 集合断言）。
- `Assert.assertEquals("Only 1 table", 1, tables.size())` → `assertThat(tables).hasSize(1)`。
- `Assume.assumeFalse("msg", isHadoopCatalog)` → `assumeThat(isHadoopCatalog).as("msg").isFalse()`，`assumeTrue` → `.isTrue()`。
- Map 断言从 `Assert.assertEquals("...", "value", nsMetadata.get("prop"))` → `assertThat(nsMetadata).containsEntry("prop", "value")`，从 `Assert.assertFalse("...", defaultMetadata.containsKey("prop"))` → `assertThat(defaultMetadata).doesNotContainKey("prop")`，从 `Assert.assertTrue("...", databases.stream().anyMatch(...))` → `assertThat(databases).as("...").anyMatch(...)`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTablePartitions.java`

**修改目的**：将 Flink catalog 分区表测试迁移到 `CatalogTestBase`，并扩展参数化维度（增加 format 与 cacheEnabled）。

**工作逻辑**：基类改为 `CatalogTestBase`，并额外用 `@Parameter(index = 2) private FileFormat format` 与 `@Parameter(index = 3) private Boolean cacheEnabled` 注入两个扩展参数（继承自父类的 catalogName/baseNamespace 占用 index 0/1）。`@Parameterized.Parameters` → `@Parameters`，方法返回 `List<Object[]>`（保持不变）。原构造器中 `this.format = format; config.put(CACHE_ENABLED, ...)` 移到 `@BeforeEach before()` 中（因 `@Parameter` 字段在 `before()` 之前注入）。生命周期注解 `@Before/@After` → `@BeforeEach/@AfterEach`，方法上新增 `@Override` 与 `super.before()` 调用。测试方法 `@Test` → `@TestTemplate`。断言迁移：`Assert.assertEquals("Should have 2 partition", 2, list.size())` → `assertThat(list).hasSize(2)`；`Assert.assertEquals("...", list, expected)` → `assertThat(list).as("...").isEqualTo(expected)`。移除 `org.junit.runners.Parameterized`、`org.junit.{Before,After,Assert,Test}` 等旧 import。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestMetadataTableReadableMetrics.java`

**修改目的**：将 Flink 元数据表可读指标测试迁移到 `CatalogTestBase`，并完成 JUnit 5 化（含临时目录 API 调整）。

**工作逻辑**：基类 `FlinkCatalogTestBase` → `CatalogTestBase`；删除构造器；`@Parameterized.Parameters` → `@Parameters`，方法返回 `List<Object[]>`。临时目录由 `@Rule public TemporaryFolder temp = new TemporaryFolder();` 改为 `private @TempDir Path temp;`。由于新基类的 `@TempDir` 类型为 `Path`（而非 `File`），原 `temp.newFile()` 创建数据文件输出路径的代码改为 `File testFile = File.createTempFile("junit", null, temp.toFile());`——这是 `Path` 类型下创建临时文件的等价写法，保留 `FileHelpers.writeDataFile(table, Files.localOutput(testFile), records)` 接口。生命周期 `@Before/@After` → `@BeforeEach/@AfterEach`，测试方法 `@Test` → `@TestTemplate`。移除 `org.junit.{Rule,Test,Before,After}` 与 `org.junit.rules.TemporaryFolder`、`org.junit.runners.Parameterized` import，新增 `org.junit.jupiter.api.{BeforeEach,AfterEach,TestTemplate}` 与 `org.junit.jupiter.api.io.TempDir`、`java.nio.file.Path`、`org.apache.iceberg.Parameters` 等 import。

## 小结

本提交是 Flink 模块 JUnit 5 迁移的奠基性批次：通过新建 `CatalogTestBase` 基类，确立 Flink catalog 测试的 JUnit 5 参数化模板（`@ExtendWith(ParameterizedTestExtension.class)` + `@Parameters` + `@Parameter` 字段注入 + `@TestTemplate` 方法 + `@TempDir` 实例临时目录），与 Spark 模块的 `CatalogTestBase` 设计对齐。三个首批迁移的测试类作为样板验证了迁移模式可行。`FlinkCatalogTestBase` 旧基类暂保留以服务后续批次的逐步迁移。本次净增 100 行（302 增 / 202 删），属于纯测试基础设施现代化，无生产代码改动，测试逻辑保持不变。
