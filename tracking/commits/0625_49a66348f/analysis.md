# 提交 0625：将 core 模块测试从 JUnit 4 迁移到 JUnit 5

## 提交信息

- **序号**：0625 / 4088
- **哈希**：49a66348f0284076fbbc9a1d57ce5c146a6efc60
- **短哈希**：49a66348f
- **日期**：2024-03-26（Tue Mar 26 00:23:30 2024 +0900）
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Core: Migrate tests to JUnit5 (#10027)
- **PR/Issue**：#10027

## 总体目的

Iceberg 项目一直在持续推进从 JUnit 4 向 JUnit 5 的迁移。0289 号提交（PR #9161）已经在 `iceberg-api` 测试源码集中引入了 `ParameterizedTestExtension` / `@Parameter` / `@Parameters` 这套 JUnit 5 扩展（借鉴自 Apache Flink），用以替代 JUnit 4 的 `@RunWith(Parameterized.class)` 类级字段注入式参数化测试机制，并迁移了 `parquet` 模块的一个样板测试。其后多个提交陆续迁移各模块测试。

本提交是这一迁移工作在 **`core` 模块**上的继续推进：把 `core/src/test/java/org/apache/iceberg/` 下 14 个测试类（外加 `TestBase` 与 `V2TableTestBase` 两个测试基类）从 JUnit 4 风格迁移到 JUnit 5 风格，统一使用 `@ExtendWith(ParameterizedTestExtension.class)` + `@TestTemplate` + `@Parameter` + `@Parameters` 的组合，并把断言从 JUnit 4 的 `org.junit.Assert.*` 与 AssertJ 的 `Assertions.*` 静态调用统一改为 AssertJ 的静态导入流式 API（`assertThat` / `assertThatThrownBy` / `assumeThat`）。这是 Iceberg 测试基础设施现代化的一步，目的是：

1. 让 `core` 模块测试与 `api`/`parquet` 等已迁移模块保持一致的 JUnit 5 风格；
2. 摆脱 JUnit 4 的 `@RunWith` / `@Rule` / 构造函数注入参数等已废弃机制，统一到 JUnit 5 的扩展模型（`@ExtendWith`）与字段注入参数化；
3. 把临时目录管理从 JUnit 4 的 `@Rule TemporaryFolder` 迁到 JUnit 5 原生的 `@TempDir`；
4. 把断言风格统一为 AssertJ 流式 API，提升可读性与一致性；
5. 顺带修正若干断言的语义（如把 `Set` 的无序比较改为按迭代顺序精确比较）。

## 如何达成设计目的

设计者沿用 0289 提交确立的迁移范式，对每个测试类按一套固定的"机械替换 + 局部调整"流程处理：

### 1. 类级注解与参数化机制替换

- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`
- `@Parameterized.Parameters(name = "...")` → `@Parameters(name = "...")`
- `@Parameterized.Parameter` 字段 → `@Parameter` 字段（必要时用 `@Parameter(index = N)` 指定多参数的位置）
- 构造函数注入参数（如 `public TestFoo(int formatVersion) { super(formatVersion); }`）→ 字段注入（`@Parameter private int formatVersion;`），删除构造函数
- `@Test` → `@TestTemplate`（因为参数化测试在 JUnit 5 中要用 `@TestTemplate` 而非 `@Test`）
- `public static Object[] parameters()` 返回数组 → `protected static List<Object> parameters()` 返回 `List`（`ParameterizedTestExtension` 要求 `List`）

### 2. 生命周期回调替换

- `@Before` → `@BeforeEach`
- `@After` → `@AfterEach`
- `@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`（JUnit 5 原生 `@TempDir`，字段类型为 `java.nio.file.Path`）
- 因 `@TempDir` 是 `Path` 而非 `TemporaryFolder`，原本 `temp.newFolder()` 改为 `Files.createTempDirectory(temp, "junit").toFile()`（拿到一个 `File`，兼容旧测试代码对 `File` 的依赖）

### 3. 断言风格统一

- `import org.junit.Assert;` 与 `import org.assertj.core.api.Assertions;` → 静态导入 `assertThat` / `assertThatThrownBy` / `assumeThat`
- `Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`
- `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`
- `Assert.assertTrue(x)` / `assertFalse(x)` → `assertThat(x).isTrue()` / `isFalse()`
- `Assert.assertNull(x)` → `assertThat(x).isNull()`
- `Assert.assertThrows(Clz, () -> ...)` → `assertThatThrownBy(() -> ...).isInstanceOf(Clz.class).hasMessageStartingWith(...)`
- `Assertions.assertThatThrownBy(...)`（带类名前缀）→ `assertThatThrownBy(...)`（静态导入）
- `Assume.assumeTrue("msg", cond)` → `assumeThat(x).as("msg").isEqualTo(expected)`（AssertJ 的 `Assumptions.assumeThat`，失败时跳过测试而非报错）

### 4. 测试基类调整

- 多个原本 `extends TableTestBase` 的测试类改为 `extends TestBase`。`TableTestBase` 是 JUnit 4 风格的基类（构造函数注入 `formatVersion`），`TestBase` 是 JUnit 5 风格的基类（字段注入 + `@Parameters`）。本提交让 `V2TableTestBase` 也从 `extends TableTestBase` 改为 `extends TestBase`，并用 `@Parameters` 锁定 `formatVersion=2`，这样所有继承 `V2TableTestBase` 的测试（如 `TestRowDelta`）自动获得 JUnit 5 风格的参数化。
- `TestBase` 新增一个 `listManifestLists(File)` 重载方法。原因是原本 `listManifestLists(String)`（接收 `table.location()` 字符串）定义在 `TableTestBase` 中，迁移后 `TestSequenceNumberForV2Table` 改继承 `TestBase`，不再能访问 `TableTestBase` 的版本，因此在 `TestBase` 中新增一个接收 `File` 的重载，并把 `TestSequenceNumberForV2Table` 中的调用从 `listManifestLists(table.location())` 改为 `listManifestLists(new File(table.location()))`。

### 5. 断言语义修正（顺带）

在 `TestSchemaUpdate` 与 `TestSchemaAndMappingUpdate` 中，原本对 `Set` 的断言是 `assertThat(set).isEqualTo(Sets.newHashSet("a", "b"))`——这比较的是两个 `Set` 的相等性（与顺序无关）。迁移时改为 `assertThat(set).containsExactly("a", "b")`——这断言 `Set` 的**迭代顺序**与给定顺序完全一致。这是一个语义强化：从"内容相同即可"变为"内容与顺序都相同"。这通常意味着实现已保证 `Set` 是 `LinkedHashSet`（保留插入顺序），测试顺带锁定了顺序契约。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestBase.java`

**修改目的**：为迁移后的子类提供 `listManifestLists(File)` 重载。

**工作逻辑**：新增方法

```java
List<File> listManifestLists(File tableDirToList) {
  return Lists.newArrayList(
      new File(tableDirToList, "metadata")
          .listFiles(
              (dir, name) ->
                  name.startsWith("snap")
                      && Files.getFileExtension(name).equalsIgnoreCase("avro")));
}
```

与 `TableTestBase` 中接收 `String` 的版本逻辑一致：列出 `metadata/` 目录下以 `snap` 开头、扩展名为 `avro` 的文件（即快照清单列表文件）。新增 `File` 重载是因为 `TestSequenceNumberForV2Table` 迁移后调用方传 `new File(table.location())`。

### `core/src/test/java/org/apache/iceberg/V2TableTestBase.java`

**修改目的**：把 V2 表测试基类从 JUnit 4 风格迁到 JUnit 5 风格。

**工作逻辑**：

- 从 `extends TableTestBase` 改为 `extends TestBase`；
- 删除 `public V2TableTestBase() { super(2); }` 构造函数；
- 新增 `@Parameters(name = "formatVersion = {0}") protected static List<Object> parameters() { return Arrays.asList(2); }`，通过 `ParameterizedTestExtension` 把 `formatVersion` 锁定为 2，等价于原来 `super(2)` 的效果。

### `core/src/test/java/org/apache/iceberg/TestMetricsModes.java`

**修改目的**：把 `MetricsModes` 的参数化测试迁到 JUnit 5。

**工作逻辑**：

- 类注解 `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；
- `@Parameterized.Parameters` 方法返回 `Object[]{1, 2}` → `@Parameters` 方法返回 `Arrays.asList(1, 2)`；
- 构造函数注入 `formatVersion` → `@Parameter private int formatVersion;` 字段注入；
- `@Rule public TemporaryFolder temp` → `@TempDir private Path temp`，`temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`，`tableDir.delete()` → `assertThat(tableDir.delete()).isTrue()`；
- `@After` → `@AfterEach`，`@Test` → `@TestTemplate`；
- 所有 `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`；
- `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`（静态导入）。

### `core/src/test/java/org/apache/iceberg/TestSortOrder.java`

**修改目的**：把 `SortOrder` 的参数化测试迁到 JUnit 5。

**工作逻辑**：与 `TestMetricsModes` 相同的迁移模式。值得注意的是：

- `@Before` → `@BeforeEach`，`setupTableDir()` 中 `temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`；
- `Assert.assertTrue(SortOrder.unsorted().satisfies(...))` / `assertFalse(...)` → `assertThat(...).isTrue()` / `isFalse()`；
- `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).isEqualTo(expected)`（无 `as` 时省略）；
- `assertThat(table.sortOrders()).hasSize(1).containsKey(...)` 是 AssertJ 链式断言，等价于多次 `assertEquals` + `assertTrue(containsKey)`。

### `core/src/test/java/org/apache/iceberg/TestSortOrderParser.java`

**修改目的**：把 `SortOrderParser` 的参数化测试迁到 JUnit 5。

**工作逻辑**：

- 从 `extends TableTestBase` 改为 `extends TestBase`，构造函数 `super(1)` 改为 `@Parameters` 返回 `Arrays.asList(1)`；
- `@Test` → `@TestTemplate`；
- `Assert.assertEquals(10, order.orderId())` → `assertThat(order.orderId()).isEqualTo(10)`；
- `Assertions.assertThat(order.fields().get(0).transform()).isInstanceOf(UnknownTransform.class)` 与 `Assert.assertEquals("custom_transform", order.fields().get(0).transform().toString())` 合并为 `assertThat(order.fields().get(0).transform()).isInstanceOf(UnknownTransform.class).asString().isEqualTo("custom_transform")`——这是 AssertJ 流式 API 的优势，可以在一条链上既断言类型又断言字符串表示。

### `core/src/test/java/org/apache/iceberg/TestSingleValueParser.java`

**修改目的**：把 `SingleValueParser` 的非参数化测试迁到 JUnit 5。

**工作逻辑**：

- 这不是参数化测试，所以只需 `@Test`（JUnit 5 的 `org.junit.jupiter.api.Test`），不需要 `@TestTemplate`；
- `Assert.assertThrows(Clz, () -> ...)` + `Assert.assertTrue(exception.getMessage().startsWith(...))` 两步断言，合并为 `assertThatThrownBy(() -> ...).isInstanceOf(Clz.class).hasMessageStartingWith(...)` 一条流式断言，更简洁；
- `Assert.assertEquals(JsonUtil.mapper().readTree(s1), JsonUtil.mapper().readTree(s2))` → `assertThat(JsonUtil.mapper().readTree(s2)).isEqualTo(JsonUtil.mapper().readTree(s1))`（注意 actual 与 expected 的位置对调，符合 AssertJ "actual 在前" 的约定）。

### `core/src/test/java/org/apache/iceberg/TestSequenceNumberForV2Table.java`

**修改目的**：把 V2 表序列号测试迁到 JUnit 5。

**工作逻辑**：

- 从 `extends TableTestBase` 改为 `extends TestBase`，构造函数 `super(2)` 改为 `@Parameters` 返回 `Arrays.asList(2)`；
- `@Test` → `@TestTemplate`；
- `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`；
- `listManifestLists(table.location())` → `listManifestLists(new File(table.location()))`——因 `TestBase` 新增的是 `File` 重载，需要把 `String` 包一层 `new File(...)`。

### `core/src/test/java/org/apache/iceberg/TestMicroBatchBuilder.java`

**修改目的**：把 `MicroBatchBuilder` 的参数化测试迁到 JUnit 5。

**工作逻辑**：

- 从 `extends TableTestBase` 改为 `extends TestBase`，构造函数注入 → `@Parameter` 字段注入，`Object[]{1,2}` → `Arrays.asList(1,2)`；
- `@Before` → `@BeforeEach`，`@Test` → `@TestTemplate`；
- `Assert.assertEquals(batch.snapshotId(), 1L)` → `assertThat(batch.snapshotId()).isEqualTo(1L)`；
- `Assert.assertTrue(batch.lastIndexOfSnapshot())` → `assertThat(batch.lastIndexOfSnapshot()).isTrue()`；
- `Assert.assertTrue(Iterables.isEmpty(batch5.tasks()))` → `assertThat(batch5.tasks()).isEmpty()`（AssertJ 原生支持 `Iterable` 断言）；
- 末尾 `Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`。

### `core/src/test/java/org/apache/iceberg/TestSplitPlanning.java`

**修改目的**：把 `SplitPlanning` 的参数化测试迁到 JUnit 5。

**工作逻辑**：

- 从 `extends TableTestBase` 改为 `extends TestBase`，构造函数注入 → `@Parameter` 字段注入；
- `@Rule public TemporaryFolder temp` → `@TempDir private Path temp`，`temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`；
- `@Before` → `@BeforeEach`，`@Test` → `@TestTemplate`；
- `Assert.assertEquals(4, Iterables.size(table.newScan().planTasks()))` → `assertThat(table.newScan().planTasks()).hasSize(4)`（AssertJ 直接对 `Iterable` 断言大小，省去 `Iterables.size()` 包装）；
- `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`；
- `Assert.assertTrue(task instanceof SplitPositionDeletesScanTask)` → `assertThat(task).isInstanceOf(SplitPositionDeletesScanTask.class)`。

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java`

