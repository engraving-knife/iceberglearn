# 提交 0579：Data, Flink, Spark: Migrate TestAppenderFactory and subclasses to JUnit5

## 提交信息

- **序号**：0579 / 4088
- **哈希**：8b8907e091844229f824755648acc94b7a235e1a
- **短哈希**：8b8907e09
- **日期**：2024-03-11 19:26:03 +0530
- **作者**：Naveen Kumar <nk1506@gmail.com>
- **提交说明**：Data, Flink, Spark: Migrate TestAppenderFactory and subclasses to JUnit5 (#9862)
- **PR/Issue**：#9862

## 总体目的

本提交把跨 `data` / `flink` / `spark` 三个模块的 `TestAppenderFactory` 抽象测试基类及其所有子类从 JUnit 4 迁移到 JUnit 5，是继 0578（Snapshot 测试迁移）之后 Iceberg 测试框架现代化的又一步。涉及 10 个文件、253 行新增 / 272 行删除。具体目标：

1. 将抽象基类 `TestAppenderFactory`（data 模块）从 JUnit4 的 `@RunWith(Parameterized.class)` + 构造器注入模型，切换到 JUnit5 的 `ParameterizedTestExtension` + `@Parameter` 字段注入模型，父类由 `TableTestBase` 改为 `TestBase`；
2. 同步迁移 data 模块另外两个参数化测试类 `TestBaseTaskWriter`、`TestTaskEqualityDeltaWriter`；
3. 迁移 data / flink（v1.16/v1.17/v1.18）/ spark（v3.3/v3.4/v3.5）共 7 个 `TestAppenderFactory` 的具体子类——这些子类原先通过构造器向上传递 `(fileFormat, partitioned)` 参数，迁移后因基类改用字段注入而删除构造器，把仅依赖静态 `SCHEMA` 的字段改为字段初始化器；
4. 把断言从 JUnit4 `Assert.*` 统一为 AssertJ 静态导入 `assertThat`，临时目录操作从 `TemporaryFolder.newFolder()` 改为 `Files.createTempDirectory(Path, prefix)`。

背景与 0578 一致：Iceberg 在 `api` 模块引入了借鉴 Flink 的 `ParameterizedTestExtension`、`@Parameters`、`@Parameter` 三件套（位于 `api/src/test/java/org/apache/iceberg/`），用于在 JUnit5 中以字段注入方式实现类级参数化测试。`TestBase` 是已就绪的 JUnit5 测试基类，其 `formatVersion` 字段标注了 `@Parameter(index = 0)`，等待子类的 `@Parameters` 方法提供 index 0 的值。

## 如何达成设计目的

整体设计与 0578 相同：复用 `ParameterizedTestExtension` + `TestBase`。但本提交有一个**关键的参数化模型适配点**：

这些 Appender/TaskWriter 测试类原本只把 `fileFormat`（和 `partitioned`）作为参数化维度，`formatVersion` 固定为 `FORMAT_V2`（通过构造器 `super(FORMAT_V2)` 硬编码）。而在 JUnit5 的 `ParameterizedTestExtension` 字段注入模型下，`TestBase.formatVersion` 标注了 `@Parameter(index = 0)`，扩展会从 `@Parameters` 方法返回的每个参数数组中取 index 0 注入 `formatVersion`。因此迁移后必须把 `FORMAT_V2` 作为每个参数数组的**第一个元素**（index 0）显式列出，使其注入 `formatVersion`；`fileFormat` 与 `partitioned` 分别为 index 1、index 2，由各自标注 `@Parameter(index = 1)` / `@Parameter(index = 2)` 的字段接收。

参数化迁移范式（以 `TestAppenderFactory` 为例）：
```
// JUnit4
@RunWith(Parameterized.class)
public abstract class TestAppenderFactory<T> extends TableTestBase {
  @Parameterized.Parameters(name = "FileFormat={0}, Partitioned={1}")
  public static Object[] parameters() {
    return new Object[][] { {"avro", false}, {"avro", true}, ... };
  }
  private final FileFormat format;
  private final boolean partitioned;
  public TestAppenderFactory(String fileFormat, boolean partitioned) {
    super(FORMAT_V2);
    this.format = FileFormat.fromString(fileFormat);
    this.partitioned = partitioned;
  }
  @Test public void testDataWriter() { ... }
}

// JUnit5
@ExtendWith(ParameterizedTestExtension.class)
public abstract class TestAppenderFactory<T> extends TestBase {
  @Parameter(index = 1) protected FileFormat format;
  @Parameter(index = 2) private boolean partitioned;
  @Parameters(name = "formatVersion = {0}, FileFormat={1}, partitioned={2}")
  protected static List<Object> parameters() {
    return Arrays.asList(
        new Object[] {FORMAT_V2, FileFormat.AVRO, false},
        new Object[] {FORMAT_V2, FileFormat.AVRO, true}, ...);
  }
  @TestTemplate public void testDataWriter() { ... }
}
```

附带优化：文件格式参数由字符串（`"avro"`）改为直接使用 `FileFormat.AVRO` 等枚举常量，省去了 `FileFormat.fromString(fileFormat)` 的转换，类型更安全。

子类适配逻辑：原先具体子类（如 `TestGenericAppenderFactory`）的构造器形如 `public TestGenericAppenderFactory(String fileFormat, boolean partitioned) { super(fileFormat, partitioned); this.gRecord = GenericRecord.create(SCHEMA); }`。迁移后基类无构造器参数（改用字段注入），子类的构造器随之删除；其中 `gRecord` 等字段仅依赖静态 `SCHEMA`、不依赖参数，故直接改为字段初始化器 `private final GenericRecord gRecord = GenericRecord.create(SCHEMA);`。

## 修改详情

### `data/src/test/java/org/apache/iceberg/io/TestAppenderFactory.java`

**修改目的**：将抽象测试基类迁移到 JUnit5 参数化模型，是本次跨模块迁移的枢纽。

**工作逻辑**：
- 父类 `TableTestBase` → `TestBase`；`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。
- 字段注入：新增 `@Parameter(index = 1) protected FileFormat format;` 与 `@Parameter(index = 2) private boolean partitioned;`（均为非 final 字段，由扩展在 `beforeEach` 反射注入），取代原先构造器中赋值的 `private final` 字段。
- `@Parameters` 方法返回 6 组参数（avro/orc/parquet × 分区/非分区），每组三元素 `[FORMAT_V2, FileFormat.X, bool]`，index 0 的 `FORMAT_V2` 注入 `TestBase.formatVersion`。显示名模板为 `"formatVersion = {0}, FileFormat={1}, partitioned={2}"`。
- `@Before` → `@BeforeEach`；`setupTable()` 中 `this.tableDir = temp.newFolder()` 改为 `Files.createTempDirectory(temp, "junit").toFile()`（`temp` 现为 `@TempDir Path`）；`Assert.assertTrue(tableDir.delete())` 改为 `assertThat(tableDir.delete()).isTrue()`。
- 4 个测试方法 `@Test` → `@TestTemplate`。断言 `Assert.assertEquals("msg", expected, actual)` 改为 `assertThat(actual).as("msg").isEqualTo(expected)`；对 `Set`/集合的比较同样改为 `assertThat(...).isEqualTo(...)`。

### `data/src/test/java/org/apache/iceberg/io/TestBaseTaskWriter.java`

**修改目的**：将 BaseTaskWriter 测试迁移到 JUnit5 参数化模型。

**工作逻辑**：与 `TestAppenderFactory` 同构迁移。`@Parameter(index = 1) protected FileFormat format;`（仅一个参数化维度 + index 0 的 FORMAT_V2）。`@Parameters` 返回 3 组 `[FORMAT_V2, FileFormat.X]`。`@Before` → `@BeforeEach`、`@Test` → `@TestTemplate`。`temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`。断言迁移包括：数组长度断言 `Assert.assertEquals(0, result.dataFiles().length)` → `assertThat(result.dataFiles()).hasSize(0)`；`Assert.assertFalse(Files.exists(path))` → `assertThat(path).doesNotExist()`；`Assert.assertEquals("msg", n, files.size())` → `assertThat(files).as("msg").hasSize(n)`。

### `data/src/test/java/org/apache/iceberg/io/TestTaskEqualityDeltaWriter.java`

**修改目的**：将 EqualityDeltaWriter 测试（upsert / 去重 / 纯插入场景）迁移到 JUnit5 参数化模型。本文件改动量最大。

**工作逻辑**：同构迁移。`@Parameter(index = 1) protected FileFormat format;`，`@Parameters` 返回 3 组 `[FORMAT_V2, FileFormat.X]`。`@Before` → `@BeforeEach`、`@Test` → `@TestTemplate`。临时目录创建用全限定名 `java.nio.file.Files.createTempDirectory(temp, "junit").toFile()`。断言迁移覆盖大量 `Assert.assertEquals`（含 `FileContent.POSITION_DELETES`/`EQUALITY_DELETES` 枚举比较、`ImmutableList` 记录列表比较）。AssertJ 习惯将实际值放在 `assertThat(actual)`，因此多处把原 `Assert.assertEquals(expected, actual)` 的参数顺序调整为 `assertThat(actual).isEqualTo(expected)`。

需注意一个显示名模板的小瑕疵：`@Parameters(name = "formatVersion = {0}, FileFormat = {0}")` 中两个占位符都写成 `{0}`，第二个应为 `{1}`（仅影响测试显示名、不影响逻辑）。

### `data/src/test/java/org/apache/iceberg/TestGenericAppenderFactory.java`

**修改目的**：适配基类构造器签名变更。

**工作逻辑**：删除构造器 `public TestGenericAppenderFactory(String fileFormat, boolean partitioned)`，将原在构造器中初始化的 `gRecord` 改为字段初始化器 `private final GenericRecord gRecord = GenericRecord.create(SCHEMA);`。因基类 `TestAppenderFactory` 已无带参构造器且 `format`/`partitioned` 改由扩展注入，子类无需、也无法再向上传递这两个参数。

### `flink/v1.16|v1.17|v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkAppenderFactory.java`（3 个相同文件）

**修改目的**：适配基类构造器签名变更。

**工作逻辑**：与 `TestGenericAppenderFactory` 相同——删除构造器，`rowType` 改为字段初始化器 `private final RowType rowType = FlinkSchemaUtil.convert(SCHEMA);`。三个 Flink 版本（1.16/1.17/1.18）的改动完全一致。

### `spark/v3.3|v3.4|v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkAppenderFactory.java`（3 个相同文件）

**修改目的**：适配基类构造器签名变更。

**工作逻辑**：同上——删除构造器，`sparkType` 改为字段初始化器 `private final StructType sparkType = SparkSchemaUtil.convert(SCHEMA);`。三个 Spark 版本（3.3/3.4/3.5）的改动完全一致。

## 小结

本提交将跨 data/flink/spark 三模块的 `TestAppenderFactory` 抽象基类及 9 个相关测试类/子类从 JUnit4 迁移到 JUnit5，核心是改用 `ParameterizedTestExtension` + `@Parameter` 字段注入模型，并把原先硬编码的 `FORMAT_V2` 提升为参数数组的 index 0 以适配 `TestBase.formatVersion` 的注入要求；7 个具体子类因基类去掉带参构造器而同步删除构造器、改为字段初始化器。迁移不改变被测代码与测试覆盖语义，但有两处细节值得注意：一是文件格式参数从字符串改为 `FileFormat` 枚举（更安全）；二是 `TestTaskEqualityDeltaWriter` 的 `@Parameters` 显示名模板存在 `{0}` 重复占位的小瑕疵（不影响逻辑）。回迁到 1.4.x 的注意事项与 0578 相同：需确认 1.4.x 是否已具备 `TestBase`、`ParameterizedTestExtension`、`@Parameter`/`@Parameters` 等 JUnit5 基础设施，否则不宜直接 cherry-pick。
