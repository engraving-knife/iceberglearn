# 提交 0578：Core: Migrate tests to JUnit5

## 提交信息

- **序号**：0578 / 4088
- **哈希**：5ce5c788cba38e5b11745e7e6be24921cae65bb5
- **短哈希**：5ce5c788c
- **日期**：2024-03-11 22:53:24 +0900
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Core: Migrate tests to JUnit5 (#9892)
- **PR/Issue**：#9892

## 总体目的

本提交把 `iceberg-core` 模块中围绕 Snapshot 的一组测试类从 JUnit 4 迁移到 JUnit 5（Jupiter），是 Iceberg 渐进式测试框架现代化工作的一部分。涉及 9 个文件、412 行新增 / 446 行删除。具体迁移目标：

1. 将参数化测试类从 JUnit4 的 `@RunWith(Parameterized.class)` + 构造器注入模型，切换到 JUnit5 的自定义扩展 `ParameterizedTestExtension` + 字段注入模型；
2. 将测试基类从 JUnit4 风格的 `TableTestBase` 切换到 JUnit5 风格的 `TestBase`；
3. 将 `@Test` / `@Before` / `@Rule TemporaryFolder` 等 JUnit4 API 替换为 JUnit5 的 `@TestTemplate` / `@BeforeEach` / `@TempDir`；
4. 将断言从 JUnit4 的 `org.junit.Assert.*` 与 `org.assertj.core.api.Assertions`（非静态导入）统一为 AssertJ 的静态导入 `assertThat` / `assertThatThrownBy` / `assumeThat`，使断言更流畅、可读性更高。

背景：JUnit 4 已进入维护模式，JUnit 5 提供了更现代的扩展模型（`Extension`）、更好的依赖注入、对 Java 8+ 的原生支持。Iceberg 在 `api` 模块中已引入了借鉴自 Flink 的 `ParameterizedTestExtension` 与 `@Parameters` 注解（位于 `api/src/test/java/org/apache/iceberg/`），用于在 JUnit5 中实现类级参数化测试，本提交即在各 core 测试中落地使用该机制。

## 如何达成设计目的

整体设计是"逐类机械迁移 + 复用既有的 JUnit5 基础设施"，主要借助三个已存在的 JUnit5 组件：

1. **`TestBase`**（`core/src/test/java/org/apache/iceberg/TestBase.java`）：JUnit5 版本的测试基类，自身标注 `@ExtendWith(ParameterizedTestExtension.class)`，使用 `@TempDir Path` 提供临时目录、`@BeforeEach`/`@AfterEach` 管理生命周期，并定义了 `formatVersion` 字段（由参数化扩展注入）。它对应 JUnit4 时代的 `TableTestBase`。迁移的测试类把父类从 `TableTestBase` 改为 `TestBase`。

2. **`ParameterizedTestExtension`**（`api/src/test/java/org/apache/iceberg/ParameterizedTestExtension.java`）：实现 `TestTemplateInvocationContextProvider` 的自定义 JUnit5 扩展，替代 JUnit4 的 `@RunWith(Parameterized.class)`。其工作逻辑为：
   - 通过 `AnnotationSupport.findAnnotatedMethods` 在测试类（含父类，TOP_DOWN 遍历）中查找唯一标注 `@Parameters` 的静态方法并调用它获取参数值；
   - 参数值可以是 `Object[][]` 或 `Collection`，其中 `Collection` 的每个元素若非数组则包装为单元素数组；
   - 若测试类含 `@Parameter` 标注的字段，则用**字段注入**方式（`FieldInjectingHook` 在 `beforeEach` 中通过反射给字段赋值）；否则用**构造器参数解析**方式（`ConstructorParameterResolver`）。本提交的测试类走字段注入路径（`formatVersion` 字段在 `TestBase` 中以 `@Parameter` 标注）；
   - 用 `@Parameters(name=...)` 的模板生成每次调用的显示名（如 `formatVersion = 1`）。

3. **`@Parameters`**（`api/src/test/java/org/apache/iceberg/Parameters.java`）：方法级注解，替代 JUnit4 的 `@Parameterized.Parameters`，仅有一个 `name` 属性（默认 `{index}`）。

基于以上组件，每个参数化测试类的迁移范式为：
```
// JUnit4
@RunWith(Parameterized.class)
public class TestXxx extends TableTestBase {
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() { return new Object[] {1, 2}; }
  public TestXxx(int formatVersion) { super(formatVersion); }
  @Test public void testFoo() { ... }
}

// JUnit5
@ExtendWith(ParameterizedTestExtension.class)
public class TestXxx extends TestBase {
  @Parameters(name = "formatVersion = {0}")
  protected static List<Object> parameters() { return Arrays.asList(1, 2); }
  @TestTemplate public void testFoo() { ... }
}
```

关键点：JUnit5 中参数化的每个测试方法必须标注 `@TestTemplate`（而非 `@Test`），因为 `ParameterizedTestExtension` 是 `TestTemplateInvocationContextProvider`，只有 `@TestTemplate` 标注的方法才会被该扩展识别并多次调用。子类重复声明 `@ExtendWith(ParameterizedTestExtension.class)` 是显式写法（实际可由 `TestBase` 继承获得）。

## 修改详情

### `core/src/test/java/org/apache/iceberg/LocalTableOperations.java`

**修改目的**：让该测试辅助类适配 JUnit5 的 `@TempDir Path` 临时目录机制。

**工作逻辑**：原本持有 `org.junit.rules.TemporaryFolder temp`，构造器接收 `TemporaryFolder`，创建元数据文件时调用 `temp.newFile(name).getAbsolutePath()`。迁移后改为持有 `java.nio.file.Path temp`，构造器接收 `Path`，创建文件改用 `File.createTempFile("junit", null, temp.toFile()).getAbsolutePath()`。这是必须的，因为 JUnit5 的 `@TempDir` 注入的是 `Path`（或 `File`），不再有 `TemporaryFolder.newFile(name)` 的便捷方法，需直接用 JDK 的 `File.createTempFile` 在指定目录下创建临时文件。注意前缀由具体文件名改为固定 `"junit"`（后缀 `null`），由系统生成唯一名。

### `core/src/test/java/org/apache/iceberg/TestSnapshot.java`

**修改目的**：将 Snapshot 测试类从 JUnit4 参数化迁移到 JUnit5。

**工作逻辑**：父类 `TableTestBase` → `TestBase`；去掉 `@RunWith(Parameterized.class)` 与构造器，改用 `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameters protected static List<Object> parameters()` 返回 `Arrays.asList(1, 2)`；所有 `@Test` 改为 `@TestTemplate`。断言方面：`Assume.assumeTrue("Delete files only supported in V2", formatVersion >= 2)` 改为 `assumeThat(formatVersion).as("Delete files only supported in V2").isGreaterThanOrEqualTo(2)`（静态导入 `org.assertj.core.api.Assumptions.assumeThat`）；`Assert.assertEquals("msg", expected, actual)` 改为 `assertThat(actual).as("msg").isEqualTo(expected)`；对 `Iterable` 的大小断言由 `Assert.assertEquals("msg", 1, Iterables.size(it))` 改为 `assertThat(it).as("msg").hasSize(1)`。

### `core/src/test/java/org/apache/iceberg/TestSnapshotJson.java`

**修改目的**：将 Snapshot JSON 序列化测试迁移到 JUnit5（非参数化类）。

**工作逻辑**：`@Rule public TemporaryFolder temp = new TemporaryFolder()` 改为 `@TempDir private Path temp`；`org.junit.Test` 改为 `org.junit.jupiter.api.Test`；`LocalTableOperations(temp)` 因 `temp` 类型变为 `Path` 而自动适配。断言由 `Assert.assertEquals/assertNull` 改为 `assertThat(...).isEqualTo(...).isNull()`；原来混用的 `Assertions.assertThat(json).isEqualTo(...)` 统一为静态导入 `assertThat`。创建 manifest list 文件由 `temp.newFile("manifests" + UUID.randomUUID())` 改为 `File.createTempFile("manifests", null, temp.toFile())`，并移除不再需要的 `java.util.UUID` 导入。

### `core/src/test/java/org/apache/iceberg/TestSnapshotLoading.java`

**修改目的**：将 Snapshot 加载测试迁移到 JUnit5 参数化模型。

**工作逻辑**：父类与参数化迁移同 `TestSnapshot`；`@Before` 改为 `@BeforeEach`；`@Test` 改为 `@TestTemplate`。该类原本已使用 AssertJ 的 `assertThat`/`assertThatThrownBy`，仅需把 `org.assertj.core.api.Assumptions.assumeThat`（静态方法引用）改为静态导入 `assumeThat`。

### `core/src/test/java/org/apache/iceberg/TestSnapshotManager.java`

**修改目的**：将 SnapshotManager（cherry-pick、branch/tag 管理、rollback、事务）测试迁移到 JUnit5 参数化模型。这是本提交改动量最大的文件。

**工作逻辑**：父类与参数化迁移同前；所有 `@Test` 改为 `@TestTemplate`。断言迁移覆盖面广：`Assert.assertEquals/ assertNull/ assertNotEquals/ assertSame/ assertNotNull/ assertTrue` 全部改为对应 AssertJ 链式断言；`Assertions.assertThatThrownBy(...)` 改为静态导入 `assertThatThrownBy`；`Assertions.assertThat(...)` 改为静态导入 `assertThat`。

需特别注意一个**行为强化**：`testSnapshotManagerInvalidParameters` 原实现为 `Assert.assertThrows("Incorrect input transaction: null", IllegalArgumentException.class, () -> new SnapshotManager(null))`——JUnit4 的 `assertThrows(String message, ...)` 中第一个参数是断言失败时的描述信息，并非校验异常消息，因此原测试只验证抛出了 `IllegalArgumentException`。迁移后改为 `assertThatThrownBy(() -> new SnapshotManager(null)).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid input transaction: null")`，新增了对异常消息 `"Invalid input transaction: null"` 的严格断言（注意此处用的是 `Invalid` 而非原描述里的 `Incorrect`，后者只是失败描述、并非实际异常文本）。此外该方法移除了 `throws Exception` 声明（JUnit5 不再需要）。

### `core/src/test/java/org/apache/iceberg/TestSnapshotRefParser.java`

**修改目的**：将 SnapshotRef 的 JSON 解析测试迁移到 JUnit5（非参数化类）。

**工作逻辑**：`org.junit.Test` 改为 `org.junit.jupiter.api.Test`（保留 `@Test`，无需 `@TestTemplate`）；移除 `org.junit.Assert` 与 `org.assertj.core.api.Assertions` 导入，改为静态导入 `assertThat` / `assertThatThrownBy`。`Assert.assertEquals("msg", expected, actual)` 改为 `assertThat(actual).as("msg").isEqualTo(expected)`；`Assertions.assertThatThrownBy(...)` 改为 `assertThatThrownBy(...)`。

### `core/src/test/java/org/apache/iceberg/TestSnapshotSelection.java`

**修改目的**：将 Snapshot 选择测试迁移到 JUnit5 参数化模型。

**工作逻辑**：父类与参数化迁移同前；`@Test` 改为 `@TestTemplate`。断言迁移：`Assert.assertEquals("msg", 0, listManifestFiles().size())` 改为 `assertThat(listManifestFiles()).hasSize(0)`（去掉描述）；`Assert.assertEquals("msg", 2, Iterables.size(table.snapshots()))` 改为 `assertThat(table.snapshots()).hasSize(2)`；`Assert.assertNotNull("msg", x)` 改为 `assertThat(x).isNotNull()`。

### `core/src/test/java/org/apache/iceberg/TestSnapshotSummary.java`

**修改目的**：将 Snapshot 摘要统计测试迁移到 JUnit5 参数化模型。

**工作逻辑**：父类与参数化迁移同前；`@Test` 改为 `@TestTemplate`。该类的断言迁移有一个值得注意的改进：对 `Map<String,String> summary` 的多处校验，原先用多条 `Assert.assertEquals("10", summary.get(KEY))` 与 `Assert.assertNull(summary.get(KEY))` 分散断言，迁移后合并为单条 AssertJ 链式断言，例如：
```java
assertThat(summary)
    .containsEntry(SnapshotSummary.ADDED_FILE_SIZE_PROP, "10")
    .containsEntry(SnapshotSummary.TOTAL_FILE_SIZE_PROP, "10")
    .doesNotContainKey(SnapshotSummary.REMOVED_FILE_SIZE_PROP);
```
这利用了 AssertJ 对 Map 的 `containsEntry` / `doesNotContainKey` 流式断言，可读性更好，且能在一次断言中同时校验多个键值。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`

**修改目的**：对该超大测试类做最小化迁移——仅把临时目录机制切换到 JUnit5，以适配 `LocalTableOperations` 现在接收 `Path` 的签名。

**工作逻辑**：`@Rule public TemporaryFolder temp = new TemporaryFolder()` 改为 `@TempDir private Path temp`；`org.junit.Test` 改为 `org.junit.jupiter.api.Test`；`org.junit.Rule` / `org.junit.rules.TemporaryFolder` 导入替换为 `org.junit.jupiter.api.io.TempDir` 与 `java.nio.file.Path`；`createManifestListWithManifestFile` 中 `temp.newFile("manifests" + UUID.randomUUID())` 改为 `File.createTempFile("manifests", null, temp.toFile())`。注意该类**保留了** `import org.assertj.core.api.Assertions;` 与 `import org.junit.Assert;`（文件其余部分仍有大量 JUnit4 断言未迁移），说明本次只迁移与临时目录相关的部分，属于渐进式迁移的折中——因 `LocalTableOperations` 签名变更而必须同步调整，但未对全文件做断言现代化。

## 小结

本提交将 core 模块 8 个 Snapshot 相关测试类与 1 个测试辅助类（`LocalTableOperations`）从 JUnit4 迁移到 JUnit5，统一使用 Iceberg 自有的 `ParameterizedTestExtension` + `@Parameters` 参数化模型与 `TestBase` 基类，断言统一为 AssertJ 静态导入风格，临时目录由 `@Rule TemporaryFolder` 切换为 `@TempDir Path`。迁移整体是机械式的、不改变被测代码，但有两处值得注意：一是 `testSnapshotManagerInvalidParameters` 强化了对异常消息的校验；二是 `TestTableMetadata` 仅做了最小化迁移（临时目录部分），保留了其余 JUnit4 断言，属渐进式。回迁到 1.4.x 时需注意：1.4.x 仍以 JUnit4 为主，本提交依赖的 `TestBase`、`ParameterizedTestExtension`、`@Parameters` 是否已在 1.4.x 分支存在需确认；若 1.4.x 未引入这些 JUnit5 基础设施，则不宜直接 cherry-pick，应连同 JUnit5 基础设施一起迁移或保持 1.4.x 的 JUnit4 风格。
