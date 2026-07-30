# 提交 3008：Spark, Flink: replace deprecated cleanExpiredFiles in expireSnapshots (#14832)

## 提交信息

- **序号**：3008 / 4088
- **哈希**：9a048825fc8d83982ca6d4ea27c07c1c573cd8a6
- **短哈希**：9a048825f
- **日期**：2025-12-12 15:07:03 -0800
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Spark, Flink: replace deprecated cleanExpiredFiles in expireSnapshots (#14832)
- **PR/Issue**：#14832

## 总体目的

Iceberg 的快照过期（expire snapshots）API `ExpireSnapshots` 在 core 层提供，引擎模块（Spark、Flink）在调用它时有两种典型用法：一是让 core 层自行清理过期文件（`cleanExpiredFiles(true)`），二是在调用 core 层只完成元数据过期、文件清理交给引擎的分布式 action 自己做（`cleanExpiredFiles(false)`）。早期 API 用布尔方法 `cleanExpiredFiles(boolean)` 表达这一开关。

随着对清理粒度需求的细化，`ExpireSnapshots` 引入了更细粒度的 `cleanupLevel(CleanupLevel)` API 与三档枚举 `CleanupLevel`：`NONE`（跳过所有文件清理，仅移除快照元数据）、`METADATA_ONLY`（仅清理 manifest/manifest list 等元数据文件，保留数据文件）、`ALL`（完整清理数据与元数据文件）。旧的 `cleanExpiredFiles(boolean)` 自 1.11.0 起被标记 `@Deprecated`，计划在 2.0.0 移除：`cleanExpiredFiles(false)` 等价于 `cleanupLevel(NONE)`，`cleanExpiredFiles(true)` 等价于 `cleanupLevel(ALL)`。继续使用弃用方法会产生弃用警告，且在 2.0.0 升级后将无法编译。

此前已有提交（如 #14695，对应序号 2933）将 Spark 4.0 的 `ExpireSnapshotsSparkAction` 切换到 `cleanupLevel`。本提交继续把剩余仍使用弃用方法的位置——Spark 3.4/3.5 的 action、Flink 1.20/2.0/2.1 的 `ExpireSnapshotsProcessor`、以及共享的 `CatalogTests`——统一迁移到 `cleanupLevel(CleanupLevel)`，消除弃用警告并为 2.0.0 做准备。

## 如何达成设计目的

整体思路是机械替换：对每处 `cleanExpiredFiles(false)` 改为 `cleanupLevel(ExpireSnapshots.CleanupLevel.NONE)`，对每处 `cleanExpiredFiles(true)` 改为 `cleanupLevel(ExpireSnapshots.CleanupLevel.ALL)`，并补上对应 `CleanupLevel` 的 import。改动覆盖 6 个文件，分布在 core 的共享测试基类 `CatalogTests`、两个 Spark 版本（v3.4、v3.5）的 `ExpireSnapshotsSparkAction`、三个 Flink 版本（v1.20、v2.0、v2.1）的 `ExpireSnapshotsProcessor`。语义保持等价，不改任何清理行为。

## 修改详情

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (+2/-1 lines)

**修改目的**：在共享的 catalog 测试基类中用非弃用 API 替代弃用方法。

**工作逻辑**：
新增 `import org.apache.iceberg.ExpireSnapshots;`，并将 `expireSnapshots().cleanExpiredFiles(false)` 改为 `expireSnapshots().cleanupLevel(ExpireSnapshots.CleanupLevel.NONE)`。该测试场景意在只过期快照元数据、跳过文件清理（`CleanupLevel.NONE`），与原 `cleanExpiredFiles(false)` 语义一致。`CatalogTests` 是所有 catalog 实现的共享测试基类，改动一处即覆盖所有 catalog 的相应测试。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/ExpireSnapshotsProcessor.java` (+1/-1 lines)

**修改目的**：把 Flink 1.20 维护算子中的弃用调用替换为 `cleanupLevel(ALL)`。

**工作逻辑**：
将 `.cleanExpiredFiles(true)` 改为 `.cleanupLevel(ExpireSnapshots.CleanupLevel.ALL)`。`ExpireSnapshotsProcessor` 是 Flink 维护流程中执行快照过期的算子，此处原意是让 core 层在过期时完整清理数据与元数据文件（`ALL`），等价于原 `cleanExpiredFiles(true)`。算子自身已通过 `deleteWith` 把要删除的文件输出到 `DELETE_STREAM` 做计数，core 层的 `ALL` 清理与之配合完成实际删除。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/ExpireSnapshotsProcessor.java` (+1/-1 lines)

**修改目的**：同上，对 Flink 2.0 版本做相同替换。

**工作逻辑**：
与 v1.20 完全一致，`.cleanExpiredFiles(true)` → `.cleanupLevel(ExpireSnapshots.CleanupLevel.ALL)`。Flink 各版本目录结构平行，每个版本需单独修改。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/ExpireSnapshotsProcessor.java` (+1/-1 lines)

**修改目的**：同上，对 Flink 2.1 版本做相同替换。

**工作逻辑**：
与 v1.20、v2.0 完全一致，`.cleanExpiredFiles(true)` → `.cleanupLevel(ExpireSnapshots.CleanupLevel.ALL)`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/ExpireSnapshotsSparkAction.java` (+2/-1 lines)

**修改目的**：把 Spark 3.4 分布式过期 action 中对 core 层的弃用调用替换为 `cleanupLevel(NONE)`。

**工作逻辑**：
新增 `import org.apache.iceberg.ExpireSnapshots.CleanupLevel;`，并将 `expireSnapshots.cleanExpiredFiles(false).commit()` 改为 `expireSnapshots.cleanupLevel(CleanupLevel.NONE).commit()`。`ExpireSnapshotsSparkAction` 自身会在 Spark 分布式框架中更高效地删除文件，因此在调用 core 层 `ExpireSnapshots` 时设为 `NONE`，让 core 层仅移除快照元数据、跳过文件删除，避免重复清理（与序号 2933 处理 Spark 4.0 的逻辑一致）。语义等价于原 `cleanExpiredFiles(false)`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/ExpireSnapshotsSparkAction.java` (+2/-1 lines)

**修改目的**：同上，对 Spark 3.5 版本做相同替换。

**工作逻辑**：
与 Spark 3.4 完全一致：新增 `CleanupLevel` import，`cleanExpiredFiles(false)` → `cleanupLevel(CleanupLevel.NONE)`。Spark 3.4 与 3.5 的 `ExpireSnapshotsSparkAction` 源码在该处相同，改动也相同。

## 总结

该提交是一次聚焦的弃用 API 迁移，把 Spark 3.4/3.5、Flink 1.20/2.0/2.1 以及共享 `CatalogTests` 中对已弃用 `cleanExpiredFiles(boolean)` 的调用统一替换为更细粒度的 `cleanupLevel(CleanupLevel)`（`false`→`NONE`、`true`→`ALL`）。语义完全等价、不改任何清理行为，核心价值在于消除弃用警告、统一各引擎版本对 core 层过期 API 的调用方式，并为未来 Iceberg 2.0.0 移除旧 API 后仍可顺利编译升级扫清障碍，是继 Spark 4.0 之后对弃用方法清理的延续与补全。
