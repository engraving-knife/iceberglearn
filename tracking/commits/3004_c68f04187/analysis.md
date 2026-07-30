# 提交 3004：Core: Expose the stats of the manifest file content cache (#13560)

## 提交信息

- **序号**：3004 / 4088
- **哈希**：c68f0418774782f73abca8d9e9c366198910c101
- **短哈希**：c68f04187
- **日期**：2025-12-12 12:40:26 +0100
- **作者**：gaborkaszab
- **提交说明**：Core: Expose the stats of the manifest file content cache (#13560)
- **PR/Issue**：#13560

## 总体目的

Iceberg 在读取 manifest 文件时，会通过 `ManifestFiles` 维护一个"manifest 文件内容缓存"——按 `FileIO` 维度（外层 `Cache<FileIO, ContentCache>`，内层 `ContentCache` 按 manifest 路径缓存文件字节）缓存 manifest 文件内容，避免重复从底层存储读取。这个缓存基于 Caffeine，并在构建时调用了 `.recordStats()` 启用统计。`ContentCache.stats()` 已经能返回 Caffeine 的 `CacheStats`（含 hitCount、missCount、evictionCount 等）。

但问题是：`ContentCache.stats()` 返回的是 Caffeine 的 `com.github.benmanes.caffeine.cache.stats.CacheStats`，这是一个 Caffeine 内部类型。外部模块（如引擎集成、REST Catalog 上报、监控埋点）如果想观察 manifest 缓存的命中/缺失/淘汰情况，就只能依赖 Caffeine 类型，存在两个缺陷：

1. **耦合**：调用方必须依赖 Caffeine 库，且 `CacheStats` 上方法众多、语义随 Caffeine 版本变化（例如 `loadCount` 被重命名为 `loadSuccessCount`、`requestCount` 被移除），跨版本兼容性差。
2. **没有统一上报通道**：Iceberg 已经有 `MetricsReport` 接口体系（`ScanReport`、`CommitReport` 都实现它，并通过 `MetricsReporter` 上报），但缓存统计没有进入这个体系，无法通过统一的 metrics 通道暴露给监控。

本提交的目的就是：把 manifest 文件内容缓存的统计信息封装成一个新的 `CacheMetricsReport`（实现 `MetricsReport`），只暴露稳定的 `hitCount`、`missCount`、`evictionCount` 三个字段，并通过 `ManifestFiles.contentCacheStats(FileIO)` 这一公共入口对外提供。这样外部调用方无需直接依赖 Caffeine 类型，就能拿到缓存命中情况，便于排查"manifest 缓存是否生效、命中率是否正常、是否频繁淘汰"等问题。同时把测试中对 Caffeine `CacheStats` 的直接依赖迁移到新的 `contentCacheStats` API 上，并适配 Caffeine 新版 API 的变化（`loadCount` → `loadSuccessCount`、`requestCount` 被移除）。

## 如何达成设计目的

整体思路是"在 Caffeine `CacheStats` 之上包一层稳定的不可变报告类型"：

1. 新建 `CacheMetricsReport`（抽象类，用 Immutables 生成 `ImmutableCacheMetricsReport`），实现 `MetricsReport`，暴露 `hitCount()`、`missCount()`、`evictionCount()` 三个抽象方法；提供静态工厂 `CacheMetricsReport.of(CacheStats)` 从 Caffeine `CacheStats` 提取这三个字段。
2. 在 `ManifestFiles` 中新增公共方法 `contentCacheStats(FileIO io)`，内部调用已有的包级 `contentCache(io).stats()` 拿到 `CacheStats`，再用 `CacheMetricsReport.of(...)` 包装返回。
3. 新增 `TestCacheMetricsReport` 直接验证 `CacheMetricsReport.of(CacheStats)` 在空缓存和有命中/缺失/淘汰的缓存上能正确提取三个字段。
4. 调整 `TestManifestCaching` 中已有的断言：把对 `cache.stats().missCount()` / `requestCount()` 的直接调用改为通过 `ManifestFiles.contentCacheStats(table.io())` 取值，并把 `loadCount()` 改为 `loadSuccessCount()`、`requestCount()` 改为 `hitCount() + missCount()` 以适配 Caffeine 新版 API。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestFiles.java` (+6/-0 lines)

**修改目的**：新增公共入口 `contentCacheStats(FileIO)`，对外暴露 manifest 文件内容缓存的统计信息。

**工作逻辑**：
新增 import `org.apache.iceberg.metrics.CacheMetricsReport`，并新增方法：

```java
/** Get statistics of the manifest file content cache for a FileIO. */
public static CacheMetricsReport contentCacheStats(FileIO io) {
  return CacheMetricsReport.of(contentCache(io).stats());
}
```

`contentCache(io)` 是 `ManifestFiles` 中已有的包级方法，它从外层 `CONTENT_CACHES`（`Cache<FileIO, ContentCache>`）取出该 `FileIO` 对应的内层 `ContentCache`；`ContentCache.stats()` 返回内层 Caffeine 缓存的 `CacheStats`（即 manifest 文件级别的命中/缺失/淘汰统计，而不是 FileIO 级别的）。`CacheMetricsReport.of(...)` 把这个 `CacheStats` 转成稳定的 `CacheMetricsReport`。方法声明为 `public static`，与已有的 `dropCache(FileIO)` 公共方法对齐，让外部（引擎、监控、REST 上报）能按 `FileIO` 取到该缓存的运行统计。

### `core/src/main/java/org/apache/iceberg/metrics/CacheMetricsReport.java` (+39/-0 lines, new file)

**修改目的**：定义稳定的缓存统计报告类型，作为 `MetricsReport` 体系的新成员。

**工作逻辑**：

```java
@Value.Immutable
public abstract class CacheMetricsReport implements MetricsReport {
  public abstract long hitCount();
  public abstract long missCount();
  public abstract long evictionCount();

  public static CacheMetricsReport of(CacheStats stats) {
    return ImmutableCacheMetricsReport.builder()
        .hitCount(stats.hitCount())
        .missCount(stats.missCount())
        .evictionCount(stats.evictionCount())
        .build();
  }
}
```

设计要点：

- 用 Immutables（`@Value.Immutable`）生成 `ImmutableCacheMetricsReport`，与 `ScanReport`、`CommitReport` 等 metrics 体系内的报告类型保持风格一致，便于序列化与跨进程传输。
- 实现 `MetricsReport` 接口，使其能被 `MetricsReporter` 统一处理，未来可接入已有的 REST 上报、`InMemoryMetricsReporter` 等通道。
- 只暴露三个最关键且语义稳定的字段：`hitCount`（命中数，避免了一次存储访问）、`missCount`（缺失数，触发了一次 manifest 加载）、`evictionCount`（淘汰数，缓存容量压力信号）。这三个字段在 Caffeine 各版本中语义稳定，不会像 `loadCount`/`requestCount` 那样被重命名或移除。
- 静态工厂 `of(CacheStats)` 是唯一的构造入口，把 Caffeine 类型隔离在这一处——Caffeine 升级时只需改这一行，调用方无感。

### `core/src/test/java/org/apache/iceberg/TestManifestCaching.java` (+9/-5 lines)

**修改目的**：把测试中对 Caffeine `CacheStats` 的直接调用迁移到新的 `contentCacheStats` API，并适配 Caffeine 新版 API 变化。

**工作逻辑**：

第一处改动（首次加载验证）：

```java
- assertThat(cache.stats().loadCount())
+ assertThat(cache.stats().loadSuccessCount())
     .as("All manifest files should be recently loaded")
     .isEqualTo(numFiles);
- long missCount = cache.stats().missCount();
+ long missCount = ManifestFiles.contentCacheStats(table.io()).missCount();
```

`loadCount()` 改为 `loadSuccessCount()`——这是 Caffeine 新版 API 的重命名（`loadCount` 在新版本被拆分为 `loadSuccessCount` 与 `loadFailureCount`）。同时把"取 missCount"从直接 `cache.stats().missCount()` 改为通过新的 `ManifestFiles.contentCacheStats(table.io()).missCount()`，验证新公共 API 可用。

第二处改动（第二次扫描应全部命中缓存）：

```java
- assertThat(cache.stats().missCount())
+ assertThat(ManifestFiles.contentCacheStats(table.io()).missCount())
     .as("All manifest file reads should hit cache")
     .isEqualTo(missCount);
```

同样改为通过新 API 取 missCount，断言第二次扫描的 missCount 与首次相同（即第二次没有新增 miss）。

第三处改动（缓存容量为 1 时文件不被缓存）：

```java
- assertThat(cache.stats().loadCount())
+ assertThat(cache.stats().loadSuccessCount())
     .as("File should not be loaded through cache")
     .isEqualTo(0);
- assertThat(cache.stats().requestCount()).as("Cache should not serve file").isEqualTo(0);
+ assertThat(
+         ManifestFiles.contentCacheStats(table.io()).hitCount()
+             + ManifestFiles.contentCacheStats(table.io()).missCount())
+     .as("Cache should not serve file")
+     .isEqualTo(0);
```

`loadCount()` → `loadSuccessCount()` 同样是 Caffeine API 适配；`requestCount()` 改为 `hitCount() + missCount()`——因为 Caffeine 新版移除了 `requestCount()`，等价表达是"命中数 + 缺失数 = 总请求数"。这里同时改用新 `contentCacheStats` API 取 hitCount/missCount，验证新 API 在"缓存完全未服务"场景下也正确。

### `core/src/test/java/org/apache/iceberg/metrics/TestCacheMetricsReport.java` (+62/-0 lines, new file)

**修改目的**：单元测试 `CacheMetricsReport.of(CacheStats)` 的转换正确性。

**工作逻辑**：
新增两个测试：

- `testNoInputStats`：用一个全新的、未记录统计的 Caffeine 缓存（`Caffeine.newBuilder().build().stats()`）构造 `CacheMetricsReport`，断言三个字段都为 0。这验证了 `of(CacheStats)` 在空统计上不会 NPE 或返回错误值。
- `testCacheMetricsFromCaffeineCache`：构造一个带 `maximumWeight(300)` 和自定义 `Weigher((key, value) -> value * 100)` 的 Caffeine 缓存（`.recordStats()`），依次 `get(1)`、`get(1)`、`get(2)`、`get(3)`：

  - 第一次 `get(1)` 是 miss + load（成功）；
  - 第二次 `get(1)` 是 hit；
  - `get(2)`、`get(3)` 都是 miss + load；
  - 由于 weight = value*100，weight 1=100、2=200、3=300，maxWeight=300，所以加载 3 后会触发淘汰，`cleanUp()` 后 evictionCount 应为 2（entry 1 和 entry 2 被淘汰）。

  断言 `hitCount` 为 1、`missCount` 为 3、`evictionCount` 为 2，精确验证 `of(CacheStats)` 从 Caffeine `CacheStats` 提取这三个字段的正确性。

## 总结

该提交把 manifest 文件内容缓存的 Caffeine `CacheStats` 封装为稳定的 `CacheMetricsReport`（实现 `MetricsReport`），并通过 `ManifestFiles.contentCacheStats(FileIO)` 公共入口对外暴露 hit/miss/eviction 三个关键字段。改动让外部调用方无需直接依赖 Caffeine 类型即可观察缓存命中情况，便于排查 manifest 缓存命中率与容量压力，并与 Iceberg 既有的 `MetricsReport` 上报体系对齐。测试侧新增 `TestCacheMetricsReport` 验证转换正确性，并把 `TestManifestCaching` 中对 Caffeine `CacheStats` 的直接调用迁移到新 API，顺带适配了 Caffeine 新版 `loadCount → loadSuccessCount`、`requestCount` 移除的 API 变化。
