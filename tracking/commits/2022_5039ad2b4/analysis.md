# 提交 2022：Core: Add test cases for row lineage metadata (#12843)

## 提交信息

- **序号**：2022 / 4088
- **哈希**：5039ad2b4ad83e3416e6abe0f3dcf3de65ba91bf
- **短哈希**：5039ad2b4
- **日期**：2025-04-21 19:02:45 -0700
- **作者**：Ryan Blue
- **提交说明**：Core: Add test cases for row lineage metadata (#12843)
- **PR/Issue**：#12843

## 总体目的

这个提交虽然标题是"添加测试用例"，但实际上包含了对行血统（row lineage）元数据处理的重要重构和 bug 修复，同时新增了大量测试用例。主要工作包括：

1. **引入 Delegates 模式**：新增 `Delegates` 类，提供 `DelegatingContentFile`、`DelegatingDataFile`、`DelegatingDeleteFile` 等委托基类，消除 `SnapshotProducer.PendingDeleteFile` 和 `V3Metadata.DataFileWrapper` 中大量重复的委托方法。

2. **修复 first_row_id 处理**：在 `MergingSnapshotProducer` 中，添加数据文件时通过 `Delegates.suppressFirstRowId` 抑制 first_row_id（因为 first_row_id 应由清单列表写入器分配，而非来自写入端）。在 `copyManifest` 中移除了传递 first_row_id（copyAppend 时应读取 null），并新增校验确保不能追加已分配 first_row_id 的清单。

3. **重构 PendingDeleteFile**：将 `SnapshotProducer.PendingDeleteFile` 改为继承 `Delegates.PendingDeleteFile`，标记为 `@Deprecated`（将在 1.10.0 移除）。

4. **重构 V3Metadata.DataFileWrapper**：改为继承 `Delegates.DelegatingContentFile`，消除约 120 行重复的委托方法。

5. **新增测试**：`TestRowLineageAssignment` 新增 150 行测试，覆盖更多行 ID 分配场景。

## 如何达成设计目的

通过引入委托模式（Delegate pattern）来统一 `ContentFile` 的包装实现。`Delegates.DelegatingContentFile` 作为基类，提供所有 `ContentFile` 接口方法的默认委托实现。子类（`DelegatingDataFile`、`DelegatingDeleteFile`、`PendingDeleteFile`、`DataFileWrapper`）只需覆写需要自定义行为的方法（如 `firstRowId()`、`dataSequenceNumber()` 等），大幅减少样板代码。

关键组件协作：`MergingSnapshotProducer` 使用 `Delegates.suppressFirstRowId` 包装数据文件 → `SnapshotProducer` 使用 `Delegates.pendingDeleteFile` 包装删除文件 → `V3Metadata.DataFileWrapper` 继承委托基类用于 Avro 序列化。

## 修改详情

### `core/src/main/java/org/apache/iceberg/Delegates.java` (新增, +295/-0 lines)

**修改目的**：提供 ContentFile 委托模式的统一实现。

**工作逻辑**：

1. **`suppressFirstRowId(F file)`**：静态工厂方法，如果文件是 DataFile 且有非 null 的 firstRowId，返回一个 `DelegatingDataFile` 子类实例，其 `firstRowId()` 返回 null。用于在 `MergingSnapshotProducer` 中添加数据文件时抑制 first_row_id，确保该值由清单列表写入器在提交时分配。

2. **`pendingDeleteFile(DeleteFile file, Long dataSequenceNumber)`**：静态工厂方法，创建 `PendingDeleteFile` 实例。

3. **`DelegatingContentFile<F>`**：抽象基类，实现 `ContentFile<F>` 接口，所有方法委托给 `wrapped` 对象。提供 `setWrapped` 和 `wrap` 抽象方法供子类实现。

4. **`DelegatingDataFile`**：继承 `DelegatingContentFile<DataFile>`，实现 `DataFile` 接口，提供 `copy()` 系列方法的委托实现。

5. **`DelegatingDeleteFile`**：继承 `DelegatingContentFile<DeleteFile>`，实现 `DeleteFile` 接口。

6. **`PendingDeleteFile`**：继承 `DelegatingDeleteFile`，覆写 `dataSequenceNumber()` 返回指定的序列号，用于在提交删除文件时设置数据序列号。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (修改, +12/-162 lines)

**修改目的**：将 `PendingDeleteFile` 改为委托模式，大幅减少代码。

**工作逻辑**：
`PendingDeleteFile` 从直接实现 `DeleteFile` 接口（约 160 行委托方法）改为继承 `Delegates.PendingDeleteFile`（仅保留构造函数），标记为 `@Deprecated`。删除文件的处理逻辑中使用 `Delegates.PendingDeleteFile` 类型检查。

### `core/src/main/java/org/apache/iceberg/V3Metadata.java` (修改, +6/-118 lines)

**修改目的**：将 `DataFileWrapper` 改为委托模式。

**工作逻辑**：
`DataFileWrapper` 从直接实现 `ContentFile<F>` 接口（约 120 行委托方法）改为继承 `Delegates.DelegatingContentFile<F>`。`wrap` 方法改为调用 `setWrapped`。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java` (修改, +12/-6 lines)

**修改目的**：修复 first_row_id 处理逻辑。

**工作逻辑**：
1. 添加数据文件时使用 `Delegates.suppressFirstRowId(file)` 包装，确保 first_row_id 被抑制（设为 null），因为该值应由清单列表写入器在提交时分配。
2. 添加删除文件时使用 `Delegates.pendingDeleteFile` 替代 `PendingDeleteFile`。
3. `appendManifest` 新增校验：不能追加已分配 first_row_id 的清单（`manifest.firstRowId() == null`）。
4. `copyManifest` 移除了传递 `manifest.firstRowId()` 参数，改为传 null（copyAppend 时读取 first_row_id 为 null，因为复制的是提交前的清单）。

### `core/src/main/java/org/apache/iceberg/ManifestFiles.java` (修改, +3/-3 lines)

**修改目的**：`copyAppendManifest` 移除 firstRowId 参数。

### `core/src/main/java/org/apache/iceberg/ManifestReader.java` (修改, +5/-4 lines)

**修改目的**：适配 copyAppendManifest 签名变更。

### `core/src/test/java/org/apache/iceberg/TestRowLineageAssignment.java` (修改, +150/-0 lines)

**修改目的**：新增行 ID 分配测试用例。

### 其他文件

`BaseFile.java`、`FastAppend.java`、`GenericManifestEntry.java`、`TestManifestWriterVersions.java` 小幅适配。

## 总结

本提交表面上添加测试用例，实际上是对行血统元数据处理的重要重构：引入 `Delegates` 委托模式消除大量重复代码（净减少约 296 行），修复 first_row_id 在数据文件添加和清单复制时的处理逻辑（确保由清单列表写入器统一分配），并新增了更多行 ID 分配场景的测试。`PendingDeleteFile` 和 `DataFileWrapper` 均改为继承委托基类，代码量大幅减少。
