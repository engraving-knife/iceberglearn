# 提交 1627：Spark 3.5: Fix Javadoc in ColumnarBatchUtil (#12058)

## 提交信息

- **序号**：1627 / 4088
- **哈希**：a7ed3ca3bd7c01db5b06dcfb27b764c5dbffed43
- **短哈希**：a7ed3ca3b
- **日期**：2025-01-23（Thu Jan 23 20:33:08 2025 -0800）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark 3.5: Fix Javadoc in ColumnarBatchUtil
- **PR/Issue**：#12058

## 总体目的

`ColumnarBatchUtil` 是 Spark 3.5 向量化读取路径中用于在批次内构建"行 ID 映射"和"是否删除标记数组"以跳过被删除行的工具类。其上两个核心方法 `buildRowIdMapping` 与 `buildIsDeleted` 之前的 Javadoc 注释写得比较粗糙、信息丢失甚至存在误导（比如把"应用 position deletes 和 equality deletes 后的映射结果"和"isDeleted 数组"混在一起描述，缺少中间步骤），不利于社区开发者理解删除流程。

本提交只是文档修订，不修改任何运行时逻辑：把两个方法的 Javadoc 重写为更清晰、更准确、分步骤的示例，并补充 `@param`/`@return` 的说明，方便后续维护和 onboarding。

## 如何达成设计目的

通过改写两个方法上的 Javadoc 块实现：

1. 用 `<pre>` 块包裹"初始状态 → 应用 position deletes → 应用 equality deletes"的三步示例，逐步展示数组在每一步的变化；
2. 用更直观的 `v0..v7`、`F/T` 表示数据值与布尔标记，避免原注释中"位置删除后映射为 `[0,4,5,7,-,-,-,-]`"这种把两步合并的结果直接抛出，导致读者难以理解中间过程；
3. `@return` 文案改为"返回映射数组与存活行数，无任何删除时返回 `null`"，避免原文"new num of rows in a batch"含糊不清。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchUtil.java`（修改，+35/-14）

**修改目的**：重写 `buildRowIdMapping` 与 `buildIsDeleted` 两个方法的 Javadoc，让删除流程的中间状态可读、可追踪。

**工作逻辑（仅文档层面）**：

- `buildRowIdMapping` 新注释：
  - 初始状态 `Row ID mapping: [0, 1, 2, 3, 4, 5, 6, 7]`；
  - 应用 position deletes（2、6）后 `Row ID mapping: [0, 1, 3, 4, 5, 7, -, -]`（6 条存活）；
  - 应用 equality deletes（v1、v2、v3）后 `Row ID mapping: [0, 4, 5, 7, -, -, -, -]`（4 条存活）；
  - 返回值描述为"映射数组与存活行数，无删除时返回 `null`"。
- `buildIsDeleted` 新注释：
  - 初始状态 `Is deleted array: [F, F, F, F, F, F, F, F]`；
  - 应用 position deletes 后 `[F, F, T, F, F, F, T, F]`（6 条存活）；
  - 应用 equality deletes 后 `[F, T, T, T, F, F, T, F]`（4 条存活）。
- 同时统一 `@param` 句末去掉多余的句点，保持风格一致。

## 小结

- **成效**：纯文档改进，让 `ColumnarBatchUtil` 两个核心方法的删除流程示例更准确、分步骤可读，减少后续维护者误读风险。
- **影响范围**：仅 Spark 3.5 模块单文件的 Javadoc，无运行时行为变化，无 API 变更。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支的 `ColumnarBatchUtil` 同样位于 `spark/v3.5/spark/.../vectorized/ColumnarBatchUtil.java`，可直接 cherry-pick；若 1.4.x 上的删除流程实现与 main 一致（同样走 position deletes + equality deletes 两步），注释可直接套用。若 1.4.x 已合入 deletion vector（DV）相关改动，需确认 DV 路径是否复用同一方法，必要时在注释中补充 DV 的处理说明。
