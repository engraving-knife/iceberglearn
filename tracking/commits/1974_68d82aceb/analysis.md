# 提交 1974：Spark 3.4: Migrate Spark ExtensionsTestBase-related tests (#12744)

## 提交信息

- **序号**：1974 / 4088
- **哈希**：68d82acebf92f7589a66f49d37d898c9fab43693
- **短哈希**：68d82aceb
- **日期**：2025-04-08 13:56:01 +0200
- **作者**：Tom Tanaka
- **提交说明**：Spark 3.4: Migrate Spark ExtensionsTestBase-related tests (#12744)
- **PR/Issue**：#12744

## 总体目的

本提交将 Spark 3.4 模块下一批继承自 `SparkExtensionsTestBase` 的过程（procedure）测试类从 JUnit 4 迁移到 JUnit 5（Jupiter），并改为继承新的 `ExtensionsTestBase` 基类。同时同步调整 Spark 3.5 模块中对应测试类，使其与基类提供的 `temp` 字段统一。

在迁移前，Spark 3.4 的这些测试类仍使用 JUnit 4 风格：通过构造函数注入参数化变量、使用 `@Before`/`@After`/`@Test`/`@Ignore` 注解、`@Rule TemporaryFolder`、`org.junit.runners.Parameterized.Parameters`、`org.junit.Assume` 等。而 Iceberg 项目整体正在推进测试基础设施向 JUnit 5 的 `ParameterizedTestExtension` 体系统一，Spark 3.5 等模块已完成迁移。Spark 3.4 的滞后导致两套风格并存，维护成本高，且无法享受 JUnit 5 的扩展机制与 AssertJ 假设集成。

通过将这 9 个 Spark 3.4 测试类迁移到与 Spark 3.5 一致的 JUnit 5 + `ExtensionsTestBase` 模式，并清理 Spark 3.5 中因基类已提供 `temp` 而冗余的本地 `@TempDir` 声明，使两个 Spark 版本的测试基类与风格保持一致。

## 如何达成设计目的

迁移遵循统一模式（以 `TestAddFilesProcedure` 为代表）：

1. **基类替换**：`extends SparkExtensionsTestBase` → `extends ExtensionsTestBase`，并添加 `@ExtendWith(ParameterizedTestExtension.class)`。
2. **参数化改造**：删除通过构造函数接收的参数化字段与构造函数，改用 `@Parameter(index = N)` 注解字段；`@Parameters` 来源由 `org.junit.runners.Parameterized.Parameters` 改为 `org.apache.iceberg.Parameters`。
3. **注解替换**：`@Before`→`@BeforeEach`、`@After`→`@AfterEach`、`@Test`→`@TestTemplate`、`@Ignore`→`@Disabled`。
4. **临时目录**：移除 `@Rule public TemporaryFolder temp`，改用基类提供的 `temp` 字段（`temp.toFile()`）。
5. **假设 API**：`org.junit.Assume.assumeTrue` → AssertJ 的 `assumeThat(...).isTrue()`/`isFalse()`。
6. **断言**：移除对 `org.junit.Assert` 的依赖，统一使用 AssertJ。
7. **Spark 3.5 调整**：移除本地 `@TempDir Path temp` 声明（改用基类 `temp`），清理无用 import（如 `Path`、`TempDir`），并对个别断言做小幅修正（如 `.hasSize(0)` 等）。

## 修改详情

### Spark 3.4 测试类（9 个文件，较大改动）

涉及 `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/` 下：

- `TestAddFilesProcedure.java` (修改, +69/-66)：JUnit4→JUnit5 迁移，构造函数参数化改为 `@Parameter`，`@Rule TemporaryFolder` 改为基类 `temp`，注解与假设 API 替换。
- `TestExpireSnapshotsProcedure.java` (修改, +47/-43)：同模式迁移。
- `TestMigrateTableProcedure.java` (修改, +72/-52)：同模式迁移。
- `TestRemoveOrphanFilesProcedure.java` (修改, +57/-46)：同模式迁移。
- `TestRewriteDataFilesProcedure.java` (修改, +57/-48)：同模式迁移。
- `TestRewriteManifestsProcedure.java` (修改, +54/-47)：同模式迁移。
- `TestRewritePositionDeleteFiles.java` (修改, +30/-22)：同模式迁移。
- `TestRewritePositionDeleteFilesProcedure.java` (修改, +44/-37)：同模式迁移。
- `TestSnapshotTableProcedure.java` (修改, +66/-57)：同模式迁移。

**修改目的**：将这些过程测试类统一到 JUnit 5 + `ExtensionsTestBase` 体系。

**工作逻辑**：每个类均执行上述基类替换、参数化字段注解化、生命周期/测试注解替换、临时目录统一、假设与断言 API 切换。

### Spark 3.5 测试类（6 个文件，较小改动）

涉及 `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/` 下：

- `TestAddFilesProcedure.java` (修改, +3/-3)：移除本地 `@TempDir Path temp` 及 import，改用基类 `temp`；修正一处断言。
- `TestExpireSnapshotsProcedure.java` (修改, +5/-6)：清理 `@TempDir`/import 相关冗余。
- `TestMigrateTableProcedure.java` (修改, +0/-5)：移除冗余 import/字段。
- `TestRemoveOrphanFilesProcedure.java` (修改, +12/-19)：清理 `@TempDir` 与相关字段。
- `TestRewriteDataFilesProcedure.java` (修改, +3/-3)：小幅清理。
- `TestSnapshotTableProcedure.java` (修改, +3/-2)：小幅清理。

**修改目的**：与基类 `ExtensionsTestBase` 提供的 `temp` 字段对齐，消除重复的临时目录声明。

## 总结

本提交将 Spark 3.4 模块的 9 个 procedure 测试类从 JUnit 4 迁移到 JUnit 5，统一继承 `ExtensionsTestBase` 并采用 `ParameterizedTestExtension` 参数化机制；同时清理 Spark 3.5 对应测试类中因基类已提供 `temp` 而冗余的本地 `@TempDir` 声明。改动以机械式迁移为主（注解、参数化、临时目录、假设/断言 API 替换），使两个 Spark 版本的测试基础设施风格一致。
