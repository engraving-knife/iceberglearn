# 提交 2961：Flink: Backport fix cache refreshing in dynamic sink (#14765)

## 提交信息

- **序号**：2961 / 4088
- **哈希**：c4ba60d27b02d8618621ad701e52d51b9a98d0d5
- **短哈希**：c4ba60d27
- **日期**：2025-12-05
- **作者**：aiborodin
- **提交说明**：Flink: Backport fix cache refreshing in dynamic sink (#14765)
- **PR/Issue**：#14765（回移自 #14406）

## 总体目的

本提交是 main 分支上 #14406（即序号 2960 提交）向维护分支的回移（backport），将 `TableMetadataCache` 缓存刷新逻辑 bug 修复同步到 `flink/v1.20` 和 `flink/v2.1` 两个 Flink 版本目录。原 bug 是 `needsRefresh` 方法的时间判断条件完全反转：`cacheItem.created + refreshMs > System.currentTimeMillis()` 在缓存未过期时返回 true（触发刷新），在过期时返回 false（不刷新），导致缓存形同虚设、每次查询都向 Catalog 发起请求。回移的目的是让维护分支上的用户同样受益于该修复，避免不必要的 Catalog 查询开销。两处目录的改动内容与 main 分支完全一致。

## 如何达成设计目的

与原提交 #14406 完全相同：在 `TableMetadataCache` 中引入可注入的 `Clock` 字段，将 `CacheItem` 的时间戳从构造时自动捕获改为外部传入，修正 `needsRefresh` 的比较方向为"当前时间 - 创建时间 > 刷新间隔"。测试使用 `Clock.fixed()` 验证间隔内不刷新。改动同步落地到 v1.20 与 v2.1 两套目录。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (+21/-4 lines)

**修改目的**：回移 `needsRefresh` 反向逻辑修复与 `Clock` 注入到 v1.20 目录。

**工作逻辑**：
与序号 2960 提交完全一致。新增 `Clock cacheRefreshClock` 字段，生产构造器委托给 `@VisibleForTesting` 五参数构造器（默认 `Clock.systemUTC()`）。`needsRefresh` 条件从 `cacheItem.created + refreshMs > System.currentTimeMillis()` 修正为 `cacheRefreshClock.millis() - cacheItem.createdTimestampMillis > refreshMs`。`CacheItem.created` 改为 `createdTimestampMillis` 构造器参数。所有创建 `CacheItem` 处均传入 `cacheRefreshClock.millis()`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableMetadataCache.java` (+33/-0 lines)

**修改目的**：回移缓存不刷新测试到 v1.20 目录。

**工作逻辑**：
新增 `testNoCacheRefreshingBeforeRefreshIntervalElapses` 测试，使用 `Clock.fixed()` 固定时间构造缓存（刷新间隔 100ms），验证两次 schema 查询均命中同一缓存项，`inputSchemas` 同时包含 `SCHEMA` 和 `SCHEMA2`。内容与序号 2960 提交完全一致。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (+21/-4 lines)

**修改目的**：回移相同修复到 v2.1 目录。内容与 v1.20 完全一致。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableMetadataCache.java` (+33/-0 lines)

**修改目的**：回移相同测试到 v2.1 目录。内容与 v1.20 完全一致。

## 总结

本回移提交将 `TableMetadataCache` 缓存刷新逻辑反转 bug 的修复同步到 v1.20 和 v2.1 两个 Flink 版本目录，与 main 分支的 #14406（序号 2960，针对 v2.0）形成完整覆盖，确保所有维护分支上的 Flink 动态 Sink 都能正确利用缓存减少 Catalog 查询。改动内容与原提交完全一致，无需额外适配。
