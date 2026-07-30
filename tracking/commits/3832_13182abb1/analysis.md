# 提交 3832：Core: Skip unnecessary manifest scans during expire snapshots metadata cleanup (#16691)

## 提交信息

- **序号**：3832 / 4088
- **哈希**：13182abb185220787d2ce13dd9ea025905f83800
- **短哈希**：13182abb1
- **日期**：2026-06-06 18:14:39 -0700
- **作者**：Yujiang Zhong <42907416+zhongyujiang@users.noreply.github.com>
- **提交说明**：Core: Skip unnecessary manifest scans during expire snapshots metadata cleanup (#16691)
- **PR/Issue**：#16691
- **协作者**：zhongyujiang、Claude Opus 4.6 (1M context)

## 总体目的

本提交优化 `expire snapshots`（快照过期）操作在"清理过期元数据"（`cleanExpiredMetadata`）阶段的行为，减少不必要的 manifest 扫描 I/O。在过期快照时，Iceberg 需要确定哪些 partition spec（分区规范）仍被保留的快照引用，以便清理不再被引用的旧 spec。原实现对每个保留的快照都调用 `snapshot.allManifests(io)` 读取其所有 manifest list，从中收集 `partitionSpecId` 到 `reachableSpecs` 集合。这意味着即使表只有一个 partition spec（最常见的场景，无分区或单一分区方案），也会扫描所有保留快照的全部 manifest list，造成大量无谓的 I/O。

本提交引入两层优化：
1. **单 spec 表直接跳过**：如果表当前只有一个 spec（`base.specs().size() == 1`），那么该 spec 必然是默认 spec，且所有快照都引用它，无需扫描任何 manifest 来确认 reachable specs。直接跳过整个 manifest 扫描。
2. **提前终止**：对于多 spec 表，在扫描过程中一旦 `reachableSpecs.size()` 达到 `base.specs().size()`（即所有已知 spec 都已被确认可达），就停止扫描剩余快照的 manifest，因为继续扫描不会再发现新 spec。

这两个优化对大表（快照多、manifest 多）的过期操作性能有显著提升，尤其是单 spec 表（绝大多数生产表的常见情况）可以完全避免该阶段的 I/O。

## 如何达成设计目的

在 `RemoveSnapshots` 收集 reachable specs 的循环中：
- 循环前计算 `mayHaveExpiredSpecs = base.specs().size() > 1`，作为单 spec 快速跳过条件。
- 循环内增加条件 `if (mayHaveExpiredSpecs && reachableSpecs.size() < base.specs().size())`，仅当可能存在过期 spec 且尚未收集齐所有 spec 时才扫描该快照的 manifest。一旦收集齐，后续快照直接跳过 manifest 扫描（仍记录 schema）。
- 提交说明中提到简化了 `mayHaveExpiredSpecs` 判断：原 `anyMatch` 检查（判断唯一 spec 是否为默认 spec）是不可达的，因为默认 spec 始终在 specs 列表中，`size()==1` 时该唯一 spec 必为默认 spec，故直接用 size 判断即可。

## 修改详情

### `core/src/main/java/org/apache/iceberg/RemoveSnapshots.java` (+7/-3 lines)

**修改目的**：在收集 reachable specs 时跳过不必要的 manifest 扫描。

**工作逻辑**：
- 循环前新增 `boolean mayHaveExpiredSpecs = base.specs().size() > 1;`：
```java
boolean mayHaveExpiredSpecs = base.specs().size() > 1;
```
- 在 `Tasks.foreach(idsToRetain).run(...)` 的 lambda 中，把原本无条件的 manifest 扫描改为有条件执行：
```java
if (mayHaveExpiredSpecs && reachableSpecs.size() < base.specs().size()) {
  snapshot.allManifests(ops.io()).stream()
      .map(ManifestFile::partitionSpecId)
      .forEach(reachableSpecs::add);
}
reachableSchemas.add(snapshot.schemaId());
```
- 单 spec 表（`mayHaveExpiredSpecs=false`）直接跳过 manifest 扫描。
- 多 spec 表在 `reachableSpecs` 收集齐所有 spec 后，剩余快照也跳过扫描。
- `reachableSchemas` 仍按快照记录（schema 收集逻辑不变，因为 schema 数量与扫描成本关系不同）。

## 总结

本提交通过两层优化显著减少 `expire snapshots` 在元数据清理阶段的 manifest 扫描 I/O：单 spec 表完全跳过，多 spec 表在收集齐所有 spec 后提前终止。这对大表过期操作的性能提升显著，且改动极小、风险低。逻辑正确性有保证——单 spec 表的所有快照必然引用唯一 spec，多 spec 表收集齐后无新 spec 可发现。本提交由人与 AI（Claude Opus 4.6）协作完成，体现了 AI 辅助在性能优化分析中的应用。
