# 提交 3388：Core: Rename MAX_ATTEMPTS to MAX_RETRIES in RESTTableScan (#15627)

## 提交信息

- **序号**：3388 / 4088
- **哈希**：a50c2d920aadbb79f978bac10d1271bc4aa63a1a
- **短哈希**：a50c2d920
- **日期**：2026-03-13 15:53:03 -0700
- **作者**：Prashant Singh
- **提交说明**：Core: Rename MAX_ATTEMPTS to MAX_RETRIES in RESTTableScan (#15627)
- **PR/Issue**：#15627

## 总体目的

将 `RESTTableScan` 中的常量 `MAX_ATTEMPTS` 重命名为 `MAX_RETRIES`，使命名更准确地反映其在 `Tasks.retry(...)` 上下文中的语义——它表示的是最大重试次数，而非总的尝试次数。同时更新注释从 "Max number of poll checks" 改为 "Max number of poll retries"。

背景：在前一个提交（#15613）中，`fetchPlanningResult()` 从 Failsafe 迁移到 Iceberg 的 `Tasks` 工具。Failsafe 中 `withMaxAttempts` 表示总尝试次数（含首次），而 `Tasks.retry(n)` 表示重试次数。原常量名 `MAX_ATTEMPTS` 沿用自 Failsafe 时代，迁移后语义已发生变化，重命名可避免后续维护者产生误解。

## 如何达成设计目的

直接重命名常量定义并更新唯一的使用点。改动范围极小：常量声明处和 `Tasks.foreach(...).retry(MAX_RETRIES)` 调用处各一处。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+2/-2 lines)

**修改目的**：常量重命名，使语义与 `Tasks.retry()` 的实际含义一致。

**工作逻辑**：
- 常量声明：`MAX_ATTEMPTS = 10` → `MAX_RETRIES = 10`，注释从 "Max number of poll checks" 改为 "Max number of poll retries"。
- 使用点：`.retry(MAX_ATTEMPTS)` → `.retry(MAX_RETRIES)`。

## 总结

这是一次小范围、低风险的重命名重构，目的是在 Failsafe→Tasks 迁移后修正常量命名语义。`MAX_ATTEMPTS` 在 Failsafe 语境下指"总尝试次数"，而在 `Tasks.retry()` 语境下指"重试次数"，重命名为 `MAX_RETRIES` 消除了语义歧义，提升了代码可读性。功能行为不变。
