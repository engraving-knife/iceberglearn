# 提交 2252：Core: PartitionsTable#partitions returns incomplete list in case of partition evolution and NULL partition values (#12528)

## 提交信息

- **序号**：2252 / 4088
- **哈希**：fa162de0151121adbec33cbbdd029e6f61da762b
- **短哈希**：fa162de01
- **日期**：2025-06-18 20:02:11 +0200
- **作者**：Denys Kuzmenko
- **提交说明**：Core: PartitionsTable#partitions returns incomplete list in case of partition evolution and NULL partition values
- **PR/Issue**：#12528

## 总体目的

本提交修复了 `PartitionsTable` 在分区演化和存在 NULL 分区值时返回不完整分区列表的 bug。在 Iceberg 表中，当分区规范发生演化（partition evolution）时，不同版本的分区规范可能包含不同数量和类型的分区字段。例如，一个表最初可能只有一个分区字段 `dt`，后来演化添加了第二个分区字段 `region`。对于演化前的数据文件，其分区值中 `region` 字段为 NULL。

问题的根源在于 `StructLikeMap` 使用默认的比较器来比较分区键。当分区演化导致不同 spec 的分区数据被投影到统一类型时，`StructProjection` 对象的比较行为不正确——两个逻辑上相同的分区（例如同一个 `dt` 值但来自不同 spec 的分区）可能因为投影字段数量不同或 NULL 值处理不一致而被视为不同的键，导致同一逻辑分区的数据被分散到多个 Partition 对象中，最终返回的分区列表不完整或包含重复项。

## 如何达成设计目的

- 引入自定义的 `PartitionComparator`，在比较 `StructLike` 之前先比较 `StructProjection` 的投影字段数量，确保只有投影字段数量相同的结构才会进入值比较阶段。
- 在 `StructProjection` 中新增 `projectedFields()` 方法，返回实际有效的（非 -1）投影字段数量。
- 重构 `PartitionsTable.partitions()` 方法，用 `StructLikeMap` 配合自定义比较器替代原有的 `PartitionMap` 内部类，并使用 `computeIfAbsent` 方法简化分区查找/创建逻辑。
- 增强 `StructLikeMap` 和 `StructLikeWrapper` 以支持自定义 `Comparator<StructLike>`，而非仅依赖类型推导的默认比较器。
- 修复 `Partition.update()` 中 `specId` 的更新逻辑，确保 `specId` 在快照时间戳更新时同步更新，而非仅在 DATA 文件处理时更新。

## 修改详情

### `api/src/main/java/org/apache/iceberg/util/StructProjection.java` (修改, +5/0 lines)

**修改目的**：新增 `projectedFields()` 方法以支持分区比较逻辑。

**工作逻辑**：新增 `public int projectedFields()` 方法，通过统计 `positionMap` 数组中值不为 -1 的元素数量来返回实际投影的字段数。`positionMap` 中 -1 表示该位置的字段未被投影（即不存在于源结构中），非 -1 值表示源结构中的对应位置。这个方法使得比较器可以判断两个 StructProjection 是否投影了相同数量的字段。

### `core/src/main/java/org/apache/iceberg/PartitionsTable.java` (修改, +63/-52 lines)

**修改目的**：修复分区列表不完整的 bug，重构分区收集逻辑。

**工作逻辑**：
1. `partitions()` 方法不再使用 `PartitionMap` 内部类，改为直接使用 `StructLikeMap.create(partitionType, new PartitionComparator(partitionType))`，传入自定义比较器。
2. 使用 `partitions.computeIfAbsent(key, () -> new Partition(key, partitionType))` 替代原来的 `partitions.get(partition).update(file, snapshot)` 模式，简化了查找或创建分区的逻辑。
3. 新增 `PartitionComparator` 私有静态内部类，实现 `Comparator<StructLike>` 接口。在 `compare()` 方法中，如果两个比较对象都是 `StructProjection`，首先比较它们的 `projectedFields()` 值（投影字段数量），如果不同则直接返回比较结果，避免不同投影宽度的结构被误判为相等。只有投影字段数量相同时才使用默认的类型比较器进行值比较。
4. `Partition.update()` 方法中，将 `this.specId = file.specId()` 的赋值从 `DATA` 分支移到快照时间戳更新的逻辑块中，确保 `specId` 在任何文件类型（包括删除文件）更新快照时也被正确更新。

### `core/src/main/java/org/apache/iceberg/util/StructLikeMap.java` (修改, +31/-XX lines)

**修改目的**：支持自定义比较器并新增 computeIfAbsent 方法。

**工作逻辑**：新增 `create(Types.StructType type, Comparator<StructLike> comparator)` 工厂方法，允许传入自定义比较器。原有的 `create(Types.StructType type)` 方法委托给新方法，使用 `Comparators.forType(type)` 作为默认比较器。构造函数改为接收比较器参数，并将其传递给 `StructLikeWrapper.forType(type, comparator)`。新增 `computeIfAbsent(StructLike struct, Supplier<T> valueSupplier)` 方法，封装了"查找或创建"模式。

### `core/src/main/java/org/apache/iceberg/util/StructLikeWrapper.java` (修改, +13/-XX lines)

**修改目的**：支持通过自定义比较器创建 StructLikeWrapper。

**工作逻辑**：新增 `forType(Types.StructType type, Comparator<StructLike> comparator)` 工厂方法，直接接收比较器参数。原有的 `forType(Types.StructType type)` 方法委托给新方法。移除了私有构造函数中从类型推导比较器的逻辑，改为直接使用传入的比较器。

### 测试文件 (修改/新增)

- `TestMetadataTableScansWithPartitionEvolution.java` (新增, +86 lines)：针对分区演化和 NULL 分区值场景的测试
- `MetadataTableScanTestBase.java`、`TestBase.java`、`TestTables.java`：测试基础设施的增强，支持分区演化场景的测试设置

## 总结

本提交修复了一个在分区演化场景下导致分区列表不完整的重要 bug。核心修复是在比较分区键时考虑 `StructProjection` 的投影字段数量，避免不同 spec 产生的投影结构被错误地视为相等或不相等。同时增强了 `StructLikeMap` 和 `StructLikeWrapper` 的灵活性，使其支持自定义比较器。这是一个影响数据正确性的重要修复。
