# 提交 2076：Spark 3.4: Migrate sql tests to JUnit5

## 提交信息

- **序号**：2076 / 4088
- **哈希**：60def0857dc88d2147f860413efcb6a0415311ae
- **短哈希**：60def0857
- **日期**：2025-05-05 11:59:19 +0200
- **作者**：Tom Tanaka
- **提交说明**：Spark 3.4: Migrate sql tests to JUnit5 (#12945)
- **PR/Issue**：#12945

## 总体目的

Iceberg 的 Spark 3.4 测试套件此前仍在使用 JUnit 4 框架，而项目其他部分（以及 Spark 3.5 模块）已逐步迁移到 JUnit 5。JUnit 4 使用构造器参数化（继承 `SparkCatalogTestBase` 并通过构造器接收 `catalogName`、`implementation`、`config` 参数），而 JUnit 5 提供了更现代的扩展机制（`@ExtendWith`）和参数化测试支持（`@TestTemplate`、`@Parameter`、`@Parameters`）。JUnit 4 的 `Assert.assertEquals` 也被 AssertJ 的流式断言 `assertThat(...).isEqualTo(...)` 取代，后者提供更好的可读性和失败信息。

本提交将 Spark 3.4 模块中 `spark/sql` 包下的一组写入测试从 JUnit 4 迁移到 JUnit 5，使其与 Spark 3.5 模块的测试基础设施保持一致。迁移涉及分区写入、非分区写入、分支写入、WAP 分支写入、CTAS 写入等测试类。

同时，本提交还对 Spark 3.5 模块中对应的测试基类做了少量调整：为其添加 `@ExtendWith(ParameterizedTestExtension.class)` 注解，确保参数化测试扩展被正确激活。

## 如何达成设计目的

迁移的核心设计是将 JUnit 4 的测试模式转换为 JUnit 5 模式，关键组件协作关系如下：

- **`ParameterizedTestExtension`**：Iceberg 自定义的 JUnit 5 扩展，替代 JUnit 4 的 `SparkCatalogTestBase` 构造器参数化机制，通过 `@ExtendWith` 激活。
- **`@TestTemplate`**：JUnit 5 注解，替代 JUnit 4 的 `@Test`，用于标记参数化测试方法，使每个参数组合执行一次。
- **`@Parameter` / `@Parameters`**：用于声明参数化测试的参数来源和字段注入，替代 JUnit 4 构造器参数。
- **`CatalogTestBase` / `TestBaseWithCatalog`**：JUnit 5 版本的测试基类，替代 JUnit 4 的 `SparkCatalogTestBase` / `SparkTestBaseWithCatalog`。
- **AssertJ `assertThat`**：替代 JUnit 4 的 `Assert.assertEquals`，提供流式断言。

## 修改详情

### Spark 3.4 测试文件（8 个文件，主体迁移）

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/PartitionedWritesTestBase.java` (修改, +37/-36 lines)

**修改目的**：将分区写入测试基类从 JUnit 4 迁移到 JUnit 5。

**工作逻辑**：
- 将 import 从 JUnit 4（`org.junit.Before/After/Test/Assert`、`SparkCatalogTestBase`）替换为 JUnit 5（`org.junit.jupiter.api.BeforeEach/AfterEach/TestTemplate`、`ParameterizedTestExtension`、`CatalogTestBase`）和 AssertJ。
- 添加 `@ExtendWith(ParameterizedTestExtension.class)` 类注解。
- 移除构造器（不再需要通过构造器传递参数），改为继承 `CatalogTestBase`。
- `@Before` → `@BeforeEach`，`@After` → `@AfterEach`，`@Test` → `@TestTemplate`。
- 所有 `Assert.assertEquals(msg, expected, actual)` → `assertThat(actual).as(msg).isEqualTo(expected)`。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/UnpartitionedWritesTestBase.java` (修改, +37/-36 lines)

**修改目的**：将非分区写入测试基类从 JUnit 4 迁移到 JUnit 5，改动模式与 `PartitionedWritesTestBase` 完全一致。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestPartitionedWrites.java` (修改, +1/-8 lines)

**修改目的**：简化子类，移除 JUnit 4 构造器。

**工作逻辑**：
移除了构造器和 `Map` import，类体简化为 `public class TestPartitionedWrites extends PartitionedWritesTestBase {}`，因为 JUnit 5 参数化通过注解而非构造器实现。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestUnpartitionedWrites.java` (修改, +1/-8 lines)

**修改目的**：同上，简化非分区写入测试子类。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestPartitionedWritesToBranch.java` (修改, +1/-8 lines)

**修改目的**：简化分区写入到分支测试子类，移除构造器。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestUnpartitionedWritesToBranch.java` (修改, +8/-6 lines)

**修改目的**：将非分区写入到分支测试子类迁移到 JUnit 5。

**工作逻辑**：
移除构造器，添加 `@ExtendWith(ParameterizedTestExtension.class)` 注解，将 `@Override` 的 setup/teardown 方法注解从 `@Before`/`@After` 改为 `@BeforeEach`/`@AfterEach`，`@Test` 改为 `@TestTemplate`。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestPartitionedWritesToWapBranch.java` (修改, +10/-12 lines)

**修改目的**：将分区写入到 WAP 分支测试子类迁移到 JUnit 5，移除 `commitTarget()` override（移至基类或简化）。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestPartitionedWritesAsSelect.java` (修改, +43/-25 lines)

**修改目的**：将 CTAS（Create Table As Select）分区写入测试迁移到 JUnit 5，这是改动最大的文件。

**工作逻辑**：
- 从继承 `SparkTestBaseWithCatalog` 改为继承 `TestBaseWithCatalog`。
- 使用 `@Parameter(index = 3)` 注解注入 `targetTable` 字段，并通过 `@Parameters(name = "...")` 静态方法提供参数（catalogName、implementation、config、targetTable 四个参数）。
- `@Before`/`@After`/`@Test` → `@BeforeEach`/`@AfterEach`/`@TestTemplate`。
- `Assert.assertEquals` → AssertJ `assertThat`。

### Spark 3.5 测试文件（6 个文件，小调整）

以下文件均为在已有的 JUnit 5 测试上添加 `@ExtendWith(ParameterizedTestExtension.class)` 注解和对应 import：

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/PartitionedWritesTestBase.java` (修改, +3/-0 lines)

添加 `ParameterizedTestExtension` import 和 `@ExtendWith(ParameterizedTestExtension.class)` 注解。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/UnpartitionedWritesTestBase.java` (修改, +3/-0 lines)

同上。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestPartitionedWritesAsSelect.java` (修改, +3/-0 lines)

同上。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestPartitionedWritesToBranch.java` (修改, +3/-0 lines)

同上。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestUnpartitionedWritesToBranch.java` (修改, +3/-0 lines)

同上。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestPartitionedWritesToWapBranch.java` (修改, +5/-3 lines)

添加 `ParameterizedTestExtension` import 和 `@ExtendWith` 注解，同时移除了不再需要的 `commitTarget()` override 方法。

## 总结

本提交将 Spark 3.4 模块中 `spark/sql` 包的 8 个写入测试文件从 JUnit 4 迁移到 JUnit 5，采用 `ParameterizedTestExtension` 扩展、`@TestTemplate`/`@Parameter`/`@Parameters` 参数化机制和 AssertJ 流式断言，与 Spark 3.5 模块保持一致。同时对 Spark 3.5 的 6 个对应测试文件补充了 `@ExtendWith(ParameterizedTestExtension.class)` 注解，确保参数化扩展正确激活。属于测试基础设施统一化迁移工作。
