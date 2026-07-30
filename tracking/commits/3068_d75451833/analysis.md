# 提交 3068：Core: Reduce manifest logging noise on drop table (#14969)

## 提交信息

- **序号**：3068 / 4088
- **哈希**：d75451833a91c2e747b1f9f6e141b83e7679614f
- **短哈希**：d75451833
- **日期**：2026-01-06
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core: Reduce manifest logging noise on drop table (#14969)
- **PR/Issue**：#14969

## 总体目的

本提交旨在降低执行 `drop table` 操作时的 manifest 日志噪音。在 `CatalogUtil` 的 drop table 流程中，需要删除该表关联的所有 manifest 文件。原有实现使用 `LOG.info("Manifests to delete: {}", Joiner.on(", ").join(manifestsToDelete));` 将所有待删除 manifest 的完整路径以逗号分隔拼成一个长字符串，以 INFO 级别输出。对于生产环境中数据量大的表，manifest 文件数量可能非常庞大（成百上千甚至更多），这会在日志中产生极长的单行输出，淹没其他有用信息，增加日志排查难度，并可能对日志收集系统造成压力。

修复后的策略遵循日志最佳实践：INFO 级别只输出待删除 manifest 的数量（`"{} Manifests to delete "`），让运维人员快速了解规模而不被细节淹没；具体的 manifest 路径降级到 DEBUG 级别逐条输出，仅在实际排障需要时通过开启 DEBUG 模式查看。

## 如何达成设计目的

改动仅涉及 `CatalogUtil.java` 一个文件。将单条 INFO 级别的全量路径拼接日志，拆分为一条 INFO 级别的计数日志和一组 DEBUG 级别的逐条路径日志，并移除不再使用的 `Joiner` import。

## 修改详情

### `core/src/main/java/org/apache/iceberg/CatalogUtil.java` (+6/-2 lines)

**修改目的**：将 drop table 时 manifest 删除日志从 INFO 级全量输出降级为 INFO 计数 + DEBUG 逐条输出。

**工作逻辑**：
- 移除 import `org.apache.iceberg.relocated.com.google.common.base.Joiner`，因为不再需要拼接路径。
- 将原 `LOG.info("Manifests to delete: {}", Joiner.on(", ").join(manifestsToDelete));` 替换为两段逻辑：
  1. `LOG.info("{} Manifests to delete ", manifestsToDelete.size());`——以 INFO 级别输出待删除 manifest 的数量，便于快速了解规模。
  2. `if (LOG.isDebugEnabled()) { for (ManifestFile manifest : manifestsToDelete) { LOG.debug("Deleting manifest file: {}", manifest.path()); } }`——在 DEBUG 开启时逐条输出 manifest 路径。使用 `isDebugEnabled()` 守卫避免在 DEBUG 关闭时不必要的字符串构造与方法调用开销。

## 总结

本提交通过将 drop table 的 manifest 删除日志从 INFO 级全量路径拼接改为 INFO 计数 + DEBUG 逐条，有效减少了生产环境日志噪音，同时保留了需要排障时通过 DEBUG 级别查看明细的能力，是一个务实的日志优化。
