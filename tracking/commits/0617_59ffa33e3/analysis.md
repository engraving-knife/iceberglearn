# 提交 0617：Core 模块测试从 JUnit4 迁移到 JUnit5

## 提交信息

- **序号**：0617 / 4088
- **哈希**：59ffa33e3d317ca2a3b2e0c3ea4a787ae19cc010
- **短哈希**：59ffa33e3
- **日期**：2024-03-22 01:50:00 +0900
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Core: Migrate tests to JUnit5 (#10014)
- **PR/Issue**：#10014

## 总体目的

本提交将 Apache Iceberg `core` 模块下 10 个测试类从 JUnit 4 迁移到 JUnit 5（Jupiter），是 Iceberg 项目分阶段把全部测试基础设施过渡到 JUnit5 的连续工作中的一环。其背景动机包括：

1. **统一现代化测试栈**：JUnit 5 模块化更好（`junit-jupiter-api` / `-engine` / `-params`），扩展模型（`Extension`）取代了 JUnit4 的 `Runner` + `Rule`，可组合性强。Iceberg 早在 PR #9161（提交 892e47cd3）就已引入自研的 `ParameterizedTestExtension`、`@Parameter`、`@Parameters` 以及 JUnit5 版本的 `TestBase`，为本次迁移奠定了基础设施。
2. **改善断言可读性**：迁移同步把 JUnit4 的 `Assert.assertEquals(...)` 等老式断言替换为 AssertJ 的流式断言 `assertThat(...).isEqualTo(...)`，错误信息更友好、可链式组合（如 `.extracting(Snapshot::schemaId).containsExactly(...)`）。
3. **统一参数化测试范式**：JUnit4 通过 `@RunWith(Parameterized.class)` + 构造器注入参数；JUnit5 改用 `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameter` 字段注入 + `@TestTemplate` 方法注解，无需为每个参数组合写构造器。

迁移涉及 10 个文件，新增 667 行、删除 910 行，净减少 243 行，主要源于去掉了构造器样板代码以及将多行 `Assert.assertEquals` 折叠为一行 AssertJ 流式调用。

## 如何达成设计目的

迁移采用统一的机械性替换规则，配合 Iceberg 已有的 JUnit5 基础设施完成。核心替换映射如下：

| 旧（JUnit4） | 新（JUnit5 / Iceberg 自研） |
| --- | --- |
| `@RunWith(Parameterized.class)` | `@ExtendWith(ParameterizedTestExtension.class)` |
| `@Parameterized.Parameters` | `@Parameters`（Iceberg 自研注解，`api/src/test/java/org/apache/iceberg/Parameters.java`） |
| `@Parameterized.Parameter` | `@Parameter(index=N)`（Iceberg 自研注解） |
| 构造器注入参数 | `@Parameter` 字段注入 |
| `extends TableTestBase` | `extends TestBase`（JUnit5 版本基类） |
| `@Test`（参数化类中） | `@TestTemplate` |
| `@Test`（非参数化类中） | `@Test`（`org.junit.jupiter.api.Test`） |
| `@Before` / `@After` | `@BeforeEach` / `@AfterEach` |
| `@Rule public TemporaryFolder temp = new TemporaryFolder();` | `@TempDir private Path temp;` |
| `Assert.assertEquals/assertTrue/assertNull/assertNotNull` | `assertThat(...).isEqualTo/isTrue/isNull/isNotNull` |
| `Assertions.assertThatThrownBy(...)` | 静态导入 `assertThatThrownBy(...)` |
| `org.junit.Assume.assumeThat` | `org.assertj.core.api.Assumptions.assumeThat` |
| `temp.newFolder()` + `Assert.assertTrue(delete())` | `Files.createTempDirectory(temp, "junit").toFile()` + `assertThat(delete()).isTrue()` |
| `Lists.transform(snapshots, Snapshot::schemaId)` + `assertEquals(ImmutableList.of(...), ...)` | `assertThat(snapshots).extracting(Snapshot::schemaId).containsExactly(...)` |
| `new Object[] {1, 2}` 返回 `Object[]` | `Arrays.asList(1, 2)` 返回 `List<Object>`，方法签名改为 `protected static List<Object> parameters()` |
| `import org.junit.*` | `import org.junit.jupiter.api.*` |

设计上的关键点：

- **复用已有基础设施**：迁移并未引入新的测试框架，而是依赖 PR #9161 已落地的 `ParameterizedTestExtension`（一个 JUnit5 `TestTemplateInvocationContextProvider`，从 Flink 移植而来），让 Iceberg 在 JUnit5 下依然能像 JUnit4 那样做"类级别参数化"（同一个测试类按参数整体复用一套字段），而不是 JUnit5 原生 `@ParameterizedTest` 的"方法级别参数化"。
- **字段注入而非构造器注入**：JUnit5 的 `TestTemplate` 机制不允许像 JUnit4 那样通过构造器传参，因此用 `@Parameter(index=N)` 标注字段，由 `ParameterizedTestExtension` 在每次调用前通过反射注入对应索引的参数。这消除了"每个参数化类都要写一个带参构造器"的样板代码。
- **`@Parameters` 方法签名变更**：从 `public static Object[]` 改为 `protected static List<Object>`，与 `ParameterizedTestExtension` 的契约一致；返回值用 `Arrays.asList(...)` 构造，对单参数直接列元素，对多参数用 `new Object[] {1, "main"}` 数组作为 list 的一个元素。
- **临时目录机制变更**：JUnit5 的 `@TempDir Path temp` 字段在测试类实例化时由 JUnit 注入一个共享的临时目录路径，因此原 JUnit4 中 `temp.newFolder()` 创建子目录的写法改为 `Files.createTempDirectory(temp, "junit").toFile()`，再对该子目录调用 `delete()` 以腾出位置给 Iceberg 创建表目录。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestReplacePartitions.java`

**修改目的**：把"动态覆盖分区"测试从 JUnit4 参数化迁移到 JUnit5。

**工作逻辑**：

- 类头改为 `@ExtendWith(ParameterizedTestExtension.class) public class TestReplacePartitions extends TestBase`。
- 多参数（`formatVersion` + `branch`）通过 `@Parameter(index = 1) private String branch;` 注入 `branch`，而 `formatVersion` 字段由父类 `TestBase` 通过 `@Parameter(index = 0)` 接收（`TestBase` 中已带该字段）。
- `@Parameters` 方法返回 `Arrays.asList(new Object[] {1, "main"}, new Object[] {1, "testBranch"}, new Object[] {2, "main"}, new Object[] {2, "testBranch"})`，对应 4 种参数组合。
- 所有 `@Test` 改为 `@TestTemplate`；`Assume.assumeThat` 改为 `org.assertj.core.api.Assumptions.assumeThat`；断言全部改为 AssertJ 流式写法。
- `Assertions.assertThatThrownBy` 改为静态导入 `assertThatThrownBy`。

### `core/src/test/java/org/apache/iceberg/TestScanDataFileColumns.java`

**修改目的**：把"扫描数据文件列统计"测试迁移到 JUnit5。

**工作逻辑**：

- 这不是参数化测试，但同样迁移了生命周期注解与临时目录机制。
- `@Rule public final TemporaryFolder temp = new TemporaryFolder();` 改为 `@TempDir private Path temp;`。
- `@Before public void createTables()` 改为 `@BeforeEach public void createTables()`。
- 临时目录创建语句 `File location = temp.newFolder("shared"); Assert.assertTrue(location.delete());` 改为 `File location = Files.createTempDirectory(temp, "junit").toFile(); assertThat(location.delete()).isTrue();`——在共享的 `@TempDir` 下再创建一个子目录并删除，使 Iceberg 可以在其位置创建表元数据目录。
- `Assert.assertNull(...)` → `assertThat(...).isNull()`；`Assert.assertEquals(2, fileTask.file().valueCounts().size())` → `assertThat(fileTask.file().valueCounts()).hasSize(2)`。

### `core/src/test/java/org/apache/iceberg/TestScanSummary.java`

**修改目的**：把"扫描摘要"测试迁移到 JUnit5 参数化。

**工作逻辑**：

- 类头改为 `@ExtendWith(ParameterizedTestExtension.class) public class TestScanSummary extends TestBase`。
- `@Parameters` 方法返回 `Arrays.asList(1, 2)`，对应 formatVersion 1 和 2。
- 删除了 `public TestScanSummary(int formatVersion) { super(formatVersion); }` 构造器。
- 多处 `Assert.assertEquals("msg", expected, actual)` 转为 `assertThat(actual).isEqualTo(expected)`，省去消息参数（AssertJ 失败时自带可视化对比）。
- `Assertions.assertThatThrownBy(...)` 改为静态导入 `assertThatThrownBy`。
- `Lists.newArrayList(table.snapshots()).size()` 简化为 `assertThat(table.snapshots()).hasSize(1)`，不再需要 Guava 包裹。

### `core/src/test/java/org/apache/iceberg/TestScansAndSchemaEvolution.java`

**修改目的**：把"扫描与模式演进"测试迁移到 JUnit5 参数化。

**工作逻辑**：

- 该类不继承 `TableTestBase`/`TestBase`，直接使用 `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameter private int formatVersion;` 字段注入。
- `@Parameter` 默认 `index = 0`，对应 `@Parameters` 返回 `Arrays.asList(1, 2)` 中的第一个（也是唯一一个）参数。
- `@Rule public TemporaryFolder temp = new TemporaryFolder();` 改为 `@TempDir private Path temp;`。
- `@After public void cleanupTables()` 改为 `@AfterEach public void cleanupTables()`。

### `core/src/test/java/org/apache/iceberg/TestSchemaAndMappingUpdate.java`

**修改目的**：把"模式与 NameMapping 更新"测试迁移到 JUnit5 参数化。

**工作逻辑**：

- 类头 `@ExtendWith(ParameterizedTestExtension.class) public class TestSchemaAndMappingUpdate extends TestBase`。
- 多处 `Assert.assertEquals("msg", expected, actual)` 与 `Assert.assertNotNull("msg", obj)` / `Assert.assertNull("msg", obj)` 转为 AssertJ：`assertThat(actual).isEqualTo(expected)`、`assertThat(obj).isNotNull()`、`assertThat(obj).isNull()`。
- 大量类似 `(Integer) table.schema().findField("count").fieldId()` 与 `updated.find("count").id()` 比较的代码，原写法需要手动拆箱成 `Integer`，迁成 `assertThat(updated.find("count").id()).isEqualTo(table.schema().findField("count").fieldId())`——这里 AssertJ 内部会处理基本类型与包装类型的相等比较。

### `core/src/test/java/org/apache/iceberg/TestSchemaID.java`

**修改目的**：把"快照 schemaId 跟踪"测试迁移到 JUnit5 参数化。

**工作逻辑**：

- 迁移基础模式与其它参数化类一致。
- 最有特色的改动：原本用 `Lists.transform(Lists.newArrayList(table.snapshots()), Snapshot::schemaId)` 把 `Iterable<Snapshot>` 转成 schemaId 列表，再用 `Assert.assertEquals(ImmutableList.of(onlyId), ...)` 比对——迁成 AssertJ 流式 `assertThat(table.snapshots()).extracting(Snapshot::schemaId).containsExactly(onlyId, onlyId, onlyId)`，不仅消除 Guava 调用链，还让"期望列表"的长度直接体现为 `containsExactly` 的可变参数个数。
- "current snapshot's schemaId should be old since update schema doesn't create new snapshot" 这种原来作为 `Assert.assertEquals` 第一个 String 参数的失败消息，被改写到 `assertThat(...).as("...").isEqualTo(...)`，利用 AssertJ 的 `.as()` 提供描述。

### `core/src/test/java/org/apache/iceberg/TestSchemaUpdate.java`

**修改目的**：把 `SchemaUpdate` 单元测试迁移到 JUnit5。

**工作逻辑**：

- 这是非参数化测试，仅做注解迁移（`org.junit.Test` → `org.junit.jupiter.api.Test`）与断言风格迁移。
- 去掉了 `org.assertj.core.api.Assertions` 静态引用，改用 `assertThat` / `assertThatThrownBy` 静态导入。
- 大量 `Assert.assertEquals("msg", expected, actual)` 简化为 `assertThat(actual).isEqualTo(expected)`，例如类型转换、rename、delete 等场景。
- 异常断言 `Assertions.assertThatThrownBy(() -> ...)` 改为 `assertThatThrownBy(() -> ...)`，链式 `.isInstanceOf(IllegalArgumentException.class).hasMessage(...)` 保持不变。

### `core/src/test/java/org/apache/iceberg/TestTableUpdatePartitionSpec.java`

**修改目的**：把"分区 spec 更新"测试迁移到 JUnit5 参数化。

**工作逻辑**：类头改为 `@ExtendWith(ParameterizedTestExtension.class) extends TestBase`；`@Test` 改为 `@TestTemplate`；`@Parameters` 返回 `Arrays.asList(1, 2)`；删除带参构造器；断言改 AssertJ。

### `core/src/test/java/org/apache/iceberg/TestTimestampPartitions.java`

**修改目的**：把"时间戳分区"测试迁移到 JUnit5 参数化。

**工作逻辑**：

- 类头改为 `@ExtendWith(ParameterizedTestExtension.class) public class TestTimestampPartitions extends TestBase`。
- `@Parameters` 返回 `Arrays.asList(1, 2)`，删除带参构造器。
- 临时目录改用 `Files.createTempDirectory(temp, "junit").toFile()` + `assertThat(delete()).isTrue()`。
- `Assert.assertEquals(table.currentSnapshot().allManifests(table.io()).size(), 1)` 改为 `assertThat(table.currentSnapshot().allManifests(table.io())).hasSize(1)`。

### `core/src/test/java/org/apache/iceberg/TestUpdatePartitionSpec.java`

**修改目的**：把 `UpdatePartitionSpec` 测试迁移到 JUnit5 参数化。

**工作逻辑**：与其它参数化类相同模式，类头改为 `@ExtendWith(ParameterizedTestExtension.class) extends TestBase`；`@Parameters` 返回 `Arrays.asList(1, 2)`；`@Test` → `@TestTemplate`；删除带参构造器；`Assert.assertEquals("Should match expected spec", expected, updated)` 改为 `assertThat(updated).isEqualTo(expected)`；`Assertions.assertThatThrownBy` 改为静态导入 `assertThatThrownBy`。

## 小结

本提交是测试基础设施现代化迁移的延续，机械性强、风险低。所有改动都局限在 `core/src/test/java/org/apache/iceberg/` 下的 10 个测试类，不触碰生产代码，也不改变任何被测行为的语义。

**影响范围**：

- 仅影响测试编译与测试执行，不影响发布产物。
- 依赖 PR #9161 已落地的 JUnit5 基础设施（`ParameterizedTestExtension`、`@Parameter`、`@Parameters`、`TestBase`），如果回迁到 1.4.x，必须确保这些基础设施类也已存在；1.4.x 上若尚未合入 PR #9161，需要先合入它（或等效改动），否则编译会失败。
- 该 PR 之后还有后续 JUnit5 迁移 PR，本次只是其中一批。

**回迁到 1.4.x 的注意事项**：

1. **依赖前置**：必须先回迁 #9161（`api/src/test/java/org/apache/iceberg/Parameter.java`、`Parameters.java`、`ParameterizedTestExtension.java`）以及 `core/src/test/java/org/apache/iceberg/TestBase.java`（如果 1.4.x 尚未包含）。
2. **行为等价**：迁移是纯结构性重构，参数集（`formatVersion` 1 / 2、`branch` main / testBranch）和断言内容均未改变，回迁不会改变测试覆盖度。
3. **AssertJ 依赖**：Iceberg 测试已统一使用 AssertJ，无需新增依赖。
4. **临时目录机制差异**：JUnit5 的 `@TempDir Path` 与 JUnit4 的 `TemporaryFolder` 行为略有不同（前者是共享目录，需要在其下创建子目录再删除）。回迁时若直接套用模板，需注意 `Files.createTempDirectory(temp, "junit").toFile()` 的写法，避免在共享目录上直接 `delete()` 导致后续测试用例找不到目录。
5. **维护分支意义有限**：1.4.x 是维护分支，主要目标是修 bug 与兼容性补丁，做大规模测试框架迁移收益有限，但若 1.4.x 上有需要在本批测试类上加新用例，迁到 JUnit5 后写法更现代、可读性更好，可考虑回迁。
