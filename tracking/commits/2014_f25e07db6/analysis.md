# 提交 2014：Core: Support first-row-id for manifests and manifest lists (#12672)

## 提交信息

- **序号**：2014 / 4088
- **哈希**：f25e07db6318184272d5c98dede4d268ee5288ab
- **短哈希**：f25e07db6
- **日期**：2025-04-18 10:50:18 -0700
- **作者**：Ryan Blue
- **提交说明**：Core: Support first-row-id for manifests and manifest lists (#12672)
- **PR/Issue**：#12672

## 总体目的

这个提交为 Iceberg V3 格式引入了行级血统（row lineage）的核心基础设施：在清单（manifest）和清单列表（manifest list）中支持 `first_row_id` 字段，用于为每个数据文件中的行分配唯一的行 ID。这是 Iceberg 行级血统特性的基础组件之一。

行级血统的目标是为表中的每一行分配一个全局唯一的、单调递增的行 ID（`_row_id`），使得可以在跨快照的演进过程中追踪每一行数据的来源和变更历史。为此，需要在提交时为每个新写入的数据文件分配一个"起始行 ID"（first-row-id），该文件中的行从该 ID 开始依次编号。

本提交实现了 first-row-id 在三个层面的传递和分配：
1. **数据文件层面**：`DataFile` 新增 `first_row_id` 字段（field ID 142），记录该文件中第一行对应的行 ID。
2. **清单层面**：`ManifestFile` 新增 `first_row_id` 字段（field ID 520），记录该清单中 ADDED 数据文件的起始行 ID。`ManifestReader` 在读取时根据清单的 first-row-id 为其中尚未分配行 ID 的数据文件分配连续的行 ID。
3. **清单列表层面**：`ManifestListWriter` 的 V3Writer 在写入清单列表时，为每个数据清单分配 first-row-id，并跟踪下一个可分配的行 ID（nextRowId），最终传递给快照。

此外，`SnapshotProducer` 中移除了之前的 `calculateAddedRows` 方法，改为直接从 `ManifestListWriter` 获取已分配的行数（`assignedRows`），并新增了对 REPLACE 操作的验证逻辑（added records 不应超过 replaced records）。

## 如何达成设计目的

整体设计思路是通过分层传递 first-row-id 来实现行 ID 的自动分配：

1. **表元数据层**：表维护一个 `nextRowId`（下一个可分配的行 ID），作为行 ID 分配的起点。
2. **清单列表写入层**（`ManifestListWriter.V3Writer`）：接收表的 `firstRowId`（即 `base.nextRowId()`），在写入每个数据清单时为其分配 first-row-id，并按该清单的 existing + added 行数推进 nextRowId。这样为旧版本（pre-v3）清单中未分配行 ID 的数据文件预留了空间。
3. **清单写入层**（`ManifestWriter.V3Writer`）：接收 first-row-id 并写入清单文件的元数据中。
4. **清单读取层**（`ManifestReader`）：读取清单时，如果清单有 first-row-id，则为其中未分配行 ID 的非 DELETED 数据文件依次分配连续行 ID（通过 `idAssigner` 函数）。
5. **V3Metadata 包装层**：`ManifestFileWrapper` 和 `ManifestEntryWrapper` 处理 V3 schema 中新增字段的读写，确保 first-row-id 在 Avro 序列化时正确处理。

关键组件协作关系：`SnapshotProducer` → `ManifestLists.write()` → `ManifestListWriter.V3Writer`（分配 first-row-id） → `ManifestWriter.V3Writer`（写入 first-row-id 到清单） → `ManifestReader`（读取时分配行 ID 到数据文件） → `V3Metadata` 包装器（Avro schema 适配）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/ContentFile.java` (修改, +7/-0 lines)

**修改目的**：在 `ContentFile` 接口中新增 `firstRowId()` 默认方法。

**工作逻辑**：
新增 `default Long firstRowId()` 方法，返回 null。该方法返回该数据文件中第一行对应的行 ID（用于 `_row_id` 为 null 的新行）。

### `api/src/main/java/org/apache/iceberg/DataFile.java` (修改, +3/-0 lines)

**修改目的**：在 `DataFile` schema 中新增 `FIRST_ROW_ID` 字段定义。

**工作逻辑**：
新增 `Types.NestedField FIRST_ROW_ID = optional(142, "first_row_id", LongType.get(), "Starting row ID to assign to new rows")`，并将其加入 `PARTITION_COLUMNS` 等字段列表中，field ID 为 142。

### `api/src/main/java/org/apache/iceberg/ManifestFile.java` (修改, +16/-1 lines)

**修改目的**：在 `ManifestFile` schema 中新增 `FIRST_ROW_ID` 字段定义和接口方法。

**工作逻辑**：
新增 `Types.NestedField FIRST_ROW_ID = optional(520, "first_row_id", LongType.get(), ...)`，field ID 为 520，将其加入 `SCHEMA`，并新增 `default Long firstRowId()` 方法返回该清单中 ADDED 数据文件的起始行 ID。

### `core/src/main/java/org/apache/iceberg/BaseFile.java` (修改, +32/-5 lines)

**修改目的**：在 `BaseFile` 中支持 `firstRowId` 字段的存储、读取和设置。

**工作逻辑**：
1. 新增 `private Long firstRowId` 字段。
2. 在构造函数和拷贝构造中增加 `firstRowId` 参数的处理。
3. 新增 `firstRowId()` getter 和 `setFirstRowId(long)` setter 方法。
4. 在 `set(int pos, Object value)` 和 `get(int pos)` 方法中，由于新增了 firstRowId 字段，将 case 17-20 的字段位置全部后移一位（referencedDataFile 从 17 变为 18，contentOffset 从 18 变为 19，contentSizeInBytes 从 19 变为 20，fileOrdinal 从 20 变为 21），新增 case 17 对应 firstRowId。
5. 在 `toString()` 中新增 firstRowId 的输出。

### `core/src/main/java/org/apache/iceberg/GenericManifestFile.java` (修改, +29/-5 lines)

**修改目的**：在 `GenericManifestFile` 中支持 `firstRowId` 字段。

**工作逻辑**：
1. 新增 `private Long firstRowId` 字段。
2. 在主构造函数中新增 `firstRowId` 参数。
3. 新增 `firstRowId()` 方法。
4. 在 `get(int basePos)` 和 `set(int basePos, Object value)` 中新增 case 15 对应 firstRowId。
5. 在拷贝构造和 `copy()` 方法中处理 firstRowId。
6. 将旧构造函数标记为 `@Deprecated`（将在 1.10.0 移除）。
7. 调整 `copy()` 中构造参数顺序以匹配新构造函数。

### `core/src/main/java/org/apache/iceberg/ManifestReader.java` (修改, +47/-3 lines)

**修改目的**：在读取清单时根据 first-row-id 为数据文件分配行 ID。

**工作逻辑**：
1. 新增 `private final Long firstRowId` 字段和对应的构造函数重载。
2. 构造函数中校验 `firstRowId` 仅对 DATA_FILES 类型有效（delete manifest 不使用行 ID）。
3. 在 `entries()` 方法中，新增对 `idAssigner(firstRowId)` 的转换：如果 firstRowId 非 null，则创建一个 `Function`，维护 `nextRowId` 计数器，遍历每个非 DELETED 的 `BaseFile`，如果其 `firstRowId()` 为 null 则分配当前 `nextRowId` 并按 `recordCount()` 递增。
4. 在读取投影中确保 `RECORD_COUNT` 字段被包含（行 ID 分配需要记录数）。

### `core/src/main/java/org/apache/iceberg/ManifestWriter.java` (修改, +22/-10 lines)

**修改目的**：在写入清单时传递 first-row-id。

**工作逻辑**：
1. `ManifestWriter` 基类新增 `firstRowId` 字段和构造参数。
2. `V3Writer` 构造函数新增 `firstRowId` 参数并传递给父类。
3. `V2Writer`、`V1Writer`、`V3DeleteWriter`、`V2DeleteWriter` 均传递 `null`（这些版本不使用行 ID）。
4. `toManifestFile()` 中将 `firstRowId` 传递给 `GenericManifestFile` 构造函数。

### `core/src/main/java/org/apache/iceberg/ManifestListWriter.java` (修改, +32/-3 lines)

**修改目的**：在写入清单列表时分配 first-row-id。

**工作逻辑**：
1. 基类新增 `nextRowId()` 方法返回 null。
2. `V3Writer` 新增 `firstRowId` 构造参数和 `nextRowId` 字段，在 metadata 中记录 `first-row-id`。
3. `prepare(ManifestFile)` 方法中：如果是 delete manifest 或已有 firstRowId，则直接包装（不分配）；否则分配当前 `nextRowId`，并按 `existingRowsCount + addedRowsCount` 推进 `nextRowId`（为旧版本数据文件预留空间）。
4. `V3Writer` 覆盖 `nextRowId()` 返回最新的 `nextRowId`，供 `SnapshotProducer` 获取已分配行数。

### `core/src/main/java/org/apache/iceberg/ManifestLists.java` (修改, +5/-2 lines)

**修改目的**：在 `ManifestLists.write()` 方法中传递 `firstRowId` 参数。

**工作逻辑**：
`write()` 方法新增 `Long firstRowId` 参数，传递给 `V3Writer` 构造函数。V1/V2 writer 不受影响。

### `core/src/main/java/org/apache/iceberg/ManifestFiles.java` (修改, +42/-8 lines)

**修改目的**：在清单读写和复制操作中传递 first-row-id。

**工作逻辑**：
1. `read()` 方法将 `manifest.firstRowId()` 传递给 `ManifestReader` 构造函数。
2. 新增私有 `newWriter()` 方法支持 `firstRowId` 参数，仅在 V3 时传递。
3. `copyAppendManifest()` 和 `copyRewriteManifest()` 新增 `firstRowId` 参数，在复制时传递给 reader 和 writer。注意 copyAppend 读取时传 null（因为复制的是提交前的清单），copyRewrite 读取时传 firstRowId。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (修改, +59/-32 lines)

**修改目的**：在快照提交时使用 first-row-id 并获取已分配行数。

**工作逻辑**：
1. `ManifestLists.write()` 调用新增 `base.nextRowId()` 参数。
2. 移除了 `calculateAddedRows` 方法，改为从 `writer.nextRowId() - base.nextRowId()` 计算已分配行数（`assignedRows`）。
3. 新增对 REPLACE 操作的验证：added records 不应超过 replaced records（因为部分记录可能已被 delete file 删除）。
4. `BaseSnapshot` 构造参数从 `firstRowId, addedRows` 改为 `nextRowId, assignedRows`。

### `core/src/main/java/org/apache/iceberg/V3Metadata.java` (修改, +44/-5 lines)

**修改目的**：在 V3 schema 中支持 first-row-id 字段的 Avro 序列化。

**工作逻辑**：
1. `MANIFEST_FILE_SCHEMA` 新增 `ManifestFile.FIRST_ROW_ID` 字段。
2. `ManifestFileWrapper` 新增 `wrappedFirstRowId` 字段，`wrap()` 方法新增 `firstRowId` 参数。在 `get(int pos)` case 15 中：如果 `wrappedFirstRowId` 非 null（新分配的），校验 content 为 DATA 且原 firstRowId 为 null，返回 wrappedFirstRowId；否则返回原 firstRowId（校验非 null）。
3. `ManifestFileWrapper` 新增 `firstRowId()` 方法委托给 wrapped。
4. `MANIFEST_ENTRY_SCHEMA` 新增 `DataFile.FIRST_ROW_ID`。
5. `ManifestEntryWrapper` 的 `get(int pos)` 中新增 case 16 返回 firstRowId（仅对 DATA content），后续 case 后移。新增 `firstRowId()` 方法。

### `core/src/main/java/org/apache/iceberg/DataFiles.java` (修改, +11/-1 lines)

**修改目的**：在 `DataFiles.Builder` 中支持 firstRowId。

**工作逻辑**：
Builder 新增 `firstRowId` 字段、`withFirstRowId(Long)` 方法，在 `copy()` 和 `build()` 中处理该字段。

### `core/src/main/java/org/apache/iceberg/GenericDataFile.java` (修改, +4/-1 lines)

**修改目的**：在 `GenericDataFile` 构造函数中支持 firstRowId。

### `core/src/main/java/org/apache/iceberg/GenericDeleteFile.java` (修改, +1/-0 lines)

**修改目的**：delete 文件不使用 first-row-id，传递 null。

### `core/src/test/java/org/apache/iceberg/TestRowLineageAssignment.java` (新增, +705/-0 lines)

**修改目的**：新增行 ID 分配的完整测试。

**工作逻辑**：
测试覆盖了多种场景下的行 ID 分配：简单追加、覆盖（overwrite）、删除后追加、manifest 复制、多个 manifest 的行 ID 分配顺序等。验证 first-row-id 在 manifest 和 manifest list 层面的正确传递，以及 `ManifestReader` 在读取时为数据文件分配连续行 ID的行为。

### 其他测试文件修改

多个测试文件（`TestManifestListVersions`、`TestManifestWriterVersions`、`TestManifestEncryption`、`TestRowLineageMetadata`、`TestTables` 等）同步更新以适配 first-row-id 字段的新增和构造函数参数变化。Spark 的 `TestMetadataTables` 和 `TestHelpers` 也做了相应适配。

## 总结

本提交是 Iceberg V3 行级血统特性的核心基础设施，在数据文件、清单和清单列表三个层面引入了 `first_row_id` 字段，实现了行 ID 在提交时的自动分配和传递。关键机制是 `ManifestListWriter.V3Writer` 在写入清单列表时为每个数据清单分配起始行 ID，`ManifestReader` 在读取时为数据文件分配连续行 ID。该实现还考虑了与旧版本的兼容性（为 pre-v3 数据文件预留行 ID 空间），并新增了 REPLACE 操作的验证逻辑。
