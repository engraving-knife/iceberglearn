# 提交 3000：Flink: Log on cache refresh in dynamic sink (#14792)

## 提交信息

- **序号**：3000 / 4088
- **哈希**：fc981b499a09cef352f123daa70668d765dfe282
- **短哈希**：fc981b499
- **日期**：2025-12-11 19:33:05 +0100
- **作者**：aiborodin
- **提交说明**：Flink: Log on cache refresh in dynamic sink (#14792)
- **PR/Issue**：#14792

## 总体目的

Iceberg 的 Flink 动态 sink（`flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/`）通过 `TableMetadataCache` 缓存表的 schema、partition spec、branch 等元数据，避免每条记录都去访问 catalog。缓存有"按时间刷新"机制：每条缓存项记录 `createdTimestampMillis`，超过 `refreshMs` 后下一次访问会触发一次刷新（重新向 catalog 拉取元数据）。

在生产环境中，这种"周期性刷新"是调试与可观测性的关键信号——刷新意味着 sink 重新查询了 catalog，可能与上游 schema 变更、分区演进、分支切换等行为相关。如果刷新没有日志，运维人员在排查"sink 为什么突然开始写新 schema"或"为什么某段时间延迟升高"时，就缺少关键的时间点锚点，无法把 sink 行为与 catalog 端的变更对齐。

本提交的目的就是在缓存项因超时被刷新时打印一条 INFO 级日志，记录是哪张表（`TableIdentifier`）被刷新、距上次刷新过去了多少毫秒。这样运维和开发可以从 sink 日志中直接观察到每一次元数据刷新事件，便于排查动态 sink 的行为异常。顺带把 `needsRefresh` 方法的逻辑从单行三元表达式重构为可读性更高的多分支结构，便于插入日志语句。

## 如何达成设计目的

整体思路是在判定"需要刷新"的那条分支上记录日志。由于日志需要知道"是哪张表"和"过了多久"，原本 `needsRefresh(CacheItem, boolean)` 的签名缺少 `TableIdentifier` 参数，因此作者先把签名改为 `needsRefresh(TableIdentifier identifier, CacheItem cacheItem, boolean allowRefresh)`，更新全部 4 处调用点，再把方法体从单行表达式展开为多分支并在超时分支打日志。改动集中在 `TableMetadataCache.java` 一个文件。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (+22/-8 lines)

**修改目的**：在缓存项因超时触发刷新时记录 INFO 日志，并把 `needsRefresh` 改为多分支结构。

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

逻辑上与原先的 `allowRefresh && (cacheItem == null || clock.millis() - createdTimestampMillis > refreshMs)` 等价，但分成三个早返回分支：不允许刷新→false；无缓存项→true（首次加载，不日志）；超时→打日志后返回 true。日志只在"超时刷新"分支输出，不会在首次加载（`cacheItem == null`）时刷屏。

日志内容 `"Refreshing table metadata for {} after {} millis"` 把表标识符和实际经过时长都打了出来——经过时长 `timeElapsedMillis` 比配置阈值 `refreshMs` 更有诊断价值，因为它能反映"实际刷新间隔"，便于发现刷新被推迟（例如某表长时间没被访问）或被频繁触发（例如时钟异常、配置过短）等情况。`LOG` 字段是 `TableMetadataCache` 类已有的 SLF4J Logger（`LoggerFactory.getLogger(TableMetadataCache.class)`），无需新增。

## 总结

该提交为 Flink 动态 sink 的 `TableMetadataCache` 增加了"缓存超时刷新"的 INFO 日志，并把 `needsRefresh` 重构为多分支结构以便插入日志。改动虽小但提升了动态 sink 的可观测性——运维人员可以从日志中直接看到每一次元数据刷新事件及其对应的表和实际间隔，便于排查 schema 变更、刷新频率异常等问题。后续 commit 3003 会把该改动 backport 到 Flink v1.20 分支。
