# 提交 0615：Core: Migrate tests to JUnit5

## 提交信息

- **序号**：0615 / 4088
- **哈希**：aa17c0a3090a0843fd7e3111a5285d6e23d24dcd
- **短哈希**：aa17c0a30
- **日期**：2024-03-20（Wed Mar 20 16:30:19 2024 +0100）
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Core: Migrate tests to JUnit5 (#9994)
- **PR/Issue**：#9994

## 总体目的

本提交是 Iceberg `core` 模块从 JUnit4 迁移到 JUnit5 的持续推进工作中的一步（与 0613 号提交同属一个系列、同一作者），目标是将 6 个仍基于 JUnit4 的测试类迁移到 JUnit5 平台，并把 JUnit4 的 `Assert.*` / `Assume.*` 替换为更现代的 AssertJ 流式断言与假设 API。

被迁移的 6 个测试类都围绕"文件级写操作"展开：
- `TestDeleteFiles`——删除数据文件；
- `TestFastAppend`——快速追加；
- `TestMergeAppend`——合并追加；
- `TestOverwrite`——覆盖写（含动态分区覆盖）；
- `TestOverwriteWithValidation`——覆盖写校验；
- `TestRewriteFiles`——重写数据/删除文件。

这些是 Iceberg 表写入路径最核心、最高频使用的 API，测试用例量大（单文件常上千行），因此本提交在 0613 的基础上把迁移工作推进到写入链路，是 Iceberg 测试基础设施现代化的关键一步。

本提交与 0613 的根本动机一致：JUnit4 已停止活跃演进，JUnit5 的扩展模型更灵活、生命周期注解更丰富，且统一到 JUnit5 + AssertJ 后失败诊断信息更友好。两者都是同一个分阶段迁移计划中的不同批次。

## 如何达成设计目的

作者沿用 0613 确立的迁移模式（同一作者，规则一致），但对本批次的两个特性做了更深入的运用：

### 1. 多参数参数化（双参数：formatVersion + branch）

本批次 5 个文件（除 `TestFastAppend` 外）都使用**双参数**参数化：`(formatVersion, branch)`，参数组合为 `{1,"main"}`、`{1,"testBranch"}`、`{2,"main"}`、`{2,"testBranch"}` 四组。这意味着每个测试方法会在两种表格式版本 × 两种分支上下文下各跑一次，共 4 次。

实现上利用了 `ParameterizedTestExtension` 的字段注入机制，并通过**继承**复用父类的参数字段：

```java
// 父类 TestBase（JUnit5 基类）已有：
@Parameters(name = "formatVersion = {0}")
protected static List<Object> parameters() { ... }
@Parameter protected int formatVersion;  // 默认 index=0

// 子类 TestMergeAppend 重写 parameters() 提供双参数，并新增 index=1 的字段：
@ExtendWith(ParameterizedTestExtension.class)
public class TestMergeAppend extends TestBase {
  @Parameter(index = 1)        // 显式指定接收第 2 个参数
  private String branch;

  @Parameters(name = "formatVersion = {0}, branch = {1}")
  protected static List<Object> parameters() {
    return Arrays.asList(
        new Object[] {1, "main"},
        new Object[] {1, "testBranch"},
        new Object[] {2, "main"},
        new Object[] {2, "testBranch"});
  }
  // formatVersion 字段从 TestBase 继承，由扩展自动注入 index=0
```

`ParameterizedTestExtension` 通过 `AnnotationSupport.findAnnotatedMethods(..., HierarchyTraversalMode.TOP_DOWN)` 自顶向下查找 `@Parameters` 方法，子类重写的 `parameters()` 会优先于父类被调用；而 `@Parameter` 字段注入通过反射在类层次中查找匹配 index 的字段——`formatVersion`（index 0）在 `TestBase` 中，`branch`（index 1）在子类中。这种"父类持公共参数、子类扩展额外参数"的设计让多参数参数化可以优雅复用。

`branch` 参数用于测试 Iceberg 的分支写能力：`"main"` 走主分支，`"testBranch"` 走自定义分支，验证写操作在分支上下文下的正确性（如 `commit(table, append, branch)` 把变更提交到指定分支而非 main）。

### 2. JUnit4 `Assume` 迁移到 AssertJ `assumeThat`

本批次多个文件涉及 v2-only 功能（如 delete files 重写、sequence number），需要用假设跳过 v1 场景。JUnit4 写法：

```java
Assume.assumeTrue("Rewriting delete files is only supported in iceberg format v2. ", formatVersion > 1);
```

迁移到 AssertJ 的假设 API（`org.assertj.core.api.Assumptions.assumeThat`）：

```java
assumeThat(formatVersion)
    .as("Rewriting delete files is only supported in iceberg format v2 or later")
    .isGreaterThan(1);
```

- `assumeThat` 返回一个断言对象，调用 `isGreaterThan(1)`；当条件不满足时，AssertJ 会抛出 JUnit5 的 `TestAbortedException`，使测试被跳过（skipped）而非失败。
- `.as(msg)` 为假设附加描述，跳过时会在报告里显示原因，比 JUnit4 `Assume.assumeTrue(msg, cond)` 的纯布尔判断更语义化。
- 这种写法与 0612 号提交"用 `.as()` 而非 `.withFailMessage()`"的理念一脉相承——优先用流式、保留上下文的 API。

### 3. 其余迁移规则（与 0613 一致）

- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`
- `@Parameterized.Parameters` → `@Parameters`，返回类型 `Object[]` → `List<Object>`
- 构造器参数注入 → `@Parameter` 字段注入，构造器删除
- `extends TableTestBase` → `extends TestBase`
- `@Test` → `@TestTemplate`（参数化类中）
- `Assert.assertEquals/assertTrue/assertNull/assertSame` → `assertThat(...).isEqualTo/isTrue/isNull/isSameAs`
- `Assertions.assertThatThrownBy(...)`（限定调用）→ `assertThatThrownBy(...)`（静态导入）
- `Assert.assertEquals(msg, expected, actual)` 中的自定义消息大多被丢弃，依赖 AssertJ 默认的 actual/expected 输出；少数通过 `.as(msg)` 保留
- 集合断言合并：`Assert.assertEquals(msg, ImmutableList.of(a,b), list)` → `assertThat(list).containsExactly(a, b)`

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestDeleteFiles.java`（115 行改动）

**修改目的**：迁移删除文件测试到 JUnit5，含双参数参数化与假设迁移。

**工作逻辑**：

原 `extends TableTestBase` + 构造器 `(int formatVersion, String branch)`，迁移后 `extends TestBase` + `@Parameter(index=1) String branch` + 重写 `parameters()` 返回 4 组双参数组合。测试方法 `@Test → @TestTemplate`。

值得注意的转换：
- `Assume.assumeTrue(formatVersion == 2)` → `assumeThat(formatVersion).isEqualTo(2)`（`testCannotDeleteFileWhereNotAllRowsMatchPartitionFilter` 中跳过 v1）。
- `Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`（静态导入化），用于验证 `deleteFromRowFilter` 触发 `ValidationException`。
- 分区碰撞测试 `testDeleteWithCollision`：`Assert.assertEquals(msg, ImmutableList.of(partitionOne, partitionTwo), beforeDeletePartitions)` → `assertThat(beforeDeletePartitions).containsExactly(partitionOne, partitionTwo)`，去掉中间 `ImmutableList.of` 包装与消息。

### `core/src/test/java/org/apache/iceberg/TestFastAppend.java`（307 行改动）

**修改目的**：迁移快速追加测试到 JUnit5。本批唯一只使用单参数（formatVersion）的文件。

**工作逻辑**：

`extends TableTestBase` + 构造器 `(int formatVersion)` → `extends TestBase` + `parameters() = Arrays.asList(1, 2)`（单参数，不引入 branch）。`@Test → @TestTemplate`。大量 `Assert.assertEquals/assertTrue/assertNull` 批量替换为 AssertJ，例如：
- `Assert.assertEquals("Table should start empty", 0, listManifestFiles().size())` → `assertThat(listManifestFiles()).isEmpty()`
- `Assert.assertNull("Should not have a current snapshot", base.currentSnapshot())` → `assertThat(base.currentSnapshot()).isNull()`
- `Assert.assertEquals("Last sequence number should be 0", 0, base.lastSequenceNumber())` → `assertThat(base.lastSequenceNumber()).isEqualTo(0)`

异常测试用 `assertThatThrownBy(append::commit).isInstanceOf(...).hasMessageContaining(...)` 替代 try/catch + `Assert.fail()`。

### `core/src/test/java/org/apache/iceberg/TestMergeAppend.java`（561 行改动，本提交最大）

**修改目的**：迁移合并追加测试到 JUnit5，双参数参数化。

**工作逻辑**：

`TestMergeAppend` 是本提交改动量最大的文件，覆盖 merge append 在不同 formatVersion 与 branch 组合下的行为。迁移要点：
- 双参数参数化（formatVersion + branch），用 `@Parameter(index=1)` 注入 branch。
- 数十个测试方法 `@Test → @TestTemplate`。
- 大量 manifest 与 snapshot 断言迁移：`Assert.assertEquals("msg", expected, actual)` → `assertThat(actual).isEqualTo(expected)`，`hasSize`、`containsExactly`、`isEmpty` 等集合断言广泛使用。
- `Assertions.assertThatThrownBy` → `assertThatThrownBy` 静态导入化，用于验证 `CommitFailedException` 等异常。

### `core/src/test/java/org/apache/iceberg/TestOverwrite.java`（104 行改动）

**修改目的**：迁移覆盖写测试到 JUnit5，双参数参数化。

**工作逻辑**：

`extends TestTestBase` + 构造器双参数 → `extends TestBase` + `@Parameter(index=1) String branch`。`@Test → @TestTemplate`。Assert→AssertJ 批量替换，模式与 `TestMergeAppend` 一致。

### `core/src/test/java/org/apache/iceberg/TestOverwriteWithValidation.java`（226 行改动）

**修改目的**：迁移覆盖写校验测试到 JUnit5，双参数参数化。

**工作逻辑**：

测试覆盖写操作的校验逻辑（如校验数据完整性、分区匹配等）。同样使用双参数（formatVersion + branch），`@Test → @TestTemplate`，Assert→AssertJ。十几个测试方法完成迁移。

### `core/src/test/java/org/apache/iceberg/TestRewriteFiles.java`（304 行改动）

**修改目的**：迁移文件重写测试到 JUnit5，双参数参数化，含密集的 `Assume → assumeThat` 迁移。

**工作逻辑**：

`TestRewriteFiles` 测试数据文件与删除文件的重写（rewrite）操作。由于"重写 delete files"与"sequence number"都是 v2-only 特性，本文件有 4 处 `Assume.assumeTrue` 需要迁移：

```java
// 改动前
Assume.assumeTrue("Rewriting delete files is only supported in iceberg format v2. ", formatVersion > 1);
// 改动后
assumeThat(formatVersion)
    .as("Rewriting delete files is only supported in iceberg format v2 or later")
    .isGreaterThan(1);
```

注意迁移时顺手把消息中的 "v2." 修正为 "v2 or later"（因为 `isGreaterThan(1)` 语义上是"v2 或更高"，比原来的 `> 1` 表述更准确）。双参数参数化（formatVersion + branch），`@Test → @TestTemplate`，其余 Assert→AssertJ 转换同前。

## 小结

本提交将 `core` 模块下 6 个围绕文件写操作（删除/追加/覆盖/重写）的测试类从 JUnit4 迁移到 JUnit5，并把 `Assert.*` / `Assume.*` 替换为 AssertJ 流式断言与假设 API。整体净减少 267 行（675 增 / 942 删）。

- **影响范围**：仅 `core` 模块测试代码，不触及生产代码；测试覆盖行为不变（同样的写操作在 v1/v2 × main/testBranch 组合下各跑一次）。
- **与 0613 的关系**：同系列、同作者、同迁移模式。0613 迁移事务与分区规格测试，本提交迁移文件写操作测试。两者共同把 Iceberg 写入链路的核心测试统一到 JUnit5 平台。
- **本批次的两个亮点**：
  1. **多参数参数化**——通过 `@Parameter(index = N)` 与父类 `TestBase` 的 `formatVersion`（index 0）字段继承，实现 `(formatVersion, branch)` 双参数注入，验证分支写能力，是比 0613 单参数更复杂的运用。
  2. **`Assume → assumeThat` 迁移**——把 JUnit4 假设替换为 AssertJ 假设 API，跳过 v1 场景时仍保留描述信息，与 0612 "用 `.as()` 保留上下文"的理念一致。
- **回迁到 1.4.x 的注意事项**：与 0613 完全相同——1.4.x 仍是 JUnit4，直接 cherry-pick 会失败，因为依赖 `TestBase`、`ParameterizedTestExtension`、`@Parameters`/`@Parameter` 等 JUnit5 基础设施尚未回迁。若要回迁必须先回迁整套 JUnit5 基础设施链。鉴于 1.4.x 已是维护分支，且本提交纯属测试框架迁移（无功能变更），建议不回迁；如需 1.4.x 的某个写入路径 bug 修复，应优先回迁对应的功能性提交而非测试基础设施迁移。
