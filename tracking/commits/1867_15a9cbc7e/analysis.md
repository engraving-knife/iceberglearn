# 提交 1867：Spark 3.4: Migrate TestBase related tests in spark and actions to JUnit5 (#12552)

## 提交信息

- **序号**：1867 / 4088
- **哈希**：15a9cbc7e322f0e29be97b5d488ae78e29454b1e
- **短哈希**：15a9cbc7e
- **日期**：2025-03-17 15:50:57 +0100
- **作者**：Tom Tanaka
- **提交说明**：Spark 3.4: Migrate TestBase related tests in spark and actions to JUnit5 (#12552)
- **PR/Issue**：#12552

## 总体目的

这是 1850（#12501，Spark 3.4 测试基类迁移到 JUnit 5）的后续提交。1850 完成了测试基类（`TestBase`/`TestBaseWithCatalog`/`CatalogTestBase`）的迁移，并把 `TestFunctionCatalog`、`TestPathIdentifier`、`TestAlterTable` 三个直接使用基类的测试切换到新基类。但 Spark 3.4 模块中仍有大量测试类继承旧的 `SparkTestBase`（JUnit 4 风格），未完成迁移：

- `TestSpark3Util`、`TestSparkSessionCatalog`（spark 测试）
- `TestDeleteReachableFilesAction`、`TestExpireSnapshotsAction`、`TestRemoveDanglingDeleteAction`、`TestRewriteTablePathsAction`、`TestSparkFileRewriter`（spark actions 测试）

这些测试仍用 `extends SparkTestBase`、`@Before`/`@Test`/`@Rule public TemporaryFolder`、`Assert.assertEquals` 等 JUnit 4 API，与新基类 `TestBase`（JUnit 5）不一致。本提交把这 7 个 3.4 测试类全部迁移到新基类与 JUnit 5，并对 3.5 的对应测试做同步调整，使两个版本的测试风格完全对齐。

## 如何达成设计目的

对每个测试类执行机械式迁移：
1. 基类 `SparkTestBase` → `TestBase`（或带 catalog 的对应基类）。
2. JUnit 4 注解 → JUnit 5：`@Before`→`@BeforeEach`、`@BeforeClass`→`@BeforeAll`、`@Test`→`@Test`（非参数化）或 `@TestTemplate`（参数化）。
3. `@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;` 或 `@TempDir private File tableDir;`。
4. `Assert.assertEquals(expected, actual)` / `Assert.assertTrue(...)` / `Assert.assertThrows(...)` → AssertJ `assertThat(actual).isEqualTo(expected)` / `assertThat(...).isTrue()` / `assertThatThrownBy(...).isInstanceOf(...).hasMessage(...)`。
5. 参数化测试加 `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameters` + `@Parameter`。
6. 移除 `import org.junit.*`，新增 `import org.junit.jupiter.api.*` 与 `import static org.assertj.core.api.Assertions.*`。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestSpark3Util.java` (修改, +44/-46 lines)

**修改目的**：迁移 `TestSpark3Util` 到 `TestBase` + JUnit 5。

**工作逻辑**：基类 `SparkTestBase` → `TestBase`；`import org.junit.Assert/Test` → `org.junit.jupiter.api.Test` + AssertJ 静态导入；大量 `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`。测试逻辑不变。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestSparkSessionCatalog.java` (修改, +23/-43 lines)

**修改目的**：迁移 `TestSparkSessionCatalog` 到 `TestBase` + JUnit 5。

**工作逻辑**：基类 `SparkTestBase` → `TestBase`；`@BeforeClass`→`@BeforeAll`、`@Before`→`@BeforeEach`；`Assert.assertTrue(...equals("default"))` → `assertThat(...).containsExactly("default")`；`Assert.assertThrows(IllegalArgumentException.class, () -> ...)` + `Assert.assertEquals(msg, exception.getMessage())` → `assertThatThrownBy(() -> ...).isInstanceOf(IllegalArgumentException.class).hasMessage(...)`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestDeleteReachableFilesAction.java` (修改, +56/-85 lines)

**修改目的**：迁移 `TestDeleteReachableFilesAction` 到 `TestBase` + JUnit 5。

**工作逻辑**：基类 `SparkTestBase` → `TestBase`；`@Rule TemporaryFolder` → `@TempDir Path`；`@Before`→`@BeforeEach`；`@Test`→`@Test`；`Assert.*` → `assertThat`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestExpireSnapshotsAction.java` (修改, +242/-226 lines)

**修改目的**：迁移 `TestExpireSnapshotsAction` 到 `TestBase` + JUnit 5，并引入参数化 formatVersion。

**工作逻辑**：基类 `SparkTestBase` → `TestBase`；加 `@ExtendWith(ParameterizedTestExtension.class)`；新增 `@Parameter private int formatVersion` 与 `@Parameters(name = "formatVersion = {0}") protected static List<Object> parameters() { return Arrays.asList(2, 3); }`，让测试在 v2/v3 表上各跑一次；`@Rule TemporaryFolder` → `@TempDir Path temp` + `@TempDir File tableDir`；`@Before`→`@BeforeEach`、`@Test`→`@TestTemplate`；引入 `assumeThat`（替代 `Assume.assumeTrue`）；`Assert.*` → `assertThat`。这是本提交改动量最大的文件。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveDanglingDeleteAction.java` (修改, +18/-21 lines)

**修改目的**：迁移 `TestRemoveDanglingDeleteAction` 到 `TestBase` + JUnit 5。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (修改, +25/-27 lines)

**修改目的**：迁移 `TestRewriteTablePathsAction` 到 `TestBase` + JUnit 5。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestSparkFileRewriter.java` (修改, +33/-27 lines)

**修改目的**：迁移 `TestSparkFileRewriter` 到 `TestBase` + JUnit 5。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkSessionCatalog.java` (修改, +6/-6 lines)

**修改目的**：同步 3.5 的 `TestSparkSessionCatalog`，保持与 3.4 一致。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestDeleteReachableFilesAction.java` (修改, +3/-3 lines)

**修改目的**：同步 3.5 的对应测试。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestExpireSnapshotsAction.java` (修改, +44/-40 lines)

**修改目的**：同步 3.5 的 `TestExpireSnapshotsAction`，引入参数化 formatVersion。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestSparkFileRewriter.java` (修改, +38/-32 lines)

**修改目的**：同步 3.5 的 `TestSparkFileRewriter`。

## 小结

- **成效**：Spark 3.4 模块剩余 7 个继承 `SparkTestBase` 的测试类全部迁移到 `TestBase` + JUnit 5，与 3.5 完全对齐；`TestExpireSnapshotsAction` 额外引入 v2/v3 参数化测试，扩大覆盖。3.5 的 4 个对应测试同步调整。至此 Spark 3.4 的 JUnit 5 迁移基本完成。
- **影响范围**：spark 3.4 模块 7 个测试文件、spark 3.5 模块 4 个测试文件，合计 11 个文件、+532/-556 行。仅测试代码，无生产代码改动。
- **回迁到 1.4.x 的注意事项**：纯测试迁移，回迁相对安全但工作量大。需先回迁 1850（测试基类迁移）以保证 `TestBase`/`TestBaseWithCatalog`/`ParameterizedTestExtension` 可用。若 1.4.x 的 Spark 3.4 测试仍用 JUnit 4 的 `SparkTestBase`，建议与 1850 一并回迁以减少后续冲突。`TestExpireSnapshotsAction` 的参数化改动较大，回迁时需整体替换。建议回迁。
