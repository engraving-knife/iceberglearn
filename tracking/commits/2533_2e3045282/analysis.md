# 提交 2533：Docs: Add exactly once semantics note to Flink docs (#13875)

## 提交信息

- **序号**：2533 / 4088
- **哈希**：2e30452826fce5e7c55e4e6ad74e35d212979036
- **短哈希**：2e3045282
- **日期**：2025-08-20 16:17:03 +0200
- **作者**：Robin Moffatt
- **提交说明**：Docs: Add exactly once semantics note to Flink docs (#13875)
- **PR/Issue**：#13875

## 总体目的

Flink Iceberg sink 通过两阶段提交（依赖 Flink checkpoint 机制）实现 exactly-once 语义，但 `flink-writes.md` 文档页面没有显式声明这一点，用户在评估 sink 可靠性时缺少明确依据。本次提交在 Flink Writes 文档开头补充一行说明，明确 sink 提供 exactly-once 语义保证。

同时顺手修正了一个英文笔误：原文 "Iceberg support batch and streaming writes With" 中 "With" 大写且语法不顺，改为 "with"，让语句更通顺。

## 如何达成设计目的

- 在文档首段后追加一行 `The Flink Iceberg sink guarantees exactly-once semantics.`
- 把原句中的 `With` 改为小写 `with`。

## 修改详情

### `docs/docs/flink-writes.md` (+3/-1)

**修改目的**：明确 exactly-once 语义保证并修正笔误。

**工作逻辑**：在概述段落后增加一行声明，让用户一眼可见 sink 的可靠性保证；同时统一首句大小写。

## 总结

一次轻量文档补全：在 Flink Writes 文档显式声明 sink 的 exactly-once 语义，并修正首句的大小写笔误，提升文档准确性与可信度。
