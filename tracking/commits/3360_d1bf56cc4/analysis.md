# 提交 3360：Core, Spark: Unify bulk deletion (#15501)

## 提交信息

- **序号**：3360 / 4088
- **哈希**：d1bf56cc408211fbf1bf67eea277d9a0846b7108
- **短哈希**：d1bf56cc4
- **日期**：2026-03-09
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core, Spark: Unify bulk deletion (#15501)
- **PR/Issue**：#15501

## 总体目的

该提交旨在统一 Iceberg 代码库中分散在多处的文件批量删除逻辑。在本次改动之前，判断 `FileIO` 是否支持批量删除（`SupportsBulkOperations`）、调用 `bulkIO.deleteFiles()`、捕获 `BulkDeletionFailureException` 以及在非批量模式下回退到并发删除（`Tasks` + `ThreadPools.getWorkerPool()`）这套模式在多个类中被重复实现，存在大量代码重复。

具体而言，`BaseTransaction.deleteUncommittedFiles()`、`SparkTableUtil.deleteManifests()`、`SparkCleanupUtil.deleteFiles()` 以及 `CatalogUtil` 内部多处都各自实现了"先尝试批量删除、失败则回退"的分支逻辑。这种重复带来几个问题：一是维护成本高，任何对批量删除行为（如日志格式、异常处理）的调整都需要在多处同步修改；二是行为不一致，例如 `SparkCleanupUtil` 在批量删除时记录了"Deleted N file(s)"的 info 日志和"Deleted only X of Y"的 warn 日志，而 `BaseTransaction` 只记录 warn 日志，`CatalogUtil` 又有自己的日志格式；三是 `BulkDeletionFailureException` 这一专门表示批量删除部分失败的异常类型在不同调用点处理方式不一。

本次改动将所有这些逻辑统一收拢到 `CatalogUtil.deleteFiles()` 方法族中，调用方只需传入 `FileIO`、文件列表和文件类型描述，由 `CatalogUtil` 统一处理批量/并发删除及异常处理。

## 如何达成设计目的

核心思路是在 `CatalogUtil` 中提供统一入口：

1. 新增重载方法 `deleteFiles(FileIO io, Iterable<String> files, String type)`，内部委托给带 `concurrent` 参数的版本（默认 `true`），为调用方提供更简洁的 API。
2. 在已有 `deleteFiles(FileIO, Iterable, String, boolean)` 中统一处理 `BulkDeletionFailureException`，单独捕获该异常并打印包含失败对象数量的告警日志。
3. 将原来重载的私有 `deleteFiles` 方法重命名为 `concurrentlyDeleteFiles`，消除命名歧义。
4. 各调用方（`BaseTransaction`、`SparkTableUtil`、`SparkCleanupUtil`、`RewritePositionDeletesCommitManager`）删除各自的批量/并发删除实现，改为调用 `CatalogUtil.deleteFiles()`。

涉及 Core 模块 3 个文件和 Spark 各版本（v3.4/v3.5/v4.0/v4.1）下 8 个文件，共 11 个文件。

## 修改详情

### `core/src/main/java/org/apache/iceberg/CatalogUtil.java` (+25/-17 lines)

**修改目的**：统一批量删除入口，新增便捷重载方法并改进异常处理与日志。

**工作逻辑**：
- 新增 import `BulkDeletionFailureException`。
- `dropTableData` 方法中多处对 `deleteFiles` 的调用去掉了冗余的 `true` 参数（`concurrent`），改用新的三参数重载。
- 新增公共重载方法 `deleteFiles(FileIO io, Iterable<String> files, String type)`，文档说明"批量删除优先，否则并发删除"，内部调用 `deleteFiles(io, files, type, true)`。
- 在四参数 `deleteFiles` 方法中：
  - 使用 Java 16 的 pattern matching 将 `if (io instanceof SupportsBulkOperations bulkIO)` 简化。
  - 单独捕获 `BulkDeletionFailureException e`，记录 `LOG.warn("Failed to bulk delete {} {} files", e.numberFailedObjects(), type, e)`，利用异常携带的失败对象数量信息。
  - 其余 `RuntimeException` 仍记录通用告警。
  - 非批量分支中，`concurrent` 为 true 时调用重命名后的 `concurrentlyDeleteFiles`，否则逐个删除。
- 将原私有方法 `deleteFiles` 重命名为 `concurrentlyDeleteFiles`，并将日志格式从 `"Failed to delete {} file {}"` 改为 `"Failed to delete {} file: {}"`（增加冒号提升可读性）。
- `dropPartitions` 中也去掉冗余 `true` 参数。

### `core/src/main/java/org/apache/iceberg/BaseTransaction.java` (+1/-19 lines)

**修改目的**：移除 `deleteUncommittedFiles` 中重复的批量/并发删除逻辑，改用统一入口。

**工作逻辑**：
原 `deleteUncommittedFiles` 方法内联了完整的 `if (io instanceof SupportsBulkOperations)` 分支与 `Tasks.foreach(...).executeWith(ThreadPools.getWorkerPool())` 回退逻辑，共约 15 行。本次将其整体替换为一行：

```java
CatalogUtil.deleteFiles(ops.io(), paths, "uncommitted");
```

同时移除了不再需要的 import：`BulkDeletionFailureException`、`SupportsBulkOperations`、`Tasks`、`ThreadPools`。

### `core/src/main/java/org/apache/iceberg/actions/RewritePositionDeletesCommitManager.java` (+1/-1 lines)

**修改目的**：简化位置删除重写后的文件清理调用。

**工作逻辑**：
将 `CatalogUtil.deleteFiles(table.io(), filePaths, "position delete", true)` 改为 `CatalogUtil.deleteFiles(table.io(), filePaths, "position delete")`，使用新增的三参数重载。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+2/-12 lines)

