# 提交 2960：Flink: Fix cache refreshing in dynamic sink (#14406)

## 提交信息

- **序号**：2960 / 4088
- **哈希**：8db3d2115d948ab8b150cd0ca7ae3fe1a17aa1d3
- **短哈希**：8db3d2115
- **日期**：2025-12-05
- **作者**：aiborodin
- **提交说明**：Flink: Fix cache refreshing in dynamic sink (#14406)
- **PR/Issue**：#14406

## 总体目的

Flink 动态 Sink 中的 `TableMetadataCache` 负责缓存表元数据（schema、分支、分区规格等），以减少对 Catalog 的重复查询。该缓存设有刷新间隔 `refreshMs`，通过 `needsRefresh` 方法判断缓存项是否需要刷新。然而 `needsRefresh` 中的时间判断逻辑存在严重 bug：原条件为 `cacheItem.created + refreshMs > System.currentTimeMillis()`，这表示"创建时间 + 刷新间隔 > 当前时间"，即缓存**尚未过期**时返回 `true`（需要刷新），而缓存**已过期**时反而返回 `false`（不需要刷新）。这与预期完全相反——缓存新鲜时不断刷新，缓存过期时反而不刷新，导致缓存形同虚设且产生不必要的 Catalog 查询开销。

本提交修正了这个反向逻辑，将判断条件改为 `cacheRefreshClock.millis() - cacheItem.createdTimestampMillis > refreshMs`，即"当前时间 - 创建时间 > 刷新间隔"，只有当缓存真正过期时才触发刷新。同时引入可注入的 `Clock` 以支持测试中对时间的控制，并将 `CacheItem` 的时间戳从构造时自动捕获改为由外部传入，实现时间源的统一管理。

## 如何达成设计目的

核心修改在 `TableMetadataCache` 中：新增 `Clock cacheRefreshClock` 字段，通过新增的 `@VisibleForTesting` 构造器注入，生产构造器默认使用 `Clock.systemUTC()`；`CacheItem` 的 `created` 字段改为 `createdTimestampMillis` 并通过构造器参数传入（值来自 `cacheRefreshClock.millis()`）；`needsRefresh` 方法使用注入的 Clock 计算时间差并修正比较方向。测试新增 `testNoCacheRefreshingBeforeRefreshIntervalElapses`，使用 `Clock.fixed()` 固定时间，验证缓存项在刷新间隔内不会被错误刷新。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (+21/-4 lines)

**修改目的**：修复 `needsRefresh` 的反向时间判断逻辑，并引入可注入的 `Clock` 支持测试。

**工作逻辑**：

1. **引入 Clock 依赖**：新增 `import java.time.Clock`，在类中增加 `private final Clock cacheRefreshClock` 字段。原有四参数构造器委托给新的五参数构造器（`@VisibleForTesting`），生产路径传入 `Clock.systemUTC()`，测试路径可传入 `Clock.fixed()` 固定时间。所有时间获取从 `System.currentTimeMillis()` 改为 `cacheRefreshClock.millis()`。

2. **修正 needsRefresh 逻辑**：原条件 `cacheItem.created + refreshMs > System.currentTimeMillis()` 语义为"创建时间加刷新间隔仍大于当前时间"——即缓存未过期时返回 true（错误地触发刷新）。新条件为 `cacheRefreshClock.millis() - cacheItem.createdTimestampMillis > refreshMs`，语义为"当前时间减去创建时间已超过刷新间隔"——即缓存已过期时才返回 true（正确触发刷新）。这是本提交的核心 bug 修复。

3. **CacheItem 时间戳外部化**：原 `CacheItem` 中 `private final long created = System.currentTimeMillis()` 在构造时自动捕获系统时间。改为 `private final long createdTimestampMillis` 通过构造器参数传入，值来自 `cacheRefreshClock.millis()`。所有创建 `CacheItem` 的位置（`update` 方法、`loadTable` 的 `NoSuchTableException` 分支）均同步传入 `cacheRefreshClock.millis()`。这使得缓存项的时间戳与 `needsRefresh` 的时间源一致，且可被测试控制。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableMetadataCache.java` (+33/-0 lines)

**修改目的**：新增测试验证缓存在刷新间隔内不会被错误刷新。

**工作逻辑**：
新增 `testNoCacheRefreshingBeforeRefreshIntervalElapses` 测试。该测试创建一张表，用 `Clock.fixed(Instant.now(), ZoneId.systemDefault())` 构造一个时间固定的 `TableMetadataCache`（刷新间隔 100ms），调用 `cache.update` 将表元数据放入缓存。随后两次调用 `cache.schema` 分别用 `SCHEMA2`（与表 schema 一致）和 `SCHEMA`（字段更少）查询缓存，验证：
- 第一次查询返回的 schema 与 `SCHEMA2` 一致（命中缓存，未触发刷新）；
- 第二次查询返回 `CompareSchemasVisitor.Result.DATA_CONVERSION_NEEDED`（需要数据转换），且返回的表 schema 仍为 `SCHEMA2`；
- 最终断言 `CacheItem.inputSchemas()` 同时包含 `SCHEMA` 和 `SCHEMA2` 两个输入 schema，证明两次查询都命中了同一缓存项而非触发刷新重建。

由于 Clock 被固定，时间不会流逝，`needsRefresh` 始终返回 false（缓存未过期），从而验证了修复后缓存不会在间隔内被错误刷新。

## 总结

本提交修复了 Flink 动态 Sink `TableMetadataCache` 中一个逻辑反转的严重 bug：原 `needsRefresh` 在缓存新鲜时返回 true（触发刷新）、过期时返回 false（不刷新），导致缓存完全失效并产生大量冗余 Catalog 查询。修复后条件正确反映"已过期才刷新"的语义，同时通过引入可注入 `Clock` 使缓存刷新行为可被测试确定性验证。配套测试用固定时钟验证了间隔内不刷新的正确行为。
