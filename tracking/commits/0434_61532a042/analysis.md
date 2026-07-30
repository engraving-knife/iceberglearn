# 提交 0434：Flink: Backport #9364 to 1.16 and 1.17 for Create CatalogTestBase for migration to JUnit5 (#9601)

## 提交信息

- **序号**：0434
- **哈希**：61532a042adcd2c916b62ace533129e9e7ad3128
- **短哈希**：61532a042
- **日期**：2024-02-01 00:07:56 -0800
- **作者**：Rodrigo <rmenesespinillos@apple.com>
- **提交说明**：Flink: Backport #9364 to 1.16 and 1.17 for Create CatalogTestBase for migration to JUnit5 (#9601)
- **PR/Issue**：#9601（本提交为 #9364 向 Flink 1.16/1.17 的回移 backport）

## 总体目的

本提交是一个测试基础设施迁移提交，目的是把 Flink 1.16 与 1.17 两个版本的测试基类从 JUnit4 迁移到 JUnit5（JUnit Jupiter）。Iceberg 项目正在推进整体测试栈向 JUnit5 迁移，主干（main）上的较新 Flink 版本模块已经完成这一迁移，而 1.16/1.17 这两个仍在维护的旧版本分支尚未跟进。本提交通过回移 #9364 的成果，使旧版本分支的测试基类与新版本保持一致，为后续继续把更多 Flink 测试用例迁移到 JUnit5 打下基础。

迁移的核心动作是新建一个 JUnit5 风格的 `CatalogTestBase` 抽象基类，替代原先基于构造器参数化的 `FlinkCatalogTestBase`（JUnit4 的 `@RunWith(Parameterized.class)` 模式）。JUnit4 的参数化测试依赖构造器接收参数，而 JUnit5 推荐使用字段注入 + `@TestTemplate` + 扩展（Extension）机制。Iceberg 为此自带了 `ParameterizedTestExtension` 与 `@Parameter`/`@Parameters` 注解，使迁移后的测试既符合 JUnit5 范式，又能保留原有的参数化组合（catalogName / baseNamespace / format / cacheEnabled 等）。

迁移还顺带把断言风格从 JUnit4 的 `Assert.assertEquals/assertTrue/assertFalse` 与 `Assume.assumeFalse/assumeTrue`，统一替换为 AssertJ 的流式断言 `assertThat(...).isEqualTo/isTrue/isFalse()` 与 `assumeThat(...)`，使断言更可读、失败信息更友好。临时目录也从 JUnit4 的 `@Rule TemporaryFolder` 改为 JUnit5 的 `@TempDir`。这些改动不影响被测产品代码，纯粹是测试层的现代化。

## 如何达成设计目的

实现路径是在 `flink/v1.16` 和 `flink/v1.17` 两个模块下各新增一个完全相同的 `CatalogTestBase.java`（两者 blob 相同，index 均为 `91ed3c4ad`），把原先散落在 `FlinkCatalogTestBase` 中的参数化配置、catalog 创建、warehouse 路径管理、with 子句拼接等逻辑迁移过来并改为 JUnit5 注解驱动。随后把三个继承 `FlinkCatalogTestBase` 的测试类改为继承新的 `CatalogTestBase`，并把所有 JUnit4 注解、断言、假设、临时目录 API 替换为 JUnit5/AssertJ 等价物。由于新旧基类参数化机制不同（构造器注入 vs 字段注入），各子类的参数声明也从构造器参数 + `@Parameterized.Parameters` 方法改为 `@Parameter` 字段 + `@Parameters` 方法。

## 修改详情

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/CatalogTestBase.java`（新增，1.17 下同名文件内容完全一致）

**修改目的**：提供 JUnit5 风格的 Flink Catalog 测试基类，替代旧的 `FlinkCatalogTestBase`。

**工作逻辑**：

该抽象类继承自 `TestBase`，并用 `@ExtendWith(ParameterizedTestExtension.class)` 注册 Iceberg 自带的参数化扩展。关键设计点：

1. **参数化字段注入**：用 `@Parameter(index = 0)` 注入 `catalogName`、`@Parameter(index = 1)` 注入 `baseNamespace`，取代 JUnit4 通过构造器接收参数的方式。子类可继续用 `@Parameter(index = 2/3)` 扩展更多参数（如 `TestFlinkCatalogTablePartitions` 的 format/cacheEnabled）。

2. **`@Parameters` 方法**：`parameters()` 返回三组参数组合——`testhive`+空 namespace、`testhadoop`+空 namespace、`testhadoop_basenamespace`+两级 namespace，覆盖 Hive catalog、Hadoop catalog、以及带 baseNamespace 的 Hadoop catalog 三种场景。

3. **`@BeforeEach before()`**：根据 `catalogName` 判断是否 Hadoop catalog，据此选择 `validationCatalog`（HadoopCatalog 实例或复用 `catalog`），组装 `config`（type、catalog type、URI、warehouse location 等），计算 `flinkDatabase` 与 `icebergNamespace`，并通过 `sql("CREATE CATALOG ...")` 在 Flink TableEnvironment 中注册 catalog。

4. **`@AfterEach clean()`**：调用 `dropCatalog` 清理。

5. **`@TempDir` 仓库目录**：用 `@TempDir protected File hiveWarehouse` 与 `@TempDir protected File hadoopWarehouse` 取代 JUnit4 的 `TemporaryFolder`，分别作为 Hive 与 Hadoop catalog 的 warehouse 根目录。

6. 辅助方法 `warehouseRoot()`、`getFullQualifiedTableName()`、`getURI()`、`toWithClause()` 保持原有逻辑，供子类复用。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogDatabase.java`（1.17 下同名文件改动一致）

