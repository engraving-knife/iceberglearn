# 提交 2059：Spark 3.4: Migrate tests in sql (#12934)

## 提交信息

- **序号**：2059 / 4088
- **哈希**：f7be3a66fa252915f8b2b2b03bf38ba579004a45
- **短哈希**：f7be3a66f
- **日期**：2025-04-30 11:34:46 +0200
- **作者**：Tom Tanaka
- **提交说明**：Spark 3.4: Migrate tests in sql (#12934)
- **PR/Issue**：#12934

## 总体目的

Iceberg Spark 测试套件正在从 JUnit 4 迁移到 JUnit 5，并从老的 `SparkCatalogTestBase`/`SparkTestBase` 测试基类迁移到新的 `CatalogTestBase`/`TestBase` + `ParameterizedTestExtension` 体系。本提交针对 Spark 3.4 与 Spark 3.5 模块下 `spark/sql` 包内的 9 个测试类完成这次迁移，使这些测试与已经迁移的其他模块保持一致，并最终为后续移除 JUnit 4 与老测试基类扫清障碍。

这是一次纯测试基础设施迁移：测试用例的业务逻辑（断言内容、SQL 语句）保持不变，只更换测试框架 API（注解、生命周期方法、断言工具）与基类。

## 如何达成设计目的

迁移采用统一模式：
1. **基类替换**：`extends SparkCatalogTestBase` → `extends CatalogTestBase`；`SparkTestBase.xxx` 静态字段引用改为 `TestBase.xxx`。
2. **参数化方式**：原 JUnit 4 构造器参数（`catalogName, implementation, config`）改为 `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameters` 工厂方法 + `@Parameter` 字段注入。部分需要额外参数（如 `binaryTableName`）的测试把它加入 parameters 数组。
3. **生命周期注解**：`@BeforeClass` → `@BeforeAll`；`@Before` → `@BeforeEach`；`@After` → `@AfterEach`；`@Test` → `@TestTemplate`。
4. **断言工具**：`org.junit.Assert` 的 `assertEquals`/`assertTrue` 等改为 `org.assertj.core.api.Assertions.assertThat` 与基类提供的 `assertEquals` 工具；异常断言改为 AssertJ 的 `assertThatThrownBy`。
5. **Spark 3.5 同步**：Spark 3.5 中对应文件此前已完成大部分迁移，本次仅补加 `@ExtendWith(ParameterizedTestExtension.class)` 注解，使 3.4 与 3.5 完全对齐。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestAggregatePushDown.java` (修改, 大量行变化)

**修改目的**：迁移到 JUnit 5 + 新基类。

**工作逻辑**：
- `extends SparkCatalogTestBase` → `extends CatalogTestBase`，加 `@ExtendWith(ParameterizedTestExtension.class)`。
- 删除 JUnit 4 构造器。
- `@BeforeClass` → `@BeforeAll`，`@After` → `@AfterEach`，`@Test` → `@TestTemplate`。
- `SparkTestBase.metastore/hiveConf/spark/catalog` 改为 `TestBase.xxx`，并增加 `TestBase.spark.close()` 后再创建新 SparkSession 的逻辑。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestCreateTable.java` (修改)

**修改目的**：同上迁移；该文件改动量较大（303 行）。

**工作逻辑**：
- 基类替换、参数化迁移、生命周期注解迁移。
- 大量 `Assert.assertEquals`/`Assert.assertTrue` 替换为 AssertJ `assertThat`。
- 部分异常断言改为 `assertThatThrownBy`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestCreateTableAsSelect.java` (修改)

**修改目的**：同上迁移。

**工作逻辑**：
基类与注解迁移，断言改写为 AssertJ。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestDeleteFrom.java` (修改)

**修改目的**：同上迁移。

**工作逻辑**：
基类、参数化、注解、断言迁移。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestDropTable.java` (修改)

**修改目的**：同上迁移。

**工作逻辑**：
基类、参数化、注解、断言迁移。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestFilterPushDown.java` (修改)

**修改目的**：同上迁移。

**工作逻辑**：
基类、参数化、注解、断言迁移。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestNamespaceSQL.java` (修改)

**修改目的**：同上迁移；改动量较大（261 行）。

**工作逻辑**：
基类、参数化、注解、断言迁移；大量 namespace 相关断言改写为 AssertJ。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestRefreshTable.java` (修改)

**修改目的**：同上迁移。

**工作逻辑**：
基类、参数化、注解、断言迁移。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (修改, +216/-行)

**修改目的**：同上迁移；本文件额外引入 `@Parameter` 字段注入 `binaryTableName`，并通过 `@Parameters` 工厂为每个 catalog 配置生成对应的 binaryTableName。

**工作逻辑**：
- `@Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}, binaryTableName = {3}")` 工厂方法返回 HIVE/HADOOP/SPARK 三组配置及对应 binaryTableName。
- `@Parameter(index = 3) private String binaryTableName;` 注入第 4 个参数。
- `@BeforeEach createTables()` 中将 scan event listener 注册与建表合并（原 `@Before` 拆成构造器 + `@Before`）。
- 注解与断言迁移。

### Spark 3.5 对应 8 个文件 (修改, 各 +3 行左右)

**修改目的**：为 Spark 3.5 中已完成大部分迁移的测试类补加 `@ExtendWith(ParameterizedTestExtension.class)` 注解，使其与新体系完全对齐。

**工作逻辑**：
在 `TestAggregatePushDown`、`TestCreateTable`、`TestCreateTableAsSelect`、`TestDeleteFrom`、`TestDropTable`、`TestNamespaceSQL`、`TestRefreshTable`、`TestSelect` 顶部添加 `@ExtendWith(ParameterizedTestExtension.class)` 注解（部分文件还涉及少量 import 与字段调整）。

## 总结

本提交将 Spark 3.4（及对齐 Spark 3.5）`spark/sql` 包下 9 个测试类从 JUnit 4 + `SparkCatalogTestBase`/`SparkTestBase` 迁移到 JUnit 5 + `CatalogTestBase`/`TestBase` + `ParameterizedTestExtension`。迁移涉及基类替换、参数化方式重构、生命周期与测试注解更换、断言工具改写为 AssertJ。业务逻辑不变，是测试基础设施现代化的一部分。
