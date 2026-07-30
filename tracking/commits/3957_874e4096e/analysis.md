# 提交 3957：Core, Spark: Ensure correct delete file sizes in rewrite table action (#15470)

## 提交信息

- **序号**：3957 / 4088
- **哈希**：874e4096e5d16fddbbf43b91f20e248616232cc3
- **短哈希**：874e4096e
- **日期**：2026-06-27 14:50:32 -0600
- **作者**：Matt Butrovich
- **提交说明**：Core, Spark: Ensure correct delete file sizes in rewrite table action (#15470)
- **PR/Issue**：#15470

## 总体目的

本提交修复了 Iceberg 的 `RewriteTablePath` action 中的一个数据一致性问题：在重写表路径时，position delete 文件的内容会被重写（因为其中嵌入的数据文件路径需要替换），但重写后的 manifest 中记录的 `file_size_in_bytes` 仍然是原始文件的大小，而非重写后文件的实际大小。这导致元数据中记录的文件大小与磁盘上的实际文件大小不一致，可能引发后续读取或验证问题。

问题的根因在于：路径重写会改变 position delete 文件中嵌入的数据文件路径字符串的长度（例如 `/path/to/` 变为 `/path/new/`），因此重写后的文件大小会发生变化。但旧代码在重写 delete manifest 时直接 `copy` 了原始 DeleteFile 的元数据，没有更新文件大小。

本提交通过在重写 position delete 文件后测量实际文件大小，并将该大小传递给 manifest 重写逻辑，确保 `file_size_in_bytes` 与磁盘上的实际文件一致。

## 如何达成设计目的

设计方案分两层：

1. **Core 层（`RewriteTablePathUtil`）**：新增 `rewritePositionDelete` 方法（替代旧的 `rewritePositionDeleteFile`），返回重写后文件的大小（通过 writer 的 `length()` 获取，而非额外的 HEAD 请求）。同时新增 `rewriteDeleteManifest` 的重载，接受一个 `Map<String, Long> rewrittenDeleteFileSizes` 参数，在写入 manifest 时用测量的实际大小替换原始 `file_size_in_bytes`。旧方法被标记为 `@Deprecated`。

2. **Spark 层（`RewriteTablePathSparkAction`）**：调整执行顺序——先重写 position delete 文件（收集大小映射），再重写 delete manifest（使用大小映射）。通过 Spark broadcast 将大小映射分发给各个 executor。

## 修改详情

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java` (+89/-31 lines)

**修改目的**：新增返回文件大小的重写方法，并在 manifest 重写时使用实际大小。

**工作逻辑**：
- 旧 `rewriteDeleteManifest` 标记 `@Deprecated`，委托给新重载并传入空 map（保持兼容）。
- 新 `rewriteDeleteManifest` 接受 `rewrittenDeleteFileSizes` 参数，传递给 `rewriteDeleteManifestEntry`。
- `rewriteDeleteManifestEntry` 中对 POSITION_DELETES 类型：
  ```java
  long fileSizeInBytes =
      rewrittenDeleteFileSizes.getOrDefault(file.location(), file.fileSizeInBytes());
  ```
  即从 map 中查找测量大小，找不到则回退到原始大小（处理 DELETED 条目等未重写情况）。
- `newPositionDeleteEntry` 新增 `fileSizeInBytes` 参数，通过 `.withFileSizeInBytes(fileSizeInBytes)` 设置。
- 新 `rewritePositionDelete` 方法返回 `long`（文件大小），在 writer 关闭后通过 `writer.length()` 获取。DV 文件的 `rewriteDVFile` 也改为返回 `long`。

### `core/src/test/java/org/apache/iceberg/TestRewriteTablePathUtil.java` (+97/-2 lines)

**修改目的**：验证未重写的 DELETED 条目保留原始大小，已重写的 LIVE 条目使用测量大小。

**工作逻辑**：新增 `testRewriteDeleteManifestFallsBackToOriginalSizeForDeletedEntries` 测试，构造包含一个 LIVE 条目（FILE_A_DELETES）和一个 DELETED 条目（FILE_B_DELETES）的 manifest，传入测量大小 map（仅含 FILE_A）。验证重写后 manifest 中 LIVE 条目使用测量大小 9999L，DELETED 条目保留原始大小。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+160/-35 lines)

**修改目的**：调整执行顺序，先重写 delete 文件再重写 manifest，并通过 broadcast 传递大小映射。

**工作逻辑**：
- 调整 `rewriteTablePath` 主流程：先从 manifest 列表中筛选 delete manifest，调用 `positionDeletesToRewrite` 枚举需重写的 position delete 文件，调用 `rewritePositionDeletes` 获取大小映射，最后调用 `rewriteManifests` 时 broadcast 大小映射。
- 新增 `positionDeletesToRewrite`：用 Spark flatMap 分布式读取 delete manifest，收集所有 position delete 文件，通过 `DeleteFileSet` 去重。
- `rewritePositionDeletes` 改为返回 `Map<String, Long>`：先按 location 去重物理文件（多个 DV 可共享同一 Puffin 文件），然后用 `map` 算子重写并返回 `(路径, 大小)` 元组，`collectAsList` 后转为 map。
- `rewriteManifests` 及相关方法新增 `Broadcast<Map<String, Long>> rewrittenDeleteFileSizes` 参数。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+304/-1 lines)

**修改目的**：验证端到端场景下 delete 文件大小的正确性。

**工作逻辑**：新增测试覆盖不同版本（v2/v3）和不同 delete 文件类型（position delete、DV），验证重写后 manifest 中记录的文件大小与磁盘上实际文件大小一致。

### Spark v3.4/v3.5 模块的同名文件

**修改目的**：同样的修改同步到 spark v3.4 模块（`spark/v3.4/spark/src/main/java/...` 和 `spark/v3.4/spark/src/test/java/...`），以及 v3.5 模块。保持各 Spark 版本的行为一致。

## 总结

本提交修复了一个元数据一致性问题：rewrite table path 后 delete manifest 中的 `file_size_in_bytes` 与实际文件大小不匹配。修复方案通过在重写 position delete 文件时测量实际大小并传递给 manifest 重写逻辑，确保了元数据的准确性。这是一个重要的数据完整性修复，因为不正确的文件大小可能导致读取器无法正确定位文件内容或在验证时失败。
