# 提交 0653：Core, Flink, Spark: Migrate remaining subclasses of TableTestBase to JUnit5

## 提交信息
- **序号**：0653 / 4088
- **哈希**：815b2c649fa51b0ec837a7fa761961820eadfef0
- **短哈希**：815b2c649
- **日期**：2024-04-02（Tue Apr 2 20:12:29 2024 +0900）
- **作者**：Tom Tanaka
- **提交说明**：Core, Flink, Spark: Migrate remaining subclasses of TableTestBase to JUnit5 (#10063)
- **PR/Issue**：#10063

> 备注：仓库实际提交信息中的 PR 编号为 #10063（与任务清单中标注的 #10016 不一致，以仓库 git 元数据为准）。

## 总体目的

本提交是 Iceberg 项目将测试体系从 JUnit 4 迁移到 JUnit 5（Jupiter）这一长期工作的收尾之一。在此之前，项目已经存在一个基于 JUnit 5 的基类 `TestBase`，并已迁移了大量测试类；但仍有一批测试类继续继承旧的 JUnit 4 基类 `TableTestBase`。本提交的目标是：将所有"剩余的" `TableTestBase` 子类（分布在 core、data、flink 多版本、spark 多版本模块中）彻底迁移到 JUnit 5，并最终删除旧的 `TableTestBase` 基类本身，从而消除项目中 JUnit 4 与 JUnit 5 并存的二元状态。

`TableTestBase` 是 Iceberg 测试基础设施中的核心基类之一，它提供了一套通用的表测试夹具：预定义的 `SCHEMA`、`SPEC`（分区规格）、若干预构造的 `DataFile`/`DeleteFile` 常量（如 `FILE_A`、`FILE_A_DELETES` 等），以及表目录、元数据目录的创建与清理逻辑。许多针对表元数据、文件写入器、Flink sink/source、Spark 写入器的测试都继承它以复用这些夹具。旧版本基于 JUnit 4（`org.junit.*`：`@Before`、`@Test`、`@Rule TemporaryFolder`、`@RunWith(Parameterized.class)`），而项目已确立以 JUnit 5 为目标，因此需要把这一批"剩余子类"统一迁移。

迁移完成后，`TableTestBase.java`（753 行）被整体删除，所有原先引用 `TableTestBase.SPEC`/`FILE_A`/`SCHEMA` 等常量的地方改引用 `TestBase` 上对应的同名常量；所有继承 `TableTestBase` 的类改为继承 `TestBase`。这是一个跨 46 个文件、净删除约 885 行（新增 571、删除 1456）的较大重构。

## 如何达成设计目的

整体策略是"替换基类 + 统一迁移模式"，分以下几个层面：

1. **删除旧基类，统一切换到新基类**：删除 `core/src/test/java/org/apache/iceberg/TableTestBase.java`，将所有 `extends TableTestBase` 改为 `extends TestBase`，将所有静态常量引用 `TableTestBase.XXX` 改为 `TestBase.XXX`。`TestBase` 是此前已存在的 JUnit 5 版基类，提供了与 `TableTestBase` 等价的常量与夹具，但生命周期注解、临时目录机制均已基于 JUnit 5。

2. **参数化测试机制切换**：JUnit 4 通过 `@RunWith(Parameterized.class)` + `@Parameterized.Parameters` + 构造器注入参数来运行参数化测试。本提交统一改用 Iceberg 自定义的 JUnit 5 扩展机制：`@ExtendWith(ParameterizedTestExtension.class)` + `@Parameters`（自定义注解）+ `@Parameter`（字段注入）+ `@TestTemplate`（替代 `@Test`）。这样参数通过字段注入而非构造器注入，子类无需再编写接收 `formatVersion`/`fileFormat` 的构造器并向上传递。

3. **生命周期注解切换**：`@Before`（JUnit 4）→ `@BeforeEach`（JUnit 5）。

4. **临时目录机制切换**：JUnit 4 的 `TemporaryFolder.newFolder()` → JUnit 5 的 `@TempDir`（`java.nio.file.Path`），对应代码改为 `Files.createTempDirectory(temp, "junit").toFile()`。

5. **断言库切换（部分）**：在 `TestFileWriterFactory` 等文件中，将 `org.junit.Assert.*` 与 `org.junit.Assume` 替换为 AssertJ 的 `assertThat`/`assumeThat` 流式断言；其余文件保留 `Assert.*`，留待后续提交（见 0655）继续清理。

6. **子类构造器移除**：由于父类不再需要 `formatVersion` 构造参数（改由 `@Parameter` 注入），抽象子类（如 `WriterTestBase`、`TestFileWriterFactory`、`TestPartitioningWriters`）和具体子类（如 `TestGenericFileWriterFactory`、`TestSparkFileWriterFactory`）都删除了原本转发参数的构造器。

7. **跨版本复制**：Flink（v1.16/v1.17/v1.18）与 Spark（v3.5）的并行多版本模块中存在同名测试文件，迁移模式在各版本中保持一致地重复应用，确保各版本模块的测试同步演进。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TableTestBase.java`（删除）
**修改目的**：移除旧的 JUnit 4 表测试基类，彻底消除 JUnit 4 测试基础设施。
**工作逻辑**：该文件原 753 行，定义了 `SCHEMA`、`SPEC`、`BUCKETS_NUMBER`、`FILE_A` 等一系列 `public static` 测试常量，以及基于 `@Rule TemporaryFolder`、`@Before`、`@After` 的表目录创建/清理逻辑和 `formatVersion` 构造器。本提交将其整体删除。这些能力已由 JUnit 5 版的 `TestBase` 提供，故所有引用方改指向 `TestBase`。

### `core/src/test/java/org/apache/iceberg/TestContentFileParser.java`
**修改目的**：将 `TableTestBase` 常量引用改为 `TestBase`。
**工作逻辑**：该测试类本身已在较早提交迁移到 JUnit 5（使用 `@ParameterizedTest` + `@MethodSource`），但仍引用 `TableTestBase.SPEC`/`FILE_A`/`SCHEMA`。本提交把这些引用逐处替换为 `TestBase.SPEC`/`FILE_A`/`SCHEMA`，使该测试不再依赖已删除的旧基类。

### `core/src/test/java/org/apache/iceberg/TestFileScanTaskParser.java`
**修改目的**：将 `TableTestBase` 常量引用改为 `TestBase`。
**工作逻辑**：把 `TableTestBase.SPEC`/`FILE_A`/`FILE_A_DELETES`/`FILE_A2_DELETES`/`SCHEMA` 替换为 `TestBase.*`，并顺带合并了一处多行调用为单行以保持格式整洁。

### `core/src/test/java/org/apache/iceberg/util/TestTableScanUtil.java`
**修改目的**：将 `TableTestBase` 引用改为 `TestBase`。
**工作逻辑**：import 由 `org.apache.iceberg.TableTestBase` 改为 `org.apache.iceberg.TestBase`，方法体内 `TableTestBase.SPEC`/`SCHEMA` 替换为 `TestBase.*`。该类本身已用 JUnit 5 `@Test`，仅常量来源需切换。

### `data/src/test/java/org/apache/iceberg/io/WriterTestBase.java`
**修改目的**：抽象写入器测试基类从 `TableTestBase` 切换到 `TestBase`，并移除 `formatVersion` 构造器。
**工作逻辑**：原 `public abstract class WriterTestBase<T> extends TableTestBase` 带构造器 `WriterTestBase(int formatVersion){ super(formatVersion); }`，改为 `public abstract class WriterTestBase<T> extends TestBase` 并删除该构造器。这样参数化版本号改由子类通过 `@Parameter` 注入。

### `data/src/test/java/org/apache/iceberg/io/TestFileWriterFactory.java`
**修改目的**：迁移为 JUnit 5 参数化扩展，并切换断言/假设库。
**工作逻辑**：这是本提交中改动最完整的样例之一，集中体现了全部迁移模式：
- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；
- `@Parameterized.Parameters` 静态方法返回 `Object[]`/`Object[][]` → `@Parameters` 静态方法返回 `List<Object>`（`Arrays.asList`），并在参数数组首位补上 `formatVersion=2`（原为类常量 `TABLE_FORMAT_VERSION`，现纳入参数化）；
- 构造器 `(FileFormat, boolean)` + 字段赋值 → `@Parameter(index=1)`/`@Parameter(index=2)` 字段注入（index 0 为 formatVersion，由父类消费）；
- `@Before` → `@BeforeEach`；
- `@Test` → `@TestTemplate`；
- `temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`；
- `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`；`Assert.assertTrue(x)` → `assertThat(x).isTrue()`；`Assume.assumeFalse("msg", cond)` → `assumeThat(cond).as("msg").isFalse()`。断言消息从第一参数迁移到 `.as(...)` 链式调用。

### `data/src/test/java/org/apache/iceberg/io/TestPartitioningWriters.java`
**修改目的**：迁移为 JUnit 5 参数化扩展。
**工作逻辑**：与 `TestFileWriterFactory` 同样的模式。原构造器 `(FileFormat)` 转发 `super(TABLE_FORMAT_VERSION)`，现删除构造器，改用 `@Parameter(index=1) FileFormat fileFormat`；`@Parameters` 返回含 `formatVersion=2` 的参数列表；`@Test`→`@TestTemplate`、`@Before`→`@BeforeEach`、`temp.newFolder()`→`Files.createTempDirectory(...)`。本文件中多数 `Assert.*` 暂时保留，留待 0655 清理。

### `data/src/test/java/org/apache/iceberg/io/TestPositionDeltaWriters.java` 与 `TestRollingFileWriters.java`
**修改目的**：同上，迁移为 JUnit 5 参数化扩展。
**工作逻辑**：应用与 `TestPartitioningWriters` 一致的注解/构造器/临时目录迁移模式。

### `data/src/test/java/org/apache/iceberg/data/TestGenericFileWriterFactory.java`
**修改目的**：移除因父类构造器删除而冗余的子类构造器。
**工作逻辑**：原 `public TestGenericFileWriterFactory(FileFormat, boolean){ super(...); }` 被删除，同时移除不再使用的 `FileFormat` import。该具体子类现在完全依赖父类的 `@Parameter` 字段注入。

### `flink/v1.{16,17,18}/flink/src/test/java/org/apache/iceberg/flink/sink/TestDeltaTaskWriter.java`（三份）
**修改目的**：Flink sink 写入器测试迁移到 JUnit 5 参数化扩展。
**工作逻辑**：`extends TableTestBase` → `extends TestBase`；`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；构造器 `(String fileFormat)` 中 `super(FORMAT_V2)` + `FileFormat.fromString(...)` 删除，改为 `@Parameter(index=1) FileFormat format` + `@Parameters` 返回含 `formatVersion=2` 的 `Arrays.asList`；`@Before`→`@BeforeEach`、`@Test`→`@TestTemplate`、`temp.newFolder()`→`Files.createTempDirectory(...)`。三个 Flink 版本的改动完全一致。

### `flink/v1.{16,17,18}/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergFilesCommitter.java`（三份）
**修改目的**：Flink sink committer 测试迁移到 JUnit 5 参数化扩展。
**工作逻辑**：除上述通用模式外，参数表由原 `{"avro", 1, "main"}` 等字符串+int 组合改为 `{1, FileFormat.AVRO, "main"}` 等，把 `formatVersion` 作为首参、`FileFormat` 枚举直接传入（避免 `FileFormat.fromString`）。`@Parameter(index=1) FileFormat format`、`@Parameter(index=2) String branch`。`@Test`→`@TestTemplate`。`setupTable` 中临时目录创建方式同步切换。

### `flink/v1.{16,17,18}/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamingMonitorFunction.java` 与 `TestStreamingReaderOperator.java`（各三份）
**修改目的**：Flink source 测试迁移到 JUnit 5 参数化扩展。
**工作逻辑**：应用与 sink 侧一致的迁移模式（注解、构造器、临时目录）。其中 v1.17 的 `TestFlinkPartitioningWriters` 改动行数略多（10 行），可能含额外的格式整理。

### `flink/v1.{16,17,18}/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkFileWriterFactory.java`、`TestFlinkPartitioningWriters.java`、`TestFlinkPositionDeltaWriters.java`、`TestFlinkRollingFileWriters.java`（各三份，每个仅 5 行）
**修改目的**：移除因父类（`TestFileWriterFactory`/`TestPartitioningWriters` 等）构造器删除而冗余的子类构造器。
**工作逻辑**：每个文件仅删除形如 `public TestFlinkXxx(FileFormat, boolean){ super(...); }` 的构造器及对应 import，共约 5 行。这与 `TestGenericFileWriterFactory` 的处理一致。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSpark{FileWriterFactory,PartitioningWriters,PositionDeltaWriters,RollingFileWriters}.java`（各一份，每个仅 5 行）
**修改目的**：移除 Spark 写入器测试子类中冗余的构造器。
**工作逻辑**：与 Flink 侧对应子类相同，删除转发 `FileFormat`/`boolean` 给父类的构造器及 import。这些子类现在依赖父类 `TestFileWriterFactory`/`TestPartitioningWriters` 等的 `@Parameter` 注入。

## 小结
- **成效**：成功达成目的。删除了 753 行的旧 JUnit 4 基类 `TableTestBase`，将 46 个文件中的剩余子类与常量引用统一迁移到 JUnit 5 基类 `TestBase` 与 Iceberg 自定义的 `ParameterizedTestExtension` 参数化机制，消除了 JUnit 4/5 并存状态。
- **影响范围**：core、data、flink（v1.16/v1.17/v1.18）、spark（v3.5）多个模块的测试基础设施。涉及写入器测试（FileWriterFactory/Partitioning/PositionDelta/Rolling）、Flink sink/source 测试（DeltaTaskWriter/IcebergFilesCommitter/StreamingMonitorFunction/StreamingReaderOperator）以及若干解析器/工具类测试。仅影响测试代码，不影响生产代码。
- **回迁到 1.4.x 的注意事项**：
  1. **前置依赖**：本提交依赖 `TestBase`（JUnit 5 版基类）及其 `Parameter`/`Parameters`/`ParameterizedTestExtension` 自定义扩展已存在于 1.4.x。若 1.4.x 尚未引入这些基础设施，需先回迁相关前置提交，否则编译会失败。
  2. **构造器联动**：父类构造器删除会连带要求所有子类删除转发构造器；回迁时务必把 0653 涉及的父类与全部子类一并回迁，否则子类构造器会因找不到父类构造器而编译失败。
  3. **多版本同步**：Flink/Spark 多版本模块中存在同名文件，回迁时需对各版本同步应用，避免某版本遗漏导致该版本测试仍引用已删除的 `TableTestBase`。
  4. **断言清理分两步**：本提交仅部分文件切换到 AssertJ 断言，许多文件仍保留 `Assert.*`；完整的断言清理在 0655 完成。回迁时建议 0653 与 0655 一并回迁，以避免遗留混合断言风格。
  5. **PR 编号核对**：仓库实际 PR 为 #10063，注意勿与任务清单中的 #10016 混淆。