**修改目的**：把 `RemoveSnapshots` 的多参数参数化测试迁到 JUnit 5。

**工作逻辑**：这是体量最大的迁移（586 行变化）。该测试有两个参数：`formatVersion`（1/2）与 `incrementalCleanup`（true/false），共 4 组组合。

- 类注解 `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`；
- `@Parameterized.Parameters(name = "formatVersion = {0}, incrementalCleanup = {1}")` → `@Parameters(...)`，返回值从 `Object[][]` 改为 `Arrays.asList(new Object[]{...}, ...)`；
- `private final boolean incrementalCleanup;` + 构造函数注入 → `@Parameter(index = 1) private boolean incrementalCleanup;`（`index = 1` 因为 `incrementalCleanup` 是参数组第二个元素，`formatVersion` 默认 `index = 0`）；
- `Assume.assumeTrue("Delete files only supported in V2 spec", formatVersion == 2)` → `assumeThat(formatVersion).as("Delete files only supported in V2 spec").isEqualTo(2)`（AssertJ `Assumptions.assumeThat`，失败时跳过测试而非报错，等价 JUnit 4 `Assume.assumeTrue`）；
- `@Test` → `@TestTemplate`；
- 大量 `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`、`Assert.assertNull(x)` → `assertThat(x).isNull()`；
- `Sets.newHashSet(firstSnapshot.manifestListLocation())` 与 `deletedFiles` 的 `assertEquals` → `assertThat(deletedFiles).containsExactly(firstSnapshot.manifestListLocation())`（从 Set 相等改为按顺序精确断言）。

