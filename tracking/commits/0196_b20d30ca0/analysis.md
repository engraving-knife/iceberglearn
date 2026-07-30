# 提交 0196：Spark: Create base classes for migration to JUnit5 (#9129)

## 提交信息

- **序号**：0196 / 4088
- **哈希**：b20d30ca04ddc19e440036b0c554f0b16ec61a1c
- **短哈希**：b20d30ca0
- **日期**：2023-11-24
- **作者**：Tom Tanaka
- **提交说明**：Spark: Create base classes for migration to JUnit5 (#9129)
- **PR/Issue**：#9129

## 总体目的

这个提交为 Spark v3.5 测试套件从 JUnit 4 迁移到 JUnit 5（Jupiter）铺设基础设施。Iceberg 项目长期以来使用 JUnit 4 作为测试框架（`org.junit.Assert`、`@BeforeClass/@AfterClass`、`@Before/@After` 等注解），而 JUnit 5 提供了更现代的扩展模型（`@ExtendWith`、`@RegisterExtension`）、参数化测试（`@ParameterizedTest` + `@MethodSource`）、生命周期注解（`@BeforeAll/@AfterAll`、`@BeforeEach/@AfterEach`）以及与 AssertJ 流式断言更自然的集成。迁移是一个渐进过程，但首先要有一套与旧 `SparkTestBase`（JUnit 4）等价能力的新基类，后续才能逐个迁移具体测试类。

本提交的核心动机是：旧有的 JUnit 4 测试基类（如 `SparkTestBase`）与 JUnit 5 不兼容，直接迁移每个测试类会需要重复改造大量样板代码（SparkSession 启停、Hive Metastore 启停、catalog 配置、临时目录管理、SQL 执行辅助等）。通过先建立分层的新基类体系（`TestBase` → `TestBaseWithCatalog` → `CatalogTestBase`），可以让后续每个测试类的迁移变成"换 import + 改父类 + 替换断言"的机械操作，显著降低迁移成本与出错风险。

该提交引入了三个新的抽象基类、把断言工具从 JUnit 4 `Assert` 切换到 AssertJ `Assertions`，并完成了首个具体测试类 `TestSparkFileRewriter` 的迁移作为示范，验证了新基类体系的可用性。这对 Iceberg 测试基础设施的现代化演进具有奠基意义。

## 如何达成设计目的

整体设计采用三层继承体系，把测试基础设施按职责分层拆分：最底层 `TestBase`（继承 `SparkTestHelperBase`）负责 SparkSession 与 Hive Metastore 的生命周期及通用 SQL 辅助方法；中间层 `TestBaseWithCatalog` 负责仓库目录与 catalog 配置（构造时绑定 catalogName/implementation/properties 并写入 spark conf）；顶层 `CatalogTestBase` 提供参数化测试所需的 `parameters()` 静态方法（生成 HIVE/HADOOP/SPARK 三种 catalog 配置的参数流）。同时把 `SparkTestHelperBase` 的断言全部改为 AssertJ 风格，并将 `TestSparkFileRewriter` 作为首个迁移样例从旧的 `SparkTestBase` 切换到新的 `TestBase`。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestBase.java`（新增，287 行）

**修改目的**：创建 JUnit 5 体系下的核心测试基类，替代旧 JUnit 4 的 `SparkTestBase`，集中承载 SparkSession/Hive Metastore 的启停与所有通用测试辅助逻辑。

**工作逻辑**：
- 继承 `SparkTestHelperBase`，声明静态字段 `metastore`、`hiveConf`、`spark`、`sparkContext`、`catalog`。
- 用 JUnit 5 的 `@BeforeAll` / `@AfterAll`（替代 JUnit 4 的 `@BeforeClass`/`@AfterClass`）实现 `startMetastoreAndSpark()` 与 `stopMetastoreAndSpark()`：启动 `TestHiveMetastore`，构建 `SparkSession`（local[2]、动态分区覆写、Hive 支持），加载 `HiveCatalog` 并创建 `default` namespace。
- 提供大量辅助方法：`sql()` 执行 SparkSQL 并返回 `List<Object[]>`、`scalarSql()` 取单值、`withSQLConf()` 临时修改并恢复 `SQLConf`（校验静态配置不可改）、`withDefaultTimeZone()` 临时切换时区、`withUnavailableFiles()`/`withUnavailableLocations()` 临时移动数据文件模拟文件缺失场景、`executeAndKeepPlan()` 通过 `QueryExecutionListener` 捕获执行计划（处理 `AdaptiveSparkPlanExec`）、`jsonToDF()`/`append()` 数据写入辅助、`tablePropsAsString()` 拼接表属性。
- 定义函数式接口 `Action` 供上述 lambda 风格辅助方法使用。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestBaseWithCatalog.java`（新增，131 行）

**修改目的**：在 `TestBase` 之上增加 catalog 配置层，管理 warehouse 临时目录并按构造参数注册 Spark catalog，使每个测试子类可以针对特定 catalog（HIVE/HADOOP/SPARK）运行。

**工作逻辑**：
- 使用 JUnit 5 的 `@TempDir`（替代手动 `File.createTempFile`）管理 warehouse 目录，`@BeforeAll createWarehouse()` / `@AfterAll dropWarehouse()` 通过 AssertJ 断言删除结果。
- 构造方法支持传入 `SparkCatalogConfig` 枚举或 `(catalogName, implementation, config)` 三元组；构造时将 catalog 注册到 `spark.sql.catalog.<name>` 系列配置，对 hadoop 类型 catalog 额外设置 warehouse 路径，并初始化 `validationCatalog`（HIVE 走 `HadoopCatalog`，否则复用父类 catalog）。
- 提供 `tableName()`/`commitTarget()`/`selectTarget()` 处理 spark_catalog 与命名 catalog 的表名前缀差异、`cachingCatalogEnabled()` 查询缓存开关、`configurePlanningMode()` 通过 ALTER TABLE 设置 `DATA_PLANNING_MODE`/`DELETE_PLANNING_MODE`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/CatalogTestBase.java`（新增，51 行）

**修改目的**：为参数化测试提供 catalog 配置参数源，便于后续用 `@ParameterizedTest + @MethodSource("parameters")` 让同一测试在多种 catalog 下运行。

**工作逻辑**：定义静态 `parameters()` 方法返回 `Stream<Arguments>`，依次产出 HIVE、HADOOP、SPARK 三种 `SparkCatalogConfig` 的 `(catalogName, implementation, properties)` 参数组；提供两个委托给父类 `TestBaseWithCatalog` 的构造方法。注释说明这些参数被独立出来，是为了避免修改时牵动大量测试套件。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/SparkTestHelperBase.java`（修改）

**修改目的**：把该辅助基类中的断言从 JUnit 4 `Assert.assertEquals`/`assertArrayEquals` 切换为 AssertJ `Assertions.assertThat(...).hasSameSizeAs(...)`/`.isEqualTo(...)`，与新基类的断言风格统一。

**工作逻辑**：`assertEquals(String, List, List)` 改为先用 `.hasSameSizeAs(expectedRows)` 校验行数再逐行比对；`assertEquals(String, Object[], Object[])` 同理用 `.hasSameSizeAs(expectedRow)`，并把 `Assert.assertArrayEquals` 的 byte 数组断言改为 `Assertions.assertThat(actualValue).isEqualTo(expectedValue)`，普通值断言改为 `.isEqualTo(expectedValue)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestSparkFileRewriter.java`（修改）

**修改目的**：作为首个迁移到新基类的具体测试类，验证 JUnit 5 基类体系可用并树立迁移范式。

**工作逻辑**：父类从 `SparkTestBase` 改为 `TestBase`；import 把 `org.junit.After`/`org.junit.Assert`/`org.junit.Test` 换成 `org.junit.jupiter.api.AfterEach`/`org.junit.jupiter.api.Test` 与 AssertJ `Assertions`；`@After` 改为 `@AfterEach`；所有 `Assert.assertEquals(message, expected, actual)` 改写为 `Assertions.assertThat(actual).as(message).isEqualTo(expected)` / `.hasSize(n)`（涉及 `validOptions()` 三个 rewriter 的期望选项集合断言、多个 `planFileGroups` 的分组数与文件数断言）。

## 小结

该提交通过建立分层的新 JUnit 5 测试基类体系并完成首个测试类的示范迁移，为 Spark 测试套件从 JUnit 4 全面迁移到 JUnit 5 奠定了基础设施，是 Iceberg 测试基础设施现代化的奠基性一步。
