# 提交 1823：Core: Ensure current and newly added view versions are retained in ViewMetadata build (#12401)

## 提交信息

- **序号**：1823 / 4088
- **哈希**：cc4fe4cc50043ccba89700f7948090ff87a5baee
- **短哈希**：cc4fe4cc5
- **日期**：2025-03-05 09:18:01 -0700
- **作者**：Leon Lin
- **提交说明**：Core: Ensure current and newly added view versions are retained in ViewMetadata build (#12401)
- **PR/Issue**：#12401

## 总体目的

该提交修复了 ViewMetadata 构建时版本过期逻辑的一个缺陷：在计算需要保留的视图版本数量时，未将"当前版本"纳入保留集合，可能导致当前版本被错误地过期清除。

Iceberg 的视图（View）维护版本历史，通过 `ViewProperties.VERSION_HISTORY_SIZE` 控制保留的版本数量。在 `ViewMetadata.Builder.build()` 中，会根据历史大小限制保留的版本数，但保证至少保留本次构建中新增的版本。

原逻辑仅统计 `changes(AddViewVersion.class)` 的数量（即本次构建新增的版本数）作为下限。问题在于：如果在一次构建中只新增了新版本但未将当前版本设为新版本，或当前版本不是本次新增的版本之一，那么当前版本（`currentVersionId` 对应的版本）可能不在"新增版本"集合中。当 `VERSION_HISTORY_SIZE` 较小且新增版本数超过历史大小时，当前版本可能被错误地过期清除，导致视图丢失其当前活跃版本。

本提交通过将当前版本 ID 与新增版本 ID 合并去重后计算保留数量，确保当前版本和所有新增版本都得到保留。

## 如何达成设计目的

修改 `ViewMetadata.Builder.build()` 中计算 `numVersionsToKeep` 的逻辑：不再简单使用 `changes(AddViewVersion.class).count()`（新增版本计数），而是构建一个 `ImmutableSet`，收集所有 AddViewVersion 变更对应的版本 ID，并加入 `currentVersionId`，然后取集合大小。这样既去重了可能重复的版本，又确保当前版本被纳入保留集合。最终 `numVersionsToKeep = Math.max(numVersions, historySize)`。

## 修改详情

### core/src/main/java/org/apache/iceberg/view/ViewMetadata.java (修改, 多行)

- 新增 `import ImmutableSet`。
- 修改 `build()` 方法中的版本保留计算逻辑：
  - 旧代码：`int numAddedVersions = (int) changes(MetadataUpdate.AddViewVersion.class).count();` 然后 `int numVersionsToKeep = Math.max(numAddedVersions, historySize);`
  - 新代码：构建 `ImmutableSet`，addAll 从 `changes(AddViewVersion.class).map(v -> v.viewVersion().versionId())` 收集的版本 ID 集合，再 `add(currentVersionId)`，最后取 `.size()` 作为 `numVersions`。`numVersionsToKeep = Math.max(numVersions, historySize)`。
- 注释更新为"expire old versions, but keep at least the versions added in this builder and the current version"。

### core/src/test/java/org/apache/iceberg/view/TestViewMetadata.java (修改, 25 lines)

新增 `versionsAddedInCurrentBuildAreRetained` 测试：构建含 3 个版本（v1/v2/v3）的视图，设置 VERSION_HISTORY_SIZE=2。验证首次构建保留 v1；二次构建添加 v2、v3 后全部保留（当前 v1 + 新增 v2/v3）；再次重建后过期 v2，保留当前 v1 和最新 v3。覆盖了当前版本与新版本同时存在的场景。

## 小结

该提交修复了视图版本过期可能误删当前版本的缺陷，逻辑改动精准。影响范围为 core 模块的 view 元数据管理。回迁到 1.4.x 分支时，若该分支已包含 ViewMetadata 的版本过期逻辑，可直接 cherry-pick；改动仅涉及 build() 方法的几行计算逻辑，回迁风险低。建议回迁以避免视图当前版本丢失问题。注意完整哈希对应的短哈希为 `cc4fe4cc5`（取前9位）。
