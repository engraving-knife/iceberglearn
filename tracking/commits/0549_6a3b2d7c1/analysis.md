# 提交 0549：Spark: Remove/migrate remaining JUnit4 tests

## 提交信息

- **序号**：0549 / 4088
- **哈希**：6a3b2d7c153412b01c746debb018c544516f2bbd
- **短哈希**：6a3b2d7c1
- **日期**：2024-02-28 17:28:09 +0900
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Spark: Remove/migrate remaining JUnit4 tests (#9817)
- **PR/Issue**：#9817

## 总体目的

本提交是 Spark 3.5 模块 JUnit5 迁移工作的最终清理步骤。在前序提交（特别是 0546 / #9790）将所有测试子类迁移到 JUnit5 后，旧的 JUnit4 测试基类（`SparkTestBase`、`SparkTestBaseWithCatalog`、`SparkCatalogTestBase`、`SparkExtensionsTestBase`）已无任何子类引用，成为死代码。本提交完成两件事：

1. **删除 4 个 JUnit4 测试基类**（共 550 行），消除死代码、避免后续误用、减少 JUnit4 与 JUnit5 共存的认知负担。
2. **迁移 2 个仍残留 JUnit4 引用的测试文件**：
   - `TestSparkFunctions.java`：`import org.junit.Test` → `import org.junit.jupiter.api.Test`
   - `TestSparkMetadataColumns.java`：`org.junit.Assume.assumeTrue/assumeFalse` → AssertJ `assumeThat`

完成本提交后，Spark 3.5 模块的测试代码完全摆脱 JUnit4 API 依赖（除 vintage engine 兼容层外），所有测试统一使用 JUnit5 + AssertJ 栈。

## 如何达成设计目的

设计思路是"先确认无引用，再删除"。提交者通过 IDE 或 grep 确认 4 个 JUnit4 基类已无任何 `extends` 引用（所有子类在 0546 及更早 PR 中已迁移到对应的 JUnit5 基类），因此可安全删除。对应的 JUnit5 替代类如下：

| JUnit4 基类（已删除） | JUnit5 替代类 |
|---|---|
| `SparkTestBase` | `TestBase` |
| `SparkTestBaseWithCatalog` | `TestBaseWithCatalog` |
| `SparkCatalogTestBase` | `CatalogTestBase` |
| `SparkExtensionsTestBase` | `ExtensionsTestBase` |

对于 2 个残留 JUnit4 引用的测试文件，做最小化迁移：
- `TestSparkFunctions` 仅需替换 `@Test` 的 import（该类无参数化、无 Before/After，是最简单的迁移）。
- `TestSparkMetadataColumns` 主体已是 JUnit5（`@TestTemplate`、`@BeforeEach`），仅 `Assume.assumeTrue/assumeFalse` 三处调用仍用 JUnit4 API，替换为 AssertJ 的 `assumeThat` 静态导入。

## 修改详情

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkExtensionsTestBase.java`（删除）

**修改目的**：删除 JUnit4 版 spark-extensions 测试基类，消除死代码。

**工作逻辑**：该类原为 spark-extensions 测试的 JUnit4 基类，继承 `SparkCatalogTestBase`，提供 `@BeforeClass startMetastoreAndSpark()` 初始化 Hive metastore + SparkSession（带 `IcebergSparkSessionExtensions` 扩展）+ HiveCatalog。其 JUnit5 替代 `ExtensionsTestBase` 已在 0546 提交中被所有测试子类采用。删除前已确认无任何 `extends SparkExtensionsTestBase` 引用残留。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/SparkCatalogTestBase.java`（删除）

**修改目的**：删除 JUnit4 版参数化 catalog 测试基类。

**工作逻辑**：该类使用 `@RunWith(Parameterized.class)` + `@Parameterized.Parameters` 提供 3 组 catalog 配置（HIVE、HADOOP、SPARK），并维护 `@Rule public TemporaryFolder temp`。继承 `SparkTestBaseWithCatalog`。其 JUnit5 替代 `CatalogTestBase` 使用 `@ExtendWith(ParameterizedTestExtension.class)` + `@Parameters` 实现等效参数化，`temp` 改为 `TestBase` 中的字段。删除前已确认无引用。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/SparkTestBaseWithCatalog.java`（删除）

**修改目的**：删除 JUnit4 版"带 catalog 的 Spark 测试基类"。

**工作逻辑**：该类继承 `SparkTestBase`，提供 catalog 配置（catalogName、catalogConfig、validationCatalog、validationNamespaceCatalog、tableIdent、tableName）、warehouse 创建/清理（`@BeforeClass createWarehouse` / `@AfterClass dropWarehouse`）、辅助方法（`tableName(name)`、`commitTarget()`、`selectTarget()`、`cachingCatalogEnabled()`、`configurePlanningMode()`）以及 `@Rule public TemporaryFolder temp`。其 JUnit5 替代 `TestBaseWithCatalog` 已承载等价功能。删除前已确认无引用。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/SparkTestBase.java`（删除）

**修改目的**：删除 JUnit4 版 Spark 测试最底层基类（287 行，4 个文件中最大）。

**工作逻辑**：该类是整个 Spark 3.5 JUnit4 测试体系的根基类，继承 `SparkTestHelperBase`，提供：
- 静态共享资源：`metastore`（TestHiveMetastore）、`hiveConf`、`spark`（SparkSession）、`sparkContext`（JavaSparkContext）、`catalog`（HiveCatalog）。
- 生命周期：`@BeforeClass startMetastoreAndSpark()` 启动 metastore + Spark + 创建 default namespace；`@AfterClass stopMetastoreAndSpark()` 停止清理。
- SQL 辅助：`sql(query, args)`、`scalarSql(query, args)`、`row(values...)`、`dbPath(dbName)`。
- 测试工具：`withUnavailableFiles/withUnavailableLocations`（临时移走文件模拟不可用）、`withDefaultTimeZone`（临时切换时区）、`withSQLConf`（临时修改 SQLConf）、`jsonToDF`、`append`、`tablePropsAsString`、`executeAndKeepPlan`（捕获执行计划）、`waitUntilAfter`。
- 函数式接口 `Action`。

其 JUnit5 替代 `TestBase`（继承 `SparkTestHelperBase`）已承载全部等价功能，使用 `@BeforeAll`/`@AfterAll` 替代 `@BeforeClass`/`@AfterClass`。删除前已确认无引用。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/functions/TestSparkFunctions.java`

**修改目的**：迁移该类残留的 JUnit4 `@Test` import。

**工作逻辑**：单行 import 替换：`import org.junit.Test;` → `import org.junit.jupiter.api.Test;`。该类无参数化、无 Before/After、无 Rule，是最简单的迁移——仅需把 `@Test` 注解来源从 JUnit4 切到 JUnit5。方法体内的 `Assertions.assertThat(...)` 已是 AssertJ 风格，无需改动。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java`

**修改目的**：迁移该类残留的 3 处 JUnit4 `Assume.assumeTrue/assumeFalse` 调用为 AssertJ `assumeThat`。

**工作逻辑**：该类主体已是 JUnit5（`@TestTemplate`、`@BeforeEach`、`@AfterEach`、`@BeforeAll`），但 3 处条件跳过仍用 JUnit4 的 `org.junit.Assume`。替换为 AssertJ 的 `assumeThat`（静态导入自 `org.assertj.core.api.Assumptions.assumeThat`）：

1. `testSpecAndPartitionMetadataColumns`：
   - 原：`Assume.assumeFalse(fileFormat == FileFormat.ORC && vectorized);`（仅当 ORC + vectorized 同时成立时跳过）
   - 新：拆分为两行
     ```java
     assumeThat(fileFormat).isNotEqualTo(FileFormat.ORC);
     assumeThat(vectorized).isFalse();
     ```
   - **注意：此处存在语义变化**。原 `assumeFalse(A && B)` 仅在 A 与 B 同时为真时跳过；新代码 `assumeThat(A).isNotEqualTo(ORC)` 在 A==ORC 时跳过，`assumeThat(B).isFalse()` 在 B==true 时跳过，等价于 `assumeFalse(A || B)`。即新代码跳过范围更大：ORC 但非 vectorized 的场景原来会继续执行，现在会被跳过；非 ORC 但 vectorized 的场景同理。这可能是迁移时为简化逻辑而有意放宽跳过条件（注释 "TODO: support metadata structs in vectorized ORC reads" 暗示该测试对 ORC 和 vectorized 各自都有兼容问题），也可能是无意中的语义偏差。回迁时需留意。

2. `testPositionMetadataColumnWithMultipleRowGroups`：
   - 原：`Assume.assumeTrue(fileFormat == FileFormat.PARQUET);`
   - 新：`assumeThat(fileFormat).isEqualTo(FileFormat.PARQUET);`
   - 语义等价，仅 API 风格切换。

3. `testPositionMetadataColumnWithMultipleBatches`：
   - 原：`Assume.assumeTrue(fileFormat == FileFormat.PARQUET);`
   - 新：`assumeThat(fileFormat).isEqualTo(FileFormat.PARQUET);`
   - 语义等价，仅 API 风格切换。

同时删除 `import org.junit.Assume;`，新增 `import static org.assertj.core.api.Assumptions.assumeThat;`。

## 小结

本提交是 Spark 3.5 JUnit5 迁移的收尾清理：删除 4 个共 550 行的 JUnit4 测试基类死代码，并迁移 2 个残留 JUnit4 API 引用的测试文件。改动后 Spark 3.5 测试栈完全统一到 JUnit5 + AssertJ，消除 JUnit4/5 双栈并存的维护负担。

**关键关注点**：
- `TestSparkMetadataColumns.testSpecAndPartitionMetadataColumns` 中 `assumeFalse(A && B)` → `assumeThat(A)...; assumeThat(B)...` 的拆分引入了语义变化（从 AND 变 OR），跳过条件变宽。这可能是 intentional simplification，也可能是迁移偏差，回迁时需结合测试意图判断是否接受。

**回迁到 1.4.x 的注意事项**：
- 本提交依赖 0546（#9790）及更早的 JUnit5 迁移 PR 已完成。1.4.x 若未完成前置迁移，则 4 个被删基类仍有子类引用，直接应用本提交会导致编译失败。
- 若 1.4.x 维持 JUnit4 测试栈，本提交不宜回迁。
- 若 1.4.x 已完成 JUnit5 迁移，则本提交可直接 cherry-pick，但需注意 `TestSparkMetadataColumns` 的 assume 语义变化是否可接受。若 1.4.x 中该测试的 ORC/vectorized 组合行为有特殊要求，应保留原 `assumeFalse(A && B)` 语义（可用 `assumeThat(fileFormat == FileFormat.ORC && vectorized).isFalse()` 一行实现）。
- 删除 4 个基类前，必须在 1.4.x 中再次 grep 确认无 `extends SparkExtensionsTestBase`、`extends SparkCatalogTestBase`、`extends SparkTestBaseWithCatalog`、`extends SparkTestBase` 残留引用，否则编译失败。
