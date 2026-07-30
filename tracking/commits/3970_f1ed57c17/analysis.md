# 提交 3970：Core: Include row lineage and key-id in snapshot value methods (#17015)

## 提交信息

- **序号**：3970 / 4088
- **哈希**：f1ed57c1776b7f6cc96e0d434aa6dbdea6407348
- **短哈希**：f1ed57c17
- **日期**：2026-07-01 11:22:50 -0700
- **作者**：Manu Zhang
- **提交说明**：Core: Include row lineage and key-id in snapshot value methods (#17015)
- **PR/Issue**：#17015

## 总体目的

本提交修复了 `BaseSnapshot` 的 `equals()`、`hashCode()` 和 `toString()` 方法遗漏了行血统（row lineage）相关字段和 manifest list 加密 key ID 的问题。此前，两个 snapshot 即使 `firstRowId`、`addedRows`（行血统字段）或 `keyId`（manifest list 加密密钥 ID）不同，也会被视为相等。

这会导致比较和哈希与实际元数据不一致：行 ID 分配或 manifest-list 加密密钥选择的变更不会反映在 equals/hashCode 中，可能导致缓存或去重逻辑出错。同时，这些值在 `toString()` 中也不可见，影响调试体验。

## 如何达成设计目的

将 `firstRowId`、`addedRows`、`keyId` 三个字段加入 `equals()` 的比较条件、`hashCode()` 的哈希计算和 `toString()` 的输出中。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseSnapshot.java` (+15/-2 lines)

**修改目的**：将行血统和 key ID 字段纳入值方法。

**工作逻辑**：
- `equals()` 新增三个字段的比较：
```java
&& Objects.equal(this.firstRowId, other.firstRowId())
&& Objects.equal(this.addedRows, other.addedRows())
&& Objects.equal(this.keyId, other.keyId());
```
- `hashCode()` 新增三个字段：
```java
return Objects.hashCode(
    this.snapshotId, this.parentId, this.sequenceNumber,
    this.timestampMillis, this.schemaId,
    this.firstRowId, this.addedRows, this.keyId);
```
- `toString()` 新增三个字段：
```java
.add("first-row-id", firstRowId)
.add("added-rows", addedRows)
.add("key-id", keyId)
```

### `core/src/test/java/org/apache/iceberg/TestSnapshot.java` (+30/-0 lines)

**修改目的**：验证新增字段的 equals/hashCode/toString 行为。

**工作逻辑**：新增 `snapshotValueMethodsIncludeMetadataFields` 测试，构造相同和不同的 snapshot（分别改变 firstRowId、addedRows、keyId，以及无元数据字段的 snapshot），验证：
- 相同字段 → 相等且 hashCode 相同
- 任一字段不同 → 不相等
- 无元数据字段（null）→ 不相等
- toString 包含 `first-row-id=10`、`added-rows=5`、`key-id=key-1`

## 总结

本提交修复了 `BaseSnapshot` 值方法的完整性问题，确保行血统字段（firstRowId、addedRows）和加密 key ID 参与 equals/hashCode/toString。这是一个正确性修复，避免了因元数据变更未被值方法反映而导致的潜在缓存/去重问题，同时改善了调试可见性。
