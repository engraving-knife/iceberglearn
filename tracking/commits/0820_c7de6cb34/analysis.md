# 提交 0820：Core: Reword exception message in RewriteManifests validation (#10446)

## 提交信息
- **序号**：0820 / 4088
- **哈希**：c7de6cb345995cb47312edbef6edae2f17fb8aba
- **短哈希**：c7de6cb34
- **日期**：2024-06-06 18:39:06 -0600（作者时区 2024-06-07 06:09:06 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Core: Reword exception message in RewriteManifests validation (#10446)
- **PR/Issue**：#10446

## 总体目的

本提交改进 `BaseRewriteManifests` 中"删除 manifest 校验"失败时抛出的异常信息，使其更具诊断价值。

在 `RewriteManifests`（重写 manifest）操作中，用户会指定一组要删除的旧 manifest（`deletedManifests`）。提交时 `validateDeletedManifests` 会校验这些待删除的 manifest 仍存在于当前快照中——如果某个待删除 manifest 已经不在最新快照里（通常是因为并发提交已经替换/移除了它），则抛出 `ValidationException`。

原来的异常信息是 `"Manifest is missing: %s"`，仅包含 manifest 路径，信息不够明确：既没说明是"被删除的 manifest"找不到，也没指出是在哪个快照里找不到，用户在排查并发冲突时缺乏上下文。

本提交将异常信息改为 `"Deleted manifest %s could not be found in the latest snapshot %d"`，同时包含 manifest 路径和当前快照 ID。这样用户一眼就能看出：是哪个 manifest、在哪个快照里找不到，从而快速定位是并发提交导致的冲突，便于诊断和维护任务失败排查。

## 如何达成设计目的

改动很小，分两步：

1. **给 `validateDeletedManifests` 方法增加 `currentSnapshotID` 参数**：调用处传入 `base.currentSnapshot().snapshotId()`，使方法内部能拿到当前快照 ID 用于异常信息。方法签名从 `validateDeletedManifests(Set<ManifestFile>)` 改为 `validateDeletedManifests(Set<ManifestFile>, long currentSnapshotID)`。

2. **改写异常信息**：将 `throw new ValidationException("Manifest is missing: %s", manifest.path())` 改为 `throw new ValidationException("Deleted manifest %s could not be found in the latest snapshot %d", manifest.path(), currentSnapshotID)`，把快照 ID 也格式化进消息。

测试侧同步更新两处断言（`TestRewriteManifests`），把 `hasMessageStartingWith("Manifest is missing")` 改为用 `String.format` 构造完整的新消息前缀进行匹配，确保新消息格式被测试覆盖。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseRewriteManifests.java`
**修改目的**：改进 manifest 删除校验失败的异常信息，加入快照 ID。
**工作逻辑**：
- 调用处（`apply`/提交校验流程）：`validateDeletedManifests(currentManifestSet)` → `validateDeletedManifests(currentManifestSet, base.currentSnapshot().snapshotId())`，把当前快照 ID 传入。
- 方法定义：签名增加 `long currentSnapshotID` 参数；异常信息从 `"Manifest is missing: %s"` 改为 `"Deleted manifest %s could not be found in the latest snapshot %d"`，同时格式化 manifest 路径和快照 ID。校验逻辑本身（检查 `deletedManifests` 是否都存在于 `currentManifests` 集合中）不变。

### `core/src/test/java/org/apache/iceberg/TestRewriteManifests.java`
**修改目的**：更新两处测试断言以匹配新的异常信息格式。
**工作逻辑**：两处 `assertThatThrownBy(rewriteManifests::commit)` 的断言从 `hasMessageStartingWith("Manifest is missing")` 改为 `hasMessageStartingWith(String.format("Deleted manifest %s could not be found in the latest snapshot %d", <manifestPath>, table.currentSnapshot().snapshotId()))`，分别使用对应的 manifest 路径（`firstSnapshotManifest.path()` 和 `originalDeleteManifest.path()`），精确验证新消息内容和快照 ID。

## 小结
- **成效**：异常信息从模糊的"Manifest is missing"改进为明确的"Deleted manifest X could not be found in the latest snapshot Y"，包含 manifest 路径和快照 ID，显著提升并发冲突场景下的可诊断性。
- **影响范围**：仅影响 `BaseRewriteManifests` 的校验异常信息和对应测试，不改变任何校验逻辑或控制流。可能影响依赖原异常消息文本做匹配的下游代码（如日志告警规则、测试断言）。
- **回迁注意事项**：改动小且纯局部，回迁风险极低。回迁时需注意：(1) 若 1.4.x 的 `BaseRewriteManifests` 调用点结构与 main 不同（如调用次数/位置有差异），需逐一找到 `validateDeletedManifests` 调用点补传快照 ID 参数；(2) 若有下游测试或监控以字符串 "Manifest is missing" 做匹配，需同步更新为新消息前缀 "Deleted manifest"；(3) 该异常是 `ValidationException`（CommitFailedException 体系），不重试，消息变更不影响重试行为。
