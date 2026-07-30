# 提交 3425：Flink: Fix non-deterministic operator UIDs in DynamicIcebergSink (#15687)

## 提交信息

- **序号**：3425 / 4088
- **哈希**：fce4985aa47c865ebb2442598a6cd9f984892bc5
- **短哈希**：fce4985aa4
- **日期**：2026-03-20 16:22:54 +0100
- **作者**：Maximilian Michels
- **提交说明**：Flink: Fix non-deterministic operator UIDs in DynamicIcebergSink (#15687)
- **PR/Issue**：#15687

## 总体目的

修复 `DynamicIcebergSink` 中 pre-commit 拓扑的 operator UID 不确定性问题。原有代码在 pre-commit 拓扑的 UID 中包含了 `sinkId`（一个随机 UUID），导致每次创建 sink 时 UID 都不同。Flink 使用 operator UID 来匹配 savepoint 中的状态，非确定性的 UID 会导致从 savepoint 恢复时无法匹配状态。修复方法是移除 UID 中的 `sinkId`，使其仅基于 uidPrefix 确定生成。

## 如何达成设计目的

1. 将 pre-commit 拓扑的 UID 从 `prefixIfNotNull(uidPrefix, sinkId + "-pre-commit-topology")` 改为 `prefixIfNotNull(uidPrefix, "-pre-commit-topology")`
2. 移除 `sinkId`（随机 UUID）对 UID 的影响
3. 新增测试验证 UID 的确定性和格式正确性

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+2/-1 lines)

**修改目的**：移除 pre-commit 拓扑 UID 中的 sinkId。

**工作逻辑**：
- 原有代码：`.uid(prefixIfNotNull(uidPrefix, sinkId + "-pre-commit-topology"))`
- 修复后：`.uid(prefixIfNotNull(uidPrefix, "-pre-commit-topology"))`
- `sinkId` 是一个随机 UUID，包含它会使 UID 在每次创建 sink 时都不同
- 移除 sinkId 后，UID 仅由 uidPrefix 决定，具有确定性

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+63 lines)

**修改目的**：验证 operator UID 的确定性和格式。

**工作逻辑**：

**testOperatorUidsAreDeterministic 测试**：
- 使用相同 uidPrefix 创建两次 sink，验证 UID 集合相同
- 使用不同 uidPrefix 创建 sink，验证 UID 集合不同

**testOperatorUidsFormat 测试**：
- 验证各种 uidPrefix（"test"、""、null）下 UID 的格式正确
- 验证包含 "--sink"、"--generator"、"--updater"、"--pre-commit-topology"、"Sink Committer" 等预期 UID

**createSinkAndReturnUIds 辅助方法**：
- 创建 DynamicIcebergSink 并收集所有 StreamNode 的 transformation UID
- 过滤掉 source 的 UID

## 总结

本提交修复了 DynamicIcebergSink 中 pre-commit 拓扑 operator UID 的非确定性问题。通过移除 UID 中的随机 sinkId，使 UID 仅基于 uidPrefix 确定，确保从 savepoint 恢复时能正确匹配状态。新增测试验证了 UID 的确定性和格式。
