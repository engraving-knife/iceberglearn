# 提交 1087：Flink: backport PR #10777 from 1.19 to 1.18 for sink test refactoring. (#10965)

## 提交信息

- **序号**：1087 / 4088
- **哈希**：bf459eed480c79d42cbb97ab4461881d69bc5d91
- **短哈希**：bf459eed4
- **日期**：2024-08-22 13:11:16 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: backport PR #10777 from 1.19 to 1.18 for sink test refactoring. (#10965)
- **PR/Issue**：#10965（回迁 #10777）

## 总体目的

Flink Iceberg sink 相关的测试类（`TestFlinkTableSink`、`TestFlinkIcebergSink` 等）随功能增长变得庞大且耦合严重，且这些测试在 v1.19 上已经做过一次重构（#10777）：抽取公共 SQL 测试基类、拆分出 Extended 测试类、引入 `dropDatabase` 辅助以规避 Flink 限制。本提交把这次重构从 v1.19 回迁到 v1.18，使两个 Flink 版本的测试结构保持一致，便于后续同步与维护，也修复了 v1.18 上因 FLINK-33226（不能 drop 当前使用的 database）导致的测试清理问题。

重构后，公共 SQL 工具方法集中到新的 `SqlBase` 基类，`TestBase` 改为继承 `SqlBase`；大型测试类按"基础 + Extended"模式拆分，使每个类职责单一、便于并行与定位失败；并新增 `TestFlinkIcebergSinkDistributionMode`、`TestFlinkIcebergSinkExtended`、`TestFlinkTableSinkExtended` 等独立测试类承接拆出的用例。

## 如何达成设计目的

1. **抽取 `SqlBase`**：把原本散落在 `TestBase`/`CatalogTestBase` 的 SQL 执行与断言工具（`exec`、`sql`、`assertSameElements`、`toWithClause`、`dropCatalog`、`dropDatabase`）集中到新的抽象类 `SqlBase`，`TestBase` 改为 `extends SqlBase`，`CatalogTestBase` 删除重复的 `toWithClause`。
2. **引入 `DEFAULT_CATALOG_NAME` 常量**：在 `FlinkCatalogFactory` 暴露 `default_catalog` 常量，测试用它切换 catalog 以规避 FLINK-29677/FLINK-33226（不能 drop 当前使用的 catalog/database）。
3. **新增 `dropDatabase` 辅助**：在 `SqlBase`/`TestBase` 中实现 `dropDatabase`，先切到 default catalog 的 default database 再 drop，规避 FLINK-33226。
4. **拆分测试类**：从 `TestFlinkTableSink` 移出部分用例到新的 `TestFlinkTableSinkExtended`；从 `TestFlinkIcebergSink` 移出用例到 `TestFlinkIcebergSinkExtended` 与 `TestFlinkIcebergSinkDistributionMode`；`TestFlinkIcebergSinkBase` 增强公共能力。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalogFactory.java`

**修改目的**：暴露默认 catalog 名常量供测试使用。

**工作逻辑**：新增 `public static final String DEFAULT_CATALOG_NAME = "default_catalog";`，与已有的 `DEFAULT_DATABASE_NAME` 并列。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/SqlBase.java`（新增）

**修改目的**：提供公共 SQL 测试工具基类。

**工作逻辑**：抽象类，定义 `getTableEnv()` 抽象方法；提供 `exec(env, query, args)`/`exec(query, args)` 执行 SQL；`sql(query, args)` 收集结果为 `List<Row>`；`assertSameElements` 断言无序相等；`dropCatalog`（先 `USE CATALOG default_catalog` 再 drop，规避 FLINK-29677）；`dropDatabase`（切到 default catalog 的第一个 database 再 drop，规避 FLINK-33226）；`toWithClause`（把 props map 转成 SQL with 子句字符串）。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestBase.java`

**修改目的**：改为继承 `SqlBase`，移除重复工具方法。

**工作逻辑**：`extends TestBaseUtils` 改为 `extends SqlBase`；移除 `TestBaseUtils` import；`getTableEnv()` 加 `@Override`；`dropCatalog` 中硬编码的 `"default_catalog"` 改为 `DEFAULT_CATALOG_NAME` 常量；新增 `dropDatabase` 方法（与 `SqlBase` 一致，保留在 TestBase 供已有子类使用）。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/CatalogTestBase.java`

**修改目的**：移除已迁移到 `SqlBase` 的重复方法。

**工作逻辑**：删除 `toWithClause(Map)` 静态方法（已移至 `SqlBase`）。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSink.java`

**修改目的**：精简基础测试类，移出部分用例。

**工作逻辑**：删除大量 import（不再用到的 `Transformation`、`TableEnvironmentImpl`、`PlannerBase`、`DataFile`、`DistributionMode` 等）；`clean()` 中 `DROP DATABASE` 改为 `dropDatabase(flinkDatabase, true)`；删除 `testWriteParallelism` 用例（移到 Extended）；删除尾部一些辅助方法（移到 Extended）。整体减少约 116 行。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSinkExtended.java`（新增）

**修改目的**：承接从 `TestFlinkTableSink` 拆出的扩展用例。

**工作逻辑**：336 行，包含被移出的 `testWriteParallelism` 等扩展场景测试。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestIcebergConnector.java`

**修改目的**：适配 `SqlBase` 抽取后的工具方法。

**工作逻辑**：调整对 `toWithClause` 等方法的调用方式以匹配新基类。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSink.java`

**修改目的**：精简基础 sink 测试类。

**工作逻辑**：移出大量用例与辅助方法（约 270 行减少），保留核心写入验证。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkBase.java`

**修改目的**：增强公共 sink 测试基类能力。

**工作逻辑**：补充被拆出测试类共享的辅助方法，约 51 行调整。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java`（新增）

**修改目的**：独立承接分布模式相关测试。

**工作逻辑**：180 行，覆盖 sink 的 distribution mode（none/hash/range）行为。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkExtended.java`（新增）

**修改目的**：承接从 `TestFlinkIcebergSink` 拆出的扩展用例。

**工作逻辑**：208 行，包含扩展写入场景。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSpeculativeExecutionSupport.java`

**修改目的**：适配重构后的基类。

**工作逻辑**：小调整（2 行），适配新的继承结构。

## 小结

- **成效**：把 v1.19 上的 sink 测试重构回迁到 v1.18，统一两个版本的测试结构；抽取 `SqlBase` 公共基类减少重复；拆分大型测试类为基础 + Extended，提升可维护性；新增 `dropDatabase` 规避 FLINK-33226，修复测试清理问题。
- **影响范围**：仅 `flink/v1.18` 模块测试代码（12 个文件），新增 4 个测试类，改 1 个主代码文件（仅加常量）。无产品功能变更。
- **回迁到 1.4.x 的注意事项**：不建议回迁。这是测试重构，1.4.x 的 Flink 测试结构与 v1.18/v1.19 不同（1.4.x 对应更早的 Flink 版本），强行回迁意义不大且可能与 1.4.x 既有测试冲突。`DEFAULT_CATALOG_NAME` 常量若 1.4.x 缺失可单独补，但整体测试重构不宜回迁。1.4.x 若存在同样的 FLINK-33226 清理问题，可仅回迁 `dropDatabase` 思路。
