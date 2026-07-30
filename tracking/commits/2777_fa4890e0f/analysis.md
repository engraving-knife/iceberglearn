# 提交 2777：Flink: Backport Prevent recreation of ManifestOutputFileFactory during flushing (#14385)

## 提交信息

- **序号**：2777 / 4088
- **哈希**：fa4890e0f923ae1f9fbf0065d2430e21e77174f3
- **短哈希**：fa4890e0f
- **日期**：2025-10-21 12:02:19 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport Prevent recreation of ManifestOutputFileFactory during flushing (#14385)
- **PR/Issue**：#14385（backport #14358）

## 总体目的

本提交是 PR #14358（提交 2772）向 Flink 1.20 和 Flink 2.0 版本的 backport。

2772 修复了 Flink Dynamic Iceberg Sink 中因 `ManifestOutputFileFactory` 缓存驱逐后重建导致的 manifest 文件名冲突问题。该修复最初只应用于 Flink 2.1 版本。本提交将相同的修复同步到 Flink 1.20 和 2.0 两个版本，确保所有支持的 Flink 版本都能受益于此修复。

修复内容与 2772 完全一致：(1) 用无过期的 LRU 缓存替代 Caffeine 缓存；(2) 给每个 `ManifestOutputFileFactory` 实例分配 UUID 后缀以避免文件名冲突；(3) 使用 `computeIfAbsent` 替代 `get`；(4) 移除 Caffeine 依赖；(5) 新增对应的测试。

## 如何达成设计目的

将 2772 的所有修改完整复制到 `flink/v1.20` 和 `flink/v2.0` 两个目录下。涉及的文件和修改与 2772 完全相同，只是目录路径不同：

1. `flink/v1.20/build.gradle` 和 `flink/v2.0/build.gradle`：移除 Caffeine 依赖
2. `FlinkManifestUtil.java`：新增带 suffix 参数的 `createOutputFileFactory` 重载
3. `ManifestOutputFileFactory.java`：新增 suffix 字段和构造器参数，文件名格式追加后缀
4. `DynamicIcebergSink.java`：传递 `cacheMaximumSize` 给 aggregator
5. `DynamicWriteResultAggregator.java`：替换 Caffeine 为 LRU，使用 `computeIfAbsent`，添加 UUID 后缀
6. `DynamicWriter.java`：暴露 `taskWriterFactories` 供测试使用
7. 测试文件：新增 `TestManifestOutputFileFactory`，更新 `TestDynamicCommitter`、`TestDynamicWriteResultAggregator`、`TestDynamicWriter`、`TestFlinkManifest`

## 修改详情

### `flink/v1.20/` 和 `flink/v2.0/` 下各 11 个文件（共 22 个文件，+580/-58 lines）

**修改目的**：将 2772 的修复同步到 Flink 1.20 和 2.0 版本。

**工作逻辑**：每个版本下的修改与 2772 中 Flink 2.1 版本的修改完全一致，详见 2772 的分析文档。主要修改包括：
- `build.gradle`：移除 `implementation libs.caffeine`
- `ManifestOutputFileFactory`：新增 `suffix` 参数，文件名格式从 `"%s-%s-%05d-%d-%d-%05d"` 改为 `"%s-%s-%05d-%d-%d-%05d%s"`
- `DynamicWriteResultAggregator`：Caffeine 缓存替换为 `LRUCache`，`outputFileFactory()` 使用 `computeIfAbsent` 并生成 UUID 后缀
- 新增 `TestManifestOutputFileFactory` 测试文件名格式
- 新增 `testPreventOutputFileFactoryCacheEvictionDuringFlush` 和 `testUniqueFileSuffixOnFactoryRecreation` 测试

## 总结

本提交是 2772（#14358）向 Flink 1.20 和 2.0 的 backport，确保三个支持的 Flink 版本（1.20、2.0、2.1）都获得了 `ManifestOutputFileFactory` 文件名冲突修复。修改内容与原始 PR 完全一致，仅目录路径不同。
