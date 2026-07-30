# 提交 1012：Flink: refactor sink tests to reduce the number of combinations with parameterized tests (#10777)

## 提交信息

- **序号**：1012 / 4088
- **哈希**：dc7ad7190989d50f3288ea02eb26d527c9f629c6
- **短哈希**：dc7ad7190
- **日期**：2024-08-02 08:39:10 -0700
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: refactor sink tests to reduce the number of combinations with parameterized tests (#10777)
- **PR/Issue**：#10777

## 总体目的

Iceberg 的 Flink 集成测试中，`TestFlinkIcebergSink` 和 `TestFlinkTableSink` 这两个测试类使用了 `@ExtendWith(ParameterizedTestExtension.class)` 进行参数化测试。`TestFlinkIcebergSink` 的参数化维度包含 `FileFormat`（Avro/Orc/Parquet 三选一）、`parallelism`（1 或 2）、`partitioned`（true/false），共 3×2×2=12 个组合；`TestFlinkTableSink` 的参数化维度更广（catalog 类型、命名空间、文件格式、流/批等），单测方法达到约 21 个组合。

这导致每个 `@TestTemplate` 方法实际被运行 12 次或 21 次，CI 上单类耗时显著，并且很多组合对于某些测试方法而言是无意义的重复——例如"分布模式"测试与文件格式无关，无论 Avro/Orc/Parquet，分布行为都由 Iceberg 的写入器在文件层之上控制，结果不会因 format 不同而不同。本提交的目标是通过拆分测试类、缩小参数化维度的方式，把那些与文件格式等无关的测试从昂贵的笛卡尔积中剥离出来，从而在不损失覆盖面的前提下大幅缩短 Flink sink 测试的整体运行时间，缓解 CI 压力。

## 如何达成设计目的

整体思路是"按测试关心维度拆分测试类"：保留原 `TestFlinkIcebergSink` / `TestFlinkTableSink` 作为"核心、需要全组合覆盖"的测试类（仍然走 12/21 组合），把那些只需要更少维度的测试方法移到新的测试类中，新测试类只对其关心的维度做参数化，其它维度固定为一个常量（例如统一用 Parquet）。

具体做法：

1. 抽取公共基类 `TestFlinkIcebergSinkBase`，把 `MiniClusterExtension`、`HadoopCatalogExtension`、`tableLoader/table/env` 字段、`createBoundedSource/createRows/convertToRowData` 等通用方法以及新提取的 `testWriteRow(writerParallelism, tableSchema, distributionMode)` 和 `partitionFiles(partition)` 通用辅助方法上提到基类，避免每个测试类重复声明扩展点和工具方法。
2. 新增 `TestFlinkIcebergSinkDistributionMode`，专门承载与分布模式相关的测试（如 `testJobNoneDistributeMode`、`testJobNullDistributionMode`、`testPartitionWriteMode`、`testShuffleByPartitionWithSchema`、`testOverrideWriteConfigWithUnknownDistributionMode`）。该类固定 `format = FileFormat.PARQUET`，参数化维度只剩 `parallelism` × `partitioned`，共 2×2=4 个组合，相比原 12 个组合减少 2/3。
3. 新增 `TestFlinkIcebergSinkExtended`，承载与分布模式无关的"扩展"测试（如 `testTwoSinksInDisjointedDAG`、`testOverrideWriteConfigWithUnknownFileFormat`、`testWriteRowWithTableRefreshInterval` 等）。该类不使用参数化（`@Test`），固定 `partitioned=true`、`parallelism=2`、`format=PARQUET`，每个方法只跑 1 次。
4. 对 SQL 表 sink 做类似拆分：新增 `SqlBase` 抽象类，把 SQL 工具方法（`exec`、`sql`、`assertSameElements`、`dropCatalog`、`dropDatabase`、`toWithClause`）从 `TestBase`/`CatalogTestBase` 中提取出来；`TestBase` 改为继承 `SqlBase`（不再继承 Flink 的 `TestBaseUtils`）。把 `TestFlinkTableSink` 中开销大、与多维度参数化无关的 `testWriteParallelism` 和 `testHashDistributeMode` 移到新的 `TestFlinkTableSinkExtended`，后者只对 `isStreamingJob` 做 2 组合参数化，避免在 21 组合上重复。
5. `TestFlinkIcebergSink` 中 `testWriteRow` 的私有重载被改为调用基类统一的 `testWriteRow(int, TableSchema, DistributionMode)`，传入 `parallelism` 作为写入并行度（之前私有版本隐式使用成员 `parallelism`，提取到基类后改为显式传参）。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkBase.java`

**修改目的**：把多个 Flink sink 测试类共享的环境/工具上提到基类，消除重复代码，并为新拆出的测试类提供统一入口。

**工作逻辑**：
- 新增 `@RegisterExtension` 的 `miniClusterResource`（`MiniClusterExtension`）和 `CATAL_EXTENSION`（`HadoopCatalogExtension`），原本分别散落在 `TestFlinkIcebergSink`、`TestFlinkIcebergSinkDistributionMode` 等子类中。子类若需要各自实例可以重写，但基类提供默认实现。
- 新增 `protected TableLoader tableLoader;` 字段。
- 新增 `protected void testWriteRow(int writerParallelism, TableSchema tableSchema, DistributionMode distributionMode)`：构造 `BoundedTestSource` 数据流，调用 `FlinkSink.forRow(...)` 链式 builder 指定 `table`/`tableLoader`/`tableSchema`/`writeParallelism`/`distributionMode`，然后 `env.execute()` 并断言写入的行与输入一致。注意 `writeParallelism` 现在是显式参数，与子类 `parallelism` 解耦，便于不同测试场景控制。
- 新增 `protected int partitionFiles(String partition)`：返回指定分区下数据文件数量，供分布模式断言使用。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSink.java`

**修改目的**：把与"分布模式/扩展特性"无关的核心写入测试保留在此类，仍走 12 组合；移除已迁移到新类的测试方法和重复字段。

**工作逻辑**：
- 删除本类中 `MiniClusterExtension`、`HadoopCatalogExtension`、`tableLoader` 的声明（已上提到基类）。
- 删除本类的私有 `testWriteRow(TableSchema, DistributionMode)` 与 `partitionFiles(String)`（已上提到基类且签名调整）。
- `testWriteRow()` 改为调用 `testWriteRow(parallelism, null, DistributionMode.NONE)`；`testWriteRowWithTableSchema()` 改为 `testWriteRow(parallelism, SimpleDataUtil.FLINK_SCHEMA, DistributionMode.NONE)`。
- 删除 `testJobNoneDistributeMode`、`testJobHashDistributionMode`、`testJobNullDistributionMode`、`testPartitionWriteMode`、`testShuffleByPartitionWithSchema`、`testTwoSinksInDisjointedDAG`、`testOverrideWriteConfigWithUnknownDistributionMode`、`testOverrideWriteConfigWithUnknownFileFormat`、`testWriteRowWithTableRefreshInterval` 等方法（迁移至 `TestFlinkIcebergSinkDistributionMode` 和 `TestFlinkIcebergSinkExtended`）。
- `before()` 中给字段赋值改为 `this.table = ...`、`this.env = ...`、`this.tableLoader = ...`，明确写入基类字段。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java`（新增）

**修改目的**：承载与分布模式相关的测试，参数化维度收窄到 `parallelism × partitioned`（4 组合），文件格式固定为 Parquet。

**工作逻辑**：类注释明确说明"测试分布模式无需遍历 Avro/Orc/Parquet 三种格式，把文件格式维度去掉后组合数从 12 降到 4，缩短测试时间"。`@Parameters` 提供 `{1,true},{1,false},{2,true},{2,false}` 四组。`format` 字段为 `final FileFormat.PARQUET`。包含从 `TestFlinkIcebergSink` 迁入的 `testShuffleByPartitionWithSchema`、`testJobNoneDistributeMode`、`testJobNullDistributionMode`、`testPartitionWriteMode`、`testOverrideWriteConfigWithUnknownDistributionMode`，方法体基本不变，但调用 `testWriteRow(parallelism, ...)` 显式传入并行度。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkExtended.java`（新增）

**修改目的**：承载与分布模式无关但开销较大的"扩展"测试，不参与参数化，固定单一配置，避免在 12 组合上重复运行。

**工作逻辑**：类注释说明"原 `TestFlinkIcebergSink` 每方法 12 组合昂贵且慢"。类中 `partitioned=true`、`parallelism=2`、`format=PARQUET` 均为 `final` 常量，使用普通 `@Test` 而非 `@TestTemplate`。包含从 `TestFlinkIcebergSink` 迁入的 `testTwoSinksInDisjointedDAG`（验证同一作业内向 left/right 两张表写、各自 snapshot summary 与 uidPrefix 行为）、`testOverrideWriteConfigWithUnknownFileFormat`、`testWriteRowWithTableRefreshInterval` 等。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/SqlBase.java`（新增）

**修改目的**：把 Flink SQL 测试中通用的 `exec`、`sql`、`assertSameElements`、`dropCatalog`、`dropDatabase`、`toWithClause` 等工具方法抽到独立抽象基类，供 `TestBase` 和新增的 `TestFlinkTableSinkExtended` 复用。

**工作逻辑**：
- 声明 `protected abstract TableEnvironment getTableEnv();`，子类必须提供。
- `exec(env, query, args)` 通过 `String.format` 拼接 SQL 并执行。
- `sql(query, args)` 调用 `exec` 并把 `TableResult.collect()` 的迭代结果收集为 `List<Row>`。
- `dropCatalog` / `dropDatabase` 处理 Flink 在 FLINK-29677/FLINK-33226 之后不能删除"当前在用"catalog/database 的限制，先切换到 `default_catalog`/默认 database 再删除。
- `toWithClause(Map)` 从原 `CatalogTestBase` 搬过来，把 Map 拼成 `('k1'='v1','k2'='v2')` 形式的 WITH 子句。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestBase.java`

**修改目的**：让 `TestBase` 改为继承新的 `SqlBase`，移除对 Flink `TestBaseUtils` 的继承；为 `getTableEnv()` 加 `@Override`。

**工作逻辑**：原 `public abstract class TestBase extends TestBaseUtils` 改为 `extends SqlBase`，并删除 `import org.apache.flink.test.util.TestBaseUtils;`。`getTableEnv()` 加 `@Override` 注解，因为 `SqlBase` 声明了同签名的抽象方法。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/CatalogTestBase.java`

**修改目的**：移除 `toWithClause` 静态方法（已迁移到 `SqlBase` 作为实例方法），避免重复实现。

**工作逻辑**：删除整段 `static String toWithClause(Map<String, String> props)` 方法（22 行），相应的 `import java.util.Map;` 在文件其他地方仍可能使用，故未删除 import。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSink.java`

**修改目的**：把开销大、与多维参数化无关的 `testWriteParallelism` 与 `testHashDistributeMode` 迁出到新类 `TestFlinkTableSinkExtended`，减小原类测试方法在 21 组合上的开销。

**工作逻辑**：删除 `testWriteParallelism`（约 33 行，验证 `INSERT INTO ... /*+ OPTIONS('write-parallelism'='1') */` 时 writer/committer/dummySink 各 Transformation 的并行度）和 `testHashDistributeMode`（约 60 行，验证 HASH 分布模式下每个分区每快照只有 1 个数据文件）。同步移除相关 import（`Transformation`、`TableEnvironmentImpl`、`ModifyOperation`、`PlannerBase`、`DataFile`、`DistributionMode`、`TableProperties`、`ImmutableList`、`ImmutableMap`、`Maps`、`Collections`、`Collectors`、`IntStream` 等，因已不再被本类使用）。删除 `import static org.assertj.core.api.Assertions.assertThat;`（本类剩余方法未用）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSinkExtended.java`（新增）

**修改目的**：承载从 `TestFlinkTableSink` 迁出的 `testWriteParallelism` 和 `testHashDistributeMode`，并对 `isStreamingJob` 做 2 组合参数化（流/批），不再走原 21 组合。

**工作逻辑**：类注释说明"在 `TestFlinkTableSink` 中每个方法跑 21 组合太昂贵且慢"。该类直接继承 `SqlBase`（不再继承 `CatalogTestBase`，自建一个 `HadoopCatalog` 实例），通过 `@Parameters(name = "isStreamingJob={0}")` 提供流/批两个组合，固定使用 `FileFormat.PARQUET`。`before()` 创建 warehouse、注册 Hadoop 类型的 catalog、创建库和表；`clean()` 释放资源。`testWriteParallelism` 和 `testHashDistributeMode` 的方法体与原 `TestFlinkTableSink` 中一致，只是参数化维度收窄。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestIcebergConnector.java`

**修改目的**：移除本类私有的 `toWithClause` 包装方法（原委托给 `CatalogTestBase.toWithClause`），改为直接调用 `SqlBase` 继承来的实例方法。

**工作逻辑**：删除 `private String toWithClause(Map<String, String> props) { return CatalogTestBase.toWithClause(props); }` 这 3 行（含空行）。`TestIcebergConnector extends TestBase`，而 `TestBase` 现在继承 `SqlBase`，所以类内对 `toWithClause(...)` 的调用会自动走 `SqlBase` 的实例方法，无需修改调用点。

## 小结

- **成效**：把 Flink sink/table sink 测试按"关心的维度"重新拆分，分布模式相关测试组合数从 12 降到 4（减少约 67%），扩展特性测试从 12 降到 1，`TestFlinkTableSink` 中两个高开销测试从 21 组合降到 2 组合。在保持测试覆盖面的前提下显著缩短 CI 运行时间。同时通过抽取 `TestFlinkIcebergSinkBase` 和 `SqlBase` 消除了多份重复的扩展点/工具方法代码。
- **影响范围**：仅影响 `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/` 下的测试代码（10 个文件，798 增 / 409 删）。不涉及任何生产代码、API 或构建脚本，纯测试重构。
- **回迁到 1.4.x 的注意事项**：本提交是纯测试重构，不修改产品代码逻辑，回迁风险极低。但 1.4.x 分支的 Flink 集成通常只维护单个 Flink 版本（如 v1.18 或 v1.19），而本提交针对的是 main 分支 v1.19 目录结构；如果 1.4.x 分支的目录结构或测试基础设施与本提交假设不一致（例如 `ParameterizedTestExtension`、`MiniClusterExtension` 的可用性、`TestBaseUtils` 的继承链等），cherry-pick 后需要相应调整路径和依赖。另外，1.4.x 若已经存在功能等价的测试拆分或工具类，需避免冲突。整体而言适合回迁，但需结合 1.4.x 实际 Flink 版本目录与测试基类现状做适配。
