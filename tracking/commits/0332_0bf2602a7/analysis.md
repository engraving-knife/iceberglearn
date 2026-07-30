# 提交 0332：Flink 1.17: Create JUnit5 version of TestFlinkScan (#9185)

## 提交信息

- **序号**：0332 / 4088
- **哈希**：0bf2602a7a79ca9d525832f1e72c99b053d6b2af
- **短哈希**：0bf2602a7
- **日期**：2024-01-05 13:04:01 +0100
- **作者**：CG
- **提交说明**：Flink 1.17: Create JUnit5 version of TestFlinkScan (#9185)
- **PR/Issue**：#9185

## 总体目的

本提交将 Iceberg Flink 1.17 模块的测试套件从 JUnit 4 迁移到 JUnit 5（Jupiter），核心是把 `TestFlinkScan` 及其依赖的辅助类、子类全套测试改为基于 JUnit 5 的写法。这是 Iceberg 项目整体向 JUnit 5 迁移这一长期工程在 Flink 1.17 分支上的延续。

JUnit 4 是过去十年 Java 生态的事实标准，但存在若干结构性短板：单测试类只能有一个 `@Rule` 字段链、参数化测试需借助 `@RunWith(Parameterized.class)` 而与其它 Runner 互斥、生命周期注解粒度不足（如缺少 `@BeforeAll`/`@AfterAll` 对非静态方法的支持差异）、扩展模型基于 Runner 而难以组合等。JUnit 5 引入的 Jupiter 编程模型用 `@ExtendWith` 提供可组合的扩展模型，用 `@RegisterExtension` 支持有状态的扩展实例字段，用 `@TestTemplate` 配合扩展实现灵活的参数化，并通过 `@TempDir` 等内置扩展简化了原本依赖 `TemporaryFolder` 规则的临时目录管理。这些改进对 Flink 这种本身测试栈较重（需要起 MiniCluster）的场景尤为有价值。

本提交针对 Flink 1.17 这一支，使 `TestFlinkScan` 这条关键的 source 扫描测试链路（包括 SQL 执行、IcebergSource bounded、InputFormat 等多个子类）全面 JUnit5 化，与项目其它模块（如核心模块、Spark 模块）的迁移节奏保持一致，便于后续统一升级和维护。值得注意的是，作者没有直接重写 JUnit4 类，而是引入了 JUnit5 版本的扩展类（`MiniFlinkClusterExtension`、`HadoopCatalogExtension`）作为基础设施，使迁移后的测试能复用这些扩展。

## 如何达成设计目的

提交以"替换测试基础设施 API"为主线，配套修改被多个模块共享的 `GenericAppenderHelper` 让其支持 `java.nio.file.Path` 形式的临时目录，从而支撑 JUnit5 `@TempDir` 的使用。整体迁移遵循一套固定的模式映射规则（见下文"迁移模式"），逐个文件替换注解、API 与生命周期方法，并把 JUnit4 的构造函数参数注入改为 JUnit5 的字段注入 + `@Parameter`。

## 修改详情

本次提交共修改 8 个文件，300 行新增、306 行删除。属于 JUnit5 迁移类，下面先列出迁移模式，再针对核心文件说明。

### JUnit5 迁移模式总结

整个提交贯穿以下机械化的映射规则：

- **运行器与扩展**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。这里使用的是 Iceberg 自定义的 `ParameterizedTestExtension`（位于 `org.apache.iceberg` 包），而非 JUnit5 原生方案，便于复用项目内统一的参数化风格。
- **测试方法注解**：参数化测试方法 `@Test` → `@TestTemplate`（因为参数化由扩展驱动，每个参数组合运行一次需要 `@TestTemplate`）。
- **生命周期**：`@Before` → `@BeforeEach`；`@ClassRule` 静态字段 → `@RegisterExtension` 静态字段；`@Rule` 实例字段 → `@RegisterExtension` 实例字段。
- **资源扩展替换**：
  - `MiniClusterWithClientResource`（来自 `org.apache.flink.test.util`）→ `MiniClusterExtension`（来自 `org.apache.flink.test.junit5`），通过 Iceberg 包装类 `MiniFlinkClusterExtension.createWithClassloaderCheckDisabled()` 创建。
  - `HadoopCatalogResource` → `HadoopCatalogExtension`。
  - `TemporaryFolder`（JUnit4 Rules）→ `@TempDir Path temporaryDirectory`（JUnit5 内置扩展，直接注入 `java.nio.file.Path`）。
- **参数化方式**：从「构造函数接收 `Object[]` 参数 + `@Parameterized.Parameters` 返回 `Object[]`」改为「`@Parameter` 字段注入 + `@Parameters` 返回 `Collection<FileFormat>`」。这样不再需要带参构造函数，子类也不再需要 `super(fileFormat)`。
- **断言 API**：`org.junit.Assert.assertEquals(msg, expected, actual)` → `assertThat(actual).as(msg).isEqualTo(expected)`；`Assertions.assertThatThrownBy(...)`（AssertJ 类全限定调用）→ 静态导入的 `assertThatThrownBy(...)`。
- **假设 API**：`org.junit.Assume.assumeTrue(...)` → `org.assertj.core.api.Assumptions.assumeThat(...)`（见 `TestFlinkInputFormat`），用 AssertJ 的 Assumptions 替代 JUnit4 的 Assume，避免混用两套假设 API。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkScan.java`

**修改目的**：将核心抽象测试基类 `TestFlinkScan` 从 JUnit4 迁移到 JUnit5，这是整条测试链路的根。

**工作逻辑**：
原类用 `@RunWith(Parameterized.class)` 配合带参构造 `TestFlinkScan(String fileFormat)` 接收参数，通过 `@Parameterized.Parameters(name = "format={0}")` 提供 `avro`/`parquet`/`orc` 三种文件格式参数；类上挂 `@ClassRule` 的 `MiniClusterWithClientResource` 和 `TemporaryFolder`，实例上挂 `@Rule` 的 `HadoopCatalogResource`。

迁移后改为：
```java
@ExtendWith(ParameterizedTestExtension.class)
public abstract class TestFlinkScan {
  @RegisterExtension
  protected static MiniClusterExtension miniClusterResource =
      MiniFlinkClusterExtension.createWithClassloaderCheckDisabled();

  @TempDir protected Path temporaryDirectory;

  @RegisterExtension
  protected static final HadoopCatalogExtension catalogExtension =
      new HadoopCatalogExtension(TestFixtures.DATABASE, TestFixtures.TABLE);

  @Parameter protected FileFormat fileFormat;

  @Parameters(name = "format={0}")
  public static Collection<FileFormat> fileFormat() {
    return Arrays.asList(FileFormat.AVRO, FileFormat.PARQUET, FileFormat.ORC);
  }
  ...
}
```

要点：
- `fileFormat` 字段由 `@Parameter` 注入，参数源方法返回强类型的 `Collection<FileFormat>` 而非 `Object[]`，类型更安全。
- 所有原 `catalogResource.xxx()` 调用替换为 `catalogExtension.xxx()`，`TEMPORARY_FOLDER` 替换为 `temporaryDirectory`。
- 每个测试方法注解从 `@Test` 改为 `@TestTemplate`，因为它们依赖参数化扩展。
- 断言改用 AssertJ 链式风格，例如：
  ```java
  assertThat(inputRecord.getField(name))
      .as("Projected field " + name + " should match")
      .isEqualTo(actualRecord.getField(i));
  ```

### `data/src/test/java/org/apache/iceberg/data/GenericAppenderHelper.java`

**修改目的**：为 `GenericAppenderHelper` 增加 `java.nio.file.Path` 形式的构造函数重载，使其同时支持 JUnit4 `TemporaryFolder` 和 JUnit5 `@TempDir` 两种临时目录来源，供 Flink 测试迁移使用。

**工作逻辑**：
新增字段 `private final Path temp;` 与原 `TemporaryFolder tmp` 并存。新增两个构造函数：
```java
public GenericAppenderHelper(Table table, FileFormat fileFormat, Path temp, Configuration conf) {
  ...
  this.tmp = null;
  this.temp = temp;
}

public GenericAppenderHelper(Table table, FileFormat fileFormat, Path temp) {
  this(table, fileFormat, temp, null);
}
```
原 `TemporaryFolder` 构造函数标 `@Deprecated`，并在其实现中把 `temp` 置为 `null`。`writeFile` 方法改为兼容两种来源：
```java
File file = null != tmp ? tmp.newFile() : File.createTempFile("junit", null, temp.toFile());
assertThat(file.delete()).isTrue();
```
即优先使用 `TemporaryFolder`，否则用 `Files.createTempFile` 在 `Path` 指定目录下创建。同时把 `Assert.assertTrue(file.delete())` 替换为 `assertThat(file.delete()).isTrue()`。这种"双轨并存 + 旧 API 标记 Deprecated"的做法，让其它仍在使用 JUnit4 的模块不受影响，渐进迁移。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkInputFormat.java`

**修改目的**：迁移 `TestFlinkInputFormat` 子类。

**工作逻辑**：
- 移除带参构造 `TestFlinkInputFormat(String fileFormat)` 及其 `super(fileFormat)` 调用（因为父类改为字段注入）。
- `@Test` → `@TestTemplate`。
- 引入 `import static org.assertj.core.api.Assumptions.assumeThat;`，把原 `Assume.assumeTrue(...)` 替换为 `assumeThat(...).isTrue()`，统一假设 API 到 AssertJ。
- `catalogResource` → `catalogExtension`，`TEMPORARY_FOLDER` → `temporaryDirectory`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkScanSql.java` / `TestIcebergSourceBoundedSql.java`

**修改目的**：迁移两个 SQL 形式的子类。

**工作逻辑**：
- `import org.junit.Before;` → `import org.junit.jupiter.api.BeforeEach;`，`@Before` → `@BeforeEach`。
- 移除带参构造函数。
- `catalogResource.warehouse()` → `catalogExtension.warehouse()`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSource.java` / `TestIcebergSourceBounded.java`

**修改目的**：迁移两个抽象/具体子类。

**工作逻辑**：
- 移除 `@RunWith(Parameterized.class)` 与带参构造（参数化由父类 `TestFlinkScan` 通过扩展统一处理）。
- `catalogResource.catalog()` → `catalogExtension.catalog()`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java`

**修改目的**：迁移 `TestHelpers` 工具类（361 行变更，最大的一处）。

**工作逻辑**：
该类是 Flink 测试中广泛使用的断言辅助类，内部大量使用 `org.junit.Assert.assertEquals` 等 JUnit4 断言。本次将其整体替换为 AssertJ 风格（`assertThat(...).isEqualTo(...)` 等），使工具类不再依赖 JUnit4 运行时。这是支撑整个 Flink 测试套件 JUnit5 化的关键一环——否则即便测试类本身用 JUnit5，工具类内部的 JUnit4 断言仍会强制引入 JUnit4 依赖。文件变更行数最多（361 行），但都是同质的断言替换。

## 小结

本提交完成了 Iceberg Flink 1.17 模块 `TestFlinkScan` 测试链路的 JUnit5 迁移，建立了从 JUnit4 到 JUnit5 的完整映射模式（运行器→扩展、`@Rule`→`@RegisterExtension`、`@Test`→`@TestTemplate`、构造函数参数→`@Parameter` 字段、`TemporaryFolder`→`@TempDir`、JUnit4 断言→AssertJ），同时通过给 `GenericAppenderHelper` 增加 `Path` 构造函数兼顾尚未迁移的模块，是一次渐进、可复用的迁移实践，为后续 Flink 其它测试类的迁移提供了模板。
