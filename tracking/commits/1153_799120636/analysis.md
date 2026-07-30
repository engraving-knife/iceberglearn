# 提交 1153：API, Core: Add manifestLocation API to ContentFile (#11044)

## 提交信息

- **序号**：1153 / 4088
- **哈希**：799120636e8f5f19c1d7f217ab4968f524bb1246
- **短哈希**：799120636
- **日期**：2024-09-12（Thu Sep 12 22:50:54 2024 -0600）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：API, Core: Add manifestLocation API to ContentFile (#11044)
- **PR/Issue**：#11044

## 总体目的

Iceberg 的 `ContentFile<F>`（`DataFile`、`DeleteFile` 的父接口）此前已经提供了 `fileOrdinal()` 等"文件在 manifest 中的位置"信息，但缺少"这个文件是从哪个 manifest 文件读出来的"这一关键信息。这意味着下游引擎（Spark / Flink 等）在拿到 `FileScanTask` 之后无法知道 `DataFile` 来自哪一份 manifest 文件，而该信息对调试、血缘追踪、缓存预取、以及按 manifest 做文件分组等场景都很有价值。

本提交在 `ContentFile` 接口上新增 `manifestLocation()` 默认方法，返回该文件所属 manifest 的路径（`String`），若该文件不是从 manifest 读取而是用户直接构造的则返回 `null`。同时在 `BaseFile`、`InheritableMetadataFactory` 中实际填充该字段，让通过 manifest 读取出来的 `DataFile` / `DeleteFile` 都能正确带回所属 manifest 的路径。

## 如何达成设计目的

1. 在 `api` 模块的 `ContentFile` 接口上添加 `default String manifestLocation()` 方法，默认返回 `null`，对二进制兼容、对实现类不强制。
2. 在 `core` 模块的 `BaseFile`（`DataFile` / `DeleteFile` 的公共抽象基类）上新增 `manifestLocation` 字段、对应的 setter 与 getter，并在拷贝构造函数中复制该字段。
3. 在 `InheritableMetadataFactory` 的 `BaseInheritableMetadata` 中携带 `manifestLocation`（即 `manifest.path()`），在 `apply(FileEntry)` 阶段调用 `file.setManifestLocation(manifestLocation)`，让通过 manifest 读取出来的每个 `ContentFile` 都带上来源 manifest 路径。
4. 对 V1/V2/V3 元数据中那些"包装已有 ContentFile 但不来自 manifest 读取"的 wrapper 类（如 `IndexedDataFile` / `IndexedDeleteFile` 等），显式 override `manifestLocation()` 返回 `null`，因为这些 wrapper 是在投影/索引时构造的，原始 manifest 上下文已丢失，不应错误地保留旧值。
5. 测试上：在 `TestManifestReader` 中新增两个用例分别验证 data 文件与 delete 文件读出后 `manifestLocation()` 等于 manifest 自身 `path()`；在 `DataTableScanTestBase` 中新增两个用例验证 scan 阶段 `FileScanTask.file().manifestLocation()` 与 `fileScanTask.deletes()` 中 `DeleteFile.manifestLocation()` 都能正确指向所属 manifest。

## 修改详情

### `api/src/main/java/org/apache/iceberg/ContentFile.java`

**修改目的**：在 API 层暴露 `manifestLocation()` 默认方法。

**工作逻辑**：在接口顶部新增：

```java
/**
 * Returns the path of the manifest which this file is referenced in or null if it was not read
 * from a manifest.
 */
default String manifestLocation() {
  return null;
}
```

使用 `default` 方法保证对现有实现类（包括第三方实现）二进制兼容；默认返回 `null` 表示"不是从 manifest 读出"的语义。

### `core/src/main/java/org/apache/iceberg/BaseFile.java`

**修改目的**：在公共基类中真正承载 `manifestLocation` 字段。

**工作逻辑**：
- 新增字段 `private String manifestLocation = null;`，与 `fileOrdinal`、`partitionSpecId` 等并列。
- 在"按引用复制"的拷贝构造函数 `BaseFile(BaseFile<F> toCopy, boolean copyStats, Set<Integer> requestedColumnIds)` 中新增 `this.manifestLocation = toCopy.manifestLocation;`，保证投影/裁剪 stats 后副本仍能携带来源信息。
- 新增 package-private setter `void setManifestLocation(String manifestLocation)`，供 `InheritableMetadataFactory` 在读取 manifest 时填充。
- 新增 `@Override public String manifestLocation()` getter，返回该字段。

### `core/src/main/java/org/apache/iceberg/InheritableMetadataFactory.java`

**修改目的**：在 manifest 读取链路上把 manifest 路径注入到每个 `ContentFile`。

**工作逻辑**：
- `BaseInheritableMetadata` 新增字段 `manifestLocation`，构造函数追加该参数；`forManifest(ManifestFile manifest)` 调用处补传 `manifest.path()`。
- `apply(ManifestEntry<? extends ContentFile<?>> manifestEntry)` 方法在原有 `setSpecId` / `setDataSequenceNumber` / `setFileSequenceNumber` 之后新增一行 `file.setManifestLocation(manifestLocation);`，这样每个从 manifest 读出的 entry 都会被打上"我来自这个 manifest"的标记。

### `core/src/main/java/org/apache/iceberg/V1Metadata.java` / `V2Metadata.java` / `V3Metadata.java`

**修改目的**：让投影/索引 wrapper 显式声明 `manifestLocation()` 为 `null`。

**工作逻辑**：三个文件各自在其内部 wrapper 类（V1 的 `IndexedDataFile`、V2 的 `IndexedDataFile`/`IndexedDeleteFile`、V3 同理）中新增：

```java
@Override
public String manifestLocation() {
  return null;
}
```

这些 wrapper 是在按列投影、列裁剪时由原始 `ContentFile` 包装而来，可能跨越多个 manifest；保留原始 manifest 路径会产生歧义，故而显式返回 `null`。同时 wrapper 本身已经显式 override 了 `fileOrdinal()`、`fileSequenceNumber()` 等返回 `null`，此处保持一致风格。

### `core/src/test/java/org/apache/iceberg/TestManifestReader.java`

**修改目的**：验证 manifest 读取后 `manifestLocation()` 字段被正确填充。

**工作逻辑**：
- 在 `FILE_COMPARISON_CONFIG` 的忽略字段列表中追加 `"manifestLocation"`，因为已有的大量对比测试不关心该新字段（避免大面积失败）。
- 新增 `testDataFileManifestPaths`：写入一份包含 `FILE_A`/`FILE_B`/`FILE_C` 的 data manifest，读取后断言每个 `DataFile.manifestLocation()` 等于 `manifest.path()`。
- 新增 `testDeleteFileManifestPaths`：v2+ 才有 delete 文件，写入 delete manifest 后读取，断言每个 `DeleteFile.manifestLocation()` 等于 `manifest.path()`。

### `core/src/test/java/org/apache/iceberg/DataTableScanTestBase.java`

**修改目的**：在 scan 层端到端验证 manifest 路径能透传到 `FileScanTask`。

**工作逻辑**：
- 新增 `import java.util.Collection;` `import java.util.stream.Collectors;` `import org.apache.iceberg.util.CharSequenceMap;`。
- 重载 `validateExpectedFileScanTasks`：新增带 `CharSequenceMap<String> fileToManifest` 参数的版本；若传入非 null，则在遍历 `FileScanTask` 时断言 `fileToManifest.get(dataFile.path())` 等于 `dataFile.manifestLocation()`。原方法重载为委托。
- 新增 `testManifestLocationsInScan`：通过两次 fast append 提交 `FILE_A`、然后 `FILE_B`+`FILE_C`，构造两个不同的 data manifest；用 `CharSequenceMap` 记录每个 file 期望对应的 manifest 路径，调用 scan 后断言每个 `DataFile` 的 `manifestLocation()` 与期望一致。
- 新增 `testManifestLocationsInScanWithDeleteFiles`（仅 v2）：append `FILE_A` 后再 `addDeletes` 一个 delete file；先验证 `FILE_A` 的 `manifestLocation()` 指向第一个 data manifest，再验证 scan 出来的 delete file 的 `manifestLocation()` 指向当前 snapshot 的 delete manifest。

## 小结

- **成效**：下游引擎现在可以从 `ContentFile.manifestLocation()` 直接获取"这个文件来自哪份 manifest"，便于调试、血缘追踪、按 manifest 分组执行等场景；同时不破坏任何已有 API 与二进制兼容性。
- **影响范围**：`api` 模块新增一个 default 方法（无破坏性）；`core` 模块在 `BaseFile` / `InheritableMetadataFactory` 中填充字段；V1/V2/V3 wrapper 显式返回 null；测试增强。无元数据格式变更，无序列化变更。
- **回迁到 1.4.x 的注意事项**：
  1. 这是一个纯增量 API，对 1.4.x 完全向前兼容，回迁风险很低。
  2. 回迁时必须同时携带 `api`、`core` 两侧改动，否则 `BaseFile` 等子类无法编译（如果只回迁 api 默认方法则可独立编译但功能不生效）。
  3. 需注意 V1/V2/V3 wrapper 中也要 override `manifestLocation()` 返回 null，否则当 wrapper 委托给原始 `ContentFile` 时（若 wrapper 内部委托模式不同）可能错误地透出旧值。1.4.x 若 V3 模块尚未引入（取决于 1.4.x 是否已合入 V3 支持），可仅回迁 V1/V2 部分。
  4. 测试侧需要把 `manifestLocation` 加入 `FILE_COMPARISON_CONFIG` 忽略字段，否则 1.4.x 现有 `TestManifestReader` 的对比用例会失败。
  5. 由于不改变 manifest 文件格式与 metadata JSON 结构，回迁后与已存在的 1.4.x 表/manifest 完全兼容，无升级路径。
