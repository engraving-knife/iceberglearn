# 提交分析：Flink: Migrate tests to JUnit5 (#10232)

## 提交信息

| 项目 | 内容 |
| --- | --- |
| 哈希 | `6f0d9dd47c693dc2aa13a38fab3d56473d925563` |
| 短哈希 | `6f0d9dd47` |
| 作者 | Tom Tanaka |
| 提交时间 | 2024-04-30 19:23:55 +0900 |
| 提交标题 | Flink: Migrate tests to JUnit5 (#10232) |
| 提交正文 | （无附加正文） |
| 变更范围 | 33 个文件（v1.17 / v1.18 / v1.19 三个 Flink 版本目录各 11 个文件），1036 行新增，1489 行删除 |

每个 Flink 版本目录下的变更文件（三个版本完全对应）：
- `flink-runtime/src/integration/java/.../IcebergConnectorSmokeTest.java`（修改）
- `flink/src/test/java/.../flink/FlinkTestBase.java`（**删除**）
- `.../flink/TestCatalogLoader.java`（修改）
- `.../flink/TestChangeLogTable.java`（修改）
- `.../flink/TestFlinkAnonymousTable.java`（修改）
- `.../flink/TestFlinkHiveCatalog.java`（修改）
- `.../flink/TestIcebergConnector.java`（修改）
- `.../flink/source/ChangeLogTableTestBase.java`（修改）
- `.../flink/source/TestBoundedTableFactory.java`（修改）
- `.../flink/source/TestFlinkSourceConfig.java`（修改）
- `.../flink/source/TestFlinkTableSource.java`（修改）

> 说明：`TestBase.java`（新的 JUnit5 基类）并未由本提交引入，而是在更早的提交中已存在于 main 分支；本提交负责把剩余测试从旧基类 `FlinkTestBase`（JUnit4）迁移到新基类 `TestBase`（JUnit5），并删除旧的 `FlinkTestBase`。

## 总体目的

将 Iceberg Flink 模块（v1.17 / v1.18 / v1.19 三个版本目录）的测试从 JUnit 4 迁移到 JUnit 5（Jupiter）。迁移统一了测试框架版本，使 Flink 测试与 Iceberg 其它已迁移到 JUnit5 的模块保持一致，并为后续使用 JUnit5 的高级扩展机制（`@ExtendWith`、`@RegisterExtension`、`@TempDir` 等）打下基础。本提交是一次纯测试侧的框架迁移，不改变被测产品的行为。

## 如何达成设计目的

### 迁移目标：从 `FlinkTestBase`（JUnit4）切换到 `TestBase`（JUnit5）

新旧两个基类承担相同的职责（启动 MiniCluster、启动 Hive Metastore、提供 `TableEnvironment`/`sql`/`exec`/`assertSameElements` 等工具方法），但框架不同：

| 维度 | 旧 `FlinkTestBase`（JUnit4） | 新 `TestBase`（JUnit5） |
| --- | --- | --- |
| MiniCluster | `@ClassRule MiniClusterWithClientResource` | `@RegisterExtension MiniClusterExtension`（经 `MiniFlinkClusterExtension`） |
| 临时目录 | `@ClassRule TemporaryFolder TEMPORARY_FOLDER`（J4） | `@TempDir Path temporaryDirectory`（J5） |
| 生命周期 | `@BeforeClass` / `@AfterClass` | `@BeforeAll` / `@AfterAll` |
| 继承 | `extends TestBaseUtils` | `extends TestBaseUtils`（不变） |

由于 `TestBase` 已先于本提交存在，本次迁移的核心动作是：把各测试类的 `extends FlinkTestBase` 改为 `extends TestBase`，把测试中用到 JUnit4 临时目录（`TEMPORARY_FOLDER.newFolder()`）的写法改为 JUnit5 的 `@TempDir` 注入目录（`Files.createTempDirectory(temporaryDirectory, "junit")`），并删除已无引用的旧基类 `FlinkTestBase`。

### 关键迁移模式（逐类归纳）

**模式 A：注解与导入的整体替换**

所有测试类统一进行如下导入/注解替换：

| JUnit 4 | JUnit 5 |
| --- | --- |
| `org.junit.Test` | `org.junit.jupiter.api.Test` |
| `org.junit.Before` | `org.junit.jupiter.api.BeforeEach` |
| `org.junit.After` | `org.junit.jupiter.api.AfterEach` |
| `org.junit.BeforeClass` | `org.junit.jupiter.api.BeforeAll` |
| `org.junit.AfterClass` | `org.junit.jupiter.api.AfterAll` |
| `org.junit.Assert.*` | AssertJ `assertThat` / `assertThatThrownBy` |
| `org.assertj.core.api.Assertions`（实例调用） | AssertJ 静态导入 `assertThat` / `assertThatThrownBy` |

**模式 B：参数化测试从 JUnit4 `Parameterized` 迁移到 Iceberg 自研扩展**

对于参数化测试（`TestIcebergConnector`、`TestChangeLogTable`），迁移模式为：

```java
// JUnit 4
@RunWith(Parameterized.class)
public class TestIcebergConnector extends FlinkTestBase {
  private final String catalogName;
  ...
  @Parameterized.Parameters(name = "...")
  public static Iterable<Object[]> parameters() { ... }

  public TestIcebergConnector(String catalogName, ...) {  // 构造函数注入参数
    this.catalogName = catalogName; ...
  }

  @Test public void testXxx() { ... }
}

// JUnit 5
@ExtendWith(ParameterizedTestExtension.class)
public class TestIcebergConnector extends TestBase {
  @Parameter(index = 0) private String catalogName;       // 字段注入替代构造函数
  ...

  @Parameters(name = "...")
  public static Iterable<Object[]> parameters() { ... }   // 保留

  @TestTemplate public void testXxx() { ... }             // @Test → @TestTemplate
}
```

要点：
- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。这里使用的是 **Iceberg 自研**的 `org.apache.iceberg.ParameterizedTestExtension` / `org.apache.iceberg.Parameter` / `org.apache.iceberg.Parameters`，而非 JUnit5 自带的 `ParameterizedTest`。其设计动机是：JUnit5 原生参数化测试要求把参数化方法标注为 `@ParameterizedTest` 并放在非参数化类上，与 JUnit4 那种“类级参数化 + 每个测试方法跑全参数”的模型不同；Iceberg 的 `ParameterizedTestExtension` 复刻了 JUnit4 的类级参数化语义，使迁移成本最小——只需把构造函数注入改为 `@Parameter` 字段注入、`@Test` 改为 `@TestTemplate`，参数工厂方法 `@Parameters` 保持不变。
- 构造函数被删除：参数由 `@Parameter(index = N)` 注入到字段，因此不再需要带参构造函数。
- 子类化（`IcebergConnectorSmokeTest extends TestIcebergConnector`）因此可以简化为无参空类：
  ```java
  // 旧
  public class IcebergConnectorSmokeTest extends TestIcebergConnector {
    public IcebergConnectorSmokeTest(String catalogName, Map<String,String> properties, boolean isStreaming) {
      super(catalogName, properties, isStreaming);
    }
  }
  // 新
  public class IcebergConnectorSmokeTest extends TestIcebergConnector {}
  ```

**模式 C：`TestName` 规则 → `TestInfo` 参数注入**

`ChangeLogTableTestBase` 原本用 JUnit4 的 `@Rule TestName name = new TestName();` 取当前测试方法名作为表名（`name.getMethodName()`）。JUnit5 没有 `TestName` 规则，改为通过 `@BeforeEach` 方法注入 `TestInfo`：

```java
// JUnit 4
@Rule public TestName name = new TestName();
@After public void clean() { sql("DROP TABLE IF EXISTS %s", name.getMethodName()); }

// JUnit 5
protected String tableName;
@BeforeEach
public void setup(TestInfo testInfo) {
  assertThat(testInfo.getTestMethod()).isPresent();
  this.tableName = testInfo.getTestMethod().get().getName();
}
@AfterEach public void clean() { sql("DROP TABLE IF EXISTS %s", tableName); }
```

子类 `TestBoundedTableFactory` 原本在方法内 `String table = name.getMethodName();`，迁移后直接复用基类的 `tableName` 字段，消除了重复取方法名的逻辑。

**模式 D：临时目录从 `TemporaryFolder` 迁移到 `@TempDir`**

旧基类提供 `TEMPORARY_FOLDER`（JUnit4 `@ClassRule`），各测试用 `TEMPORARY_FOLDER.newFolder()`。新基类提供 `@TempDir Path temporaryDirectory`（JUnit5），各测试改为：

```java
// 旧
File warehouseDir = TEMPORARY_FOLDER.newFolder();
// 新
File warehouseDir = Files.createTempDirectory(temporaryDirectory, "junit").toFile();
```

涉及 `TestFlinkAnonymousTable`、`TestFlinkHiveCatalog`（去掉自身的 `@Rule TemporaryFolder tempFolder`，统一用基类的 `temporaryDirectory`）、`TestIcebergConnector`（`createWarehouse()` 由 static 改为实例方法）、`TestChangeLogTable`（`@BeforeClass createWarehouse` 合并进 `@BeforeEach before`）、`TestFlinkTableSource`（`@BeforeClass createWarehouse` 合并进 `@BeforeEach before`，`warehouse` 由 static 改为局部变量）。

注意一个重要副作用：`TestChangeLogTable` 与 `TestFlinkTableSource` 把原本 `@BeforeClass`（类级，只跑一次）的 warehouse 创建**降级**为 `@BeforeEach`（每测试方法跑一次）。这是合理的——JUnit5 下参数化测试每个参数组合都会重新实例化，warehouse 与 catalog/table 也需要随之重建以保证隔离；同时每方法一个临时目录避免了状态串扰。

**模式 E：断言从 JUnit4 `Assert.*` 统一为 AssertJ**

大量 `Assert.assertEquals(expected, actual)` / `Assert.assertTrue(...)` 被改写为 AssertJ 链式断言：

```java
// 旧
Assert.assertEquals("Should have 3 records", 3, result.size());
Assertions.assertThat(result).containsAnyElementsOf(expectedList);
// 新
assertThat(result).hasSize(3);
assertThat(result).containsAnyElementsOf(expectedList);
```

并统一改为静态导入 `static org.assertj.core.api.Assertions.assertThat` / `assertThatThrownBy`，去除 `Assertions.` 前缀。一些集合比较从“用 `Sets.newHashSet` 包装后比较”改为 AssertJ 的 `containsExactlyInAnyOrder` / `containsExactlyInAnyOrderElementsOf`，语义更清晰。

## 修改详情（代表性文件）

> 三个 Flink 版本目录的改动完全对应，下面以 v1.19 为代表说明；v1.17 / v1.18 的 diff 内容与 v1.19 一致。

### `FlinkTestBase.java`（删除，每版本 -147 行左右）
- 完整删除旧的 JUnit4 基类。其职责（MiniCluster、metastore 启停、`getTableEnv`、`sql`/`exec`/`assertSameElements`/`dropCatalog`/`dropDatabase`）已由更早提交引入的 `TestBase`（JUnit5）承担。

### `TestCatalogLoader.java`
- `extends FlinkTestBase` → `extends TestBase`。
- `@BeforeClass`/`@AfterClass` → `@BeforeAll`/`@AfterAll`。
- `Assert.assertTrue(...)` → `assertThat(...).isTrue()`；`Assert.assertEquals("my_value", ...)` → `assertThat(...).contains(entry(...))`。
- 导入切换为 JUnit5 + AssertJ 静态导入。

### `TestIcebergConnector.java`（参数化测试，模式 B）
- `@RunWith(Parameterized.class)` → `@ExtendWith(ParameterizedTestExtension.class)`。
- 构造函数注入 → `@Parameter(index=...)` 字段注入；删除带参构造函数。
- `@Parameterized.Parameters` → `@Parameters`。
- `@Test` → `@TestTemplate`。
- `@After` → `@AfterEach`。
- `@ClassRule TemporaryFolder WAREHOUSE` 去掉，`createWarehouse()` 改为实例方法并用 `Files.createTempDirectory(temporaryDirectory, "junit")`。
- 断言全部改写为 AssertJ。

### `TestChangeLogTable.java`（参数化测试，模式 B + D）
- 同样切换到 `ParameterizedTestExtension` + `@Parameter` + `@TestTemplate`。
- `@BeforeClass createWarehouse` 合并入 `@BeforeEach before`；`warehouse` 由 static 改为实例字段。
- 断言改写为 AssertJ（`hasSameSizeAs`、`containsExactlyInAnyOrderElementsOf`、`isEqualTo` 等）。

### `TestFlinkHiveCatalog.java`（模式 D）
- 删除自身的 `@Rule TemporaryFolder tempFolder`，统一用基类 `temporaryDirectory`。
- `Files.createTempDirectory(temporaryDirectory, "junit").toFile()` 替代 `tempFolder.newFolder()`。
- `Assert.assertTrue("...", Files.exists(...))` → `assertThat(...).exists()`。
- `Assert.assertEquals(..., 2, Files.list(...).count())` → `assertThat(...).as(...).isEqualTo(2)`。

### `TestFlinkAnonymousTable.java`
- `extends FlinkTestBase` → `extends TestBase`；`TEMPORARY_FOLDER.newFolder()` → `Files.createTempDirectory(temporaryDirectory, "junit").toFile()`。
- `Assertions.assertThat(...)` → 静态 `assertThat(...)`。

### `ChangeLogTableTestBase.java`（模式 C）
- `@Rule TestName` → `@BeforeEach setup(TestInfo)` 注入 `tableName`。
- `@After` → `@AfterEach`；`name.getMethodName()` → `tableName`。
- `extends FlinkTestBase` → `extends TestBase`。

### `TestBoundedTableFactory.java`
- 不再在方法内取 `name.getMethodName()`，直接用基类 `tableName`。
- `Assert.assertEquals(...)` → `assertThat(...).isEqualTo(...)` / `.isEmpty()`。

### `TestFlinkSourceConfig.java`
- `extends TestFlinkTableSource`（不变），`@Test` 保留（非参数化方法）。
- `Assert.assertEquals(3, result.size())` → `assertThat(result).hasSize(3)`。
- `Assertions.assertThatThrownBy(...)` → 静态 `assertThatThrownBy(...)`。

### `TestFlinkTableSource.java`（模式 D）
- `extends FlinkTestBase` → `extends TestBase`。
- `@BeforeClass createWarehouse` 合并入 `@BeforeEach before`；`warehouse` 由 static 改为方法局部变量。
- 构造函数中注册 `Listeners` 的逻辑移入 `@BeforeEach before`。
- `@After` → `@AfterEach`。
- 大量 `Assert.assertEquals`/`Assert.assertTrue`/`Assertions.assertThatThrownBy` 改写为 AssertJ 静态导入形式。

### `IcebergConnectorSmokeTest.java`（模式 B 子类简化）
- 删除带参构造函数，简化为 `class IcebergConnectorSmokeTest extends TestIcebergConnector {}`。
- 清理无用导入（`java.util.Map` 等）。

## 小结

### 成效
- 完成 Flink 模块（三个版本目录）从 JUnit4 到 JUnit5 的框架迁移，删除旧基类 `FlinkTestBase`，统一到新基类 `TestBase`。
- 通过 Iceberg 自研的 `ParameterizedTestExtension` 复刻 JUnit4 类级参数化语义，使迁移机械且低风险：参数工厂方法 `@Parameters` 基本不动，仅做“构造注入→字段注入、`@Test`→`@TestTemplate`”。
- 顺带把断言统一到 AssertJ 链式风格，临时目录统一到 JUnit5 `@TempDir`，提升了测试可读性与一致性。
- 净删除 453 行（1036 增 / 1489 删），代码量下降主要来自删除 `FlinkTestBase`（每版本约 147 行 ×3）与简化子类构造函数。

### 影响范围
- 仅影响 Flink 模块的测试代码（`flink/v1.17`、`flink/v1.18`、`flink/v1.19` 三个目录），不触及任何主代码或产品行为。
- 测试的生命周期语义有局部变化：部分原 `@BeforeClass`（类级一次）的 warehouse/catalog 创建降级为 `@BeforeEach`（每方法一次），在 JUnit5 参数化模型下这是必要的隔离调整，可能略微增加单测耗时，但保证测试间状态隔离。
- 迁移后 Flink 测试依赖 JUnit5（Jupiter）+ Iceberg `ParameterizedTestExtension` + AssertJ，构建环境需具备对应依赖（main 分支已具备）。

### 回迁注意事项（1.4.x ← main）
- **强依赖前置提交**：本提交依赖更早引入的 `TestBase.java`（JUnit5 基类）、`MiniFlinkClusterExtension`、`ParameterizedTestExtension`/`Parameter`/`Parameters` 等。若 1.4.x 尚未引入这些基础设施，必须**连同其引入提交一起回迁**，否则编译会因找不到 `TestBase`、`ParameterizedTestExtension` 等而失败。
- **三目录需同步**：v1.17 / v1.18 / v1.19 三个目录的改动完全对应，回迁时应整体携带，避免出现某版本已迁移、另一版本仍用旧基类的混用状态。
- **Flink 版本差异**：各 Flink 版本对 JUnit5 的支持程度不同（`MiniClusterExtension`、`@TempDir` 等在不同 Flink 版本中的可用性与坐标可能不同）。回迁前需确认 1.4.x 上各 Flink 版本的测试依赖是否已包含 JUnit5 所需的 flink-test-junit5 等构件；若某版本（如 v1.17）缺少对应 JUnit5 桥接，可能需要额外补充依赖或暂缓该版本迁移。
- **生命周期语义变化**：原 `@BeforeClass` 降级为 `@BeforeEach` 的几处（`TestChangeLogTable`、`TestFlinkTableSource`）在回迁后需跑全量 Flink 测试验证无状态串扰；若 1.4.x 有依赖“warehouse 全局只建一次”的隐式假设的测试，需特别关注。
- **删除 `FlinkTestBase` 的前提**：只有确认 1.4.x 上已无任何类引用 `FlinkTestBase` 后才能删除该文件；若 1.4.x 上还有其它（本提交未覆盖的）测试仍继承 `FlinkTestBase`，删除会导致编译失败，需先把这些类一并迁移或保留旧基类。
- 风险整体可控但工作量集中在依赖前置项的核对；建议回迁时按“先补 `TestBase` 等基础设施 → 再迁移三个目录的测试 → 最后删 `FlinkTestBase`”的顺序进行，并在每步跑编译验证。
