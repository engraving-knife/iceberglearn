# 提交 0613：Core: Migrate tests to JUnit5

## 提交信息

- **序号**：0613 / 4088
- **哈希**：fae0f8140b4f0b1dee20679f98e8be7e9b93a373
- **短哈希**：fae0f8140
- **日期**：2024-03-20（Wed Mar 20 16:06:38 2024 +0900）
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Core: Migrate tests to JUnit5 (#9999)
- **PR/Issue**：#9999

## 总体目的

本提交是 Iceberg `core` 模块从 JUnit4 迁移到 JUnit5 的持续推进工作中的一步，目标是将 9 个仍基于 JUnit4 的测试类迁移到 JUnit5 平台，并顺手把 JUnit4 的 `Assert.*` 断言替换为更现代的 AssertJ 流式断言。

这是 Iceberg 一项分阶段进行的大型技术债清理工作的一部分：项目此前已建立了 JUnit5 的测试基础设施——`api` 模块提供了自定义的 `ParameterizedTestExtension`（移植自 Apache Flink，用于在 JUnit5 中实现类级参数化测试，替代 JUnit4 的 `@RunWith(Parameterized.class)`），`core` 模块也提供了 `TestBase`（作为旧 `TableTestBase` 的 JUnit5 对应物）。但仍有大量测试类停留在 JUnit4 上。本提交针对一批围绕"事务（Transaction）"与"分区规格（PartitionSpec）"的测试类完成迁移，让它们统一到 JUnit5 平台。

迁移的深层动机包括：
1. JUnit4 已停止活跃演进，JUnit5 是社区主流且持续维护的测试框架；
2. JUnit5 的扩展模型（`@ExtendWith`）比 JUnit4 的 Runner 机制更灵活，可以组合多个扩展；
3. 统一到 JUnit5 后可以享受更丰富的生命周期注解（`@BeforeEach`/`@AfterEach`/`@BeforeAll`）、参数注入、嵌套测试等能力；
4. 把断言统一到 AssertJ 后，失败信息更丰富、可读性更好，且与项目其他已迁移模块保持一致。

## 如何达成设计目的

作者采用"机械但成体系"的迁移模式，对每个文件套用相同的转换规则。以下是迁移中应用的核心转换模式：

### 1. 参数化测试机制迁移

JUnit4 用 `@RunWith(Parameterized.class)` + 构造器参数 + `@Parameterized.Parameters` 实现；JUnit5 改用自定义扩展：

| JUnit4 | JUnit5（Iceberg 约定） |
|---|---|
| `@RunWith(Parameterized.class)` | `@ExtendWith(ParameterizedTestExtension.class)` |
| `@Parameterized.Parameters(name="...")` 注解的 `public static Object[] parameters()` | `@Parameters(name="...")` 注解的 `protected static List<Object> parameters()` |
| 构造器 `public TestXxx(int formatVersion)` 注入参数 | `@Parameter private int formatVersion;` 字段注入 |
| `@Test`（在参数化类中每个参数跑一次） | `@TestTemplate`（JUnit5 模板测试，配合扩展按参数多次调用） |
| `extends TableTestBase` | `extends TestBase`（JUnit5 版基类，已用 `@ExtendWith(ParameterizedTestExtension.class)` 装饰） |

`ParameterizedTestExtension` 实现 `TestTemplateInvocationContextProvider`：它通过反射查找类上被 `@Parameters` 注解的静态方法，调用拿到参数集合，再为每组参数创建一个 `TestTemplateInvocationContext`，并通过 `ParameterResolver` 把 `@Parameter` 字段填充为对应参数值。这样每个被 `@TestTemplate` 注解的方法都会按参数集合执行多次。

### 2. 生命周期与临时目录迁移

- `@Before` → `@BeforeEach`
- `@After` → `@AfterEach`
- `@Rule public TemporaryFolder temp = new TemporaryFolder();` → `@TempDir private Path temp;`
- `temp.newFolder()` → `Files.createTempDirectory(temp, "junit").toFile()`（JUnit5 的 `@TempDir` 注入的是 `Path`，需要手动创建子目录后再转 `File`，以保持与原代码 `File tableDir` 字段类型兼容）

### 3. 断言从 JUnit4 Assert 迁移到 AssertJ

- `Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`
- `Assert.assertEquals(msg, expected, actual)` → `assertThat(actual).as(msg).isEqualTo(expected)` 或直接 `assertThat(actual).isEqualTo(expected)`（丢弃自定义消息，依赖 AssertJ 默认输出）
- `Assert.assertTrue(x)` → `assertThat(x).isTrue()`
- `Assert.assertNull(x)` → `assertThat(x).isNull()`
- `Assert.assertSame(expected, actual)` → `assertThat(actual).isSameAs(expected)`
- `Assert.assertEquals("msg", n, collection.size())` → `assertThat(collection).hasSize(n)`（更语义化）
- 复杂断言合并：`Assert.assertEquals(ImmutableMap.of(k,v), map)` + `Assert.assertNull(map.get(MAX))` → `assertThat(map).containsExactly(entry(k,v)).doesNotContainKey(MAX)`（一次断言覆盖正反两面）
- 异常断言：`assertThatThrownBy(...)` 替代 `try/catch + Assert.fail()`

### 4. 净代码量减少

由于 AssertJ 流式断言比 JUnit4 的 `Assert.assertEquals(msg, expected, actual)` 更紧凑，且很多自定义失败消息被丢弃（依赖 AssertJ 默认的 actual/expected 输出），整个提交净减少 243 行（594 增 / 837 删），代码更简洁。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestCommitReporting.java`

**修改目的**：把 commit reporting（提交指标上报）测试从 JUnit4 迁移到 JUnit5。

**工作逻辑**：

原类 `extends TableTestBase` 并通过 `super(2)` 构造器硬编码 formatVersion=2（即只测 v2 表格式）。迁移后改为 `extends TestBase`，并显式声明参数化：

```java
@ExtendWith(ParameterizedTestExtension.class)
public class TestCommitReporting extends TestBase {
  @Parameters(name = "formatVersion = {0}")
  protected static List<Object> parameters() {
    return Arrays.asList(2);  // 仍只测 v2，但走参数化通道，便于未来扩展
  }
```

3 个测试方法 `addAndDeleteDataFiles`、`addAndDeleteDeleteFiles`、`addAndDeleteManifests` 的注解从 `@Test` 改为 `@TestTemplate`（因为类被参数化扩展装饰）。断言未改动（原本已用 AssertJ）。

### `core/src/test/java/org/apache/iceberg/TestPartitionSpecParser.java`

**修改目的**：迁移 PartitionSpecParser 序列化/反序列化测试到 JUnit5，并把 JUnit4 `Assert` 替换为 AssertJ。

**工作逻辑**：

原 `extends TableTestBase` + `super(1)`，迁移后 `extends TestBase` + `parameters() = Arrays.asList(1)`。注意此处的 `@Test` 注解保留不变（非 `@TestTemplate`）——因为虽然类被 `@ExtendWith(ParameterizedTestExtension.class)` 装饰，但 `ParameterizedTestExtension` 只对 `@TestTemplate` 方法按参数多次执行；对 `@Test` 方法仍只执行一次。此处测试方法本身不依赖 formatVersion 参数差异，故用 `@Test` 即可。

断言迁移示例：
```java
// 改动前
Assert.assertEquals(expected, PartitionSpecParser.toJson(table.spec(), true));
Assert.assertEquals(2, spec.fields().size());
Assert.assertEquals("To/from JSON should produce equal partition spec", spec, roundTripJSON(spec));
// 改动后
assertThat(PartitionSpecParser.toJson(table.spec(), true)).isEqualTo(expected);
assertThat(spec.fields()).hasSize(2);
assertThat(roundTripJSON(spec)).isEqualTo(spec);  // 自定义消息被丢弃
```

### `core/src/test/java/org/apache/iceberg/TestPartitionSpecInfo.java`

**修改目的**：迁移分区规格信息测试到 JUnit5，含 `@Rule`/`@Before`/`@After`/构造器注入的完整转换。

**工作逻辑**：

此文件覆盖了迁移的所有典型场景：

1. **临时目录**：`@Rule public TemporaryFolder temp` → `@TempDir private Path temp`，并在 `setupTableDir()` 中把 `temp.newFolder()` 改为 `Files.createTempDirectory(temp, "junit").toFile()`。
2. **参数注入**：原 `private final int formatVersion;` + 构造器 → `@Parameter private int formatVersion;`。
3. **复合断言合并**：例如
   ```java
   // 改动前：先断言相等，再单独断言 get(MAX_VALUE) 为 null
   Assert.assertEquals(spec, table.spec());
   Assert.assertEquals(ImmutableMap.of(spec.specId(), spec), table.specs());
   Assert.assertNull(table.specs().get(Integer.MAX_VALUE));
   // 改动后：一次断言同时校验"包含且仅包含指定条目"与"不含 MAX_VALUE 键"
   assertThat(table.specs())
       .containsExactly(entry(spec.specId(), spec))
       .doesNotContainKey(Integer.MAX_VALUE);
   ```
   `containsExactly` 既能验证期望条目存在，又能验证不存在其他条目，比原来 `assertEquals(map, actual)` 更严格（会拒绝多余条目）；`.doesNotContainKey(MAX_VALUE)` 则显式覆盖原 `assertNull` 检查。
4. **Schema 比较方式变化**：`Assert.assertTrue("Schema must have only \"data\" column", table.schema().sameSchema(expectedSchema))` 改为 `assertThat(table.schema().asStruct()).isEqualTo(expectedSchema.asStruct())`，从语义化布尔断言改为结构相等比较。

### `core/src/test/java/org/apache/iceberg/TestPartitioning.java`

**修改目的**：迁移分区演进测试到 JUnit5，引入 `assertThatThrownBy` 异常断言。

**工作逻辑**：

与 `TestPartitionSpecInfo` 类似的 `@Rule → @TempDir`、`@Before/@After → @BeforeEach/@AfterEach` 转换。值得注意的是把 JUnit4 风格的字符串消息迁移掉了：
```java
Assert.assertEquals("Should have 2 specs", 2, table.specs().size());
→ assertThat(table.specs()).hasSize(2);
Assert.assertEquals("Types must match", expectedType, actualType);
→ assertThat(actualType).isEqualTo(expectedType);
```
原本带描述前缀的消息（"Should have 2 specs"）在迁移后丢失——这是一种取舍：作者优先选择 AssertJ 的简洁写法，相信 `hasSize(2)` 失败时的默认输出（"expected size:2 but was:3"）已足够诊断。此文件还引入 `assertThatThrownBy` 来替代 `try/catch + Assert.fail()` 模式处理 `ValidationException`。

### `core/src/test/java/org/apache/iceberg/TestSetPartitionStatistics.java`

**修改目的**：迁移分区统计文件设置测试到 JUnit5。

**工作逻辑**：

展示了带双 formatVersion 参数的迁移（`Arrays.asList(1, 2)`），即同一测试在 v1 与 v2 表格式下各跑一次。关键转换：
- `Assert.assertSame("msg", base, table.ops().current())` → `assertThat(table.ops().current()).isSameAs(base)`（`isSameAs` 校验引用相等，对应 `==`）。
- 多个 `Assert.assertEquals("msg", ImmutableList.of(file), metadata.partitionStatisticsFiles())` 合并为 `assertThat(metadata.partitionStatisticsFiles()).containsExactly(partitionStatisticsFile)`，去掉了对 `ImmutableList.of` 的中间包装。
- 私有辅助方法 `assertTableMetadataVersion` 也一并迁移：`Assert.assertEquals(String.format("Table should be on version %s", expected), expected, (int) version())` → `assertThat(version()).isEqualTo(expected)`。

### `core/src/test/java/org/apache/iceberg/TestSetStatistics.java`

**修改目的**：迁移表统计设置测试到 JUnit5，模式与 `TestSetPartitionStatistics` 一致。

**工作逻辑**：同样 `extends TableTestBase` → `extends TestBase`，参数化 `[1, 2]`，`@Test` → `@TestTemplate`，`Assert.assertEquals` → `assertThat().isEqualTo`，`Assert.assertSame` → `assertThat().isSameAs`，`Assert.assertTrue` → `assertThat().isTrue()`。

### `core/src/test/java/org/apache/iceberg/TestCreateTransaction.java`（330 行改动）

**修改目的**：迁移"创建事务"测试到 JUnit5。这是改动量第二大的文件。

**工作逻辑**：

`TestCreateTransaction` 测试 `ReplaceFiles`、`AppendFiles` 等事务操作在不同 formatVersion 下的行为。迁移要点：
1. 基类与参数化转换同前述文件。
2. 大量 `Assert.assertEquals` / `Assert.assertTrue` / `Assert.assertNull` 批量替换为 AssertJ，由于原文件断言密集，删减幅度大。
3. 部分断言合并：例如对 manifest 列表的断言从 `Assert.assertEquals("msg", expectedList, actualList)` 改为 `assertThat(actualList).containsExactlyElementsOf(expectedList)` 或 `hasSameSizeAs` 等 AssertJ 集合断言。

### `core/src/test/java/org/apache/iceberg/TestReplaceTransaction.java`（255 行改动）

**修改目的**：迁移"替换事务"测试到 JUnit5。

**工作逻辑**：与 `TestCreateTransaction` 同类，测试 `ReplacePartitions`、`OverwriteByFilter` 等事务操作。同样执行基类切换、参数化注解替换、`@Test→@TestTemplate`、Assert→AssertJ 的批量转换。

### `core/src/test/java/org/apache/iceberg/TestTransaction.java`（462 行改动，最大）

**修改目的**：迁移事务核心测试到 JUnit5，是本提交改动量最大的文件。

**工作逻辑**：

`TestTransaction` 是事务功能的主测试类，覆盖 `Transaction` 接口的 commit/append/overwrite/delete 等核心路径。迁移后：
- `extends TableTestBase` → `extends TestBase`，保留 `parameters() = Arrays.asList(1, 2)` 双参数化。
- 几十个测试方法的 `@Test` → `@TestTemplate`。
- 数百处 `Assert.*` 调用替换为 AssertJ 流式断言，是本文件净删减行数的主要来源。
- 部分 `Assert.fail("...")` 配合 try/catch 的异常测试改为 `assertThatThrownBy(() -> ...).isInstanceOf(XxxException.class).hasMessageContaining("...")`，更声明式。

## 小结

本提交将 `core` 模块下 9 个测试类（围绕事务与分区规格）从 JUnit4 迁移到 JUnit5，并把所有 JUnit4 `Assert.*` 断言替换为 AssertJ 流式断言。整体净减少 243 行（594 增 / 837 删），代码更紧凑、失败诊断信息更丰富。

- **影响范围**：仅 `core` 模块测试代码，不触及生产代码；测试覆盖行为不变（仍是同样的事务与分区功能在 v1/v2 表格式下各跑一次）。
- **关键基础设施**：依赖此前已引入的 `api` 模块 `ParameterizedTestExtension`（自定义 JUnit5 扩展，移植自 Flink）与 `core` 模块 `TestBase`（JUnit5 版表测试基类）。本提交只是消费这些基础设施，未引入新的扩展机制。
- **迁移模式可复用**：本提交确立的转换规则（`@RunWith→@ExtendWith`、构造器注入→`@Parameter` 字段注入、`@Test→@TestTemplate`、`@Rule→@TempDir`、`Assert→assertThat`）是后续其余 JUnit4 测试类迁移的模板。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支当前仍是 JUnit4（`TableTestBase` 仍存在且被广泛继承），且工作区这些测试文件还停留在 JUnit4。直接 cherry-pick 本提交会失败，因为依赖的 `TestBase`、`ParameterizedTestExtension`、`@Parameters`/`@Parameter` 注解等基础设施可能尚未回迁到 1.4.x。若要回迁本提交，必须先回迁整条 JUnit5 基础设施链（`ParameterizedTestExtension`、`TestBase` 及相关注解），再批量迁移测试类。鉴于 1.4.x 已是维护分支，回迁整套 JUnit5 迁移成本高、收益低，建议评估是否值得；若仅需要某个 bug 修复（而非测试框架升级），应优先回迁对应的功能性提交而非本测试基础设施迁移。
