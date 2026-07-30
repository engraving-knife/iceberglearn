# 提交 1766：Core: Handle partition evolution case in PartitionStatsUtil#computeStats (#12137)

## 提交信息

- **序号**：1766 / 4088
- **哈希**：de644158a20c4a8d1b79fbc7773ae5a58dd316ac
- **短哈希**：de644158a
- **日期**：2025-02-20 12:44:02 +0100
- **作者**：Denys Kuzmenko
- **提交说明**：Core: Handle partition evolution case in PartitionStatsUtil#computeStats (#12137)
- **PR/Issue**：#12137

## 总体目的

本提交旨在修复 `PartitionStatsUtil.computeStats` 方法在处理分区演化（partition evolution）场景下的 bug。

当 Iceberg 表经历分区演化后（例如将 bucket(2) 变为 bucket(4)），表中会存在不同分区规范的数据文件。在计算分区统计时，原代码使用从 `coercedPartition` 派生的 `key` 作为统计映射表的查找键。问题在于 `coercedPartition` 是一个可变对象（mutable object），在遍历文件的过程中可能被修改/重用。当 `computeIfAbsent` 方法使用这个可变对象作为查找键时，后续迭代修改该对象会导致之前存入映射表中的键也被意外修改（因为它们引用同一个对象），从而导致不同分区的统计数据被错误合并或覆盖。

本提交通过使用文件分区数据的副本（`file.partition().copy()`）作为查找键来修复此问题，确保每个分区的键是独立的不可变副本。

## 如何达成设计目的

提交修改 `computeStats` 方法中 `statsMap.computeIfAbsent` 的调用，将第二个参数从 `key`（派生自可变的 `coercedPartition`）改为 `((PartitionData) file.partition()).copy()`（文件原始分区数据的深拷贝）。这确保映射表中使用的键是每个文件分区数据的独立副本，不会因后续迭代而被修改。

同时新增了 `testPartitionStatsWithBucketTransformSchemaEvolution` 测试用例，模拟分区演化场景（bucket(2) → bucket(4)），验证分区统计在演化前后都能正确计算。

## 修改详情

### `core/src/main/java/org/apache/iceberg/PartitionStatsUtil.java`（修改, +4/-1 lines）

**修改目的**：修复分区演化场景下统计键被意外修改的问题。

**工作逻辑**：

原代码：
```java
PartitionStats stats =
    statsMap.computeIfAbsent(specId, key, () -> new PartitionStats(key, specId));
```

修复后：
```java
PartitionStats stats =
    statsMap.computeIfAbsent(
        specId,
        ((PartitionData) file.partition()).copy(),
        () -> new PartitionStats(key, specId));
```

关键变更：
- `computeIfAbsent` 的第二个参数（作为映射表的查找键）从 `key`（派生自可变的 `coercedPartition`）改为 `((PartitionData) file.partition()).copy()`。
- `file.partition()` 返回文件原始的分区数据（不受 coercion 影响），`.copy()` 创建该数据的深拷贝，确保键是独立的不可变副本。
- 第三个参数（统计对象的工厂函数）仍使用 `key` 和 `specId` 创建 `PartitionStats`，因为 `key` 在此处仅用于初始化统计对象，不作为映射表键。

### `core/src/test/java/org/apache/iceberg/TestPartitionStatsUtil.java`（修改, +162/-7 lines）

**修改目的**：添加分区演化场景的测试用例。

**工作逻辑**：

1. **新增 `testPartitionStatsWithBucketTransformSchemaEvolution` 测试**：
   - 创建一个使用 `identity("c2")` + `bucket("c1", 2)` 分区规范的表。
   - 添加 2 个数据文件（c2="foo", c1=0 和 c1=1），提交快照1。
   - 验证分区统计：2 个分区（foo/0, foo/1），各自包含 1 个文件。
   - 执行分区演化：移除 `bucket("c1", 2)`，添加 `bucket("c1", 4)`。
   - 添加 4 个新数据文件（c2="bar", c1=0/1/2/3），提交快照2。
   - 验证分区统计：6 个分区——2 个旧分区（foo/0/null, foo/1/null，新规范中 bucket(2) 字段为 null）和 4 个新分区（bar/null/0, bar/null/1, bar/null/2, bar/null/3）。
   - 验证每个分区的文件数、记录数、文件大小、快照 ID 等统计信息正确。

2. **重构 `partitionData` 辅助方法**：
   - 将原来接受固定参数的方法改为可变参数 `partitionData(Types.StructType partitionType, Object... fields)`，以支持不同字段数量的分区数据创建。

## 小结

- **成效**：修复了分区演化场景下分区统计键被意外修改的 bug，确保每个分区的统计数据使用独立的键副本，不会因后续迭代而被错误覆盖。
- **影响范围**：修改 Core 模块的 `PartitionStatsUtil`，影响使用 `compute_table_stats` 或其他依赖分区统计功能的场景，特别是经历过分区演化的表。
- **回迁到 1.4.x 的注意事项**：此 bug 修复建议回迁到 1.4.x 分支。需确认 1.4.x 分支中 `PartitionStatsUtil` 的 `computeStats` 方法是否存在相同的问题（即使用可变的 `coercedPartition` 派生键而非分区数据副本）。同时需确认 1.4.x 分支中 `statsMap.computeIfAbsent` 的方法签名是否接受三个参数（specId, partitionData, supplier）。如果 1.4.x 分支的方法签名不同，可能需要调整修复方式。无特殊前置依赖。
