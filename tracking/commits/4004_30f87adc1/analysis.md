# 提交 4004：Revert "Core: Test geometry and geography metrics keep counts without bounds (#17131)" (#17141)

## 提交信息

- **序号**：4004 / 4088
- **哈希**：30f87adc15408f798dfe3f087a6a74f0f69321ab
- **短哈希**：30f87adc1
- **日期**：2026-07-09 10:53:23 +0200
- **作者**：pvary
- **提交说明**：Revert "Core: Test geometry and geography metrics keep counts without bounds (#17131)" (#17141)
- **PR/Issue**：#17141

## 总体目的

本提交是 revert 操作，撤销提交 4002（dd35b7840，"Core: Test geometry and geography metrics keep counts without bounds (#17131)"），将 `TestMetrics.java` 中新增的 46 行测试代码删除。

revert 的原因是：提交 4002 引入的测试使用了 `MetricsWithStats` 和 `MetricsUtil.fromMetrics` API，但这些 API 在更早合并的提交 3996（"Parquet: Refactor ParquetMetrics to produce field stats"）中已被移除。由于合并顺序或分支状态问题，4002 的代码与 3996 的重构冲突，导致测试无法编译/运行。因此先 revert 4002，随后在 4010 以适配新 API 的形式重新引入相同的测试。

## 如何达成设计目的

通过 `git revert` 撤销 4002 的全部改动，即删除 `testMetricsForGeospatialTypes` 测试方法和 `wkbPoint` 辅助方法，恢复 `TestMetrics.java` 到 4002 之前的状态。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestMetrics.java` (+0/-46 lines)

**修改目的**：撤销 4002 新增的地理空间类型 metrics 测试。

**工作逻辑**：
删除以下内容：
- `import java.nio.ByteOrder;`（4002 新增）
- `testMetricsForGeospatialTypes` 测试方法（验证 geometry/geography 保留 counts 但无 bounds）
- `wkbPoint(double xCoord, double yCoord)` 辅助方法（生成小端 WKB Point）

文件恢复到 3996 重构后的状态，不再包含使用已移除 `MetricsWithStats`/`MetricsUtil.fromMetrics` 的代码。

## 总结

这是一次因 API 不兼容导致的临时 revert。4002 的测试逻辑本身是正确的，但其依赖的 `MetricsWithStats`/`MetricsUtil.fromMetrics` 已在 3996 重构中被删除。为解除编译冲突，先 revert 该测试，随后在 4010 以适配新 metrics API 的方式重新引入。体现了多 PR 并行开发时合并顺序带来的兼容性管理。