**修改目的**：将数据库（namespace）相关测试从 JUnit4 迁移到 JUnit5，改用 AssertJ 断言。

**工作逻辑**：

1. 基类由 `FlinkCatalogTestBase` 改为 `CatalogTestBase`，删除接收 `(catalogName, baseNamespace)` 的构造器（参数现由基类字段注入）。

2. 注解替换：`@Test` → `@TestTemplate`（参数化测试在 JUnit5 下用 `@TestTemplate` 配合扩展生成多次调用）、`@After` → `@AfterEach`。

3. 断言替换：所有 `Assert.assertEquals/assertTrue/assertFalse("msg", actual)` 改为 `assertThat(actual).as("msg").isEqualTo/isTrue/isFalse()`；`Assume.assumeFalse/assumeTrue("msg", cond)` 改为 `assumeThat(cond).as("msg").isFalse/isTrue()`。

4. 临时文件处理：`testCreateNamespaceWithLocation` 原用 `TEMPORARY_FOLDER.newFile()` + 手动 delete，改为 `temporaryDirectory.getRoot()`（来自基类的临时目录设施），断言中路径取值也随之调整为 `location.getRoot()`。

5. 测试用例本身的业务逻辑（创建/删除 namespace、列出表、设置元数据等）未变，仅断言风格现代化。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTablePartitions.java`（1.17 下同名文件改动一致）

**修改目的**：将分区列举测试迁移到 JUnit5，并改用字段注入接收 format 与 cacheEnabled 参数。

**工作逻辑**：

1. 基类改为 `CatalogTestBase`；原 `format` 为 `final` 字段在构造器中赋值，现改为 `@Parameter(index = 2) private FileFormat format` 与 `@Parameter(index = 3) private Boolean cacheEnabled`，由扩展注入。`cacheEnabled` 写入 config 的操作从构造器移到 `@BeforeEach before()` 中（在父类 `before()` 之后执行，确保 config 基础项已就位）。

2. `@Parameterized.Parameters`（JUnit4）改为 `@Parameters`（Iceberg JUnit5 扩展），方法签名返回 `List<Object[]>`。参数生成逻辑不变：遍历 ORC/AVRO/PARQUET 与 cacheEnabled true/false 的笛卡尔积。

3. 注解替换：`@Before`/`@After`/`@Test` → `@BeforeEach`/`@AfterEach`/`@TestTemplate`；断言 `Assert.assertEquals` → `assertThat(...).hasSize/isEqualTo`。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestMetadataTableReadableMetrics.java`（1.17 下同名文件改动一致）

**修改目的**：将可读性指标元数据表测试迁移到 JUnit5。

**工作逻辑**：

1. 基类由 `FlinkCatalogTestBase` 改为 `CatalogTestBase`；删除构造器，`@Parameterized.Parameters` 改为 `@Parameters`。参数化只覆盖 `testhive` + 空 namespace 一组。

2. 临时目录：`@Rule public TemporaryFolder temp` 改为 `private @TempDir Path temp`。原 `temp.newFile()` 改为 `File.createTempFile("junit", null, temp.toFile())`，因为 JUnit5 的 `@TempDir Path` 不直接提供 `newFile()`，需手动创建临时文件。

3. 注解与断言替换同前：`@Before`/`@After`/`@Test` → `@BeforeEach`/`@AfterEach`/`@TestTemplate`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/CatalogTestBase.java`、`TestFlinkCatalogDatabase.java`、`TestFlinkCatalogTablePartitions.java`、`TestMetadataTableReadableMetrics.java`

**修改目的**：对 Flink 1.17 模块做与 1.16 完全相同的迁移。

**工作逻辑**：四个文件内容与 1.16 对应文件完全一致（`CatalogTestBase.java` 的 blob index 同为 `91ed3c4ad`），是同一份改动的副本，仅为不同 Flink 版本模块分别落地。详见上文 1.16 部分的说明。

## 小结

本提交是 Iceberg 测试基础设施 JUnit5 迁移工作向 Flink 1.16/1.17 旧版本分支的延伸。其意义在于：（1）统一新旧 Flink 版本测试基类的实现范式，降低维护分歧；（2）用 JUnit5 的字段注入 + `@TestTemplate` + 扩展机制取代 JUnit4 构造器参数化，使参数化测试更灵活、可组合（子类可追加 `@Parameter`）；（3）顺带把断言统一到 AssertJ 流式风格，提升可读性与失败诊断能力。改动模式是典型的"机械迁移 + 局部适配"：注解/断言/临时目录 API 一对一替换，参数注入机制因范式差异需调整声明位置。由于 1.16 与 1.17 代码完全镜像，后续维护时两处需同步更新。