**修改目的**：统一 Spark 模块中 manifest 删除逻辑。

**工作逻辑**：
`deleteManifests` 方法原来内联了 `if (io instanceof SupportsBulkOperations)` 分支与 `Tasks.foreach` 回退逻辑。本次替换为：

```java
CatalogUtil.deleteFiles(io, Lists.transform(manifests, ManifestFile::path), "manifests");
```

移除了 `SupportsBulkOperations`、`Tasks`、`ThreadPools` 的 import，新增 `CatalogUtil` import。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkCleanupUtil.java` (+2/-23 lines)

**修改目的**：移除 Spark 清理工具中重复的批量删除封装。

**工作逻辑**：
原 `deleteFiles` 方法调用私有 `deletePaths`，后者再分派到 `bulkDelete`（处理 `BulkDeletionFailureException` 并记录 info/warn 日志）或 `delete`（并发逐个删除）。本次大幅简化：
- `deleteFiles` 方法中，若 `io instanceof SupportsBulkOperations`，直接调用 `CatalogUtil.deleteFiles(io, paths, "")`，否则仍调用本地 `delete` 方法。
- 删除了 `deletePaths` 和 `bulkDelete` 两个私有方法。
- 移除了 `BulkDeletionFailureException` import，新增 `CatalogUtil` import。

注意这里传给 `CatalogUtil.deleteFiles` 的 type 为空字符串 `""`，因为调用方 `delete` 方法自身已包含 context 日志，type 参数仅用于 `CatalogUtil` 内部的批量删除告警日志。

### `spark/v3.5/spark/...`、`spark/v4.0/spark/...`、`spark/v4.1/spark/...` 下的 `SparkTableUtil.java` 与 `SparkCleanupUtil.java`

这 6 个文件（3 个版本 × 2 个文件）的改动与上述 v3.4 版本完全一致，分别在各 Spark 版本模块中做相同的统一化处理。各文件改动量与 v3.4 对应文件相同（`SparkTableUtil.java` +2/-12，`SparkCleanupUtil.java` +2/-23）。

## 总结

本次提交通过将分散在 Core 和 Spark 各版本模块中的批量文件删除逻辑统一收拢到 `CatalogUtil.deleteFiles()` 方法族，消除了大量重复代码（净减少 134 行），统一了异常处理（特别是 `BulkDeletionFailureException` 的处理）和日志格式，降低了维护成本并保证了行为一致性。这是典型的"消除重复、统一抽象"重构，对运行时行为无破坏性影响。
