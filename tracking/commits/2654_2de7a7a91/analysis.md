# 提交 2654：Core: Make deprecated methods package-private in PartitionStats (#14119)

## 提交信息

- **序号**：2654 / 4088
- **哈希**：2de7a7a911c8cb5e621a6440409148e5e79c5740
- **短哈希**：2de7a7a91
- **日期**：2025-09-19 11:09:49 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Core: Make deprecated methods package-private in PartitionStats (#14119)
- **PR/Issue**：#14119

## 总体目的

本提交将 `PartitionStats` 类中三个在 1.10.0 标记为弃用的方法从 `public` 可见性降低为 package-private（包级私有）。这是 1.11.0 版本弃用策略的一部分——与提交 2651（#14059）直接删除弃用代码不同，本提交采用更温和的方式：先降低可见性，限制外部使用，为后续完全移除做准备。

这三个方法（`liveEntry`、`deletedEntry`、`appendStats`）在 1.10.0 的 Javadoc 中已注明"visibility will be reduced in 1.11.0"（可见性将在 1.11.0 降低）。本提交正是兑现这一承诺，将它们从 public 降级为 package-private，同时移除 `@Deprecated` 注解（因为降级后外部已无法访问，不再需要弃用标记）。

这种渐进式清理策略比直接删除更安全，因为它不会立即破坏仍在使用这些方法的代码的编译（只是限制了访问范围），同时明确表达了这些方法不属于公共 API 的意图。

## 如何达成设计目的

通过两个文件的协同修改达成目标：

1. 在 `PartitionStats.java` 中，将三个方法的 `public` 修饰符移除（使其变为 package-private），并移除 `@Deprecated` 注解和相关的 Javadoc 弃用说明。
2. 在 `.palantir/revapi.yml` 中记录这三处可见性降低的 API 破坏性变更，使 Revapi 兼容性检查通过。

## 修改详情

### `.palantir/revapi.yml` (+12/-0 lines)

**修改目的**：记录三处方法可见性降低的 API 破坏性变更。

**工作逻辑**：在 `acceptedBreaks` 的 `"1.10.0"` 版本块下，新增三条 `java.method.visibilityReduced` 记录，分别对应 `PartitionStats.liveEntry(ContentFile<?>, Snapshot)`、`PartitionStats.appendStats(PartitionStats)` 和 `PartitionStats.deletedEntry(Snapshot)`，每条标注 `justification: "Changing deprecated code"`。

### `core/src/main/java/org/apache/iceberg/PartitionStats.java` (+3/-9 lines)

**修改目的**：将三个弃用方法从 public 降级为 package-private。

**工作逻辑**：对三个方法做相同处理：
- `liveEntry(ContentFile<?> file, Snapshot snapshot)`：移除 `@Deprecated // will become package-private` 注解，将 `public void` 改为 `void`。同时从 Javadoc 中移除 `@deprecated since 1.10.0, visibility will be reduced in 1.11.0` 说明。
- `deletedEntry(Snapshot snapshot)`：同上处理。
- `appendStats(PartitionStats entry)`：同上处理。

三个方法的方法体和参数逻辑均未改变，仅调整了可见性和注解。

## 总结

本提交是 1.11.0 弃用清理工作的一部分，采用渐进式策略将 `PartitionStats` 的三个内部方法从 public 降级为 package-private。与直接删除（如提交 2651）相比，这种方式更温和，限制了外部访问同时不立即破坏现有代码编译。通过 revapi.yml 记录了这些 API 可见性变更，确保 CI 兼容性检查通过。这些方法降级后表明它们仅用于 core 模块内部，不属于公共 API 契约。
