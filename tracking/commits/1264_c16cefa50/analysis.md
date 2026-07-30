# 提交 1264：Core: Move deleteRemovedMetadataFiles(..) to CatalogUtil (#11352)

## 提交信息

- **序号**：1264 / 4088
- **哈希**：c16cefa5015dda417f80e0d59124cd92448787ab
- **短哈希**：c16cefa50
- **日期**：2024-10-21（Mon Oct 21 20:35:03 2024 +0800）
- **作者**：leesf <490081539@qq.com>
- **提交说明**：Core: Move deleteRemovedMetadataFiles(..) to CatalogUtil (#11352)
- **PR/Issue**：#11352

## 总体目的

Iceberg 在表元数据提交后，如果 `TableProperties.METADATA_DELETE_AFTER_COMMIT_ENABLED` 为 true，会删除"已从元数据日志中移除的旧 metadata 文件"（即在新旧 metadata 的 `previousFiles` 列表差集中、不再被保留的那些文件）。这一清理逻辑此前在两处有几乎相同但略有差异的实现：

1. `BaseMetastoreTableOperations.deleteRemovedMetadataFiles(TableMetadata base, TableMetadata metadata)`：供 Hive/Glue/JDBC/Nessie/REST 等 metastore-backed catalog 的表操作使用。它会检查 `FileIO` 是否实现 `SupportsBulkOperations`，若支持则用 `deleteFiles`（批量删除），否则用 `Tasks.foreach` 逐个删除（在当前线程执行，无 `executeWith`）。
2. `HadoopTableOperations.deleteRemovedMetadataFiles(TableMetadata base, TableMetadata metadata)`：供 HadoopCatalog 的表操作使用。它**不检查** `SupportsBulkOperations`，总是用 `Tasks.foreach(...).executeWith(ThreadPools.getWorkerPool())` 逐个并行删除（通过 worker 线程池）。

两处实现存在以下问题：

1. **代码重复**：核心逻辑（计算差集、决定是否删除、失败抑制与日志）几乎完全一致，违反 DRY。
2. **行为不一致**：HadoopTableOperations 的实现不利用 bulk deletion 能力（即使底层 FileIO 支持），而 BaseMetastoreTableOperations 的实现不并行（即使有大量旧文件）。两者各有优劣但未统一。
3. **维护负担**：未来若要调整清理策略（如增加并发、增加重试），需同步修改两处，容易遗漏。

本提交把 `deleteRemovedMetadataFiles` 抽取为 `CatalogUtil` 的公共静态方法 `deleteRemovedMetadataFiles(FileIO, TableMetadata, TableMetadata)`，让两个 TableOperations 都调用它，消除重复并统一行为。统一后的实现采用 BaseMetastoreTableOperations 的策略（优先 bulk deletion，否则当前线程逐个删除）。

## 如何达成设计目的

通过"提取方法到工具类 + 替换两处调用"实现：

1. 在 `CatalogUtil` 中新增 `public static void deleteRemovedMetadataFiles(FileIO io, TableMetadata base, TableMetadata metadata)`，逻辑取自 `BaseMetastoreTableOperations` 的版本（含 `SupportsBulkOperations` 检查），并把 `FileIO` 作为显式参数传入（原方法是实例方法，通过 `io()` 获取）。
2. `BaseMetastoreTableOperations.doCommit` 中的 `deleteRemovedMetadataFiles(base, metadata)` 改为 `CatalogUtil.deleteRemovedMetadataFiles(io(), base, metadata)`，删除私有方法。
3. `HadoopTableOperations.commit` 中的 `deleteRemovedMetadataFiles(base, metadata)` 改为 `CatalogUtil.deleteRemovedMetadataFiles(io(), base, metadata)`，删除私有方法。
4. 清理两个类中不再使用的 import（`Set`、`Sets`、`Iterables`、`Tasks`、`ThreadPools`、`SupportsBulkOperations` 等）。

行为变化（仅影响 `HadoopTableOperations`）：

- **获得 bulk deletion 能力**：若 `HadoopTableOperations` 使用的 FileIO 实现 `SupportsBulkOperations`（如 `S3FileIO`、`HadoopFileIO`），现在会走批量删除路径，效率更高。
- **失去并行删除**：原 `HadoopTableOperations` 用 `ThreadPools.getWorkerPool()` 并行删除旧 metadata 文件，统一后改为当前线程逐个删除（非 bulk 路径）。由于 metadata 文件数量通常很少（受 `METADATA_PREVIOUS_VERSIONS_MAX` 默认 100 限制，且每次只删除差集），串行删除的开销可忽略，简化是合理的。

## 修改详情

### `core/src/main/java/org/apache/iceberg/CatalogUtil.java`（修改，+45 行）

**修改目的**：新增统一的 `deleteRemovedMetadataFiles` 公共静态方法。

**工作逻辑**：

```java
public static void deleteRemovedMetadataFiles(
    FileIO io, TableMetadata base, TableMetadata metadata) {
  if (base == null) {
    return;
  }

  boolean deleteAfterCommit =
      metadata.propertyAsBoolean(
          TableProperties.METADATA_DELETE_AFTER_COMMIT_ENABLED,
          TableProperties.METADATA_DELETE_AFTER_COMMIT_ENABLED_DEFAULT);

  if (deleteAfterCommit) {
    Set<TableMetadata.MetadataLogEntry> removedPreviousMetadataFiles =
        Sets.newHashSet(base.previousFiles());
    // TableMetadata#addPreviousFile builds up the metadata log and uses
    // TableProperties.METADATA_PREVIOUS_VERSIONS_MAX to determine how many files should stay in
    // the log, thus we don't include metadata.previousFiles() for deletion - everything else can
    // be removed
    removedPreviousMetadataFiles.removeAll(metadata.previousFiles());
    if (io instanceof SupportsBulkOperations) {
      ((SupportsBulkOperations) io)
          .deleteFiles(
              Iterables.transform(
                  removedPreviousMetadataFiles, TableMetadata.MetadataLogEntry::file));
    } else {
      Tasks.foreach(removedPreviousMetadataFiles)
          .noRetry()
          .suppressFailureWhenFinished()
          .onFailure(
              (previousMetadataFile, exc) ->
                  LOG.warn(
                      "Delete failed for previous metadata file: {}", previousMetadataFile, exc))
          .run(previousMetadataFile -> io.deleteFile(previousMetadataFile.file));
    }
  }
}
```

关键设计点：

- `FileIO io` 作为显式参数，让调用方传入自己的 FileIO 实例，解耦于 TableOperations 实例方法。
- `base == null` 提前返回：首次提交（无 base）时无需清理。
- 仅当 `METADATA_DELETE_AFTER_COMMIT_ENABLED` 为 true 时执行删除（默认 false）。
- 差集计算：`base.previousFiles()` 减去 `metadata.previousFiles()`，得到"被移出元数据日志的旧文件"。注释说明 `metadata.previousFiles()` 中保留的文件由 `METADATA_PREVIOUS_VERSIONS_MAX` 控制，不应删除。
- 删除策略：优先 `SupportsBulkOperations.deleteFiles`（批量），否则 `Tasks.foreach` 逐个删除，`noRetry` + `suppressFailureWhenFinished` + `onFailure` 日志告警（best-effort，不阻断提交）。
- `CatalogUtil` 与 `TableProperties`、`TableMetadata` 同在 `org.apache.iceberg` 包，无需额外 import；`FileIO`、`SupportsBulkOperations`、`Sets`、`Iterables`、`Tasks`、`LOG` 均已在 CatalogUtil 中导入/声明。

### `core/src/main/java/org/apache/iceberg/BaseMetastoreTableOperations.java`（修改，-49 行）

**修改目的**：删除私有 `deleteRemovedMetadataFiles` 方法，改为调用 `CatalogUtil`。

**工作逻辑**：

- `doCommit` 中 `deleteRemovedMetadataFiles(base, metadata)` 改为 `CatalogUtil.deleteRemovedMetadataFiles(io(), base, metadata)`。
- 删除原私有方法（49 行）。
- 清理不再使用的 import：`java.util.Set`、`org.apache.iceberg.io.SupportsBulkOperations`、`org.apache.iceberg.relocated.com.google.common.collect.Iterables`、`org.apache.iceberg.relocated.com.google.common.collect.Sets`。
- 行为无变化（新方法逻辑与原方法一致）。

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopTableOperations.java`（修改，-40 行）

**修改目的**：删除私有 `deleteRemovedMetadataFiles` 方法，改为调用 `CatalogUtil`。

**工作逻辑**：

- `commit` 中 `deleteRemovedMetadataFiles(base, metadata)` 改为 `CatalogUtil.deleteRemovedMetadataFiles(io(), base, metadata)`。
- 删除原私有方法（40 行）。
- 新增 `import org.apache.iceberg.CatalogUtil;`。
- 清理不再使用的 import：`java.util.Set`、`org.apache.iceberg.relocated.com.google.common.collect.Sets`、`org.apache.iceberg.util.Tasks`、`org.apache.iceberg.util.ThreadPools`。
- **行为变化**：
  - 原实现：总是 `Tasks.foreach(...).executeWith(ThreadPools.getWorkerPool())` 并行逐个删除，不检查 bulk 支持。
  - 新实现：若 FileIO 支持 `SupportsBulkOperations` 则批量删除；否则当前线程逐个删除（无 `executeWith`）。
  - 净影响：对支持 bulk deletion 的 FileIO（如 S3FileIO）效率提升；对不支持 bulk 的 FileIO，从并行变串行，但 metadata 文件量小，影响可忽略。

## 小结

- **成效**：消除了 `BaseMetastoreTableOperations` 和 `HadoopTableOperations` 中重复的 `deleteRemovedMetadataFiles` 实现，统一到 `CatalogUtil.deleteRemovedMetadataFiles(FileIO, TableMetadata, TableMetadata)` 公共方法。减少了约 86 行重复代码，未来调整元数据文件清理策略只需改一处。
- **影响范围**：仅 `core` 模块的 3 个文件。`BaseMetastoreTableOperations` 行为不变；`HadoopTableOperations` 行为有调整（获得 bulk deletion 支持，失去并行逐个删除），但因 metadata 文件清理量小，实际影响可忽略。
- **回迁到 1.4.x 的注意事项**：
  1. 纯重构 + 行为微调，回迁风险低。需确认 1.4.x 分支上 `CatalogUtil`、`BaseMetastoreTableOperations`、`HadoopTableOperations` 的结构与 main 一致（同在 `org.apache.iceberg` 包，`CatalogUtil` 已有 `LOG` 和所需 import）。
  2. `HadoopTableOperations` 的行为变化需留意：若 1.4.x 上有用户依赖并行删除 metadata 文件的行为（不太可能，因 metadata 文件量小），回迁后会变串行。但更可能的是受益于 bulk deletion 支持。
  3. 回迁后建议跑 `TestCatalogUtil`、`TestHadoopTables`（或对应测试）确认清理逻辑正常。
  4. 若 1.4.x 上 `deleteRemovedMetadataFiles` 还有其他调用方（如 view 操作），可一并迁移到 `CatalogUtil`。
