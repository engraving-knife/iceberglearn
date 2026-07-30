# 提交 2933：Spark 4.0: expire-snapshots with cleanupLevel=None (#14695)

## 提交信息

- **序号**：2933 / 4088
- **哈希**：8a38d6e389758d372af63c4d56619c5a18832d2f
- **短哈希**：8a38d6e38
- **日期**：2025-11-29 18:01:58 +0100
- **作者**：Alessandro Nori
- **提交说明**：Spark 4.0: expire-snapshots with cleanupLevel=None (#14695)
- **PR/Issue**：#14695

## 总体目的

`ExpireSnapshotsSparkAction` 是 Iceberg Spark 模块提供的分布式快照过期（expire-snapshots）action，它利用 Spark 的并行能力来删除过期快照相关的数据文件与元数据文件。在执行过期逻辑时，该 action 会调用 core 层的 `ExpireSnapshots` API 完成"过期"步骤（移除快照元数据），而文件清理工作则由 Spark action 自身在分布式框架中更高效地完成。因此，在调用 core 层 `ExpireSnapshots` 时，需要明确告诉它**不要做文件清理**，以避免重复清理。

在此次改动前，Spark 4.0 版本的 `ExpireSnapshotsSparkAction` 通过 `expireSnapshots.cleanExpiredFiles(false).commit()` 来关闭 core 层的文件清理。然而 `cleanExpiredFiles(boolean)` 方法自 Iceberg 1.11.0 起已被标记为 `@Deprecated`（计划在 2.0.0 移除），其替代 API 是更细粒度的 `cleanupLevel(CleanupLevel)`。继续使用弃用方法会产生弃用警告，且在 2.0.0 后将无法编译。

本提交将 Spark 4.0 中的调用从弃用的 `cleanExpiredFiles(false)` 切换为 `cleanupLevel(CleanupLevel.NONE)`，消除弃用警告并为未来 2.0.0 升级做准备。`CleanupLevel.NONE` 表示"跳过所有文件清理，仅移除快照元数据"，语义上等价于原先的 `cleanExpiredFiles(false)`。

## 如何达成设计目的

改动仅涉及 Spark 4.0 模块下的 `ExpireSnapshotsSparkAction.java`：新增对 `org.apache.iceberg.ExpireSnapshots.CleanupLevel` 的导入，并将过期提交前的调用由 `expireSnapshots.cleanExpiredFiles(false).commit()` 改为 `expireSnapshots.cleanupLevel(CleanupLevel.NONE).commit()`。改动局限在 v4.0 目录（`spark/v4.0/spark/`），与 PR 标题"Spark 4.0:"对应。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/ExpireSnapshotsSparkAction.java` (+2/-1 lines)

**修改目的**：用非弃用的 `cleanupLevel(CleanupLevel.NONE)` 替代弃用的 `cleanExpiredFiles(false)`。

**工作逻辑**：

1. **新增导入**：`import org.apache.iceberg.ExpireSnapshots.CleanupLevel;`，引入 `ExpireSnapshots` 接口中定义的嵌套枚举。

2. **替换调用**：

```java
// 旧
expireSnapshots.cleanExpiredFiles(false).commit();
// 新
expireSnapshots.cleanupLevel(CleanupLevel.NONE).commit();
```

`CleanupLevel` 是 `ExpireSnapshots` 接口中的嵌套枚举，定义了三档清理级别：
- `NONE`：跳过所有文件清理，仅移除快照元数据；
- `METADATA_ONLY`：仅清理元数据文件（manifest、manifest list、统计文件），保留数据文件；
- `ALL`：同时清理元数据与数据文件（默认）。

由于 `ExpireSnapshotsSparkAction` 自身在 Spark 分布式框架中负责删除过期文件，调用 core 层过期时设为 `NONE`，让 core 层只负责移除快照元数据，避免重复删除。这与原先 `cleanExpiredFiles(false)` 的语义完全一致，但使用了 1.11.0 引入的非弃用 API。原方法 `cleanExpiredFiles(boolean)` 的 Javadoc 明确标注 `@deprecated since 1.11.0, will be removed in 2.0.0; use cleanupLevel(CleanupLevel)`。

## 总结

该提交将 Spark 4.0 的 `ExpireSnapshotsSparkAction` 中关闭 core 层文件清理的调用从弃用的 `cleanExpiredFiles(false)` 切换为 `cleanupLevel(CleanupLevel.NONE)`，消除弃用警告并避免在 Iceberg 2.0.0 移除旧 API 后编译失败。语义行为不变，是一次面向未来版本兼容性的前瞻性清理。
