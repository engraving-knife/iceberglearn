# 提交 3900：Core: Cache manifest list files in manifest content cache (#16762)

## 提交信息

- **序号**：3900 / 4088
- **哈希**：2f1e3d0dec85f79ac020911ff0b114a3f297e52b
- **短哈希**：2f1e3d0de
- **日期**：2026-06-17 15:34:32 -0700
- **作者**：Rahul Shivu Mahadev
- **提交说明**：Core: Cache manifest list files in manifest content cache (#16762)
- **PR/Issue**：#16762

## 总体目的

将 manifest list 文件纳入 Iceberg 的 manifest 内容缓存（content cache）中。此前，manifest 文件（manifest files）的内容可以被缓存以避免重复读取，但 manifest list 文件（列出快照中所有 manifest 的列表文件）在每次读取快照时都会重新从存储中加载。

manifest list 文件是 Avro 格式的元数据文件，记录了快照中包含的所有 manifest 文件路径。在表扫描、提交等操作中频繁读取。在远程存储（如 S3、GCS）上，每次读取都涉及网络请求，将 manifest list 纳入缓存可以减少网络开销，特别是在频繁扫描同一快照的场景下（如多个并发查询）。

## 如何达成设计目的

在 `ManifestLists` 类中新增 `newInputFile` 方法，该方法在创建 manifest list 的 InputFile 时检查缓存是否启用，若启用则通过 `ManifestFiles.contentCache(io).tryCache(input)` 包装为缓存输入文件。`BaseSnapshot` 中的调用点改为使用新方法。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseSnapshot.java` (+2/-1 lines)

**修改目的**：使用支持缓存的 manifest list 输入文件创建方式。

**工作逻辑**：
```java
this.allManifests =
    ManifestLists.read(
-       fileIO.newInputFile(new BaseManifestListFile(manifestListLocation, keyId)));
+       ManifestLists.newInputFile(
+           fileIO, new BaseManifestListFile(manifestListLocation, keyId)));
```
将直接调用 `fileIO.newInputFile()` 改为通过 `ManifestLists.newInputFile()` 工厂方法，后者会透明地应用缓存。

### `core/src/main/java/org/apache/iceberg/ManifestLists.java` (+10/-0 lines)

**修改目的**：新增支持缓存的 manifest list 输入文件工厂方法。

**工作逻辑**：
```java
static InputFile newInputFile(FileIO io, ManifestListFile manifestList) {
  InputFile input = io.newInputFile(manifestList);
  if (ManifestFiles.cachingEnabled(io)) {
    return ManifestFiles.contentCache(io).tryCache(input);
  }
  return input;
}
```
方法首先创建原始 InputFile，若 FileIO 启用了 manifest 缓存（通过 `ManifestFiles.cachingEnabled(io)` 检查），则通过 `contentCache(io).tryCache(input)` 返回缓存包装的 InputFile。`tryCache` 方法会在缓存命中时返回缓存内容，未命中时从原始 InputFile 读取并缓存。

### `core/src/test/java/org/apache/iceberg/TestManifestCaching.java` (+49/-6 lines)

**修改目的**：验证 manifest list 缓存行为并更新现有测试的缓存大小预期。

**工作逻辑**：

1. **更新现有测试预期**：由于 manifest list 现在也被缓存，每个 append commit 缓存的条目数从 1（manifest）变为 2（manifest + manifest list）：
```java
// 每个append commit缓存一个manifest和一个manifest list
assertThat(cache.estimatedCacheSize())
    .as("All manifest files and manifest lists should be cached")
    .isEqualTo(numFiles * 2);
```

2. **新增 `testManifestListCaching` 测试**：
   - 创建表并追加文件
   - 清空缓存确保从零开始
   - 读取快照元数据解析 manifest list -> 断言缓存大小为 1，missCount 为 1
   - 再次读取相同 manifest list -> 断言缓存命中（missCount 不变，hitCount 增加）

## 总结

将 manifest list 文件纳入 Iceberg 的 manifest 内容缓存，减少了频繁扫描同一快照时的远程存储读取开销。实现方式简洁：在 `ManifestLists` 中新增工厂方法，透明地为启用缓存的 FileIO 包装 InputFile。缓存复用已有的 `ContentCache` 基础设施，无需引入新的缓存机制。测试验证了缓存命中/未命中的正确行为，并更新了现有测试以反映缓存条目数的变化。
