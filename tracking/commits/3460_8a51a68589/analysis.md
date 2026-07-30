# 提交 3460：Flink: Backport: Fix HashKeyGenerator SelectorKey cache ignoring writeParallelism and distributionMode (#15762)

## 提交信息

- **序号**：3460 / 4088
- **哈希**：8a51a685894eace51474f512b46a187566f27202
- **短哈希**：8a51a68589
- **日期**：2026-03-25 22:35:58 -0700
- **作者**：Hayoung Lee
- **提交说明**：Flink: Backport: Fix HashKeyGenerator SelectorKey cache ignoring writeParallelism and distributionMode (#15762)
- **PR/Issue**：#15762

## 总体目的

将 PR #15740（提交 3459）中针对 Flink 2.1 的 HashKeyGenerator SelectorKey 缓存修复，移植到 Flink 1.20 和 Flink 2.0 两个版本。原始 PR 修复了 SelectorKey 缓存键忽略 `writeParallelism` 和 `distributionMode` 的 bug。

## 如何达成设计目的

- 在 Flink 1.20 和 Flink 2.0 模块中复制与 Flink 2.1 完全相同的修改
- 修改 `HashKeyGenerator.SelectorKey` 类，添加 `distributionMode` 和 `writeParallelism` 字段
- 更新 equals()、hashCode() 和 toString() 方法
- 添加相同的测试用例

## 修改详情

### Flink 1.20 模块

#### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (+25/-4 lines)
**修改目的**：在 SelectorKey 中添加 distributionMode 和 writeParallelism 字段。
**工作逻辑**：与 Flink 2.1 修改完全一致。

#### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestHashKeyGenerator.java` (+68/-0 lines)
**修改目的**：添加缓存失效测试。
**工作逻辑**：与 Flink 2.1 测试完全一致。

### Flink 2.0 模块

#### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (+25/-4 lines)
**修改目的**：与 Flink 1.20 相同的修改。

#### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestHashKeyGenerator.java` (+68/-0 lines)
**修改目的**：与 Flink 1.20 相同的测试。

## 总结

该提交是 PR #15740 的 backport，将 HashKeyGenerator SelectorKey 缓存修复从 Flink 2.1 移植到 Flink 1.20 和 Flink 2.0 两个版本。修改内容与原始 PR 完全一致，包括在 SelectorKey 中添加 `distributionMode` 和 `writeParallelism` 字段，以及添加对应的缓存失效测试。
