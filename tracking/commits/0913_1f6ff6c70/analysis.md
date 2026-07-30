# 提交 0913：Data: Switch tests to JUnit5 + AssertJ-style assertions (#10657)

## 提交信息

- **序号**：0913 / 4088
- **哈希**：1f6ff6c70c8c921f56ee9d538f14a0eca6cfe00c
- **短哈希**：1f6ff6c70
- **日期**：2024-07-09（Tue Jul 9 16:55:37 2024 +0200）
- **作者**：Attila Kreiner <kreiner.attila@gmail.com>
- **提交说明**：Data: Switch tests to JUnit5 + AssertJ-style assertions (#10657)
- **PR/Issue**：#10657

## 总体目的

Iceberg 项目正在推进一次跨多个模块的测试框架迁移：从 JUnit 4 迁移到 JUnit 5（Jupiter），同时把断言风格从 JUnit 4 的 `org.junit.Assert.*`（如 `Assert.assertEquals`、`Assert.assertNull`）切换到 AssertJ 的流式断言（`assertThat(...).as(...).isEqualTo(...)`）。这是 Iceberg 在 1.5.x/1.6.x 周期持续性技术债清理的一部分，目的是统一测试栈、利用 JUnit 5 的扩展模型和 AssertJ 更丰富的断言能力提升测试可读性与失败诊断信息。

本提交聚焦于 `data` 模块（以及与 `data` 模块测试基类 `TestMergingMetrics`、`RecordWrapperTest` 有继承关系的 `flink/v1.17`、`flink/v1.18`、`flink/v1.19`、`mr`、`spark/v3.3`、`spark/v3.4`、`spark/v3.5` 各模块中对应的子类测试）的迁移工作。`TestMergingMetrics`、`RecordWrapperTest` 是被多个引擎（Spark、Flink、MR）共享的抽象测试基类，迁移它们的同时必须同步迁移所有子类，否则编译会因 JUnit 4 与 JUnit 5 注解/规则不兼容而失败。

## 如何达成设计目的

采用机械但系统化的替换策略，针对每个测试类做以下几类改动：
1. 把 JUnit 4 的 `import org.junit.*` 替换为 JUnit 5 的 `import org.junit.jupiter.api.*` 及 AssertJ 的 `import static org.assertj.core.api.Assertions.assertThat`。
2. 把参数化测试从 `@RunWith(Parameterized.class)` + `@Parameterized.Parameters` + 构造函数 + `@Test` 改为 JUnit 5 的 `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameters` + `@Parameter` 字段注入 + `@TestTemplate`。Iceberg 自定义了 `ParameterizedTestExtension` 来兼容这种风格的参数化。
3. 把 `@Rule public TemporaryFolder temp = new TemporaryFolder()` 改为 JUnit 5 的 `@TempDir File tempDir`（或 `@TempDir Path tempDir`）。
4. 把 `@Before` 改为 `@BeforeEach`。
5. 把 `Assert.assertEquals(msg, expected, actual)` 等调用改写为 `assertThat(actual).as(msg).isEqualTo(expected)` 风格。
6. 顺手清理一些已废弃的构造函数（如 `GenericAppenderHelper`、`TestHelper` 中接收 `TemporaryFolder` 的旧构造函数被移除或标记 `@Deprecated`）。
7. 由于基类不再持有 `TemporaryFolder temp` 字段，子类（如 `TestSparkMergingMetrics`、`TestFlinkMergingMetrics`）需要直接使用基类暴露的 `tempDir` 字段，并通过 `File.createTempFile("junit", null, tempDir)` 等方式获取临时文件。

## 修改详情

### `data/src/test/java/org/apache/iceberg/RecordWrapperTest.java`

**修改目的**：把基类中的 `Assert::assertEquals` 方法引用替换为 AssertJ 风格的 lambda。

**工作逻辑**：

```diff
-import org.junit.Assert;
+import static org.assertj.core.api.Assertions.assertThat;
...
-    generateAndValidate(schema, Assert::assertEquals);
+    generateAndValidate(
+        schema, (message, expected, actual) -> assertThat(actual).as(message).isEqualTo(expected));
```

原本通过 `Assert::assertEquals` 作为 `AssertMethod` 函数式接口的实现，改为显式 lambda 包装 AssertJ 断言。注意 AssertJ 的参数顺序是 `assertThat(actual).isEqualTo(expected)`，与原 `Assert.assertEquals(expected, actual)` 在 `expected`/`actual` 顺序上需要对调。

### `data/src/test/java/org/apache/iceberg/TestMergingMetrics.java`

**修改目的**：把抽象基类 `TestMergingMetrics` 从 JUnit 4 参数化测试改造为 JUnit 5 + Iceberg `ParameterizedTestExtension` 风格，作为各引擎子类的统一基类。

**工作逻辑**：
- 移除 `@RunWith(Parameterized.class)`、`@Parameterized.Parameters`、构造函数注入，改为 `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameters` 静态方法 + `@Parameter protected FileFormat fileFormat` 字段注入。
- 把 `@Rule public TemporaryFolder temp` 改为 `@TempDir protected File tempDir`，供子类直接使用。
- 把 `@Test` 改为 `@TestTemplate`（参数化测试方法）。
- 把方法体内 `Assert.assertNull(msg, val)` / `Assert.assertEquals(msg, expected, actual)` 改为 `assertThat(val).as(msg).isNull()` / `assertThat(...).as(msg).isEqualTo(...)`，并对 `assertNaNCountMatch`、`assertBoundValueMatch` 等辅助方法做了类似改写，同时调整 `actual`/`expected` 顺序以符合 AssertJ 语义。

### `data/src/test/java/org/apache/iceberg/TestSplitScan.java`

**修改目的**：把 `TestSplitScan` 从 JUnit 4 参数化测试改造为 JUnit 5 参数化测试风格，并替换断言。

**工作逻辑**：与 `TestMergingMetrics` 类似的注解迁移（`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`、`@Parameterized.Parameters` → `@Parameters`、`@Before` → `@BeforeEach`、`@Rule TemporaryFolder` → `@TempDir File tempDir`、`@Test` → `@TestTemplate`）。`@Parameters` 的返回类型从 `Object[]` 改为 `List<Object>`，并把字符串 `"parquet"`/`"avro"` 改为直接使用 `FileFormat.PARQUET`/`FileFormat.AVRO` 枚举（避免在构造函数里再 `FileFormat.fromString`）。测试方法 `test()` 中把循环逐条 `Assert.assertEquals` 替换为 `assertThat(records).isEqualTo(expectedRecords)` 一次性比较列表。

### `data/src/test/java/org/apache/iceberg/data/DataTestHelpers.java`

**修改目的**：把通用比较辅助类中的 JUnit 4 断言替换为 AssertJ 风格。

**工作逻辑**：
- 移除 `import org.junit.Assert`。
- `Assert.assertEquals("List size should match", expected.size(), actual.size())` → `assertThat(actual).as("List size should match").hasSameSizeAs(expected)`。
- Map 同理。
- Primitive 类型分支 `Assert.assertEquals(...)` → `assertThat(actual).as(...).isEqualTo(expected)`。
- `FIXED`（byte[]）分支从 `Assert.assertArrayEquals(...)` 改为 `assertThat(actual).as(...).isEqualTo(expected)`（AssertJ 对数组有专门处理，使用 `isEqualTo` 即可比较内容）。

### `data/src/test/java/org/apache/iceberg/data/GenericAppenderHelper.java`

**修改目的**：清理 `GenericAppenderHelper` 中 `TemporaryFolder` 相关字段与构造函数，统一使用 `Path temp` 字段。

**工作逻辑**：
- 删除 `private final TemporaryFolder tmp` 字段。
- 原接收 `TemporaryFolder tmp` 的构造函数现在内部调用 `tmp.getRoot().toPath()` 转成 `Path` 赋给 `temp`，不再单独保存 `tmp`。
- 仅接收 `TemporaryFolder` 的旧构造函数加上 `@Deprecated`。
- `writeFile(...)` 方法原本通过 `null != tmp ? tmp.newFile() : File.createTempFile(...)` 二选一，现在统一为 `File.createTempFile("junit", null, temp.toFile())`。

### `data/src/test/java/org/apache/iceberg/data/TestDataFileIndexStatsFilters.java`

**修改目的**：把该测试类从 JUnit 4 迁移到 JUnit 5 + AssertJ。共 102 行改动，模式与其他文件一致（注解替换、`Assert.*` → `assertThat`、`@Rule TemporaryFolder` → `@TempDir`）。

### `data/src/test/java/org/apache/iceberg/data/TestGenericRecord.java`

**修改目的**：迁移到 JUnit 5 + AssertJ，8 行改动。模式同上。

### `data/src/test/java/org/apache/iceberg/data/TestLocalScan.java`

**修改目的**：迁移到 JUnit 5 + AssertJ，178 行改动，是 `data` 模块中改动量较大的文件之一。模式同上。

### `data/src/test/java/org/apache/iceberg/data/TestMetricsRowGroupFilter.java`

**修改目的**：迁移到 JUnit 5 + AssertJ，504 行改动，是本提交改动量最大的文件。模式同上。

### `data/src/test/java/org/apache/iceberg/data/TestMetricsRowGroupFilterTypes.java`

**修改目的**：迁移到 JUnit 5 + AssertJ，134 行改动。模式同上。

### `data/src/test/java/org/apache/iceberg/data/TestReadProjection.java`

**修改目的**：迁移到 JUnit 5 + AssertJ，421 行改动。模式同上。

### `data/src/test/java/org/apache/iceberg/data/avro/TestGenericReadProjection.java`

**修改目的**：迁移到 JUnit 5 + AssertJ，6 行改动，主要是父类签名变化导致的连带调整。

### `data/src/test/java/org/apache/iceberg/data/avro/TestSingleMessageEncoding.java`

**修改目的**：迁移到 JUnit 5 + AssertJ，28 行改动。模式同上。

### `data/src/test/java/org/apache/iceberg/data/orc/TestGenericReadProjection.java`

**修改目的**：迁移到 JUnit 5 + AssertJ，6 行改动。模式同上。

### `data/src/test/java/org/apache/iceberg/data/orc/TestOrcDataWriter.java`

**修改目的**：迁移到 JUnit 5 + AssertJ，32 行改动。模式同上。

### `data/src/test/java/org/apache/iceberg/data/orc/TestOrcRowIterator.java`

**修改目的**：迁移到 JUnit 5 + AssertJ，17 行改动。模式同上。

### `data/src/test/java/org/apache/iceberg/data/parquet/TestGenericReadProjection.java`

**修改目的**：迁移到 JUnit 5 + AssertJ，6 行改动。模式同上。

### `data/src/test/java/org/apache/iceberg/io/TestWriterMetrics.java`

**修改目的**：迁移到 JUnit 5 + AssertJ，193 行改动。模式同上。

### `data/src/test/java/org/apache/iceberg/parquet/TestGenericMergingMetrics.java`

**修改目的**：迁移到 JUnit 5 + AssertJ，10 行改动，主要是父类签名变化导致的连带调整。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestRowDataWrapper.java` / `flink/v1.18/.../TestRowDataWrapper.java` / `flink/v1.19/.../TestRowDataWrapper.java`

**修改目的**：因为父类 `RecordWrapperTest` 中的 `AssertMethod` lambda 改成了 AssertJ 风格且参数顺序对调，三个 Flink 版本的子类 `TestRowDataWrapper` 需要把传入 `assertMethod.assertEquals` 的 `expected`/`actual` 顺序对调：

```diff
       assertMethod.assertEquals(
           "Should have expected StructLike values",
-          actualWrapper.set(recordStructLike),
-          expectedWrapper.set(rowDataStructLike));
+          expectedWrapper.set(rowDataStructLike),
+          actualWrapper.set(recordStructLike));
```

### `flink/v1.17/.../source/TestFlinkMergingMetrics.java` / `flink/v1.18/.../TestFlinkMergingMetrics.java` / `flink/v1.19/.../TestFlinkMergingMetrics.java`

**修改目的**：适配父类 `TestMergingMetrics` 改造后的新签名（去除 `FileFormat` 构造参数、字段改为 `@Parameter` 注入、`temp` → `tempDir`），并迁移到 JUnit 5。

**工作逻辑**：
- 删除子类构造函数 `public TestFlinkMergingMetrics(FileFormat fileFormat)`。
- v1.17/v1.18 仍使用 `HadoopTableResource`（`@Rule` → 改为 JUnit 5 风格，仍按各自 v1.17/v1.18 既有支持）。
- v1.19 改为 `@RegisterExtension private static final HadoopCatalogExtension CATALOG_EXTENSION = ...`，并在 `writeAndGetAppender` 中 `CATALOG_EXTENSION.catalog().createTable(...)` 显式建表，把 `temp.newFile()` 改为 `File.createTempFile("junit", null, tempDir)`。
- `org.apache.iceberg.Files.localOutput(...)` 改为 `import org.apache.iceberg.Files` 后 `Files.localOutput(...)`。

### `mr/src/test/java/org/apache/iceberg/mr/TestHelper.java`

**修改目的**：移除 `TestHelper` 中接收 `TemporaryFolder` 的旧构造函数及 `tmp` 字段，统一使用 `Path temp`。

**工作逻辑**：删除 `private final TemporaryFolder tmp` 字段；删除 `@Deprecated` 的接收 `TemporaryFolder tmp` 的构造函数；`appender()` 方法中删除 `if (null != tmp) { return new GenericAppenderHelper(table, fileFormat, tmp, conf); }` 分支，直接走 `Path temp` 分支。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMergingMetrics.java` / `spark/v3.4/.../TestSparkMergingMetrics.java` / `spark/v3.5/.../TestSparkMergingMetrics.java`

