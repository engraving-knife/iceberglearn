# 提交 0566：Core: Migrate tests to JUnit5 (#9849)

## 提交信息

- **序号**：0566 / 4088
- **哈希**：ad9e4e810043260965b8a55623afb30fffa04391
- **短哈希**：ad9e4e810
- **日期**：2024-03-07 17:26:54 +0900
- **作者**：Tom Tanaka
- **提交说明**：Core: Migrate tests to JUnit5 (#9849)
- **PR/Issue**：#9849

## 总体目的

本提交将 Iceberg core 模块中 7 个与"元数据表扫描（metadata table scan）"相关的测试类从 JUnit 4 迁移到 JUnit 5。这是 Iceberg 仓库整体推进 JUnit 5 迁移工作的一个增量步骤——即把测试基础设施从 JUnit 4 的 `@RunWith(Parameterized.class)` + 构造器注入 `formatVersion` 的模式，逐步替换为仓库自研的 JUnit 5 扩展 `ParameterizedTestExtension` + `@Parameter` 字段注入模式，并把断言从 JUnit 4 的 `org.junit.Assert.*` 切换到 AssertJ 流式 API `assertThat(...)`。

被迁移的 7 个测试类均围绕"元数据表扫描"展开：`MetadataTableScanTestBase`（基类）、`TestBatchScans`、`TestEntriesMetadataTable`、`TestFindFiles`、`TestIncrementalDataTableScan`、`TestMetadataTableScans`、`TestMetadataTableScansWithPartitionEvolution`。它们都通过 `formatVersion`（取值 1 或 2）参数化，以同一套测试逻辑分别覆盖表格式 v1 和 v2。

迁移的动机是统一仓库测试栈：JUnit 4 的 `@RunWith(Parameterized.class)` 强制每个参数化类必须有带参数的构造器，与 JUnit 5 的字段注入（`@Parameter`）和生命周期注解（`@BeforeEach` 替代 `@Before`）模型不兼容；同时仓库已经基于 Flink 项目实现移植了 `ParameterizedTestExtension` 与 `@Parameters` 注解（位于 `api/src/test/java/org/apache/iceberg/`），并提供 JUnit 5 版本的 `TestBase` 作为 `TableTestBase` 的对应物，因此本次迁移只是"消费"已就绪的 JUnit 5 基础设施，把这一批测试类切换过去，从而减少仓库内 JUnit 4/5 混用的不一致性，并为后续把更多测试迁到 JUnit 5 铺平道路。

## 如何达成设计目的

整体设计思路是"机械等价迁移"：保持每个测试方法的断言语义不变，仅替换测试框架与断言库的 API 调用形式。具体替换规则如下：

1. **类级参数化机制**：`@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。后者是仓库自研的 JUnit 5 `TestTemplateInvocationContextProvider` 实现，会扫描被 `@Parameters` 标注的静态方法，把其返回值作为参数集合，对每个参数值生成一次测试调用上下文。
2. **参数提供方法**：`@Parameterized.Parameters(name = "...")` → `@Parameters(name = "...")`（仓库自定义注解，定义在 `api/src/test/java/org/apache/iceberg/Parameters.java`）。返回类型由 `Object[]` 改为 `List<Object>`（`Arrays.asList(1, 2)`），可见性由 `public` 收紧为 `protected`。
3. **formatVersion 注入**：原 JUnit 4 通过构造器 `public XxxTest(int formatVersion) { super(formatVersion); }` 注入；JUnit 5 改为父类 `TestBase` 暴露一个 `@Parameter protected int formatVersion;` 字段，由 `ParameterizedTestExtension` 通过反射注入，因此子类不再需要构造器，直接删除。
4. **基类切换**：`extends TableTestBase` → `extends TestBase`。`TestBase` 是 `TableTestBase` 的 JUnit 5 对应物，自身已声明 `@ExtendWith(ParameterizedTestExtension.class)` 与 `@Parameters` 方法，子类因此无需重复声明参数方法（但仍可覆盖）。注意子类若自行声明 `@Parameters`，则父类不再需要——本提交中 6 个直接子类都自带 `@Parameters`，而 `TestMetadataTableScans`、`TestMetadataTableScansWithPartitionEvolution` 继承 `MetadataTableScanTestBase`，后者自带 `@Parameters`，子类无需重复声明。
5. **测试方法注解**：`@Test` → `@TestTemplate`。在 `ParameterizedTestExtension` 下，所有测试方法必须使用 `@TestTemplate`（而非 `@Test`），否则不会被参数化多次调用。
6. **生命周期注解**：`@Before` → `@BeforeEach`。
7. **断言库**：`org.junit.Assert.*`（`assertEquals`、`assertTrue`、`assertNotEquals`）以及 `org.junit.Assume.assumeTrue` → AssertJ 的 `assertThat(...)`、`assumeThat(...)`。消息字符串由第一个参数移到 `.as(...)` 链式调用。
8. **Guava 工具替换**：部分对 `Iterables.size(xxx)` 的调用被改为 AssertJ 的 `assertThat(xxx).hasSize(n)` 或 `hasSizeGreaterThan(0)`；`Iterables.getOnlyElement(...)` 在能直接用 `.get(0)` 时被替换。
9. **临时目录**：`TestMetadataTableScansWithPartitionEvolution` 中 `temp.newFolder()`（JUnit 4 的 `TemporaryFolder`）改为 `Files.createTempDirectory(temp, "junit").toFile()`，因为 JUnit 5 的 `@TempDir` 注入的是 `Path`，不再提供 `newFolder` 方法。

下文逐文件说明改动详情。

## 修改详情

### `core/src/test/java/org/apache/iceberg/MetadataTableScanTestBase.java`

**修改目的**：把元数据表扫描测试的公共基类迁到 JUnit 5，使两个子类（`TestMetadataTableScans`、`TestMetadataTableScansWithPartitionEvolution`）能直接继承 JUnit 5 形态。

**工作逻辑**：
- 删除 `@RunWith(Parameterized.class)`、构造器、`@Parameterized.Parameters` 的 `Object[]` 形式；改为 `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameters` 返回 `Arrays.asList(1, 2)`，可见性 `protected`。
- 父类由 `TableTestBase` 改为 `TestBase`。
- `validateTaskScanResiduals` 中 `Assert.assertTrue("Tasks should not be empty", Iterables.size(tasks) > 0)` 改为 `assertThat(tasks).as("Tasks should not be empty").hasSizeGreaterThan(0)`；残差断言由 `Assert.assertEquals/Assert.assertNotEquals` 改为 `assertThat(...).isEqualTo/.isNotEqualTo`，并把消息挪到 `.as(...)`。
- `assertManifestFilePartition` 方法（验证 manifest 条目的分区值）改为 AssertJ 的 `.anyMatch(...)` 流式断言，移除 `StreamSupport.stream(...).anyMatch(...)` 外层包装与 `Assert.assertTrue`。
- 移除 `Iterables` 的 import。

### `core/src/test/java/org/apache/iceberg/TestBatchScans.java`

**修改目的**：把批量扫描测试迁到 JUnit 5。

**工作逻辑**：类级替换为 `@ExtendWith(ParameterizedTestExtension.class)` + `extends TestBase` + `@Parameters` 返回 `Arrays.asList(1, 2)`；所有 `@Test` 改 `@TestTemplate`。断言替换为 AssertJ：`Assert.assertEquals("Expected 2 tasks", 2, tasks.size())` → `assertThat(tasks).hasSize(2)`；带消息的断言如 `Assert.assertEquals("Task file must match", t1.file().path(), FILE_A.path())` 改写为 `assertThat(FILE_A.path()).as("Task file must match").isEqualTo(t1.file().path())`（注意实际值与期望值的顺序在迁移中被对调，因 AssertJ 习惯把"实际值"放 `assertThat`、期望值放 `isEqualTo`，符合 AssertJ 最佳实践）。`V1Assert.assertEquals` / `V2Assert.assertEquals` 这类自定义断言保持不变，因为它们是仓库内 `TableAssertions` 工具，与 JUnit 版本无关。

### `core/src/test/java/org/apache/iceberg/TestEntriesMetadataTable.java`

**修改目的**：把 entries 元数据表测试迁到 JUnit 5，并修正 `Assume` 用法。

**工作逻辑**：
- 类级替换同上；`@Test` → `@TestTemplate`。
- 断言迁移：`assertEquals("...", expected, actual)` → `assertThat(actual).as("...").isEqualTo(expected)`，例如 schema 校验、record count、manifest 数量。`Iterables.size(entriesTable.newScan().planTasks())` 改为 `assertThat(...).hasSize(n)`，`Iterables.getOnlyElement(table.currentSnapshot().allManifests(table.io())).path()` 改为 `.get(0).path()`。
- `Assume.assumeTrue("Only V2 Tables Support Deletes", formatVersion >= 2)` → `assumeThat(formatVersion).as("Only V2 Tables Support Deletes").isGreaterThanOrEqualTo(2)`（AssertJ 假设 API）。
- 静态导入从 `org.junit.Assert.assertEquals` 改为 `org.assertj.core.api.Assertions.assertThat` 和 `org.assertj.core.api.Assumptions.assumeThat`。

### `core/src/test/java/org/apache/iceberg/TestFindFiles.java`

**修改目的**：把 `FindFiles` 工具测试迁到 JUnit 5。

**工作逻辑**：类级与注解替换同前。断言中值得关注的是 `Assert.assertEquals(Sets.newHashSet("/path/to/data-e.parquet"), pathSet(files))` 被改为 `assertThat(pathSet(files)).containsExactly("/path/to/data-e.parquet")`——这里用了 AssertJ 更语义化的 `containsExactly`，比单纯 `isEqualTo` 更能表达"集合恰好包含这些元素"的意图。其余 `Assert.assertEquals(pathSet(...), pathSet(files))` 改为 `assertThat(pathSet(files)).isEqualTo(pathSet(...))`，把实际值前置。

### `core/src/test/java/org/apache/iceberg/TestIncrementalDataTableScan.java`

**修改目的**：把增量数据表扫描测试迁到 JUnit 5，并修正 `@Before` 与 `assertThatThrownBy` 用法。

**工作逻辑**：
- 类级替换同前。
- `@Before public void setupTableProperties()` → `@BeforeEach`。
- `@Test` → `@TestTemplate`。
- 异常断言原本是 `org.assertj.core.api.Assertions.assertThatThrownBy(...)`（通过类引用调用），改为静态导入 `assertThatThrownBy`，使代码更简洁。`isInstanceOf(IllegalArgumentException.class).hasMessage(...)` 链式断言本身已是 AssertJ 风格，保持不变。
- `Assert.assertTrue(listener1.event().fromSnapshotId() == 1)` 这类"assertTrue + ==" 的反模式被改为 `assertThat(listener1.event().fromSnapshotId()).isEqualTo(1)`，更符合 AssertJ 表达。`Assert.assertEquals(false, listener1.event().isFromSnapshotInclusive())` 改为 `assertThat(...).isFalse()`。`Assert.assertTrue("Replace commits are ignored", appendsBetweenScan(5, 6).isEmpty())` 改为 `assertThat(appendsBetweenScan(5, 6)).as("Replace commits are ignored").isEmpty()`。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScans.java`

**修改目的**：把元数据表扫描综合测试（约 496 行变更，是本提交最大文件）迁到 JUnit 5。

**工作逻辑**：
- 类级只加 `@ExtendWith(ParameterizedTestExtension.class)`（继承 `MetadataTableScanTestBase` 后无需重复 `@Parameters`），删除原构造器。
- `@Test` → `@TestTemplate`（数十处）。
- 大量断言迁移，模式与前述一致：`Assert.assertEquals("Should have one task", 1, Iterables.size(tasks))` → `assertThat(tasks).hasSize(1)`；`Assert.assertTrue("Tasks should not be empty", Iterables.size(tasks) > 0)` → `assertThat(tasks).as("Tasks should not be empty").hasSizeGreaterThan(0)`。
- `Assume.assumeTrue("Position deletes supported only for v2 tables", formatVersion == 2)` → `assumeThat(formatVersion).as("Position deletes supported only for v2 tables").isEqualTo(2)`。
- `constantsMap(...)` 返回 `Map`，原代码用 `.get(fieldId)` 取值后再 `assertEquals`；迁移后改为 `(Map<Integer, Integer>) constantsMap(...)` 显式转型后用 AssertJ 的 `.containsEntry(fieldId, value)`，使断言更聚焦于"该 map 包含此条目"，而不是预先取出值再比较。注意这里加了显式泛型强转 `(Map<Integer, Integer>)` / `(Map<Integer, String>)`，是因为 `constantsMap` 返回 `Map<Integer, ?>`，直接对通配符类型用 `containsEntry` 需要编译期确定 K/V 类型，强转可让 AssertJ 的泛型推断更准确。
- 残差断言 `Assert.assertEquals("Expected partition residual to be evaluated", Expression.Operation.GT, residualPred.op())` → `assertThat(residualPred.op()).isEqualTo(Expression.Operation.GT)`（去掉了 `.as(...)`，因消息非关键）。
- 新增静态导入 `entry`（虽然 diff 中显示导入但实际断言用了 `containsEntry`，保留 `entry` 以备其他用法）和 `assumeThat`。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScansWithPartitionEvolution.java`

**修改目的**：把分区演进场景下的元数据表扫描测试迁到 JUnit 5，并处理临时目录 API 差异。

**工作逻辑**：
- 类级只加 `@ExtendWith(ParameterizedTestExtension.class)`，删除构造器。
- `@Before public void createTable()` → `@BeforeEach`。
- 临时目录：`this.tableDir = temp.newFolder()` → `this.tableDir = Files.createTempDirectory(temp, "junit").toFile()`。原因：JUnit 4 的 `TemporaryFolder`（`@Rule`）提供 `newFolder()`；JUnit 5 的 `@TempDir` 注入 `Path`，需用 `Files.createTempDirectory` 创建子目录。这里把 `temp`（一个 `Path`）作为父目录传入，符合 JUnit 5 模型。
- `@Test` → `@TestTemplate`（多处）。
- 断言 `Assertions.assertThat(tasks).hasSize(1)` → `assertThat(tasks).hasSize(1)`（从类引用改为静态导入，与全文风格统一）；`allRows(tasks)` 的 size 断言同样处理。
- 移除 `org.assertj.core.api.Assertions` 的类引用 import，改为静态导入 `assertThat`、`assumeThat`、`entry`。
- 移除不再使用的 import：`Objects`、`StreamSupport`、`Iterators`，新增 `Files`、`Map`。

## 小结

本提交完成了 Iceberg core 模块"元数据表扫描"这一簇测试从 JUnit 4 到 JUnit 5 的迁移，共 7 个文件、净减 40 行（488 增 / 528 删）。迁移严格遵守"机械等价"原则：测试覆盖场景不变，断言语义不变，仅替换框架 API。关键依赖是仓库已就绪的 `ParameterizedTestExtension`（位于 `api/src/test/java/org/apache/iceberg/`，从 Flink 项目移植）与 JUnit 5 版本的 `TestBase`。迁移后这批测试统一使用 `@TestTemplate` + `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameter` 字段注入 + AssertJ 流式断言，与仓库其余已迁移测试保持一致。

回迁到 1.4.x 的注意事项：
1. 1.4.x 分支需先确认 `ParameterizedTestExtension`、`Parameters` 注解、`TestBase`（JUnit 5 版）这几个依赖已存在。本提交本身不引入这些基础设施，仅消费它们。若 1.4.x 尚未引入对应基础设施（应在更早的提交中回迁），则本提交无法独立回迁。
2. 本提交不改变任何生产代码，仅改测试，回迁后需保证编译通过且测试在 v1/v2 两种 formatVersion 下均通过。
3. 注意 `TestMetadataTableScans` 中对 `constantsMap` 返回值加了 `(Map<Integer, Integer>)` / `(Map<Integer, String>)` 强转——这是迁移时为适配 AssertJ `containsEntry` 泛型推断而引入的，回迁时需保留这些强转，否则可能编译失败。
4. `TestMetadataTableScansWithPartitionEvolution` 的临时目录改用 `Files.createTempDirectory(temp, "junit").toFile()`，依赖 `@TempDir Path temp`（来自父类 `TestBase`），回迁时需确认父类 `temp` 字段类型为 `Path`。
