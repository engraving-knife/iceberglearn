# 提交 2299：Flink: Replace Caffeine maxSize cache with LRUCache (#13382)

## 提交信息

- **序号**：2299 / 4088
- **哈希**：e73344323ddc26f9f45db15d50394e25069821d2
- **短哈希**：e73344323
- **日期**：2025-07-01 16:18:57 +0200
- **作者**：aiborodin
- **提交说明**：Flink: Replace Caffeine maxSize cache with LRUCache (#13382)
- **PR/Issue**：#13382

## 总体目的

本提交将 Flink 动态sink（Dynamic Sink）中使用的 Caffeine 缓存替换为项目内部已有的 `LRUCache`（基于 `LinkedHashMap` 实现）。Caffeine 是一个功能强大但重量级的缓存库，提供了异步清理、过期策略、统计等高级功能。然而在 Flink 动态sink的场景中，缓存仅用于简单的 maxSize 限制，不需要 Caffeine 的高级功能。

使用 Caffeine 带来了几个问题：
1. **依赖负担**：Caffeine 引入了额外的依赖，增加了项目的依赖管理复杂度
2. **性能开销**：Caffeine 的异步清理机制（`cleanUp`）不是在每次写操作后立即执行，导致 `estimatedSize()` 不精确，测试中需要手动调用 `cleanUp()`
3. **API 不一致**：Caffeine 的 `Cache` 接口与 Java 标准 `Map` 接口不同，使用时需要适配

替换为 `LRUCache` 后，缓存行为更可预测（同步驱逐），API 与 `Map` 兼容，且减少了外部依赖。

## 如何达成设计目的

核心设计思路是将所有使用 `Caffeine.newBuilder().maximumSize(n).build()` 创建的缓存替换为 `new LRUCache<>(n)`，并将 `Cache<K,V>` 类型引用替换为 `Map<K,V>`。由于 `LRUCache` 继承自 `LinkedHashMap`，它自然实现了 `Map` 接口。

关键适配点：
1. `cache.get(key, mappingFunction)` → `map.computeIfAbsent(key, mappingFunction)`
2. `cache.getIfPresent(key)` → `map.get(key)`
3. `cache.put(key, value)` → `map.put(key, value)`（不变）
4. `cache.invalidate(key)` → `map.remove(key)`
5. `cache.estimatedSize()` → `map.size()`
6. `cache.cleanUp()` → 不再需要（LRUCache 是同步驱逐的）

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/LRUCache.java` (+4/-0 lines)

**修改目的**：为 `LRUCache` 添加一个便捷构造函数，无需提供驱逐回调。

**工作逻辑**：新增构造函数 `LRUCache(int maximumSize)`，调用已有的双参数构造函数，传入一个空操作回调 `ignored -> {}`。这使得在不关心驱逐事件的场景下可以更简洁地创建缓存。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (+4/-4 lines)

**修改目的**：将 `taskWriterFactories` 从 Caffeine `Cache` 替换为 `LRUCache`。

**工作逻辑**：字段类型从 `Cache<WriteTarget, RowDataTaskWriterFactory>` 改为 `Map<WriteTarget, RowDataTaskWriterFactory>`。初始化从 `Caffeine.newBuilder().maximumSize(cacheMaximumSize).build()` 改为 `new LRUCache<>(cacheMaximumSize)`。获取缓存值的方式从 `cache.get(key, mappingFunction)` 改为 `map.computeIfAbsent(key, mappingFunction)`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (+6/-5 lines)

**修改目的**：将 `keySelectorCache` 从 Caffeine `Cache` 替换为 `LRUCache`。

**工作逻辑**：字段类型从 `Cache<SelectorKey, KeySelector<RowData, Integer>>` 改为 `Map<SelectorKey, KeySelector<RowData, Integer>>`。测试可见的 getter 方法返回类型也相应修改。获取缓存值的方式从 `cache.get(key, mappingFunction)` 改为 `map.computeIfAbsent(key, mappingFunction)`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (+14/-12 lines)

**修改目的**：将 `TableMetadataCache` 中的 Caffeine 缓存替换为 `LRUCache`。

**工作逻辑**：字段从 `Cache<TableIdentifier, CacheItem> cache` 重命名为 `Map<TableIdentifier, CacheItem> tableCache`，类型改为 `LRUCache`。所有 `cache.getIfPresent()` 调用替换为 `tableCache.get()`，`cache.put()` 替换为 `tableCache.put()`，`cache.invalidate()` 替换为 `tableCache.remove()`。`CacheItem` 内部的 `inputSchemas` 字段类型也从 `LRUCache` 改为 `Map`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableSerializerCache.java` (+5/-5 lines)

**修改目的**：将 `TableSerializerCache` 中的 Caffeine 缓存替换为 `LRUCache`。

**工作逻辑**：字段从 `Cache<String, SerializerInfo>` 改为 `Map<String, SerializerInfo>`。初始化从 Caffeine 改为 `new LRUCache<>(maximumSize)`。获取缓存值从 `serializers.get(tableName, SerializerInfo::new)` 改为 `serializers.computeIfAbsent(tableName, SerializerInfo::new)`。

### 测试文件修改 (+11/-14 lines across 4 files)

**修改目的**：适配缓存接口变更，简化测试断言。

**工作逻辑**：
- `TestHashKeyGenerator`：移除 `cleanUp()` 调用，将 `estimatedSize()` 替换为 `hasSize()`
- `TestTableMetadataCache`：移除 `cleanUp()` 调用，将 `estimatedSize()` 替换为 `isEmpty()`
- `TestTableSerializerCache`：将 `cleanUp()` 替换为 `clear()`
- `TestTableUpdater`：将 `getIfPresent()` 替换为 `get()`，适配新的 Map API

## 总结

本提交将 Flink 动态sink中的 Caffeine 缓存替换为项目内部的 `LRUCache`，减少了外部依赖，简化了缓存行为（从异步清理变为同步驱逐），并使测试更加简洁（不再需要手动调用 `cleanUp()`）。这是一个降低复杂度和依赖的合理重构。
