# 提交 3003：Flink: Backport: Log on cache refresh in dynamic sink (#14828)a

## 提交信息

- **序号**：3003 / 4088
- **哈希**：cc02655c7f70f1ac04273070835c4616bbb56d5f
- **短哈希**：cc02655c7
- **日期**：2025-12-12 08:54:40 +0100
- **作者**：aiborodin
- **提交说明**：Flink: Backport: Log on cache refresh in dynamic sink (#14828)a
- **PR/Issue**：#14828（backport PR），源 PR #14792

## 总体目的

这是一个 backport 提交，把 PR #14792（即本批次序号 3000 的提交 `fc981b499`，作者 aiborodin，2025-12-11）在 Flink v2.1 上引入的"动态 Sink 缓存刷新日志"功能，回移到 Flink v1.20 和 v2.0 两个老版本分支。

源 PR #14792 解决的问题：Flink 动态 sink 的 `TableMetadataCache` 在缓存项超时（超过 `refreshMs`）后会触发刷新（重新访问 catalog 拉取元数据），但这一关键的"刷新事件"此前没有任何日志输出。在生产环境中，元数据刷新是调试动态 sink 行为（schema 变更、分区演进、分支切换、延迟波动）的重要时间锚点——没有日志，运维就无法把 sink 行为与 catalog 端变更对齐，难以排查"sink 为什么突然开始写新 schema"这类问题。源 PR 通过在 `needsRefresh` 的超时分支打印 `LOG.info("Refreshing table metadata for {} after {} millis", identifier, timeElapsedMillis)` 解决了这一可观测性缺口，并把 `needsRefresh` 从单行三元表达式重构为多分支结构以便插入日志。

本 backport 提交的目的就是把同样的可观测性改进同步到 Flink v1.20 和 v2.0 两个版本目录——Iceberg 维护多个 Flink 版本的并行源码副本，每个版本目录的 `TableMetadataCache.java` 都是独立的一份，因此日志改进需要分别落到每个版本。源 PR 已在 v2.1 落地，本提交补齐 v1.20 和 v2.0。

## 如何达成设计目的

整体思路是"clean backport"——源 PR 的 diff 原样应用到 v1.20 和 v2.0 两个目录。改动只在 `TableMetadataCache.java` 一个文件（两个版本目录各一份）：

1. 把 `needsRefresh` 方法签名从 `needsRefresh(CacheItem cacheItem, boolean allowRefresh)` 改为 `needsRefresh(TableIdentifier identifier, CacheItem cacheItem, boolean allowRefresh)`，因为日志需要表标识符。
2. 把方法体从单行 `return allowRefresh && (cacheItem == null || ...)` 展开为多分支结构，在超时分支插入 INFO 日志。
3. 同步更新全部 4 处调用点（`exists`、`branch`、`schema`、`spec`）传入 `identifier`。

两个版本目录的改动完全对称，与源 PR 在 v2.1 上的改动一字不差。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (+22/-8 lines)

**修改目的**：为 Flink v1.20 版本的 `TableMetadataCache` 增加缓存超时刷新的 INFO 日志，并把 `needsRefresh` 重构为多分支结构。

**工作逻辑**：

签名变更：`private boolean needsRefresh(CacheItem cacheItem, boolean allowRefresh)` → `private boolean needsRefresh(TableIdentifier identifier, CacheItem cacheItem, boolean allowRefresh)`。所有调用点同步更新，传入当前 `identifier`：

- `exists(...)` 中 `needsRefresh(cached, true)` → `needsRefresh(identifier, cached, true)`
- `branch(...)` 中 `needsRefresh(cached, allowRefresh)` → `needsRefresh(identifier, cached, allowRefresh)`
- `schema(...)` 中同上
- `spec(...)` 中同上

方法体重写为：

```java
private boolean needsRefresh(
    TableIdentifier identifier, CacheItem cacheItem, boolean allowRefresh) {
  if (!allowRefresh) {
    return false;
  }

  if (cacheItem == null) {
    return true;
  }

  long nowMillis = cacheRefreshClock.millis();
  long timeElapsedMillis = nowMillis - cacheItem.createdTimestampMillis;
  if (timeElapsedMillis > refreshMs) {
    LOG.info("Refreshing table metadata for {} after {} millis", identifier, timeElapsedMillis);
    return true;
  }

  return false;
}
```

逻辑上与原先的 `allowRefresh && (cacheItem == null || clock.millis() - createdTimestampMillis > refreshMs)` 完全等价，但分成三个早返回分支：不允许刷新→false；无缓存项→true（首次加载，不打日志，避免刷屏）；超时→打日志后返回 true。日志只在"超时刷新"分支输出，记录表标识符和实际经过时长 `timeElapsedMillis`——经过时长比配置阈值 `refreshMs` 更有诊断价值，能反映刷新被推迟或被频繁触发的异常情况。`LOG` 字段是 `TableMetadataCache` 类已有的 SLF4J Logger（`LoggerFactory.getLogger(TableMetadataCache.class)`），无需新增。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (+22/-8 lines)

**修改目的**：同 v1.20，对 Flink v2.0 版本目录做相同改动。

**工作逻辑**：与 v1.20 完全相同——签名变更、4 处调用点更新、方法体多分支重构、超时分支插入 `LOG.info("Refreshing table metadata for {} after {} millis", identifier, timeElapsedMillis)`。两个版本目录的 diff 一字不差，是 Iceberg 多 Flink 版本并行维护的标准回移操作。

## 总结

该提交是 PR #14792（提交 3000）的 clean backport，把"Flink 动态 Sink 的 `TableMetadataCache` 在缓存超时刷新时打印 INFO 日志"这一可观测性改进同步到 Flink v1.20 和 v2.0 两个版本目录。改动让运维人员能从 sink 日志中直接观察到每一次元数据刷新事件及其对应的表和实际间隔，便于排查 schema 变更、刷新频率异常等问题。两个版本目录的改动与源 PR 在 v2.1 上的改动完全对称，至此该可观测性改进已覆盖 Iceberg 维护的全部三个 Flink 版本（v1.20、v2.0、v2.1）。
