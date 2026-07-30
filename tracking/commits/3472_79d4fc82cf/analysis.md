# 提交 3472：Core: Add manifest partition pruning to DV validation in MergingSnapshotProducer (#15653)

## 提交信息

- **序号**：3472 / 4088
- **哈希**：79d4fc82cf9ef2266fd53beabd8a744789744bed
- **短哈希**：79d4fc82cf
- **日期**：2026-03-27 16:06:51 -0700
- **作者**：Anoop Johnson
- **提交说明**：Core: Add manifest partition pruning to DV validation in MergingSnapshotProducer (#15653)
- **PR/Issue**：#15653

## 总体目的

在 `MergingSnapshotProducer` 的 DV（Deletion Vector）验证中添加清单分区裁剪（manifest partition pruning）优化。之前在验证冲突检测时，会遍历所有新的删除清单（delete manifests）来检查 DV 冲突，即使大部分清单的分区与冲突检测过滤器（conflict detection filter）完全无关。此优化通过分区裁剪跳过不相关的清单，显著提升验证性能。

## 如何达成设计目的

- 新增 `filterManifestsByPartition()` 方法，使用 `ManifestEvaluator` 按分区过滤清单
- 在 DV 验证前过滤出与冲突检测过滤器匹配的清单
- 同时过滤出仅包含新增文件的清单（`hasAddedFiles`）
- 处理分区规范变更的边界情况：如果有清单使用不同的分区规范，跳过裁剪

## 修改详情

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java` (+40/-1 lines)

**修改目的**：在 DV 验证前添加清单分区裁剪。

**工作逻辑**：

1. **修改 DV 验证入口**：
   ```java
   Iterable<ManifestFile> matchingManifests =
       Iterables.filter(
           filterManifestsByPartition(base, conflictDetectionFilter, newDeleteManifests),
           ManifestFile::hasAddedFiles);
   
   Tasks.foreach(matchingManifests)
       ...
   ```
   - 先通过 `filterManifestsByPartition` 按分区裁剪
   - 再通过 `hasAddedFiles` 过滤出包含新增文件的清单

2. **新增 `filterManifestsByPartition()` 方法**：
   ```java
   private Iterable<ManifestFile> filterManifestsByPartition(
       TableMetadata base, Expression conflictDetectionFilter, List<ManifestFile> manifests) {
   ```
   
   关键逻辑：
   - 如果冲突检测过滤器为 null 或 `alwaysTrue()`，跳过裁剪返回所有清单
   - 如果任何清单使用了与默认分区规范不同的 spec，跳过裁剪（避免错误排除）
   - 否则，为每个分区规范创建 `ManifestEvaluator`
   - 使用 `Projections.inclusive()` 将冲突检测过滤器投影到分区过滤器
   - 使用 `ManifestEvaluator.forPartitionFilter()` 评估每个清单是否匹配

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java` (+163/-0 lines)

**修改目的**：添加分区裁剪验证测试。

**工作逻辑**：
- 添加测试验证 DV 验证在分区裁剪后仍然正确检测冲突
- 测试不同分区场景下的验证行为
- 验证分区规范变更时的安全回退行为

## 总结

该提交在 `MergingSnapshotProducer` 的 DV 验证中添加了清单分区裁剪优化。通过使用 `ManifestEvaluator` 按分区过滤不相关的清单，减少不必要的 DV 冲突检查，显著提升写入性能。同时安全处理了分区规范变更的边界情况，确保不会错误排除需要验证的清单。
