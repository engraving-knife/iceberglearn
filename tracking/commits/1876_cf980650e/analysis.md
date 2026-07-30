# 提交 1876：Core: Make totalRecordCount optional in PartitionStats (#12226)

## 提交信息

- **序号**：1876 / 4088
- **哈希**：cf980650ede19efe2b9fe9aff521ecf8e7bd315c
- **短哈希**：cf980650e
- **日期**：2025-03-19 09:17:07 +0100
- **作者**：Ajantha Bhat
- **提交说明**：Core: Make totalRecordCount optional in PartitionStats (#12226)
- **PR/Issue**：#12226

## 总体目的

本提交将 `PartitionStats` 中的 `totalRecordCount` 字段从基本类型 `long`（默认 0）改为包装类型 `Long`（可为 null），使其真正成为可选字段，与 Iceberg 分区统计规范一致。

背景：根据 Iceberg 分区统计规范，`totalRecordCount` 是可选字段，当数据文件未提供 record count 元数据时应为 null 而非 0。但此前的实现中，`PartitionStats.totalRecordCount` 是 `long` 类型，默认值为 0，并且在 `set` 方法中将 null 值强制转为 0L。这导致无法区分"确实有 0 条记录"和"未知记录数（null）"两种情况，违反规范。

本提交将字段改为 `Long`（null 表示未知），新增 `totalRecords()` 方法返回 `Long`，将原 `totalRecordCount()` 标记为 `@Deprecated`（返回 long，null 时返回 0L 以保持兼容），并修复 `appendStats` 中的累加逻辑以正确处理 null。

## 如何达成设计目的

1. **字段类型变更**：`totalRecordCount` 从 `long` 改为 `Long`，默认 null。

2. **方法调整**：
   - 原 `totalRecordCount()` 标记 `@Deprecated`（since 1.9.0，1.10.0 移除），返回 `totalRecordCount == null ? 0L : totalRecordCount` 保持向后兼容。
   - 新增 `totalRecords()` 返回 `Long`（可为 null）。

3. **appendStats 累加逻辑**：原 `this.totalRecordCount += entry.totalRecordCount` 改为 null 安全的累加：仅当 `entry.totalRecordCount != null` 时累加；若当前为 null 则直接赋值为 entry 的值。

4. **set 方法**：case 9（totalRecordCount）原将 null 转为 0L，现直接 `(Long) value` 保留 null。

5. **测试更新**：现有测试中期望 `0L` 的 totalRecordCount 改为期望 `null`；新增 `TestPartitionStats` 测试类覆盖 appendStats 在各种 null 组合下的行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/PartitionStats.java` (修改, +16/-6 lines)

**修改目的**：将 totalRecordCount 改为可选（Long，可为 null）。

**工作逻辑**：
- 字段 `private long totalRecordCount;` 改为 `private Long totalRecordCount; // null by default`。
- 原 `totalRecordCount()` 方法标记 `@Deprecated`，返回 `totalRecordCount == null ? 0L : totalRecordCount`。
- 新增 `public Long totalRecords()` 返回 `totalRecordCount`。
- `appendStats` 中累加逻辑改为：若 `entry.totalRecordCount != null`，则若当前为 null 直接赋值，否则累加。
- `set` 方法 case 9：`this.totalRecordCount = (Long) value;`（保留 null，不再转为 0L）。

### `core/src/test/java/org/apache/iceberg/TestPartitionStats.java` (新增, +135/-0 lines)

**修改目的**：覆盖 PartitionStats 的 appendStats 行为，特别是 totalRecordCount 为 null 的场景。

**工作逻辑**：新增测试类，包含：
- `testAppendWithAllValues`：两 stats 都有 totalRecordCount，累加正确。
- `testAppendWithThisNullOptionalField`：this 的 totalRecordCount 为 null，other 有值，结果为 other 的值。
- `testAppendWithBothNullOptionalFields`：两者都 null，结果仍 null。
- `testAppendWithOtherNullOptionalFields`：this 有值，other 为 null，结果保持 this 的值。
- `testAppendEmptyStats`：空 stats append，totalRecordCount 为 null。
- `testAppendWithDifferentSpec`：spec id 不同抛 IllegalArgumentException。
- 辅助方法 `createStats`/`validateStats` 通过 `set`/`get` 按位置操作字段。

### `core/src/test/java/org/apache/iceberg/TestPartitionStatsUtil.java` (修改, +19/-19 lines)

**修改目的**：更新期望值，将原本期望 `0L` 的 totalRecordCount 改为 `null`。

**工作逻辑**：在多个测试断言中，将 Tuple 中 totalRecordCount 位置的期望值从 `0L` 改为 `null`，反映该字段现在默认为 null 而非 0。

### `data/src/test/java/org/apache/iceberg/data/TestPartitionStatsHandler.java` (修改, +12/-12 lines)

**修改目的**：同步更新 handler 测试中的期望值。

**工作逻辑**：将 totalRecordCount 相关断言从期望 `0L` 改为 `null`。

## 总结

本提交将 `PartitionStats.totalRecordCount` 从 `long` 改为 `Long`（可为 null），使其符合规范中"可选字段"的定义，能正确区分"0 条记录"和"未知记录数"。新增 `totalRecords()` 方法，废弃旧 `totalRecordCount()`（兼容返回 0L），修复 appendStats 的 null 安全累加，并新增专门的测试类覆盖各种 null 组合场景。
