# 提交 1828：Core: Don't create empty RemovePartitionSpecs MetadataUpdate (#12465)

## 提交信息

- **序号**：1828 / 4088
- **哈希**：5994b54de4675e5c40e8f8cd4cf5be48620f09fb
- **短哈希**：5994b54de
- **日期**：2025-03-06 13:27:09 +0100
- **作者**：gaborkaszab
- **提交说明**：Core: Don't create empty RemovePartitionSpecs MetadataUpdate (#12465)
- **PR/Issue**：#12465

## 总体目的

该提交修复了 `TableMetadata.Builder.removeSpecs()` 方法在无可移除分区 spec 时仍创建空 `RemovePartitionSpecs` 更新的问题。

当执行 `expireSnapshots` 并启用 `cleanExpiredMetadata()` 选项时，Iceberg 会清理不再被任何快照引用的分区 spec（通过 `removeSpecs` 方法）。但原实现无论是否有 spec 需要移除，都会创建 `MetadataUpdate.RemovePartitionSpecs` 更新并添加到 changes 列表中——即使 `specIdsToRemove` 为空集合。

这会导致：
1. **无意义的元数据更新**：表元数据中记录了一个空的分区 spec 移除操作，增加元数据噪声。
2. **不必要的 commit**：可能导致本可跳过的表元数据 commit 被触发。
3. **下游解析困惑**：下游系统看到 `RemovePartitionSpecs` 更新但发现待移除列表为空，可能产生困惑或异常。

## 如何达成设计目的

在 `TableMetadata.Builder.removeSpecs()` 方法中增加空集合判断：仅当 `specIdsToRemove` 非空时才执行 spec 列表过滤和添加 `RemovePartitionSpecs` 更新。如果为空，直接返回 `this`，不产生任何变更。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadata.java` (修改, +7/-5 lines)

**修改目的**：避免创建空的 `RemovePartitionSpecs` 更新。

**工作逻辑**：
```java
// 旧代码：无条件执行
this.specs = specs.stream()
    .filter(s -> !specIdsToRemove.contains(s.specId()))
    .collect(Collectors.toList());
changes.add(new MetadataUpdate.RemovePartitionSpecs(specIdsToRemove));

// 新代码：仅当有 spec 需要移除时执行
if (!specIdsToRemove.isEmpty()) {
    this.specs = specs.stream()
        .filter(s -> !specIdsToRemove.contains(s.specId()))
        .collect(Collectors.toList());
    changes.add(new MetadataUpdate.RemovePartitionSpecs(specIdsToRemove));
}
```

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java` (修改, +10/-3 lines)

**修改目的**：验证 expireSnapshots 不再产生空的 RemovePartitionSpecs 更新。

**工作逻辑**：将原 `testNoSchemasToRemove` 重命名为 `testNoSchemasOrSpecsToRemove`，新增 `Mockito.verify(ops, Mockito.never()).commit(any(), argThat(meta -> meta.changes().stream().anyMatch(u -> u instanceof MetadataUpdate.RemovePartitionSpecs)))` 断言，确保 commit 中不包含 RemovePartitionSpecs 更新。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java` (修改, +16 lines)

**修改目的**：直接测试 `removeSpecs` 方法在空集合和非空集合下的行为。

**工作逻辑**：新增 `testMetadataWithRemoveSpecs` 测试：传入空集合时验证 `changes()` 中不含 `RemovePartitionSpecs`；传入 `{1, 2}` 时验证 `changes()` 中含 `RemovePartitionSpecs`。

## 小结

- **成效**：修复了 `removeSpecs` 在无 spec 需移除时仍创建空更新的问题，减少元数据噪声和不必要的 commit。
- **影响范围**：仅 `TableMetadata.java` 的 `removeSpecs` 方法，影响 `expireSnapshots` + `cleanExpiredMetadata` 的行为。属于 bug 修复，低风险。
- **回迁到 1.4.x 的注意事项**：修复简单且安全，建议回迁。1.4.x 分支的 `TableMetadata.removeSpecs` 方法若有相同问题，直接 cherry-pick。测试改动也应一并回迁以验证修复效果。
