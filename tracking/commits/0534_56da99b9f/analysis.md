# 提交 0534：将 Spark procedure 测试迁移到 JUnit5

## 提交信息

- **序号**：0534 / 4088
- **哈希**：56da99b9f908237ce0a9be565258fa10cd4fe88b
- **短哈希**：56da99b9f
- **日期**：2024-02-24（Sat Feb 24 19:52:39 2024 +0900）
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Spark: Migrate procedure tests to JUnit5 (#9760)
- **PR/Issue**：#9760

## 总体目的

Iceberg 的 Spark v3.5 扩展测试套件此前基于 JUnit4 编写，依赖 JUnit4 的 `Parameterized` Runner、`@Rule` 临时目录、`org.junit.Assert` 断言等机制。项目已决定将测试基础设施整体迁移到 JUnit5（Jupiter），并在此前已经搭建好 JUnit5 基础设施：自定义的 `ParameterizedTestExtension`（`api/src/test/java/org/apache/iceberg/ParameterizedTestExtension.java`，来自 #9161）、`ExtensionsTestBase`（`spark/v3.5/spark-extensions/.../ExtensionsTestBase.java`，基于 JUnit5 的 `@BeforeAll`）、以及 `CatalogTestBase`（使用 `@TempDir Path temp` 与 `@ExtendWith(ParameterizedTestExtension.class)`）。

本提交的目的，是将 Spark v3.5 spark-extensions 模块下所有 procedure（存储过程）相关测试类从 JUnit4 一次性迁移到 JUnit5，统一到项目新的测试基础设施上，消除 JUnit4/JUnit5 混用的不一致状态，享受 JUnit5 更现代的扩展模型与 AssertJ 流式断言带来的可读性提升。

## 如何达成设计目的

迁移采用"机械且系统"的转换规则，对 20 个测试文件执行统一的模式替换。核心设计是把 JUnit4 的三大机制分别映射到 JUnit5 等价物：

### 1. 参数化测试机制：Runner → Extension

JUnit4 通过 `@RunWith(Parameterized.class)` + 构造器注入参数；JUnit5 改用 `@ExtendWith(ParameterizedTestExtension.class)`（Iceberg 自定义扩展）+ `@Parameter(index=N)` 字段注入。对于仅需 catalog 三参数（catalogName/implementation/config）的测试类，参数由基类 `CatalogTestBase` 的 `@Parameters` 提供，子类无需再声明构造器，直接继承即可；对于需要额外参数（如 `formatVersion`）的测试类（如 `TestChangelogTable`），子类自行声明 `@Parameters` 并将额外参数追加到 catalog 三参数之后（索引 3+），用 `@Parameter(index = 3)` 注入对应字段。

### 2. 生命周期注解：Junit4 → Jupiter

| JUnit4 | JUnit5 (Jupiter) |
|--------|------------------|
| `@Test`（参数化场景） | `@TestTemplate` |
| `@Before` | `@BeforeEach` |
| `@After` | `@AfterEach` |
| `@BeforeClass` | `@BeforeAll` |
| `@AfterClass` | `@AfterAll` |

参数化测试方法使用 `@TestTemplate`（而非 `@Test`），因为每个方法需在多组 catalog 参数下重复执行，`@TestTemplate` 是 JUnit5 中"模板化多次调用"的语义注解。

### 3. 临时目录：`@Rule TemporaryFolder` → `@TempDir Path`

JUnit4 的 `@Rule public TemporaryFolder temp = new TemporaryFolder()` 配合 `temp.newFolder()` 创建子目录；JUnit5 改用基类 `CatalogTestBase` 中 `@TempDir protected Path temp` 注入的 `java.nio.file.Path`，创建子目录改为 `java.nio.file.Files.createTempDirectory(temp, "junit")`。各测试类中自带的 `@Rule TemporaryFolder` 字段被删除，统一使用继承自基类的 `temp`。

### 4. 断言：`org.junit.Assert` → AssertJ `assertThat`

将 JUnit4 的 `Assert.*` 静态调用全部改写为 AssertJ 流式断言，并新增 `import static org.assertj.core.api.Assertions.assertThat`。典型映射见下文"修改详情"。注意：基类 `SparkTestHelperBase` 提供的自定义 `protected void assertEquals(String, List, List)` 辅助方法（用于比对 SQL 行集合）被**保留不动**，因为它不是 JUnit 的 `Assert.assertEquals`，而是项目自有的测试工具方法。

### 5. 基类替换：`SparkExtensionsTestBase` → `ExtensionsTestBase`

旧的 `SparkExtensionsTestBase` 继承自 JUnit4 的 `SparkCatalogTestBase`，使用 `@BeforeClass` 与构造器；新的 `ExtensionsTestBase` 继承自 JUnit5 的 `CatalogTestBase`，使用 `@BeforeAll`，无构造器。子类随基类一起切换，删除原构造器。

## 修改详情

本提交修改 20 个文件，净减少 72 行（+558 / -630）。各文件均位于 `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/`。以下按改动类型归类说明工作逻辑。

### 注解与基类迁移（全部 20 个文件通用）

**修改目的**：将每个测试类从 JUnit4 测试模型切换到 JUnit5。

**工作逻辑**（以 `TestCherrypickSnapshotProcedure.java` 为典型）：

```java
// 修改前
import org.junit.After;
import org.junit.Test;
public class TestCherrypickSnapshotProcedure extends SparkExtensionsTestBase {
  public TestCherrypickSnapshotProcedure(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }
  @After public void removeTables() { ... }
  @Test public void testCherrypickSnapshotUsingPositionalArgs() { ... }

// 修改后
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestTemplate;
public class TestCherrypickSnapshotProcedure extends ExtensionsTestBase {
  @AfterEach public void removeTables() { ... }
  @TestTemplate public void testCherrypickSnapshotUsingPositionalArgs() { ... }
```

构造器被整体删除（参数由扩展通过字段注入到基类）；`@After`→`@AfterEach`；`@Test`→`@TestTemplate`；基类由 `SparkExtensionsTestBase` 改为 `ExtensionsTestBase`；`import java.util.Map` 因构造器删除而移除。

对于需要自身额外参数的 `TestChangelogTable.java`，还额外引入 `@ExtendWith(ParameterizedTestExtension.class)`、`@Parameter(index = 3)`、`org.apache.iceberg.Parameters`，并将参数顺序从 `(formatVersion, catalogName, implementation, config)` 调整为 `(catalogName, implementation, config, formatVersion)`，使 catalog 三参数占据索引 0-2（与基类 `CatalogTestBase` 一致），`formatVersion` 后移至索引 3：

```java
@Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}, formatVersion = {3}")
public static Object[][] parameters() { ... }

@Parameter(index = 3)
private int formatVersion;
```

### 临时目录迁移（涉及使用 `temp.newFolder()` 的文件，如 `TestExpireSnapshotsProcedure`、`TestRemoveOrphanFilesProcedure`、`TestWriteAborts`）

**修改目的**：将 JUnit4 `TemporaryFolder` 用法替换为 JUnit5 `@TempDir Path` 用法。

**工作逻辑**：

```java
// 修改前（TestExpireSnapshotsProcedure）
"file:" + temp.newFolder().toString()

// 修改后
Files.createTempDirectory(temp, "junit").toFile().toURI().toString()
```

`temp` 由原来的 `TemporaryFolder` 对象变为继承自 `CatalogTestBase` 的 `@TempDir Path`。`temp.newFolder()`（创建子目录）替换为 `Files.createTempDirectory(temp, "junit")`（在 `temp` 目录下创建前缀为 "junit" 的新临时目录）。`TestWriteAborts` 中 `temp.toFile().toString()` 替换为 `Files.createTempDirectory(temp, "junit").toFile().toString()`，同时 `throws Exception` 收窄为 `throws IOException` 以匹配 `Files.createTempDirectory` 的受检异常。

`TestCallStatementParser.java` 中独立的 `@Rule public TemporaryFolder temp = new TemporaryFolder()` 字段被直接删除（该类使用静态 SparkSession，不依赖基类 temp，且测试中无需临时目录）。

### 断言迁移（涉及所有使用 `Assert.*` 的文件）

**修改目的**：用 AssertJ 流式断言替换 JUnit4 `Assert` 静态断言，提升可读性与失败信息质量。

**工作逻辑**（典型映射，汇总自多个文件）：

```java
// 等值断言（无消息）
Assert.assertEquals(7, call.args().size())
  → assertThat(seqAsJavaList(call.args())).hasSize(7);

// 等值断言（带消息）：注意 as() 前置
Assert.assertEquals("Should be 2 snapshots", 2, Iterables.size(table.snapshots()))
  → assertThat(table.snapshots()).as("Should be 2 snapshots").hasSize(2);

Assert.assertEquals("Operation must match", DataOperations.DELETE, snap3.operation())
  → assertThat(snap3.operation()).as("Operation must match").isEqualTo(DataOperations.DELETE);

// 列表精确匹配
Assert.assertEquals(ImmutableList.of("c","n","func"), JavaConverters.seqAsJavaList(call.name()))
  → assertThat(seqAsJavaList(call.name())).containsExactly("c", "n", "func");

// 布尔断言
Assert.assertTrue("Delete manifest should still exist", localFs.exists(deleteManifestPath))
  → assertThat(localFs.exists(deleteManifestPath)).as("Delete manifest should still exist").isTrue();
Assert.assertFalse("Delete manifest should be removed", localFs.exists(deleteManifestPath))
  → assertThat(localFs.exists(deleteManifestPath)).as("Delete manifest should be removed").isFalse();

// 字符串前缀
Assert.assertTrue(file1.startsWith("file:/"))
  → assertThat(file1).startsWith("file:/");

// 类型检查
Assert.assertTrue("Expected instance of " + expectedClass.getName(), expectedClass.isInstance(value))
  → assertThat(value).isInstanceOf(expectedClass);

// 过滤后计数（Iterables.filter + size → filteredOn + hasSize）
Assert.assertEquals("Snapshot ID should not be present", 0,
    Iterables.size(Iterables.filter(table.snapshots(), s -> s.snapshotId() == firstSnapshotId)))
  → assertThat(table.snapshots())
        .as("Snapshot ID should not be present")
        .filteredOn(snapshot -> snapshot.snapshotId() == firstSnapshotId)
        .hasSize(0);
```

注意点：
- `Iterables.size(coll)` 这类 Guava 调用被消除，直接对集合使用 `.hasSize(n)`。
- `Iterables.filter(...)` 被 AssertJ 的 `.filteredOn(predicate)` 取代，链式表达更清晰。
- 带消息的断言中，JUnit4 的消息参数在前，AssertJ 用 `.as(message)` 链式调用并在最前。
- 基类自定义的 `assertEquals(String, List<Object[]>, List<Object[]>)`（行比对辅助方法）保持不变，未做替换。
- 部分文件原本已用 `Assertions.assertThatThrownBy(...)` 做异常断言（AssertJ），这些无需改动；只有 `Assertions.assertThat(x).isEqualTo(y)` 形式中部分被统一为静态导入的 `assertThat`（如 `TestExpireSnapshotsProcedure` 中 `Assertions.assertThat(output.get(0)[5]))` → `assertThat(output.get(0)[5]))`）。

### 静态导入整理

**修改目的**：配合 AssertJ 断言引入静态导入，移除不再需要的导入。

**工作逻辑**：新增 `import static org.assertj.core.api.Assertions.assertThat;`（多数文件）；`TestCallStatementParser` 额外新增 `import static scala.collection.JavaConverters.seqAsJavaList;` 并移除 `import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;`（因 `containsExactly` 不再需要 `ImmutableList`）。移除 `import org.apache.iceberg.relocated.com.google.common.collect.Iterables;`（因 `Iterables.size/filter` 不再使用）。新增 `import java.nio.file.Files;`（用于 `createTempDirectory`）。

### 各文件概览

| 文件 | 主要改动 |
|------|---------|
| `TestCallStatementParser.java` | 基类不变（独立类），`@BeforeClass/@AfterClass`→`@BeforeAll/@AfterAll`，删 `@Rule TemporaryFolder`，Assert→assertThat |
| `TestChangelogTable.java` | 自定义参数化（formatVersion），`@Parameter(index=3)` 字段注入，参数顺序调整 |
| `TestCherrypickSnapshotProcedure.java` | 标准 procedure 迁移（基类、注解、删构造器） |
| `TestCreateChangelogViewProcedure.java` | 标准 procedure 迁移 |
| `TestExpireSnapshotsProcedure.java` | 大量 Assert→assertAt，`temp.newFolder()`→`Files.createTempDirectory`，`Iterables` 消除 |
| `TestFastForwardBranchProcedure.java` | 标准 procedure 迁移 |
| `TestMigrateTableProcedure.java` | 标准 procedure 迁移，Assert→assertThat |
| `TestPublishChangesProcedure.java` | 标准 procedure 迁移 |
| `TestRegisterTableProcedure.java` | 标准 procedure 迁移 |
| `TestRemoveOrphanFilesProcedure.java` | 删 `@Rule TemporaryFolder`，`temp.newFolder()`→`Files.createTempDirectory` |
| `TestRewriteDataFilesProcedure.java` | 标准 procedure 迁移，大量断言改写 |
| `TestRewriteManifestsProcedure.java` | 标准 procedure 迁移 |
| `TestRewritePositionDeleteFiles.java` | 标准 procedure 迁移 |
| `TestRewritePositionDeleteFilesProcedure.java` | 标准 procedure 迁移 |
| `TestRollbackToSnapshotProcedure.java` | 标准 procedure 迁移 |
| `TestRollbackToTimestampProcedure.java` | 标准 procedure 迁移 |
| `TestSetCurrentSnapshotProcedure.java` | 标准 procedure 迁移 |
| `TestSnapshotTableProcedure.java` | 标准 procedure 迁移 |
| `TestWriteAborts.java` | `temp.toFile()`→`Files.createTempDirectory(temp,"junit").toFile()`，异常类型收窄 |
| `TestAncestorsOfProcedure.java` | 新增 `@ExtendWith(ParameterizedTestExtension.class)` |

## 小结

**成效**：一次性完成 Spark v3.5 spark-extensions 模块 20 个 procedure 测试类从 JUnit4 到 JUnit5 的迁移，统一到项目既有的 JUnit5 测试基础设施（`ExtensionsTestBase`、`ParameterizedTestExtension`、`@TempDir`），消除了 JUnit4/JUnit5 混用状态。迁移后测试逻辑等价、断言可读性更好（AssertJ 流式 + 失败信息更丰富）、参数化机制更现代（扩展模型替代 Runner）。

**影响范围**：仅影响测试代码，不改动任何产品代码、API 或运行时行为；测试覆盖的功能（各 procedure）保持不变。

**回迁到 1.4.x 的注意事项**：
1. **依赖前置基础设施**：本迁移依赖 `ExtensionsTestBase`、`CatalogTestBase`、`ParameterizedTestExtension`、`@Parameter`/`@Parameters`（`org.apache.iceberg` 包）等 JUnit5 基类与扩展，这些来自更早的提交（如 #9161）。1.4.x 分支若尚未引入这些基础设施，则无法直接回迁本提交，需先回迁基础设施类，否则编译失败。
2. **构建配置**：需确保 1.4.x 的 spark-extensions 测试模块已声明 JUnit5（`org.junit.jupiter`）与 AssertJ 测试依赖，且 surefire 插件配置支持 JUnit5 Platform 引擎。
3. **范围匹配**：本提交仅迁移 v3.5 模块。1.4.x 若同时维护 v3.3/v3.4 模块，需确认这些模块是否也已具备对应基础设施；若 1.4.x 只对应单一 Spark 版本，需将路径映射到对应版本目录。
4. **机械迁移风险低**：改动为系统性的模式替换，语义等价，回迁冲突主要来自基础设施是否存在，而非迁移本身。
5. **`@TestTemplate` 语义**：迁移后 procedure 测试使用 `@TestTemplate`，需确认 1.4.x 的 `ParameterizedTestExtension` 能正确驱动 `@TestTemplate` 在多 catalog 参数下执行，否则测试可能只跑一次或报错。
