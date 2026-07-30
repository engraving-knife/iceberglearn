# 提交 2773：Flink: Prevent recreation of ManifestOutputFileFactory during flushing (#14358)

## 提交信息

- **序号**：2773 / 4088
- **哈希**：911a486b0eb8f55c2a44c5aa7fe62c2ca23b1d75
- **短哈希**：911a486b0
- **日期**：2025-10-20 15:16:39 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Prevent recreation of ManifestOutputFileFactory during flushing (#14358)
- **PR/Issue**：#14358

## 总体目的

本提交修复 Flink Dynamic Iceberg Sink 中 `DynamicWriteResultAggregator` 的一个文件名冲突 bug。

在 Dynamic Iceberg Sink 中，`DynamicWriteResultAggregator` 负责聚合多个 `DynamicWriter` 的写入结果，为每个表（tableName）创建并缓存 `ManifestOutputFileFactory`，用于生成 manifest 文件。原来使用 Caffeine 缓存（带 1 分钟过期和 softValues）来缓存这些 factory 和 partition spec 映射。

问题在于：Caffeine 缓存有过期和 evict 机制。当一个 `ManifestOutputFileFactory` 被缓存驱逐后，下次需要时会重新创建一个新 factory。但 `ManifestOutputFileFactory` 生成文件名的方式是基于 `flinkJobId-operatorUniqueId-subTaskId-attemptNumber-checkpointId-fileCount`，其中 `fileCount` 是一个从 0 开始的 `AtomicInteger`。新创建的 factory 的 `fileCount` 会重置为 0，导致生成的 manifest 文件名与之前被驱逐的 factory 生成的文件名相同，造成文件覆盖/冲突。这在 checkpoint flush 过程中尤其危险，因为 flush 可能跨越缓存过期时间窗口。

本提交通过两个层面解决：(1) 将 Caffeine 缓存替换为简单的 LRU 缓存（无过期时间），减少驱逐概率；(2) 给每个 factory 实例分配一个 UUID 后缀，附加到生成的文件名上，确保即使 factory 被重建也不会产生同名文件。同时移除了 Caffeine 依赖。

## 如何达成设计目的

1. **替换缓存实现**：将 `DynamicWriteResultAggregator` 中的 `Cache<String, Map<Integer, PartitionSpec>>` 和 `Cache<String, ManifestOutputFileFactory>` 从 Caffeine 缓存改为 `LRUCache<>`（基于大小的 LRU 缓存）。LRU 缓存大小由 sink 配置的 `cacheMaximumSize` 控制，不再有过期时间，减少了不必要的驱逐。

2. **为 factory 添加唯一后缀**：在 `ManifestOutputFileFactory` 构造器中新增 `suffix` 参数。生成文件路径时，在原有格式后追加 `-<suffix>`。在 `DynamicWriteResultAggregator.outputFileFactory()` 中创建 factory 时，生成一个 `UUID.randomUUID().toString()` 作为后缀。

3. **使用 `computeIfAbsent` 替代 `get`**：将 `outputFileFactories.get()` 改为 `outputFileFactories.computeIfAbsent()`，确保在缓存未命中时原子性地创建新 factory 并放入缓存，避免竞态条件。同时 `specs.getIfPresent()` 改为 `specs.get()`。

4. **移除 Caffeine 依赖**：从 `flink/v2.1/build.gradle` 中移除 `implementation libs.caffeine`，因为不再使用。

5. **新增测试**：新增 `TestManifestOutputFileFactory` 测试 factory 文件名格式（含/不含后缀）；新增 `testPreventOutputFileFactoryCacheEvictionDuringFlush` 测试在缓存大小为 0（每次都驱逐）时，两个不同 WriteTarget 产生的 manifest 路径仍然唯一；新增 `testUniqueFileSuffixOnFactoryRecreation` 测试 DynamicWriter 清空缓存后重新写入，文件名不冲突。

## 修改详情

### `flink/v2.1/build.gradle` (-3 lines)

**修改目的**：移除不再需要的 Caffeine 依赖。

**工作逻辑**：删除 `// for caching in DynamicSink` 注释和 `implementation libs.caffeine` 行。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkManifestUtil.java` (+14/-1 lines)

**修改目的**：为 `createOutputFileFactory` 新增带 suffix 参数的重载版本。

**工作逻辑**：原有无 suffix 参数的版本改为调用新版本并传 `null`。新增 `createOutputFileFactory(..., String suffix)` 重载，将 suffix 传给 `ManifestOutputFileFactory` 构造器。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/ManifestOutputFileFactory.java` (+11/-3 lines)

**修改目的**：支持文件名后缀以避免 factory 重建时的文件名冲突。

**工作逻辑**：新增 `@Nullable private final String suffix` 字段和构造器参数。`generatePath()` 方法的格式串从 `"%s-%s-%05d-%d-%d-%05d"` 改为 `"%s-%s-%05d-%d-%d-%05d%s"`，末尾追加 `suffix != null ? "-" + suffix : ""`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+1/-1 lines)

