# 提交 3998：Core: Make partition field in TrackedFile optional (#17000)

## 提交信息

- **序号**：3998 / 4088
- **哈希**：34efd94f1cdad761cbbd56d692c48c65856afcfb
- **短哈希**：34efd94f1
- **日期**：2026-07-08 09:00:36 -0700
- **作者**：gaborkaszab
- **提交说明**：Core: Make partition field in TrackedFile optional (#17000)
- **PR/Issue**：#17000

## 总体目的

本提交将 `TrackedFile` schema 中的 `partition` 字段从 `required` 改为 `optional`，使得跟踪文件（TrackedFile，用于追踪 manifest 中文件级别的元数据/统计信息）在表示未分区表的文件时，partition 字段可以为 null，而不是必须填充一个空的 `PartitionData` 占位对象。

之前为兼容未分区表，代码在多处维护了 `EMPTY_PARTITION_DATA` 单例作为占位（`BaseFile` 和 `TrackedFileStruct` 各有一份），并在构造逻辑中把 null 分区替换为该空对象。这造成了 schema 上 partition 标记为 required 但实际语义上允许"空"的不一致，也增加了维护成本。本次让 schema 诚实地反映 partition 可空的真实语义。

## 如何达成设计目的

设计思路：
1. 将 `EMPTY_PARTITION_DATA` 单例从 `BaseFile` 和 `TrackedFileStruct` 移到 `PartitionData` 类内部作为 `EMPTY` 常量，统一管理。
2. `TrackedFile.schemaWithContentStats` 和 `TrackedFileStruct.BASE_TYPE` 中的 partition 字段从 `Types.NestedField.required(...)` 改为 `optional(...)`。
3. `TrackedFileStruct` 内部 `partitionData` 字段初始值从 `EMPTY_PARTITION_DATA` 改为 `null`，构造时直接赋值传入的 partition（不再做 null 替换），copy 构造时对 null 做保护。
4. `BaseFile` 构造时对未分区情况使用 `PartitionData.EMPTY`。
5. `TrackedFileAdapters.asDataFile` 在 partition 为 null 时返回 `PartitionData.EMPTY`（保持 `DataFile` API 的非空契约）。
6. 测试相应更新：partition 字段断言从 `isRequired()` 改为 `isOptional()`，未分区场景断言 `partition()` 等于 `PartitionData.EMPTY`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/PartitionData.java` (+8/-0 lines)

**修改目的**：将空分区数据单例集中到 `PartitionData` 类中。

**工作逻辑**：
新增静态常量 `EMPTY`，是一个匿名子类实例，重写 `copy()` 返回自身（因为内容不变）。这样 `BaseFile` 和 `TrackedFileStruct` 都引用 `PartitionData.EMPTY` 而无需各自定义。

### `core/src/main/java/org/apache/iceberg/BaseFile.java` (+2/-9 lines)

**修改目的**：移除本地 `EMPTY_PARTITION_DATA` 定义，改用 `PartitionData.EMPTY`。

**工作逻辑**：
删除本地的 `EMPTY_PARTITION_DATA` 匿名子类，构造函数中 `this.partitionData = EMPTY_PARTITION_DATA` 改为 `this.partitionData = PartitionData.EMPTY`，`partitionType` 同步引用。

### `core/src/main/java/org/apache/iceberg/TrackedFile.java` (+2/-2 lines)

**修改目的**：将 schema 中 partition 字段改为 optional。

**工作逻辑**：
`schemaWithContentStats` 中 `Types.NestedField.required(PARTITION_ID, ...)` 改为 `Types.NestedField.optional(...)`。`partition()` 方法的 Javadoc 从"Returns the partition..."补充为"...or null"。

### `core/src/main/java/org/apache/iceberg/TrackedFileAdapters.java` (+1/-1 lines)

**修改目的**：处理 partition 为 null 的情况，保持 `DataFile` API 非空契约。

**工作逻辑**：
`partition()` 方法改为 `return file().partition() != null ? file().partition() : PartitionData.EMPTY;`，确保 `DataFile.partition()` 永不返回 null。

### `core/src/main/java/org/apache/iceberg/TrackedFileStruct.java` (+4/-13 lines)

**修改目的**：将 `TrackedFileStruct` 内部 partition 字段改为可空，schema 同步标记为 optional。

**工作逻辑**：
- 删除本地 `EMPTY_PARTITION_DATA` 定义。
- `BASE_TYPE` 中 partition 字段从 `required` 改为 `optional`。
- 成员 `partitionData` 初始值从 `EMPTY_PARTITION_DATA` 改为 `null`。
- 构造函数中 `if (partition != null) { this.partitionData = partition; }` 简化为 `this.partitionData = partition;`（允许 null）。
- copy 构造中 `this.partitionData = toCopy.partitionData.copy()` 改为 `toCopy.partitionData != null ? toCopy.partitionData.copy() : null`，避免 NPE。

### `core/src/test/java/org/apache/iceberg/TestTrackedFile.java` (+2/-2 lines)

**修改目的**：更新断言以反映 partition 字段为 optional。

**工作逻辑**：测试方法名从 `schemaWithContentStatsPartitionIsRequired` 改为 `schemaWithContentStatsPartitionIsOptional`，断言从 `isRequired().isTrue()` 改为 `isOptional().isTrue()`。

### `core/src/test/java/org/apache/iceberg/TestTrackedFileAdapters.java` (+1/-1 lines)

**修改目的**：更新未分区场景的断言。

**工作逻辑**：原 `assertThat(dataFile.partition().size()).isEqualTo(0)` 改为 `assertThat(dataFile.partition()).isEqualTo(PartitionData.EMPTY)`，更精确地表达语义。

## 总结

本提交通过将 `TrackedFile` schema 中 partition 字段从 required 改为 optional，让数据模型诚实地反映"未分区表文件没有分区数据"的真实语义，同时统一了空分区单例到 `PartitionData.EMPTY`，减少了重复定义。`DataFile` 的对外 API 仍保持非空契约（通过 adapter 兜底返回 `PartitionData.EMPTY`），保持了向后兼容。
