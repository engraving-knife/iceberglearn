# 提交 2708：Core: Handle unpartitioned check for a spec with all void transforms in replace partitions (#14186)

## 提交信息

- **序号**：2708 / 4088
- **哈希**：00d18e38feb5439f715ee3c13e206e9445ed2faa
- **短哈希**：00d18e38f
- **日期**：2025-09-30 08:50:19 -0700
- **作者**：Yubo Xu
- **提交说明**：Core: Handle unpartitioned check for a spec with all void transforms in replace partitions (#14186)
- **PR/Issue**：#14186

## 总体目的

本提交修复 `BaseReplacePartitions` 在分区规格（partition spec）所有字段均为 void transform 时，错误地判定为"已分区"从而导致 `replacePartitions` 行为不正确的 bug。

**背景**：Iceberg 的 `replacePartitions`（动态分区覆盖）操作在"未分区表"上的语义是：删除表中所有现有数据，然后添加新文件。`BaseReplacePartitions.apply(...)` 中通过判断当前 data spec 是否未分区来决定是否调用 `deleteByRowFilter(Expressions.alwaysTrue())` 删除全部数据。

**Bug**：原判断条件是 `dataSpec().fields().isEmpty()`，即仅当分区字段列表为空时才认为"未分区"。但 Iceberg 存在一种边界情况：分区 spec 中有字段，但所有字段的 transform 都是 `VoidTransform`（例如通过 `alwaysNull("id").alwaysNull("data")` 构建的 spec）。这种 spec 在语义上是"未分区"的（`PartitionSpec.isPartitioned()` 返回 false，因为 `fields().stream().anyMatch(f -> !f.transform().isVoid())` 不成立），但 `fields().isEmpty()` 返回 false（因为有字段，只是 transform 都是 void）。

结果：对一个 spec 全为 void transform 的表执行 `replacePartitions` 时，旧代码不会触发 `deleteByRowFilter(alwaysTrue())`，即不会删除旧数据，导致 `replacePartitions` 退化为普通 append，旧数据残留——与"替换分区"语义不符。

**修复**：将判断条件从 `dataSpec().fields().isEmpty()` 改为 `dataSpec().isUnpartitioned()`。`isUnpartitioned()` 等价于 `!isPartitioned()`，而 `isPartitioned()` 要求存在至少一个非 void transform 的字段。这样既覆盖真正空 spec 的情况，也覆盖全 void transform spec 的情况，二者都正确识别为"未分区"并触发全量删除。

## 如何达成设计目的

1. **修改判断条件**：`BaseReplacePartitions.apply(...)` 中将 `if (dataSpec().fields().isEmpty())` 改为 `if (dataSpec().isUnpartitioned())`，使全 void transform 的 spec 也走入"未分区表"分支，执行 `deleteByRowFilter(Expressions.alwaysTrue())` 删除全部旧数据。
2. **新增测试**：`TestReplacePartitions` 新增 `SPEC_ALL_VOID`（id 与 data 均为 `alwaysNull`）分区规格及对应数据文件夹具，新增 `testReplaceAllVoidUnpartitionedTable` 测试，验证对全 void transform spec 的表执行 `replacePartitions` 时，旧文件被删除（`Status.DELETED`）、新文件被添加（`Status.ADDED`），且产生 2 个 manifest（一个新增一个删除）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseReplacePartitions.java` (+1/-1 lines)

**修改目的**：修复未分区判断条件，覆盖全 void transform spec 的情况。

**工作逻辑**：`apply(TableMetadata base, Snapshot snapshot)` 方法中，将 `if (dataSpec().fields().isEmpty())` 改为 `if (dataSpec().isUnpartitioned())`。`isUnpartitioned()` 内部为 `!isPartitioned()`，而 `isPartitioned()` 为 `fields.length > 0 && fields().stream().anyMatch(f -> !f.transform().isVoid())`，即要求至少有一个非 void transform 字段才算已分区。改后，空 spec 与全 void transform spec 都被识别为未分区，触发 `deleteByRowFilter(Expressions.alwaysTrue())` 删除全部旧数据，符合 `replacePartitions` 在未分区表上的语义。

### `core/src/test/java/org/apache/iceberg/TestReplacePartitions.java` (+53/-0 lines)

**修改目的**：新增覆盖全 void transform spec 场景的测试。

**工作逻辑**：
- 新增静态夹具 `SPEC_ALL_VOID`：`PartitionSpec.builderFor(SCHEMA).alwaysNull("id").alwaysNull("data").build()`，即两个字段均为 void transform。
- 新增数据文件夹具 `FILE_ALL_VOID_UNPARTITIONED_A` 与 `FILE_ALL_VOID_UNPARTITIONED_B`，基于 `SPEC_ALL_VOID` 构建。
- 新增 `testReplaceAllVoidUnpartitionedTable`：用 `TestTables.create` 创建一张 schema 为 `SCHEMA`、spec 为 `SPEC_ALL_VOID` 的表；先 append 文件 A 并提交；再执行 `tableVoid.newReplacePartitions().addFile(FILE_ALL_VOID_UNPARTITIONED_B)` 提交；断言 metadata 版本为 2、新 snapshot 有 2 个 manifest，第一个 manifest 含文件 B 状态 `ADDED`，第二个 manifest 含文件 A 状态 `DELETED`。这验证了 `replacePartitions` 正确删除了旧文件 A 并添加了新文件 B，而非把旧数据残留。

## 总结

本提交修复了 `BaseReplacePartitions` 中未分区判断的一个边界 bug：当分区 spec 的所有字段均为 void transform（如 `alwaysNull`）时，旧代码用 `fields().isEmpty()` 判定，误认为"已分区"而跳过全量删除，导致 `replacePartitions` 在此类表上行为不正确（旧数据残留）。修复改用 `isUnpartitioned()` 判定，正确识别全 void transform spec 为未分区，触发全量删除。配套测试覆盖了该边界场景，验证替换后旧文件被删除、新文件被添加。属于正确性修复，影响 `replacePartitions` 在全 void transform spec 表上的行为。
