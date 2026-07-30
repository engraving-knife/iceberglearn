# 提交 3227：Core, Spark 4.1: Fix distributed planning for CoW operations (#15246)

## 提交信息

- **序号**：3227 / 4088
- **哈希**：854e3b2c88c3390274a1da5a25f87419cd846d39
- **短哈希**：854e3b2c8
- **日期**：2026-02-09
- **作者**：Anton Okolnychyi
- **提交说明**：Core, Spark 4.1: Fix distributed planning for CoW operations (#15246)
- **PR/Issue**：#15246

## 总体目的

本提交修复了 Spark 4.1 中分布式规划（distributed planning）模式下 Copy-on-Write（CoW）操作的若干缺陷。在 Iceberg 的分布式规划中，表扫描的规划（planning）不在每个 Spark executor 上重复执行，而是在 driver 端集中完成后将任务分发给 executor。CoW 操作在更新/删除数据时会重写数据文件，而非生成单独的删除文件（这是 MoR 模式的做法）。

问题根源在于三处代码路径未正确处理分布式规划场景：首先，`BaseDistributedDataScan` 在构建扫描器时未传播 `ignoreResiduals()` 标志，导致残留过滤器（residual filters）在分布式规划中被错误保留，可能产生不正确的结果或性能下降；其次，`SparkDistributedDataScan.ReadDeleteManifest` 在读取删除清单时始终使用行过滤器（row filter），即使当 residuals 应被忽略时也不例外，导致删除清单被错误地过滤；第三，`SparkScanBuilder.buildCopyOnWriteScan()` 直接调用 `table.newBatchScan()` 而非使用内部的 `newBatchScan()` 辅助方法，后者会根据表类型和配置决定是否使用分布式扫描（`SparkDistributedDataScan`），导致 CoW 扫描绕过了分布式规划路径，与 MoR 扫描的行为不一致。

此外，测试中验证 CoW 扫描的断言假设了固定的嵌套层级结构，但分布式规划模式下扫描对象的嵌套层级与本地模式不同，需要适配两种模式。

## 如何达成设计目的

整体思路是确保 CoW 操作在分布式规划模式下与 MoR 操作保持一致的行为：在 `BaseDistributedDataScan` 中传播 `ignoreResiduals` 标志；在 `SparkDistributedDataScan` 中根据 `ignoreResiduals` 条件性地将删除清单的过滤器设为 `alwaysTrue()`；将 `buildCopyOnWriteScan()` 中的 `table.newBatchScan()` 替换为 `newBatchScan()` 辅助方法；并修改测试以适配 LOCAL 和 DISTRIBUTED 两种规划模式的断言结构。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseDistributedDataScan.java` (+4/-0 lines)

**修改目的**：在分布式扫描构建中传播 `ignoreResiduals` 标志。

**工作逻辑**：
在 `BaseDistributedDataScan` 的扫描构建方法中，新增了对 `shouldIgnoreResiduals()` 的检查。当该条件为真时，调用 `builder.ignoreResiduals()` 将残留过滤器忽略标志传递给底层扫描器。residuals 是指无法下推到数据源进行过滤的剩余表达式部分，在分布式规划场景下，这些残留表达式不应被保留在扫描层面，而应在 Spark 层统一处理。此修改确保分布式扫描与本地扫描在 residuals 处理上保持一致。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/SparkDistributedDataScan.java` (+3/-1 lines)

**修改目的**：修正 `ReadDeleteManifest` 在忽略 residuals 时的过滤行为。

**工作逻辑**：
`ReadDeleteManifest` 构造器中，原来直接使用 `context.rowFilter()` 作为删除清单的过滤条件。修改后，当 `context.ignoreResiduals()` 为真时，将过滤器设为 `Expressions.alwaysTrue()`，即不加过滤条件地读取所有删除清单条目。这是因为当 residuals 被忽略时，行级过滤器属于 residual 范畴，不应在删除清单读取阶段被应用。新增了 `Expressions` 的导入以支持 `alwaysTrue()` 表达式。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+2/-3 lines)

**修改目的**：使 CoW 扫描使用正确的扫描构建方法。

**工作逻辑**：
将 `buildCopyOnWriteScan()` 中的 `table.newBatchScan()` 替换为 `newBatchScan()`。`newBatchScan()` 是 `SparkScanBuilder` 的私有辅助方法，它会检查表是否实现 `RequiresRemoteScanPlanning` 接口、是否为 `BaseTable` 且分布式规划已启用，据此决定返回 `table.newBatchScan()` 还是 `SparkDistributedDataScan` 实例。原代码直接调用 `table.newBatchScan()` 绕过了这一逻辑，导致 CoW 操作始终使用非分布式扫描，与 MoR 操作的行为不一致。此修改确保 CoW 扫描也遵循分布式规划配置。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestFilteredScan.java` (+6/-6 lines)

**修改目的**：适配 LOCAL 和 DISTRIBUTED 两种规划模式下的测试断言结构。

**工作逻辑**：
原测试验证 CoW 扫描的 `minRowsRequested` 时，固定使用两层 `.extracting("scan")` 链式调用，这对应本地规划模式下扫描对象的嵌套结构。修改后，测试先提取一层 `"scan"`，然后在 `LOCAL` 规划模式下再提取第二层 `"scan"`，最终统一从 `context` 中提取 `minRowsRequested`。这样使得测试在两种规划模式下都能正确验证 CoW 扫描请求的行数限制。

## 总结

本提交系统性地修复了 Spark 4.1 中 CoW 操作在分布式规划模式下的三个缺陷：residuals 标志未传播、删除清单过滤不当、CoW 扫描绕过分布式规划路径。这些修复确保 CoW 与 MoR 在分布式规划下行为一致，提升了查询正确性和与 Spark 引擎的兼容性。
