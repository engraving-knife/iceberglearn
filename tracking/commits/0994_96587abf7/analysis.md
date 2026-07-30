# 提交 0994：Flink: Remove MiniClusterResource (#10817)

## 提交信息

- **序号**：0994 / 4088
- **哈希**：96587abf7fbbeb9c728d60cbdc7bdd7e2096dad5
- **短哈希**：96587abf7
- **日期**：2024-07-30（Wed Jul 31 00:18:39 2024 +0900）
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Flink: Remove MiniClusterResource (#10817)
- **PR/Issue**：#10817

## 总体目的

Iceberg 的 Flink 集成测试依赖一个 MiniCluster（迷你 Flink 集群）来运行集成测试。此前仓库中存在两个功能高度重叠的工具类：

1. `org.apache.iceberg.flink.MiniFlinkClusterExtension`——基于 JUnit 5 `@RegisterExtension` 的标准扩展，提供 `createWithClassloaderCheckDisabled()` 等工厂方法，并持有 `DISABLE_CLASSLOADER_CHECK_CONFIG` 常量；
2. `org.apache.iceberg.flink.MiniClusterResource`——一个旧的包装类，内部其实也是调用 Flink 的 `MiniClusterWithClientResource`（JUnit 4 风格），提供 `createWithClassloaderCheckDisabled()` 工厂方法与 `DISABLE_CLASSLOADER_CHECK_CONFIG` 常量。

两者职责几乎完全相同，只是命名与底层 API 略有差异。同时存在这两个类会导致测试代码风格不统一：部分测试引用 `MiniClusterResource`，部分引用 `MiniFlinkClusterExtension`，维护者需要同时理解两套等价的工具。此外，`MiniClusterWithClientResource` 是 JUnit 4 风格的资源，而项目已全面迁移到 JUnit 5，继续保留旧包装类会阻碍统一的测试基础设施演进。

本提交的目的就是删除冗余的 `MiniClusterResource` 类，把所有引用它的测试统一改为使用 `MiniFlinkClusterExtension`，从而消除重复、统一测试基础设施。

## 如何达成设计目的

实现方式为机械式的"删除 + 替换"重构：

1. 删除三个 Flink 模块（v1.19/v1.20/v1.21，对应文件列表中的三份）下的 `MiniClusterResource.java`；
2. 把所有测试中对 `MiniClusterResource` 的引用替换为 `MiniFlinkClusterExtension`，包括：
   - `MiniClusterResource.DISABLE_CLASSLOADER_CHECK_CONFIG` → `MiniFlinkClusterExtension.DISABLE_CLASSLOADER_CHECK_CONFIG`；
   - import 语句相应调整；
   - 部分测试中字段命名规范化，如把 `miniClusterResource` 重命名为 `miniClusterExtension` 或 `MINI_CLUSTER_EXTENSION`（大写常量风格），保持与 JUnit 5 `@RegisterExtension` 惯例一致。

由于两个类对外暴露的方法/常量语义一致，替换后测试行为不变，属于纯重构，无功能变化。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/MiniClusterResource.java`（删除，v1.20、v1.21 同）

**修改目的**：删除冗余的旧包装类。

**工作逻辑**：被删除的类提供 `createWithClassloaderCheckDisabled()`（含一个带 `InMemoryReporter` 的重载）和 `DISABLE_CLASSLOADER_CHECK_CONFIG` 常量，功能与 `MiniFlinkClusterExtension` 完全重叠。删除后由 `MiniFlinkClusterExtension` 统一承担。

### 各测试文件（v1.19/v1.20/v1.21 三个模块下的 `TestBase.java`、`TestFlinkIcebergSink.java`、`TestFlinkIcebergSinkV2.java`、`TestFlinkIcebergSinkBranch.java`、`TestFlinkIcebergSinkV2Branch.java`、`TestBucketPartitionerFlinkIcebergSink.java`、`ChangeLogTableTestBase.java`、`TestStreamScanSql.java`、`TestFlinkTableSink.java`、`TestFlinkUpsert.java`、`TestIcebergConnector.java`、`TestFlinkScan.java`、`TestIcebergSourceWithWatermarkExtractor.java`、`TestSqlBase.java`、`TestIcebergSourceContinuous.java`（v1.20/v1.21）、`OperatorTestBase.java`（v1.20/v1.21）、`TestIcebergSpeculativeExecutionSupport.java`（v1.21）等）

**修改目的**：把对 `MiniClusterResource` 的引用统一替换为 `MiniFlinkClusterExtension`。

**工作逻辑**：
- 移除 `import org.apache.iceberg.flink.MiniClusterResource;`，新增/保留 `import org.apache.iceberg.flink.MiniFlinkClusterExtension;`；
- `MiniClusterResource.DISABLE_CLASSLOADER_CHECK_CONFIG` → `MiniFlinkClusterExtension.DISABLE_CLASSLOADER_CHECK_CONFIG`；
- 字段声明 `public static MiniClusterExtension miniClusterResource = MiniFlinkClusterExtension.createWithClassloaderCheckDisabled();` 在 `TestBase` 等处改名为 `miniClusterExtension`；在 `TestFlinkIcebergSink` 等处改为 `public static final MiniClusterExtension MINI_CLUSTER_EXTENSION = ...`（常量命名风格）；
- 调用处（如 `StreamExecutionEnvironment.getExecutionEnvironment(MiniClusterResource.DISABLE_CLASSLOADER_CHECK_CONFIG)`）相应改为引用 `MiniFlinkClusterExtension`。

整体属于无行为变化的引用替换，三份 Flink 模块（v1.19/v1.20/v1.21）改动模式一致，仅在 v1.20/v1.21 多出几个该版本独有的测试类需同步处理。

## 小结

- **成效**：消除了 `MiniClusterResource` 与 `MiniFlinkClusterExtension` 的重复，统一了 Flink 集成测试的 MiniCluster 基础设施，所有测试统一使用 JUnit 5 风格的 `MiniFlinkClusterExtension`，并顺带规范了字段命名。
- **影响范围**：纯测试代码重构，涉及三个 Flink 模块（v1.19/v1.20/v1.21）共 48 个文件，删除 3 个 `MiniClusterResource.java`，净减少 195 行（70 insertions, 265 deletions）。无主代码、无功能变更。
- **回迁到 1.4.x 的注意事项**：这是测试基础设施重构，不影响产品功能，是否回迁移取决于 1.4.x 分支是否希望统一测试风格。1.4.x 分支对应的 Flink 模块版本通常为 1.17/1.18/1.19，与 main 的 v1.19/v1.20/v1.21 不完全对应，回迁时需核对 1.4.x 实际存在的模块与文件集合，不能盲目照搬文件列表。风险极低，但价值也有限，可作为可选清理。注意 1.4.x 若已存在 `MiniClusterResource` 与 `MiniFlinkClusterExtension` 并存的情况，回迁前需确认 `MiniFlinkClusterExtension` 已具备等价能力。
