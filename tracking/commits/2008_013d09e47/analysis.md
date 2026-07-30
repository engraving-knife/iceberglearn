# 提交 2008：Spark 3.4: Migrate ExtensionsTestBase-related remaining tests

## 提交信息

- **序号**：2008 / 4088
- **哈希**：013d09e473faada1a3b21c155964ec4d1254d69d
- **短哈希**：013d09e47
- **日期**：2025-04-16 16:51:46 +0200
- **作者**：Tom Tanaka
- **提交说明**：Spark 3.4: Migrate ExtensionsTestBase-related remaining tests (#12813)
- **PR/Issue**：#12813

## 总体目的

本提交是 Iceberg 项目 Spark 3.4 模块 JUnit 4 到 JUnit 5 迁移工作的收尾部分，处理最后剩余的三个测试类：`TestRewriteTablePathProcedure`、`TestSparkExecutorCache` 和 `TestViews`。

随着这三个测试类完成迁移，所有继承自 `SparkExtensionsTestBase` 的测试类都已迁移到新的 `ExtensionsTestBase` 基类，因此旧的 `SparkExtensionsTestBase.java` 基类不再被任何测试引用，可以安全删除。这标志着 Spark 3.4 模块 ExtensionsTestBase 相关测试的 JUnit 5 迁移工作全面完成。

## 如何达成设计目的

1. **测试迁移**：将剩余 3 个测试类从 JUnit 4 迁移到 JUnit 5，采用与之前提交相同的迁移模式。

2. **删除旧基类**：删除不再被使用的 `SparkExtensionsTestBase.java`。

3. **Spark 3.5 测试同步**：对 Spark 3.5 中的 `TestRewriteTablePathProcedure` 进行小幅调整以保持一致性。

## 修改详情

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkExtensionsTestBase.java` (删除, -71 lines)

**修改目的**：删除不再被使用的旧 JUnit 4 测试基类。

**工作逻辑**：
该文件是旧的 JUnit 4 测试基类，继承自 `SparkCatalogTestBase`，提供 `@BeforeClass` 方法启动 Hive Metastore 和 Spark Session。随着所有测试类迁移到 `ExtensionsTestBase`（JUnit 5 基类），此文件不再被任何测试引用，因此删除。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteTablePathProcedure.java` (修改, +94/-... lines)

**修改目的**：将重写表路径过程测试从 JUnit 4 迁移到 JUnit 5。

**工作逻辑**：采用标准 JUnit 5 迁移模式：
- `extends SparkExtensionsTestBase` → `@ExtendWith(ParameterizedTestExtension.class) extends ExtensionsTestBase`
- 注解替换：`@Test` → `@TestTemplate`，`@Before` → `@BeforeEach`，`@After` → `@AfterEach`
- 构造函数注入 → `@Parameter` 字段注入
- `@Parameterized.Parameters` → `@Parameters`

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSparkExecutorCache.java` (修改, +38/-... lines)

**修改目的**：将 Spark 执行器缓存测试从 JUnit 4 迁移到 JUnit 5。

**工作逻辑**：采用标准 JUnit 5 迁移模式。移除构造函数，添加 `@ExtendWith(ParameterizedTestExtension.class)` 注解，更换基类和注解。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java` (修改, +201/-... lines)

**修改目的**：将视图测试从 JUnit 4 迁移到 JUnit 5。

**工作逻辑**：
采用标准 JUnit 5 迁移模式，同时包含以下额外修改：
- `@Before` → `@BeforeEach` 并添加 `@Override` 和 `super.before()` 调用，确保父类初始化逻辑被执行
- `@After` → `@AfterEach`，并新增 Spark session catalog 重置逻辑：`spark.sessionState().catalogManager().reset()` 和 `spark.conf().unset("spark.sql.catalog.spark_catalog")`，确保测试间状态隔离
- 将 `REST_SERVER_RULE.uri()` 改为 `restCatalog.properties().get(CatalogProperties.URI)`，适配新的基类中 REST 目录的获取方式
- 移除 `IOException` import，改用 `Paths` 等

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteTablePathProcedure.java` (修改, +13/-... lines)

**修改目的**：对 Spark 3.5 中的对应测试进行小幅调整以保持一致性。

**工作逻辑**：小幅调整以同步 Spark 3.4 的变更。

## 总结

本提交完成了 Spark 3.4 模块 ExtensionsTestBase 相关测试 JUnit 5 迁移的收尾工作，处理了最后 3 个测试类（TestRewriteTablePathProcedure、TestSparkExecutorCache、TestViews），并删除了不再被使用的旧基类 `SparkExtensionsTestBase.java`。这标志着 Spark 3.4 模块该系列测试的 JUnit 5 迁移全面完成。
