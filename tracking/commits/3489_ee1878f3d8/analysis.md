# 提交 3489：API, Core: Introduce foundational types for V4 manifest support (#15049)

## 提交信息

- **序号**：3489 / 4088
- **哈希**：ee1878f3d87c0610e6046bcccf297e459cdab650
- **短哈希**：ee1878f3d8
- **日期**：2026-03-31 17:04:19 -0700
- **作者**：Anoop Johnson
- **提交说明**：API, Core: Introduce foundational types for V4 manifest support (#15049)
- **PR/Issue**：#15049

## 总体目的

引入 V4 manifest 支持的基础类型。这些类型遵循 Iceberg Single-File Commit 规范（https://s.apache.org/iceberg-single-file-commit），将为后续 PR 中的 manifest 读写功能奠定基础。V4 manifest 的核心变化包括：
- 支持 deletion vector（DV）
- 支持嵌套 manifest（data manifest 和 delete manifest 作为内容类型）
- 新增 `REPLACED` 条目状态
- 引入行 ID（first_row_id）和位置位图（deleted/replaced positions）

目前这些类型作为 core 模块中的包级私有接口添加，未来会移至 api 模块。

## 如何达成设计目的

1. 扩展 `FileContent` 枚举，新增 `DATA_MANIFEST(3)` 和 `DELETE_MANIFEST(4)`。
2. 新增 `DeletionVector` 接口：描述 DV blob 的位置、偏移、大小和基数。
3. 新增 `EntryStatus` 枚举：新增 `REPLACED(3)` 状态。
4. 新增 `ManifestInfo` 接口：manifest 摘要信息（文件/行计数、DV 等）。
5. 新增 `Tracking` 接口：V4 manifest 条目的跟踪信息（状态、snapshot ID、序列号、行 ID、位置位图）。
6. 新增 `TrackedFile` 接口：带可选 DV 的内容文件，由 V4 manifest 跟踪。
7. 在 V2Metadata 和 V3Metadata 中添加内容类型校验，确保 V2/V3 不接受新的 V4 内容类型。
8. 添加 `TestTrackedFile` 测试验证 schema 字段顺序和 ID。

## 修改详情

### `api/src/main/java/org/apache/iceberg/FileContent.java` (+4/-2 lines)

**修改目的**：扩展 FileContent 枚举支持 V4 内容类型。

**工作逻辑**：新增 `DATA_MANIFEST(3)` 和 `DELETE_MANIFEST(4)` 枚举值，用于 V4 manifest 中嵌套的 manifest 文件类型。更新 javadoc 为通用描述。

### `core/src/main/java/org/apache/iceberg/DeletionVector.java` (+64 lines, 新文件)

**修改目的**：定义 deletion vector 元数据接口。

**工作逻辑**：
- 定义 DV 的 schema 字段：LOCATION(155)、OFFSET(144)、SIZE_IN_BYTES(145)、CARDINALITY(156)。
- 提供 `schema()` 静态方法返回 StructType。
- 接口方法：`location()`、`offset()`、`sizeInBytes()`、`cardinality()`。
- DV blob 遵循 Puffin spec 中的 deletion-vector-v1 blob 类型格式。

### `core/src/main/java/org/apache/iceberg/EntryStatus.java` (+38 lines, 新文件)

**修改目的**：定义 V4 manifest 条目状态枚举。

**工作逻辑**：新增 `REPLACED(3)` 状态，表示条目因列更新或 DV 变更被替换。原有 EXISTING(0)、ADDED(1)、DELETED(2)。

### `core/src/main/java/org/apache/iceberg/ManifestInfo.java` (+113 lines, 新文件)

**修改目的**：定义 V4 root manifest 条目引用的 manifest 摘要信息。

**工作逻辑**：
- Schema 字段包括：added/existing/deleted/replaced files count、对应 rows count、min_sequence_number、DV、DV_CARDINALITY。
- 新增 `replaced_files_count`(520) 和 `replaced_rows_count`(521) 字段对应 REPLACED 状态。
- 接口方法返回各类计数和 DV 信息。

### `core/src/main/java/org/apache/iceberg/TrackedFile.java` (+173 lines, 新文件)

**修改目的**：定义 V4 manifest 跟踪的内容文件接口。

**工作逻辑**：
- 包含 TRACKING(147)、CONTENT_TYPE(134)、LOCATION(100)、FILE_FORMAT(101)、RECORD_COUNT(103)、FILE_SIZE_IN_BYTES(104)、SPEC_ID(141)、CONTENT_STATS(146)、SORT_ORDER_ID(140)、DELETION_VECTOR(148)、MANIFEST_INFO(150)、KEY_METADATA(131)、SPLIT_OFFSETS(132)、EQUALITY_IDS(135) 字段。
- `schemaWithContentStats(Types.StructType)` 工厂方法构建完整 schema，content_stats 类型可参数化。
- 接口方法包括 tracking、contentType、location、fileFormat、recordCount、fileSizeInBytes、specId、contentStats、sortOrderId、deletionVector、manifestInfo、keyMetadata、splitOffsets、equalityIds、copy、copyWithStats、copyWithoutStats、manifestLocation、manifestPos。

### `core/src/main/java/org/apache/iceberg/Tracking.java` (+109 lines, 新文件)

**修改目的**：定义 V4 manifest 条目的跟踪信息接口。

**工作逻辑**：
- Schema 字段：STATUS(0)、SNAPSHOT_ID(1)、SEQUENCE_NUMBER(3)、FILE_SEQUENCE_NUMBER(4)、DV_SNAPSHOT_ID(5)、FIRST_ROW_ID(142)、DELETED_POSITIONS(6)、REPLACED_POSITIONS(7)。
- `isLive()` 默认方法：status 为 ADDED 或 EXISTING 时返回 true。
- 接口方法返回状态、序列号、snapshot ID、DV snapshot ID、first_row_id、deleted/replaced positions 位图。

### `core/src/main/java/org/apache/iceberg/V2Metadata.java` (+18 lines)

**修改目的**：为 V2 metadata 添加内容类型校验，防止 V4 类型。

**工作逻辑**：
- 在读取 content 字段（case 3 和 case 0）时调用 `checkContentType`。
- `checkContentType(ManifestContent)` 确保仅为 DATA 或 DELETES。
- `checkContentType(FileContent)` 确保仅为 DATA、POSITION_DELETES 或 EQUALITY_DELETES。

### `core/src/main/java/org/apache/iceberg/V3Metadata.java` (+18 lines)

**修改目的**：同 V2Metadata，为 V3 添加内容类型校验。

**工作逻辑**：与 V2Metadata 完全相同的校验逻辑。

### `core/src/test/java/org/apache/iceberg/TestTrackedFile.java` (+103 lines, 新文件)

**修改目的**：测试 TrackedFile schema 的字段顺序、ID 和 content_stats 类型参数化。

**工作逻辑**：
- `schemaWithContentStatsFieldOrder`：验证 14 个字段名称顺序。
- `schemaWithContentStatsFieldIds`：验证字段 ID 列表 (147, 134, 100, 101, 103, 104, 141, 146, 140, 148, 150, 131, 132, 135)。
- `schemaWithContentStatsUsesProvidedType`：验证 content_stats 使用传入的类型。
- `schemaWithContentStatsReflectsInput`：验证不同 schema 大小的 content_stats 字段数正确。

## 总结

这是 V4 manifest 支持的基础设施提交，引入了 5 个新的核心接口（DeletionVector、EntryStatus、ManifestInfo、Tracking、TrackedFile）和 FileContent 枚举扩展。这些类型遵循 Single-File Commit 规范，支持 deletion vector、嵌套 manifest、REPLACED 状态等 V4 特性。同时在 V2/V3 metadata 中添加了内容类型校验以防混用。这些类型目前为包级私有，后续 PR 将基于它们实现 manifest 读写。
