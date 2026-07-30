# 提交分析：Spark 3.5: Migrate tests in SQL directory to JUnit5 (#9401)

## 提交信息

- 哈希：53a1c8671dd1b9b93f4a857230008c812d79ddbf
- 短哈希：53a1c8671
- 日期：2024-01-10 17:05:08 +0100
- 作者：Chinmay Bhat (12948588+chinmay-bhat@users.noreply.github.com)
- 说明：Spark 3.5: Migrate tests in SQL directory to JUnit5 (#9401)

## 总体目的

本提交将 Spark 3.5 模块下 `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/` 目录中的全部 26 个测试类从 JUnit 4 迁移到 JUnit 5（Jupiter）。这是 Iceberg 项目跨多个模块、多个提交的"JUnit5 迁移"工作的 Spark 3.5 SQL 测试部分。JUnit 4 已进入维护模式，社区新功能与工具链生态（如更现代的参数化测试、扩展模型、与 AssertJ/Archunit 等库的更好集成）都围绕 JUnit 5 展开，迁移后能让测试代码与现代实践对齐，并为后续引入更复杂测试场景（如动态测试、条件执行、嵌套测试）扫清障碍。

迁移涉及两类基础测试类的切换：`SparkCatalogTestBase` → `CatalogTestBase`，`SparkTestBaseWithCatalog` → `TestBaseWithCatalog`。这两个新的基类在前序提交中已建立（本提交不改动基类），它们用 JUnit 5 的 `@TestTemplate` + `@Parameter` / `@Parameters` 自定义扩展替代了 JUnit 4 的 `Parameterized` runner 与构造器注入。这意味着子类不再通过构造器接收 `(catalogName, implementation, config)` 三元组，而是通过 `@Parameter(index=N)` 注解把 `@Parameters` 工厂方法返回的 `Object[][]` 中每一行注入到对应字段，由 `@TestTemplate` 驱动每个测试方法在每组参数下各执行一次。

更深层的目的是统一断言风格。迁移前测试混用 JUnit 4 的 `Assert.assertEquals/assertTrue/...`（消息参数在前）与 AssertJ 的 `Assertions.assertThatThrownBy`（消息链式）；迁移后全面切到 AssertJ 的流式 API（`assertThat(...).as("描述").isEqualTo(...)`、`assertThatThrownBy(...).isInstanceOf(...).hasMessage(...)`），并大量使用静态导入让代码更简洁。AssertJ 的链式断言可读性更好（"actual vs expected"的顺序自然）、错误信息更丰富（自动生成 diff）、对集合/异常的断言能力更强，是现代 Java 测试的事实标准。

迁移还顺带修正了一些小细节：例如 `TestSelect` 把原本在构造器里注册 `ScanEvent` listener 的副作用移到 `@BeforeEach` 方法里（更符合"每个测试独立 setup"的语义），并新增 `binaryTableName` 字段通过 `@Parameter(index=3)` 注入，把"按 catalog 名拼表名"的逻辑从构造器上移到 `@Parameters` 工厂方法，使参数化数据集中可见、可维护。`TestNamespaceSQL` 类似地把 `fullNamespace`、`isHadoopCatalog` 改为 `@Parameter` 注入字段，并显式枚举 HIVE/HADOOP/SPARK 三种 catalog 配置（用 `SparkCatalogConfig` 枚举），相比原来"在构造器里根据 catalogName 推导"更清晰。

## 如何达成设计目的

迁移以"机械替换 + 风格统一"为主，对每个测试类执行相同的转换模板：
1. 基类切换：`extends SparkCatalogTestBase` → `extends CatalogTestBase`（或 `SparkTestBaseWithCatalog` → `TestBaseWithCatalog`），删除原构造器；
2. 参数化改造：把构造器参数转为 `@Parameter(index=N)` 注解字段，新增 `@Parameters(name=...)` 静态方法返回 `Object[][]`，每行用 `SparkCatalogConfig.HIVE/HADOOP/SPARK` 枚举提供 `catalogName/implementation/properties`；
3. 生命周期注解：`@Before` → `@BeforeEach`、`@After` → `@AfterEach`、`@Test` → `@TestTemplate`（参数化测试方法用 `@TestTemplate` 而非 `@Test`，因为 `@Parameter` 扩展需要 template 驱动）；
4. 断言替换：`Assert.assertEquals(msg, expected, actual)` → `assertThat(actual).as(msg).isEqualTo(expected)`、`Assert.assertTrue(msg, cond)` → `assertThat(cond).as(msg).isTrue()`、`Assert.assertNull(msg, o)` → `assertThat(o).as(msg).isNull()`、`Assume.assumeTrue/assumeFalse` → `assumeThat(...).isTrue()/isFalse()/isEqualTo()`、`Assertions.assertThatThrownBy` → 静态导入 `assertThatThrownBy`；
5. import 清理：移除 `org.junit.*`、`java.util.Map`（不再用构造器）等无用 import，新增 `org.junit.jupiter.api.*`、`org.apache.iceberg.Parameter/Parameters`、`org.apache.iceberg.spark.{CatalogTestBase,TestBaseWithCatalog,SparkCatalogConfig}`、AssertJ 静态导入。

由于转换高度机械化，26 个文件的 diff 总体只是行数相近的"删旧增新"（1502 增 / 1462 删），不涉及测试逻辑本身的实质性改变——所有测试用例的语义、覆盖场景、断言点都保持不变。

## 修改详情

由于 26 个文件采用同一套迁移模板，下面按文件类型分组描述，并对代表性文件给出关键差异。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java
**修改目的**：把覆盖 SELECT/时间旅行/投影/过滤下推等场景的综合测试类迁移到 JUnit5。
**工作逻辑**：
- 基类 `SparkCatalogTestBase` → `CatalogTestBase`，删除原 `(catalogName, implementation, config)` 构造器。
- 新增 `@Parameter(index=3) private String binaryTableName` 字段（原为构造器局部变量）。
- 新增 `@Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}, binaryTableName = {3}")` 静态方法，返回 3 行 `Object[][]`，分别对应 HIVE / HADOOP / SPARK 三种 catalog，每行额外带 `binaryTableName`（spark_catalog 用 `default.binary_table`，其他 catalog 用 `<catalogName>.default.binary_table`）。
- 把原本在构造器里注册 `ScanEvent` listener 的代码移入 `@BeforeEach createTables()`，让每个测试方法独立注册。
- 所有 `@Test` → `@TestTemplate`，`@Before`/`@After` → `@BeforeEach`/`@AfterEach`。
- `Assert.assertEquals(msg, expected, actual)` → `assertThat(actual).as(msg).isEqualTo(expected)`；`Assume.assumeFalse("...", "spark_catalog".equals(catalogName))` → `assumeThat(catalogName).as("...").isNotEqualTo("spark_catalog")`；`Assertions.assertThatThrownBy(...)` → 静态导入 `assertThatThrownBy(...)`。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestNamespaceSQL.java
**修改目的**：迁移 namespace SQL 测试，同时把派生字段改为参数化注入。
**工作逻辑**：
- 原构造器里基于 catalogName 计算的 `fullNamespace` 与 `isHadoopCatalog` 改为 `@Parameter(index=3)` / `@Parameter(index=4)` 注入字段。
- `@Parameters` 静态方法返回 3 行配置，每行显式给出 `fullNamespace`（spark_catalog 用 `NS.toString()`，其他用 `catalogName + "." + NS`）与 `isHadoopCatalog`（仅 HADOOP 行为 true）。
- `Assume.assumeFalse("Hadoop has no default namespace configured", isHadoopCatalog)` → `assumeThat(isHadoopCatalog).as("...").isFalse()`，其余断言同样改为 AssertJ 流式。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestCreateTable.java
**修改目的**：迁移建表测试，演示 assumeTrue 的迁移。
**工作逻辑**：基类切换 + `@After` → `@AfterEach` + `@Test` → `@TestTemplate`。`Assume.assumeTrue("Hadoop has no default namespace configured", "testhadoop".equals(catalogName))` → `assumeThat(catalogName).as("...").isEqualTo("testhadoop")`。`Assert.assertFalse/assertTrue/assertNotNull/assertNull/assertEquals` 全部改写为 AssertJ。例如 `Assert.assertEquals("Should have the expected schema", expectedSchema, table.schema().asStruct())` → `assertThat(table.schema().asStruct()).as("Should have the expected schema").isEqualTo(expectedSchema)`，参数顺序从"消息、期望、实际"变为"实际、消息、期望"，可读性更自然。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/PartitionedWritesTestBase.java
**修改目的**：迁移分区写入测试基类（abstract），被子类 `TestPartitionedWrites`、`TestPartitionedWritesAsSelect`、`TestPartitionedWritesToBranch`、`TestPartitionedWritesToWapBranch` 继承。
**工作逻辑**：abstract 类去掉构造器，改 `extends CatalogTestBase`，`@Before`/`@After`/`@Test` → `@BeforeEach`/`@AfterEach`/`@TestTemplate`。所有 `Assert.assertEquals(msg, expected, actual)` 改为 `assertThat(actual).as(msg).isEqualTo(expected)`。基类迁移后所有子类自动获益，无需逐个改造（子类只需少量注解调整）。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/UnpartitionedWritesTestBase.java
**修改目的**：迁移非分区写入测试基类，与 `PartitionedWritesTestBase` 平行。
**工作逻辑**：同上模板。注意 `Assume.assumeTrue(tableName.equals(commitTarget()))` → `assumeThat(tableName.equals(commitTarget())).isTrue()`（这里 `assumeThat` 接收 boolean 表达式，是 AssertJ 对 boolean 的假设断言）。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkTruncateFunction.java
**修改目的**：迁移 Iceberg `system.truncate` Spark 函数测试（本提交中行数最多的文件）。
**工作逻辑**：基类 `SparkTestBaseWithCatalog` → `TestBaseWithCatalog`，无参数化（不需要多 catalog），`@Before` → `@BeforeEach`，`@Test` → `@TestTemplate`。原 `Assert.assertEquals((byte) 0, scalarSql("..."))` 大量改为 `assertThat(scalarSql("...")).isEqualTo((byte) 0)`，由于 AssertJ 的 `isEqualTo` 接收 `Object`，需要用 `(byte) 0` 字面量保证类型匹配，避免装箱歧义。`Assertions.assertThatThrownBy(...)` → 静态 `assertThatThrownBy(...)`。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestAggregatePushDown.java
**修改目的**：迁移聚合下推测试。
**工作逻辑**：基类切换 + 注解迁移 + 断言迁移。`@Parameters` 通过 `SparkCatalogConfig` 提供 3 种 catalog 配置。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestAlterTable.java
**修改目的**：迁移 ALTER TABLE 测试。
**工作逻辑**：同模板。原 `Assume.assumeFalse/assumeTrue` 全部改为 `assumeThat(...).isFalse()/isTrue()/isEqualTo()`，`Assert.assertThrows` 风格的异常断言改用 `assertThatThrownBy(...).isInstanceOf(...).hasMessage(...)`。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestCreateTableAsSelect.java
**修改目的**：迁移 CTAS 测试。
**工作逻辑**：同模板，覆盖 CREATE TABLE AS SELECT / REPLACE TABLE AS SELECT 等场景。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestDeleteFrom.java
**修改目的**：迁移 DELETE FROM 测试。
**工作逻辑**：同模板。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestDropTable.java
**修改目的**：迁移 DROP TABLE 测试。
**工作逻辑**：同模板。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestFilterPushDown.java
**修改目的**：迁移过滤下推测试。
**工作逻辑**：同模板。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestPartitionedWrites.java
**修改目的**：迁移分区写入测试子类（继承 `PartitionedWritesTestBase`）。
**工作逻辑**：子类只需调整 `@Parameters` 与构造器相关声明（基类已迁移，子类继承基类的注解迁移结果）。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestPartitionedWritesAsSelect.java
**修改目的**：迁移分区写入 As Select 测试子类。
**工作逻辑**：同上。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestPartitionedWritesToBranch.java
**修改目的**：迁移分支分区写入测试子类。
**工作逻辑**：同上。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestPartitionedWritesToWapBranch.java
**修改目的**：迁移 WAP（Write-Audit-Publish）分支分区写入测试子类。
**工作逻辑**：同上。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestRefreshTable.java
**修改目的**：迁移 REFRESH TABLE 测试。
**工作逻辑**：同模板。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkBucketFunction.java
**修改目的**：迁移 Iceberg `system.bucket` Spark 函数测试。
**工作逻辑**：基类 `SparkTestBaseWithCatalog` → `TestBaseWithCatalog`，断言改 AssertJ。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkDaysFunction.java
**修改目的**：迁移 `system.days` 函数测试。
**工作逻辑**：同模板。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkHoursFunction.java
**修改目的**：迁移 `system.hours` 函数测试。
**工作逻辑**：同模板。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkMonthsFunction.java
**修改目的**：迁移 `system.months` 函数测试。
**工作逻辑**：同模板。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkYearsFunction.java
**修改目的**：迁移 `system.years` 函数测试。
**工作逻辑**：同模板。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestStoragePartitionedJoins.java
**修改目的**：迁移存储分区连接（SPJ）测试。
**工作逻辑**：同模板。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestTimestampWithoutZone.java
**修改目的**：迁移无时区 timestamp 测试。
**工作逻辑**：同模板。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestUnpartitionedWrites.java
**修改目的**：迁移非分区写入测试子类。
**工作逻辑**：子类继承 `UnpartitionedWritesTestBase`，迁移基类后子类做最小化调整。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestUnpartitionedWritesToBranch.java
**修改目的**：迁移分支非分区写入测试子类。
**工作逻辑**：同上。

## 小结

本提交是 Spark 3.5 SQL 测试目录的 JUnit5 迁移，覆盖 26 个测试类、约 3000 行变更（1502 增 / 1462 删）。迁移高度机械化：基类切换（`SparkCatalogTestBase`/`SparkTestBaseWithCatalog` → `CatalogTestBase`/`TestBaseWithCatalog`）、注解替换（`@Before/@After/@Test` → `@BeforeEach/@AfterEach/@TestTemplate`）、断言风格统一（JUnit4 Assert + AssertJ 混用 → 全面 AssertJ 流式 + 静态导入）、参数化改造（构造器注入 → `@Parameter`/`@Parameters`）。迁移后测试逻辑与覆盖场景保持不变，但代码风格更现代、可读性更好、错误信息更丰富，并与 Iceberg 跨模块的 JUnit5 迁移工作保持一致。