**修改目的**：适配父类 `TestMergingMetrics` 新签名。

**工作逻辑**：删除子类构造函数；`temp.newFile()` 改为 `File.createTempFile("junit", null, tempDir)`；`org.apache.iceberg.Files.localOutput(...)` 改为 `Files.localOutput(...)`（统一 import）。

## 小结

- **成效**：完成 `data` 模块及其 Flink 1.17/1.18/1.19、MR、Spark 3.3/3.4/3.5 子模块中共享测试基类（`TestMergingMetrics`、`RecordWrapperTest`）及其子类、相关辅助类（`DataTestHelpers`、`GenericAppenderHelper`、`TestHelper`）从 JUnit 4 到 JUnit 5 + AssertJ 的迁移。共修改 29 个文件，985 行新增 / 956 行删除。
- **影响范围**：仅测试代码，无生产代码变更。涉及 `data`、`flink/v1.17|1.18|1.19`、`mr`、`spark/v3.3|3.4|3.5` 模块的测试类与辅助类。是 Iceberg 测试栈统一升级的一部分。
- **回迁到 1.4.x 的注意事项**：这是测试基础设施迁移，不影响生产功能，但回迁需谨慎：
  - 1.4.x 分支若仍以 JUnit 4 为主，单独回迁本提交会让 `data` 模块测试栈与分支其余部分（如 `core`、`api`）的 JUnit 4 风格不一致，且依赖 `ParameterizedTestExtension`（Iceberg 自定义扩展）和 AssertJ 在 1.4.x 中的可用性。需要确认 1.4.x 已引入这些依赖。
  - 由于本提交改动了多个模块共享的抽象基类（`TestMergingMetrics`、`RecordWrapperTest`），回迁时必须把所有相关子类（Flink 1.17/1.18/1.19、Spark 3.3/3.4/3.5、MR）一并同步，否则编译失败。
  - 建议：1.4.x 不主动回迁此类测试框架迁移提交，除非分支整体已经决定切换到 JUnit 5。如要回迁，应作为一组连续的 JUnit 5 迁移提交一起回迁，而非单独回迁本提交。
