# 提交 1158：Flink: Increase the number of checkpoints from 4 to 6 to fix flakiness. (#11121)

## 提交信息

- **序号**：1158 / 4088
- **哈希**：5ce7c3091ba90a07a400fd4b407a044686ee8f48
- **短哈希**：5ce7c3091
- **日期**：2024-09-16（Mon Sep 16 09:44:10 2024 -0700）
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: Increase the number of checkpoints from 4 to 6 to fix flakiness. (#11121)
  - 提交消息正文：6 checkpoints cycles seem to be more stable based on the existing TestFlinkIcebergSinkDistributionMode test.
- **PR/Issue**：#11121

## 总体目的

`TestFlinkIcebergSinkRangeDistributionBucketing` 是 Flink Iceberg Sink 的集成测试，用于验证在 range distribution + bucketing 模式下，sink 写出的数据文件能正确按 bucket 分桶。该测试通过触发若干次 Flink checkpoint 来驱动 Iceberg sink 提交（每次 checkpoint 对应一次 commit），随后断言每个 bucket 的数据分布。

原本测试设 `NUM_OF_CHECKPOINTS = 4`，但在 CI 上偶发失败（flaky）。提交者基于已有 `TestFlinkIcebergSinkDistributionMode` 测试的稳定经验，发现把 checkpoint 次数提升到 6 次能让测试更稳定。本提交把 `NUM_OF_CHECKPOINTS` 常量从 4 改为 6，同步在 Flink 1.19 与 1.20 两个模块的对应测试中修改。

## 如何达成设计目的

直接修改两个测试文件中的 `NUM_OF_CHECKPOINTS` 常量值。其他逻辑（每次 checkpoint 写 200 行、4 个 bucket、schema 等）保持不变。增加 checkpoint 次数意味着：
- 测试运行时间略增（多 2 个 checkpoint 周期）。
- 每个 bucket 累积的行数从 800 增至 1200（4*200 → 6*200），数据规模更大，bucket 分布统计更稳定，受随机性或边界条件影响更小，从而降低 flaky 概率。

无生产代码改动，纯测试稳定性优化。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkRangeDistributionBucketing.java`

**修改目的**：把 Flink 1.19 模块下该测试的 checkpoint 次数从 4 提升到 6。

**工作逻辑**：将常量定义由 `private static final int NUM_OF_CHECKPOINTS = 4;` 改为 `private static final int NUM_OF_CHECKPOINTS = 6;`。其他常量不变：`NUM_BUCKETS = 4`、`ROW_COUNT_PER_CHECKPOINT = 200`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkRangeDistributionBucketing.java`

**修改目的**：把 Flink 1.20 模块下该测试的 checkpoint 次数同步从 4 提升到 6。

**工作逻辑**：与 v1.19 完全相同的常量值修改。Flink 1.19 与 1.20 两个模块的该测试文件内容一致（diff 显示两个文件的 hash 完全相同 `a5f24e09a`），故同步修改保持两份代码一致。

## 小结

- **成效**：解决 `TestFlinkIcebergSinkRangeDistributionBucketing` 在 CI 上的 flaky 问题。增加 checkpoint 次数让每个 bucket 累积更多数据，使分布断言更稳定。提交者已在 commit message 中说明"基于已有 `TestFlinkIcebergSinkDistributionMode` 测试的经验，6 个 checkpoint 周期更稳定"。
- **影响范围**：仅 Flink 1.19 / 1.20 两个模块的同一个测试文件，每个文件改 1 行常量值。无生产代码、无 API、无构建逻辑变更。
- **回迁到 1.4.x 的注意事项**：
  1. 这是纯测试稳定性修复，回迁到 1.4.x 风险极低。
  2. 1.4.x 若维护 Flink 1.19 / 1.20 模块且该测试存在，可直接回迁；若 1.4.x 只支持更低版本 Flink（如 1.17 / 1.18），则该测试可能不存在或不 flaky，可跳过。
  3. 修改本身不改变测试覆盖的语义，只是让测试更稳定。无功能影响。
  4. 提交 message 中有拼写错误 `checkpoionts`（应为 `checkpoints`），但这是 commit 历史已固化的事实，回迁时若 cherry-pick 可保留原 message 或在 1.4.x 重新撰写时修正。
