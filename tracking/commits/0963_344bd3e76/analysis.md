# 提交 0963：Flink: parameterize Flink table source tests to test both old and FLIP-27 source implementations (#10741)

## 提交信息

- **序号**：0963 / 4088
- **哈希**：344bd3e76cb2652bd4aa980313d753ad9d678e2f
- **短哈希**：344bd3e76
- **日期**：2024-07-22 10:10:37 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: parameterize Flink table source tests to test both old and FLIP-27 source implementations (#10741)
- **PR/Issue**：#10741

## 总体目的

Iceberg 的 Flink 集成在演进过程中引入了基于 FLIP-27 的全新 Source 实现（对应配置项 `FlinkConfigOptions.TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE`），作为旧的 source 实现的并行替代方案。两种实现需要同时存在一段时间以保证兼容性，因此需要测试套件能够同时覆盖旧实现与 FLIP-27 实现的行为，避免新实现出现回归问题。

本提交之前，Flink 表 source 相关的测试（`TestFlinkTableSource`、`TestFlinkSourceConfig`）仅针对默认（旧）的 source 实现运行一次，FLIP-27 路径没有覆盖。这样新的 source 实现即使存在 bug 也难以通过 CI 暴露出来。本提交的目标就是把这一组测试参数化，使每个测试方法在 `useFlip27Source = false` 与 `useFlip27Source = true` 两种配置下各运行一次，从而保证两种实现的语义对齐。

由于 Iceberg 同时维护 Flink 1.17、1.18、1.19 三个版本的子模块，本次改动同步在三套目录中落地，逻辑一致。

## 如何达成设计目的

设计思路是把原本散落在 `TestFlinkTableSource` 中的环境搭建代码（建 catalog/database/table、注册 scan 事件监听器、清理逻辑）抽取到新的抽象基类 `TableSourceTestBase` 中，并在该基类上使用 Iceberg 自带的 `ParameterizedTestExtension` 进行参数化，参数为 `useFlip27Source` 布尔值。基类的 `getTableEnv()` 重写会根据该参数把 `TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE` 配置写入 TableEnvironment。

由于参数化的测试方法需要被 `ParameterizedTestExtension` 多次调用，原 `@Test` 注解被替换为 `@TestTemplate`，这样 JUnit 5 会基于参数集合多次执行同一方法。对于 FLIP-27 暂未实现的能力（例如 limit pushdown），通过 `Assumptions.assumeThat` 跳过对应的参数化分支，避免误报。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TableSourceTestBase.java`（以及 1.17、1.18 同名新增文件）

**修改目的**：抽出表 source 测试的公共基类，承载参数化逻辑与环境搭建/清理。

**工作逻辑**：

- 类标注 `@ExtendWith(ParameterizedTestExtension.class)`，使其支持参数化执行；
- 通过 `@Parameters(name = "useFlip27Source = {0}")` 提供参数集 `{{false}, {true}}`，每个测试方法都会以两套参数各跑一次；
- `@Parameter(index = 0) protected boolean useFlip27Source` 字段在运行时被注入当前参数值；
- `getTableEnv()` 重写：除保留原 `DEFAULT_PARALLELISM = 1` 外，新增 `setBoolean(FlinkConfigOptions.TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE.key(), useFlip27Source)`，决定本次测试走哪条 source 路径；
- `@BeforeEach before()`：注册 `ScanEvent` 监听器以验证 pushdown 行为，建临时 warehouse，创建 catalog/database/table 并插入测试数据 `(1,'iceberg',10),(2,'b',20),(3,NULL,30)`，重置 `scanEventCount` 与 `lastScanEvent`；
- `@AfterEach clean()`：按顺序 drop table/database/catalog，确保测试间隔离；
- 字段 `CATALOG_NAME`、`DATABASE_NAME`、`TABLE_NAME`、`format`、`scanEventCount`、`lastScanEvent` 由原 `TestFlinkTableSource` 迁移而来，并改为 `protected` 以便子类访问。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkTableSource.java`（以及 1.17、1.18 同名文件）

**修改目的**：将原本直接继承 `TestBase`、自管环境的测试类改为继承 `TableSourceTestBase`，并将所有 `@Test` 替换为 `@TestTemplate`，使其可被参数化运行。

**工作逻辑**：

- 删除原本内联的 `before()`、`clean()`、`getTableEnv()` 及相关字段、import；
- 类签名由 `extends TestBase` 改为 `extends TableSourceTestBase`；
- 约 30 个测试方法的注解由 `@Test` 统一替换为 `@TestTemplate`，包括 `testLimitPushDown`、`testNoFilterPushDown`、`testFilterPushDownEqual`、`testFilterPushDownIn`、`testFilterPushDownLike` 等，涵盖等值、范围、IN/NOT IN、IS NULL/IS NOT NULL、BETWEEN、LIKE、NOT 等场景；
- 测试方法体不变，原本依据 `lastScanEvent` 验证 pushdown 表达式的断言现在会在两种 source 实现下分别执行。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSourceConfig.java`（以及 1.17、1.18 同名文件）

**修改目的**：让 source 配置相关测试同样参数化，并处理 FLIP-27 尚未实现的能力差异。

**工作逻辑**：

- 类由 `extends TestFlinkTableSource` 改为 `extends TableSourceTestBase`，避免继承层级带来的参数化冲突；
- 三个方法注解由 `@Test` 改为 `@TestTemplate`；
- `testReadOptionHierarchy` 中新增 `assumeThat(useFlip27Source).isFalse();`，因为 FLIP-27 source 尚未实现 limit pushdown，在 FLIP-27 模式下跳过此用例；
- 引入 `import static org.assertj.core.api.Assumptions.assumeThat;`。

## 小结

- **成效**：Flink table source 测试套件现在对每个用例都会以新旧两种 source 实现各执行一次，对 FLIP-27 source 的回归保护显著增强；对 FLIP-27 暂未实现的 limit pushdown 用 `Assumptions` 显式跳过，避免误报。
- **影响范围**：仅测试代码，涉及 Flink 1.17/1.18/1.19 三套目录下相同的 3 个文件（共 9 个文件）；新增抽象基类 `TableSourceTestBase`，重写 `TestFlinkTableSource` 与 `TestFlinkSourceConfig` 的继承关系与测试注解，无生产代码改动。
- **回迁到 1.4.x 的注意事项**：本提交属于测试基础设施改造，**原则上可回迁**，但需要 1.4.x 分支上已经存在 `FlinkConfigOptions.TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE` 配置项以及 `ParameterizedTestExtension` 工具类，否则参数化与 FLIP-27 切换都无法生效。如果 1.4.x 分支尚无 FLIP-27 source 实现，则该参数化失去意义，建议不回迁或在确认 FLIP-27 source 已可用后再回迁；cherry-pick 时需对三个 Flink 版本目录同步处理。