### `core/src/test/java/org/apache/iceberg/TestRewriteManifests.java`

**修改目的**：把 `RewriteManifests` 的参数化测试迁到 JUnit 5。

**工作逻辑**：

- 从 `extends TableTestBase` 改为 `extends TestBase`，构造函数注入 → `@Parameter` 字段注入；
- `@Test` → `@TestTemplate`；
- `Assert.assertEquals(1, ...size())` → `assertThat(...).hasSize(1)`；
- `assumeThat(...)` 已在原代码中使用（说明部分迁移先前已开始），本次完整迁移其余部分。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java`

**修改目的**：把 `RowDelta` 的多参数参数化测试迁到 JUnit 5。

**工作逻辑**：体量第二大的迁移（439 行变化）。该测试继承 `V2TableTestBase`，并对 `branch` 参数做参数化（`"main"` / `"testBranch"`）。

- 因 `V2TableTestBase` 已迁到 JUnit 5 并锁定 `formatVersion=2`，`TestRowDelta` 的 `@Parameters` 需要把 `formatVersion` 与 `branch` 组合：`Arrays.asList(new Object[]{2, "main"}, new Object[]{2, "testBranch"})`，原 `@Parameterized.Parameters(name = "branch = {0}")` 改为 `@Parameters(name = "formatVersion = {0}, branch = {1}")`；
- `private final String branch` + 构造函数注入 → `@Parameter(index = 1) private String branch`（`index = 1` 因 `formatVersion` 占 index 0）；
- `@Test` → `@TestTemplate`；
- `Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).as("msg").isEqualTo(expected)`；
- `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`。

### `core/src/test/java/org/apache/iceberg/TestV1ToV2RowDeltaDelete.java`

**修改目的**：把 `V1ToV2RowDeltaDelete` 测试迁到 JUnit 5。

**工作逻辑**：从 `extends TableTestBase` 改为 `extends TestBase`，同样的注解替换、断言迁移模式。

### `core/src/test/java/org/apache/iceberg/TestSchemaUpdate.java` 与 `TestSchemaAndMappingUpdate.java`

**修改目的**：把 `SchemaUpdate` 相关测试迁到 JUnit 5，并顺带强化 `Set` 断言语义。

**工作逻辑**：

- `TestSchemaUpdate`：把 `assertThat(newSchema.identifierFieldIds()).as("msg").isEqualTo(Sets.newHashSet(id1, id2))` 改为 `.containsExactly(id1, id2)`——从"Set 相等"改为"按迭代顺序精确相等"。
- `TestSchemaAndMappingUpdate`：同样把 `assertThat(...names()).isEqualTo(Sets.newHashSet("id", "data"))` 改为 `assertThat(...names()).containsExactly("data", "id")`——注意顺序按字母序排列，锁定 `LinkedHashSet` 的迭代顺序。
- 这两个文件不是参数化测试类，主要是断言风格的局部调整，但仍归入本次迁移 PR 统一处理。

## 小结

本提交把 `core` 模块 14 个测试类与 2 个测试基类从 JUnit 4 风格迁移到 JUnit 5 风格，是 Iceberg 测试基础设施现代化的延续。迁移严格遵循 0289 提交确立的 `ParameterizedTestExtension` 范式，把 `@RunWith(Parameterized.class)` / `@Rule TemporaryFolder` / `Assert.*` / `Assume.assumeTrue` 等机制统一替换为 `@ExtendWith` / `@TempDir` / AssertJ 流式断言 / `assumeThat`，并顺带把若干 `Set` 断言从无序相等强化为按迭代顺序精确相等。

**影响范围**：

- 仅影响 `core/src/test/java/org/apache/iceberg/` 下的测试代码与测试基类，不动任何生产代码。
- 测试覆盖范围不变：参数化组合、断言点、生命周期回调均一一对应，未增删测试用例。
- 净行数减少（-242 行：1029 增 / 1271 删），主要因为构造函数注入参数的样板代码被字段注入取代，且 AssertJ 流式断言比 JUnit 4 的 `Assert.assertEquals("msg", expected, actual)` 更紧凑。
- `V2TableTestBase` 改继承 `TestBase` 后，所有继承它的测试类（如 `TestRowDelta`）自动获得 JUnit 5 风格参数化，后续无需重复迁移。
- 个别断言语义从"Set 相等"变为"按顺序精确相等"，这是测试契约的强化——若未来 `names()` / `identifierFieldIds()` 的底层 `Set` 实现改为无序 `HashSet`，这些断言会失败，需相应调整。

**回迁到 1.4.x 的注意事项**：

- 回迁前必须确认 1.4.x 已回迁 0289 提交（PR #9161，引入 `ParameterizedTestExtension` / `@Parameter` / `@Parameters`）。若未回迁，本提交无法独立工作——`@ExtendWith(ParameterizedTestExtension.class)` 会找不到这个扩展类。
- 必须确认 1.4.x 的 `TestBase` 与 `TableTestBase` 已存在且与本提交假设的继承关系一致。本提交让多个类从 `extends TableTestBase` 改为 `extends TestBase`，前提是 `TestBase` 已是 JUnit 5 风格基类（含 `@Parameters` 字段注入支持）。若 1.4.x 的 `TestBase` 仍是 JUnit 4 风格，需先迁移 `TestBase`/`TableTestBase`。
- `V2TableTestBase` 改继承 `TestBase` 是一个跨多个测试类的破坏性改动——所有继承 `V2TableTestBase` 的测试都会受影响。回迁时建议把 `TestBase`、`V2TableTestBase` 与所有继承它们的测试类作为一个整体回迁，避免半迁移状态。
- `TestBase` 新增的 `listManifestLists(File)` 重载与 `TestSequenceNumberForV2Table` 中 `listManifestLists(new File(table.location()))` 的调用点必须一起回迁，否则编译失败。
- AssertJ 的 `containsExactly` 对 `Set` 断言迭代顺序，若 1.4.x 的 `MappedField.names()` 或 `Schema.identifierFieldIds()` 返回的是无序 `HashSet`，回迁后这些断言会失败。回迁前需核对这些方法的返回 `Set` 实现是否保留插入顺序（`LinkedHashSet`）。
- `@TempDir` 是 JUnit 5 原生注解，要求 JUnit Jupiter API 版本 ≥ 5.0；1.4.x 的 JUnit 5 依赖版本需满足。`Files.createTempDirectory(temp, "junit")` 需要 `java.nio.file` 支持，无额外依赖。
- 本提交体量大（15 文件、净 -242 行），回迁时建议按测试类逐个迁移并跑测试验证，避免一次性回迁后大面积失败难以定位。