**修改目的**：将 `cacheMaximumSize` 传递给 `DynamicWriteResultAggregator`。

**工作逻辑**：`new DynamicWriteResultAggregator(catalogLoader)` 改为 `new DynamicWriteResultAggregator(catalogLoader, cacheMaximumSize)`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriteResultAggregator.java` (+34/-13 lines)

**修改目的**：替换 Caffeine 缓存为 LRU 缓存，使用 `computeIfAbsent` 并添加 UUID 后缀。

**工作逻辑**：移除 Caffeine 相关 import；将 `Cache` 类型字段改为 `Map`（实际用 `LRUCache`）；构造器新增 `cacheMaximumSize` 参数；`initialize()` 中用 `new LRUCache<>(cacheMaximumSize)` 替代 Caffeine builder；`outputFileFactory()` 中用 `computeIfAbsent` 替代 `get`，并在创建 factory 时生成 `UUID.randomUUID().toString()` 作为 suffix 传给 `createOutputFileFactory`；`spec()` 中用 `specs.get()` 替代 `specs.getIfPresent()`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (+5/-0 lines)

**修改目的**：暴露 `taskWriterFactories` 供测试使用。

**工作逻辑**：新增 `@VisibleForTesting Map<WriteTarget, RowDataTaskWriterFactory> getTaskWriterFactories()` 方法。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkManifest.java` (+1/-1 lines)

**修改目的**：适配 `ManifestOutputFileFactory` 构造器新增的 suffix 参数。

**工作逻辑**：构造器调用末尾加 `, null`。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/TestManifestOutputFileFactory.java` (+112 lines, 新文件)

**修改目的**：测试 `ManifestOutputFileFactory` 的文件名格式。

**工作逻辑**：包含三个测试：`testFileNameFormat` 验证无后缀时的文件名格式；`testFileNameFormatWithSuffix` 验证有后缀时的格式；`testSuffixedFileNamesWithRecreatedFactory` 验证两个不同 suffix 的 factory 生成的文件名不同。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicCommitter.java` (+12/-4 lines)

**修改目的**：适配 `DynamicWriteResultAggregator` 构造器新增的 `cacheMaximumSize` 参数。

**工作逻辑**：新增 `final int cacheMaximumSize = 10;` 字段，所有 `new DynamicWriteResultAggregator(catalogLoader)` 调用改为传入 `cacheMaximumSize`。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicWriteResultAggregator.java` (+92/-2 lines)

**修改目的**：新增测试验证缓存驱逐时文件名不冲突。

**工作逻辑**：新增 `testPreventOutputFileFactoryCacheEvictionDuringFlush` 测试，使用 `zeroCacheSize = 0`（每次都驱逐），发送两个不同 WriteTarget 的结果，执行 checkpoint 后验证两个 manifest 路径唯一。新增辅助方法 `getManifestPaths()` 从输出中提取 manifest 路径。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicWriter.java` (+32/-0 lines)

**修改目的**：新增测试验证 DynamicWriter 清空缓存后文件名不冲突。

**工作逻辑**：新增 `testUniqueFileSuffixOnFactoryRecreation` 测试，写入数据后清空 `taskWriterFactories` 缓存，再次写入并验证两次产生的文件名不同。

## 总结

本提交修复了 Flink Dynamic Iceberg Sink 中因 `ManifestOutputFileFactory` 缓存驱逐后重建导致的 manifest 文件名冲突问题。修复策略双管齐下：(1) 用无过期的 LRU 缓存替代 Caffeine 缓存，减少不必要的驱逐；(2) 给每个 factory 实例分配 UUID 后缀附加到文件名，从根本上保证即使 factory 被重建也不会产生同名文件。同时移除了 Caffeine 依赖，减轻了依赖负担。配套的三个测试从 factory 层、aggregator 层、writer 层全面验证了修复的有效性。注意此提交针对 Flink 2.1 版本，2776 是同一功能向 Flink 1.20/2.0 的 backport。
