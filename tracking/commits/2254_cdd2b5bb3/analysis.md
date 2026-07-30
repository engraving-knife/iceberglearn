# 提交 2254：Core: Clean expired metadata even if there is no snapshot to expire (#13322)

## 提交信息

- **序号**：2254 / 4088
- **哈希**：cdd2b5bb3e396d8e9264c3d79d340be1ea85c848
- **短哈希**：cdd2b5bb3
- **日期**：2025-06-18 22:20:57 +0200
- **作者**：Rui Li
- **提交说明**：Core: Clean expired metadata even if there is no snapshot to expire
- **PR/Issue**：#13322

## 总体目的

本提交修复了 `RemoveSnapshots`（快照过期清理）中的一个逻辑缺陷：当表没有快照需要过期时，过期元数据清理（如清理旧的 schema、partition spec 等元数据）也会被跳过。

在原有的实现中，`internalApply()` 方法在检测到 `base.snapshots().isEmpty()` 时直接返回当前元数据，跳过了所有后续处理。这意味着即使表上积累了大量由于 schema 演进或分区规范演进产生的过期元数据（如旧的 schema 定义、旧的 partition spec），只要没有快照需要过期，这些元数据就不会被清理。这在实际使用中会导致元数据文件持续膨胀，影响表的加载性能和管理效率。

## 如何达成设计目的

- 修改 `internalApply()` 的提前返回条件，从 `base.snapshots().isEmpty()` 改为 `base.snapshots().isEmpty() && !cleanExpiredMetadata`。
- 当 `cleanExpiredMetadata` 为 true 时，即使没有快照需要过期，也会继续执行后续逻辑，让表元数据构建器处理过期元数据的清理。
- 表元数据构建器本身已经具备判断何时应该是 no-op 的逻辑，因此不需要额外的保护。
- 新增两个测试用例验证修复行为：一个验证无快照时元数据仍能被清理，另一个验证确实无任何东西需要清理时的 no-op 行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/RemoveSnapshots.java` (修改, +4/-1 lines)

**修改目的**：允许在没有快照需要过期时仍执行元数据清理。

**工作逻辑**：将 `internalApply()` 方法中的提前返回条件从 `if (base.snapshots().isEmpty())` 修改为 `if (base.snapshots().isEmpty() && !cleanExpiredMetadata)`。添加注释说明"即使没有快照需要过期，也尝试清理过期的元数据；表元数据构建器会处理实际应为 no-op 的情况"。这样当用户配置了 `cleanExpiredMetadata(true)` 时，即使快照列表为空，方法也不会提前返回，而是继续执行到表元数据提交阶段，由元数据构建器负责实际的清理逻辑。

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java` (修改, +30/0 lines)

**修改目的**：验证修复的正确性和 no-op 安全性。

**工作逻辑**：
1. `testRemoveMetadataWithNoSnapshots()`：先对表执行多次 schema 演进（添加两列）和分区规范演进（添加分区字段），使表积累 3 个 schema 和 2 个 spec。然后执行快照过期（`expireOlderThan(now).retainLast(1).cleanExpiredMetadata(true)`），验证过期后 schema 数量减为 1、spec 数量减为 1，证明元数据被正确清理。
2. `testRemoveSnapshotsNoOp()`：在没有做过任何修改的表上执行快照过期，验证返回的 TableMetadata 与当前完全相同（`isSameAs`），证明在确实没有东西需要清理时操作是安全的 no-op。

## 总结

本提交修复了一个元数据清理的逻辑缺陷，确保当用户请求清理过期元数据时，即使没有快照需要过期，元数据清理逻辑也能正常执行。修复方式简洁——仅修改一个条件判断，同时依赖表元数据构建器已有的 no-op 保护机制确保安全性。这是一个影响表维护效率的实用修复。
