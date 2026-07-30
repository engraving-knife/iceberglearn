# 提交分析：Flink 1.16: Create JUnit5 version of TestFlinkScan

## 提交信息

- 哈希: fac03ea3c0d8555d85b1e85c8e9f6ce178bc4e9b
- 短哈希: fac03ea3c
- 日期: 2024-01-16 17:35:07 +0100
- 作者: Eduard Tudenhoefner
- 说明: Flink 1.16: Create JUnit5 version of TestFlinkScan (#9482)

## 总体目的

本次提交将 Iceberg Flink 1.16 模块下围绕 `TestFlinkScan` 的一组测试从 JUnit 4 迁移到 JUnit 5（Jupiter），目的是与社区推动的"Flink 测试栈统一升级到 JUnit 5"方向保持一致，并为后续利用 JUnit 5 提供的扩展模型（Extension Model）、参数化测试增强、生命周期注解等能力打下基础。JUnit 4 的 `@RunWith(Parameterized.class)` + `@Rule` + `@ClassRule` 模式存在扩展性差、参数化机制僵化、与 Jupiter 不可混用等限制，长期来看不利于在同一个测试类中组合多个扩展（例如同时使用 MiniCluster、临时目录、Catalog 资源等）。

具体来说，本次迁移涉及 `TestFlinkScan` 抽象基类以及继承自它的 5 个子类（`TestFlinkInputFormat`、`TestFlinkScanSql`、`TestFlinkSource`、`TestIcebergSourceBounded`、`TestIcebergSourceBoundedSql`），同时附带改造了被这些测试依赖的公共工具类 `TestHelpers`。改造范围覆盖了测试生命周期的方方面面：参数化运行器、临时目录、MiniCluster 与 HadoopCatalog 资源管理、断言库（从 JUnit 4 `Assert` 切换到 AssertJ `assertThat`）以及 `Assume` 条件假设。

值得注意的是，本次仅迁移 Flink 1.16 模块的对应测试。Flink 1.17/1.18 模块的对应测试在另外的提交中独立处理，遵循 Iceberg "每个 Flink 版本独立模块、独立迁移" 的惯例，避免一次性大改带来的回滚困难与跨模块干扰。

## 如何达成设计目的

迁移通过系统性地替换 JUnit 4 API 为 JUnit 5 等价物完成。运行器层面，`@RunWith(Parameterized.class)` 替换为 Iceberg 自定义的 `@ExtendWith(ParameterizedTestExtension.class)`（位于 `org.apache.iceberg` 包，封装了 JUnit 5 的参数化扩展），参数字段使用 `@Parameter` 注入，参数工厂方法使用 `@Parameters` 注解并直接返回 `Collection<FileFormat>`（而非 JUnit 4 的 `Object[]`），更类型安全。资源管理层面，`@ClassRule`/`@Rule` 改为 `@RegisterExtension` 静态/实例字段，对应的 `MiniClusterResource`、`HadoopCatalogResource`、`TemporaryFolder` 分别被 JUnit 5 版本的 `MiniFlinkClusterExtension`、`HadoopCatalogExtension`、`@TempDir Path` 取代。测试方法注解 `@Test` 改为 `@TestTemplate`（因为参数化扩展需要模板化执行），`@Before` 改为 `@BeforeEach`，`Assume.assumeTrue` 改为 AssertJ 的 `assumeThat(...).isNotEqualTo(...)`。整个迁移保持测试逻辑不变，只调整框架 API 调用方式，从而最大限度地降低行为变更风险。

## 修改详情

### flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkScan.java

**修改目的**：将抽象测试基类从 JUnit 4 迁移到 JUnit 5，作为整个继承体系迁移的根。

**工作逻辑**：
- 运行器：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。
- 资源扩展：`@ClassRule public static final MiniClusterWithClientResource MINI_CLUSTER_RESOURCE` → `@RegisterExtension protected static MiniClusterExtension miniClusterResource`，使用 `MiniFlinkClusterExtension.createWithClassloaderCheckDisabled()` 工厂方法。
- 临时目录：`@ClassRule public static final TemporaryFolder TEMPORARY_FOLDER` → `@TempDir protected Path temporaryDirectory`（JUnit 5 内建字段注入式临时目录，类型由 `File` 变为 `Path`）。
- Catalog 资源：`@Rule public final HadoopCatalogResource catalogResource` → `@RegisterExtension protected static final HadoopCatalogExtension catalogExtension`，构造方式从 `(TEMPORARY_FOLDER, DATABASE, TABLE)` 简化为 `(DATABASE, TABLE)`（临时目录由扩展自行管理）。
- 参数化：构造函数 `TestFlinkScan(String fileFormat)` + `@Parameterized.Parameters(name = "format={0}") public static Object[] parameters()` 替换为 `@Parameter protected FileFormat fileFormat` 字段 + `@Parameters(name = "format={0}") public static Collection<FileFormat> fileFormat()`，直接返回枚举集合，避免字符串到枚举的转换。
- 测试方法：所有 `@Test` 改为 `@TestTemplate`（参数化模板方法所必需）。
- 内部引用：类内 `catalogResource` 改为 `catalogExtension`，`TEMPORARY_FOLDER` 改为 `temporaryDirectory`，并新增静态导入 `assertThat`、`assertThatThrownBy` 用于替换 `Assert.assertEquals` 与 `Assertions.assertThatThrownBy`。

### flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java

**修改目的**：将公共断言工具类中所有的 JUnit 4 `Assert.*` 与部分 `Assertions.*` 调用统一为 AssertJ `assertThat`，以便 JUnit 5 测试类继续复用该工具类。

**工作逻辑**：
- 新增 `import static org.assertj.core.api.Assertions.assertThat;`，删除 `import org.junit.Assert;`。
- `assertRows` 中 `Assertions.assertThat(results).containsExactlyInAnyOrderElementsOf(expected)` 简化为 `assertThat(results)...`。
- `assertRecordValues` 与 `assertRowData` 等方法中，所有 `Assert.assertTrue("msg", cond)` 改为 `assertThat(x).isNotNull()` 或 `assertThat(...).as("msg").isEqualTo(expected)`、`isInstanceOf(...)`、`isEqualTo(...)` 链式调用。例如 `Assert.assertEquals("boolean value should be equal", expected, actual)` 改为 `assertThat(actual).as("boolean value should be equal").isEqualTo(expected)`，将"期望值"与"实际值"的角色也调整到 AssertJ 惯例（actual 在前）。
- 类型分支（BOOLEAN/INTEGER/LONG/.../FIXED）逐个改写，保持断言语义不变：例如 BINARY 分支改为 `assertThat(ByteBuffer.wrap((byte[]) actual)).as("Should expect a ByteBuffer").isInstanceOf(ByteBuffer.class).isEqualTo(expected)`，FIXED 分支用 `assertThat(actual).as("Should expect byte[]").isInstanceOf(byte[].class).isEqualTo(expected)` 替换 `Assert.assertArrayEquals`。

### flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkInputFormat.java

**修改目的**：将继承自 `TestFlinkSource` 的具体测试类迁移到 JUnit 5。

**工作逻辑**：删除 `public TestFlinkInputFormat(String fileFormat)` 构造函数（参数化由父类字段注入完成）；`@Test` 改为 `@TestTemplate`；`Assume.assumeTrue("Temporary skip ORC", FileFormat.ORC != fileFormat)` 改为 AssertJ 假设 `assumeThat(fileFormat).as("Temporary skip ORC").isNotEqualTo(FileFormat.ORC)`（新增静态导入 `static org.assertj.core.api.Assumptions.assumeThat`）；类内 `catalogResource` → `catalogExtension`，`TEMPORARY_FOLDER` → `temporaryDirectory`。

### flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkScanSql.java

**修改目的**：将 SQL 形式的 Flink Scan 测试迁移到 JUnit 5 生命周期。

**工作逻辑**：删除构造函数；`@Before` → `@BeforeEach`；`catalogResource.warehouse()` → `catalogExtension.warehouse()`。`TableEnvironment` 字段保留为实例字段。

### flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSource.java

**修改目的**：迁移中间抽象层 `TestFlinkSource`。

**工作逻辑**：删除构造函数 `TestFlinkSource(String fileFormat)`；类内引用 `catalogResource.catalog()` 替换为 `catalogExtension.catalog()`。其余抽象方法签名保持不变。

### flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBounded.java

**修改目的**：迁移基于 Flip-27 source 的 bounded 测试类。

**工作逻辑**：删除 `@RunWith(Parameterized.class)` 与构造函数；类内 `catalogResource.catalog()` 替换为 `catalogExtension.catalog()`。参数化与扩展通过继承自 `TestFlinkScan` 的 `@ExtendWith(ParameterizedTestExtension.class)` 自动生效。

### flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBoundedSql.java

**修改目的**：迁移 Flip-27 source 的 SQL bounded 测试类。

**工作逻辑**：删除构造函数；`@Before` → `@BeforeEach`；`catalogResource.warehouse()` → `catalogExtension.warehouse()`。

## 小结

本次提交将 Iceberg Flink 1.16 模块下围绕 `TestFlinkScan` 的整套测试继承体系（基类 + 5 个子类 + 公共 `TestHelpers`）从 JUnit 4 系统性迁移到 JUnit 5：运行器替换为 Iceberg 自定义的 `ParameterizedTestExtension`，资源管理由 `@Rule/@ClassRule` 切换到 `@RegisterExtension`/`@TempDir`，生命周期注解 `@Test`/`@Before` 切换到 `@TestTemplate`/`@BeforeEach`，断言统一到 AssertJ `assertThat`。迁移保持测试逻辑不变，仅调整框架 API 调用方式，符合社区对 Flink 模块测试栈长期演进方向的预期。
