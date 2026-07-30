# 提交 0307：Spark 3.5: Remove constructor from parameterized base class (#9368)

## 提交信息

- **序号**：0307 / 4088
- **哈希**：226a23f516bea97c1cab52a7ce2213844ca12c63
- **短哈希**：226a23f51
- **日期**：2023-12-24 15:09:18 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.5: Remove constructor from parameterized base class (#9368)
- **PR/Issue**：#9368

## 总体目的

本提交对 Spark 3.5 模块的测试基类体系进行重构，将参数化测试基类 `TestBaseWithCatalog` / `CatalogTestBase` 从「构造器注入参数」模式改造为「字段注入参数」模式，以适配 Iceberg 自定义的 JUnit 5 参数化测试扩展 `ParameterizedTestExtension`（借鉴自 Flink 项目）。

Iceberg 在 JUnit 5 中没有使用 JUnit 官方的 `@ParameterizedTest`，而是采用了一个自研的 `ParameterizedTestExtension`（位于 `api/src/test/java/org/apache/iceberg/ParameterizedTestExtension.java`）。该扩展的工作机制是：查找被 `@Parameters` 注解的静态方法获取参数矩阵 `Object[][]`，再通过反射将参数值注入到被 `@Parameter(index = N)` 注解的字段中，并要求所有测试方法使用 `@TestTemplate` 而非 `@Test`。这种扩展天然基于「字段注入」语义，与「构造器注入」存在张力。

改造前的基类 `TestBaseWithCatalog` 通过构造器接收 catalog 配置（`TestBaseWithCatalog(SparkCatalogConfig config)`、`TestBaseWithCatalog(String catalogName, String implementation, Map<String,String> config)`），并在构造器内执行 spark conf 设置、validationCatalog 初始化等副作用。这种模式带来两个问题：一是子类若需指定不同的 catalog 配置（例如 `TestMetadataTableReadableMetrics` 只能用 HIVE catalog、`TestSparkCatalogCacheExpiration` 必须用 session catalog `spark_catalog`），就必须各自声明构造器并 `super(...)` 转发，样板代码冗余；二是构造器在 JUnit 5 扩展模型中并非参数注入的推荐方式，与 `ParameterizedTestExtension` 的字段注入机制并存时容易引发混淆与维护负担。本次重构统一为字段注入，让基类自身承载参数化扩展与 `@Parameters` 默认实现，子类按需覆盖 `@Parameters` 即可，无需再写构造器。

## 如何达成设计目的

整体思路是将参数化能力下沉到基类 `TestBaseWithCatalog`：在基类上添加 `@ExtendWith(ParameterizedTestExtension.class)`，声明三个 `@Parameter` 注解字段（`catalogName`、`implementation`、`catalogConfig`）接收参数矩阵的列，并提供默认的 `@Parameters` 方法返回 HADOOP catalog 配置。原先在构造器中执行的 spark conf 设置与 validationCatalog 初始化等副作用被迁移到 `@BeforeEach before()` 方法中（因为字段注入发生在构造之后、测试之前）。子类通过覆盖 `@Parameters` 方法提供自己的 catalog 配置，并删除原本的构造器；所有测试方法将 `@Test` 替换为 `@TestTemplate`（`ParameterizedTestExtension` 的硬性要求）。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestBaseWithCatalog.java`

**修改目的**：将参数化测试基类从构造器注入改造为字段注入，使其成为参数化扩展的承载主体。

**工作逻辑**：
- 在类上添加 `@ExtendWith(ParameterizedTestExtension.class)`，使该基类（及其所有子类）成为参数化测试类。
- 新增默认 `@Parameters` 静态方法，返回 HADOOP catalog 配置矩阵（作为子类可覆盖的默认值）。
- 将原先 `final` 的 `catalogName`、`catalogConfig` 等字段改为非 final，并新增 `@Parameter(index = 0/1/2)` 注解字段 `catalogName`、`implementation`、`catalogConfig`，由扩展在每次测试调用前注入参数矩阵对应列的值。
- `validationCatalog`、`validationNamespaceCatalog`、`tableIdent`、`tableName` 改为非 final（因为它们依赖注入后的 `catalogName`，需在 `@BeforeEach` 中初始化，无法在构造期赋值）。
- 删除三个构造器（无参、`SparkCatalogConfig` 入参、三元组入参）。
- 新增 `@BeforeEach public void before()` 方法，将原构造器中的 spark conf 设置（`spark.conf().set("spark.sql.catalog." + catalogName, implementation)`、按 config 逐项设置、对 hadoop 类型补充 warehouse 设置）与 validationCatalog/validationNamespaceCatalog 初始化逻辑迁移至此，确保在字段注入完成后、每个测试方法执行前运行。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/CatalogTestBase.java`

**修改目的**：移除子抽象基类上冗余的构造器，将其参数化能力上移到 `TestBaseWithCatalog`。

**工作逻辑**：
- 将 `parameters()` 方法从 `public` 改为 `protected`，以便子类按需覆盖。
- 删除 `CatalogTestBase(SparkCatalogConfig config)` 与 `CatalogTestBase(String catalogName, String implementation, Map<String,String> config)` 两个构造器（它们只是转发到 `super`，现在基类已无对应构造器，参数化通过字段注入完成）。
- 移除不再使用的 `java.util.Map` import。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkStagedScan.java`

**修改目的**：移除子测试类中转发参数的冗余构造器。

**工作逻辑**：删除 `TestSparkStagedScan(String catalogName, String implementation, Map<String,String> config)` 构造器及其 `super(...)` 调用，并移除不再使用的 `java.util.Map` import。该类继承 `CatalogTestBase`，参数化由基类字段注入完成，无需自身声明构造器。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkTable.java`

**修改目的**：移除子测试类中转发参数的冗余构造器。

**工作逻辑**：删除 `TestSparkTable(String catalogName, String implementation, Map<String,String> config)` 构造器及 `super(...)` 调用，并移除不再使用的 `java.util.Map` import。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestMetadataTableReadableMetrics.java`

**修改目的**：将子类从「构造器指定 HIVE catalog」改造为「覆盖 `@Parameters` 指定 HIVE catalog」，并适配 `@TestTemplate`。

**工作逻辑**：
- 删除原 `TestMetadataTableReadableMetrics()` 构造器（其内部调用 `super(SparkCatalogConfig.HIVE)`，因为只有 SparkCatalog 支持 metadata table SQL 查询）。
- 新增 `@Parameters` 静态方法，返回仅含 HIVE catalog 配置的参数矩阵，注释说明「only SparkCatalog supports metadata table sql queries」。
- 将所有测试方法上的 `@Test` 替换为 `@TestTemplate`（`ParameterizedTestExtension` 要求参数化测试方法必须用 `@TestTemplate`）。
- 新增 `org.apache.iceberg.Parameters` import。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkCatalogCacheExpiration.java`

**修改目的**：将子类从「构造器指定 session catalog」改造为「覆盖 `@Parameters` 指定 session catalog」，并适配 `@TestTemplate`。

**工作逻辑**：
- 删除原无参构造器 `TestSparkCatalogCacheExpiration()`（其内部调用 `super(sessionCatalogName, sessionCatalogImpl, sessionCatalogConfig)`）。
- 移除冗余的 `sessionCatalogName`、`sessionCatalogImpl` 常量字段（其值直接内联到 `@Parameters` 矩阵中）。
- 新增 `@Parameters` 静态方法，返回包含 `spark_catalog`、`SparkSessionCatalog.class.getName()`、`sessionCatalogConfig` 的参数矩阵。
- 将所有测试方法上的 `@Test` 替换为 `@TestTemplate`。
- 新增 `org.apache.iceberg.Parameters` import。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWriterV2.java`

**修改目的**：适配 `ParameterizedTestExtension`，将测试方法注解从 `@Test` 替换为 `@TestTemplate`。

**工作逻辑**：该类继承 `TestBaseWithCatalog`，基类已参数化，因此其 6 个测试方法（`testMergeSchemaFailsWithoutWriterOption`、`testMergeSchemaWithoutAcceptAnySchema`、`testMergeSchemaSparkProperty`、`testMergeSchemaIcebergProperty`、`testWriteWithCaseSensitiveOption` 等）的 `@Test` 全部替换为 `@TestTemplate`，import 由 `org.junit.jupiter.api.Test` 改为 `org.junit.jupiter.api.TestTemplate`。无需自定义 `@Parameters`，复用基类默认的 HADOOP catalog 配置。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestDataSourceOptions.java`

**修改目的**：适配 `ParameterizedTestExtension`，将测试方法注解从 `@Test` 替换为 `@TestTemplate`。

**工作逻辑**：该类继承 `TestBaseWithCatalog`，复用基类默认 HADOOP catalog 参数。10 个测试方法（`testWriteFormatOptionOverridesTableProperties`、`testNoWriteFormatOption`、`testHadoopOptions`、`testSplitOptionsOverridesTableProperties`、`testIncrementalScanOptions`、`testMetadataSplitSizeOptionOverrideTableProperties`、`testDefaultMetadataSplitSize`、`testExtraSnapshotMetadata`、`testExtraSnapshotMetadataWithSQL`、`testExtraSnapshotMetadataWithDelete`）的 `@Test` 全部替换为 `@TestTemplate`，import 相应调整。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReadMetrics.java`

**修改目的**：适配 `ParameterizedTestExtension`，将测试方法注解从 `@Test` 替换为 `@TestTemplate`。

**工作逻辑**：该类继承 `TestBaseWithCatalog`，复用基类默认 HADOOP catalog 参数。3 个测试方法（`testReadMetricsForV1Table`、`testReadMetricsForV2Table`、`testDeleteMetrics`）的 `@Test` 替换为 `@TestTemplate`，import 相应调整。

## 小结

本提交通过将 Spark 3.5 测试基类 `TestBaseWithCatalog` 从构造器注入改造为基于 `@Parameter` 字段注入的参数化模式，使基类自身承载 `ParameterizedTestExtension` 与默认 `@Parameters`，子类只需按需覆盖 `@Parameters` 指定 catalog 配置、并将测试方法改为 `@TestTemplate`，从而消除了所有子类中冗余的构造器转发样板代码。重构统一了参数化测试的写法，降低了新增测试样例的门槛，也让不同 catalog 配置的测试组合更易于表达与维护。
