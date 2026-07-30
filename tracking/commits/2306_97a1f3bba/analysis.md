# 提交 2306：Flink: Backport replace Caffeine maxSize cache with LRUCache (#13441)

## 提交信息

- **序号**：2306 / 4088
- **哈希**：97a1f3bbae0b7be8ecdc8c6c76a3479d38079a88
- **短哈希**：97a1f3bba
- **日期**：2025-07-02 08:28:42 +0200
- **作者**：aiborodin
- **提交说明**：Flink: Backport replace Caffeine maxSize cache with LRUCache (#13441)
- **PR/Issue**：#13441

## 总体目的

本提交将 PR #13382（提交 2299）的修改回移植到 Flink 1.20 版本。PR #13382 将 Flink 动态sink中的 Caffeine 缓存替换为项目内部的 `LRUCache`（基于 `LinkedHashMap`），减少了外部依赖并简化了缓存行为。

提交 2299 已经在 Flink 2.0 中完成了这一替换，本提交将相同的修改应用到 Flink 1.20 版本，确保两个版本的代码一致性。

## 如何达成设计目的

回移植的修改与提交 2299 完全相同，涉及以下文件和变更：

1. **`LRUCache.java`**：添加无需驱逐回调的便捷构造函数
2. **`DynamicWriter.java`**：将 Caffeine `Cache` 替换为 `LRUCache`，类型改为 `Map`
3. **`HashKeyGenerator.java`**：将 Caffeine `Cache` 替换为 `LRUCache`
4. **`TableMetadataCache.java`**：将 Caffeine `Cache` 替换为 `LRUCache`
5. **`TableSerializerCache.java`**：将 Caffeine `Cache` 替换为 `LRUCache`
6. **测试文件**：适配 Map API，移除 `cleanUp()` 调用

## 修改详情

### Flink 1.20 版本文件（9个文件，与 Flink 2.0 版本对称）

修改的文件和内容与提交 2299 完全一致，只是路径前缀从 `flink/v2.0/` 改为 `flink/v1.20/`。

**核心变更**：
- 移除所有 `com.github.benmanes.caffeine.cache.Cache` 和 `Caffeine` 的导入
- 将 `Cache<K,V>` 类型替换为 `Map<K,V>`
- 将 `Caffeine.newBuilder().maximumSize(n).build()` 替换为 `new LRUCache<>(n)`
- 将 `cache.get(key, mappingFunction)` 替换为 `map.computeIfAbsent(key, mappingFunction)`
- 将 `cache.getIfPresent(key)` 替换为 `map.get(key)`
- 将 `cache.invalidate(key)` 替换为 `map.remove(key)`
- 移除测试中的 `cleanUp()` 调用，将 `estimatedSize()` 替换为 `size()` 或 `hasSize()`

### `LRUCache.java` (+4/-0 lines)

**修改目的**：添加便捷构造函数。

**工作逻辑**：新增 `LRUCache(int maximumSize)` 构造函数，调用已有构造函数并传入空操作回调。

### 其余文件变更

与提交 2299 中对应文件的修改完全相同，参见提交 2299 的分析文档。

## 总结

本提交是提交 2299 的 Flink 1.20 回移植，将 Caffeine 缓存替换为 `LRUCache`。回移植确保了 Flink 1.20 和 2.0 版本的代码一致性。修改内容与原始提交完全相同，只是目标版本不同。这体现了 Iceberg 项目在多版本维护中保持代码一致性的实践。
