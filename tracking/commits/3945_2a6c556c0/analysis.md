# 提交 3945：Core: Rename TrackedFile writer_format_version to format_version (#16952)

## 提交信息

- **序号**：3945 / 4088
- **哈希**：2a6c556c0c883c04c6d3fbd68e6aeda36b91e0aa
- **短哈希**：2a6c556c0
- **日期**：2026-06-24 16:26:58 -0700
- **作者**：gaborkaszab
- **提交说明**：Core: Rename TrackedFile writer_format_version to format_version (#16952)
- **PR/Issue**：#16952

## 总体目的

这次提交将 `TrackedFile` 接口中的 `writer_format_version` 字段重命名为 `format_version`，对应的 Java 方法 `writerFormatVersion()` 重命名为 `formatVersion()`。这个重命名使字段名更准确地反映其语义。

原名称 `writer_format_version` 暗示这是"写入器（writer）的格式版本"，但该字段实际存储的是文件本身的格式版本（如 Parquet v1/v2、ORC v1/v2 等），与写入器实现无关。新名称 `format_version` 更直接地表达了"文件的格式版本"这一含义，减少了概念混淆。

字段 ID（157）保持不变，这是 Iceberg schema 演进的重要约定——重命名字段只改变名称不改变 ID，确保元数据的向后兼容性。字段描述也从 "Writer format version" 改为 "Format version of this file"。

## 如何达成设计目的

通过全局重命名：修改 `TrackedFile` 接口中的字段常量名、方法名，以及所有引用这些名称的实现类（`TrackedFileStruct`、`TrackedFileBuilder`）和测试文件。由于这是 API 级别的重命名，所有调用方都需要同步更新。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TrackedFile.java` (+4/-4 lines)

**修改目的**：重命名接口字段和方法。

**工作逻辑**：
- 将 `WRITER_FORMAT_VERSION` 常量重命名为 `FORMAT_VERSION`，Avro 字段名从 `"writer_format_version"` 改为 `"format_version"`，描述从 "Writer format version" 改为 "Format version of this file"。字段 ID 157 不变。
- 将方法 `writerFormatVersion()` 重命名为 `formatVersion()`，文档从 "Returns the version of the writer that wrote this file" 改为 "Returns the format version of this file."

### `core/src/main/java/org/apache/iceberg/TrackedFileBuilder.java` (+8/-11 lines)

**修改目的**：同步更新 builder 中的字段和方法名。

**工作逻辑**：
- 字段 `writerFormatVersion` 重命名为 `formatVersion`。
- setter 方法 `writerFormatVersion(int)` 重命名为 `formatVersion(int)`，校验消息从 "Invalid writer format version" 改为 "Invalid format version"。
- `build()` 中校验消息从 "Missing required field: writer format version" 改为 "Missing required field: format version"。
- `from()` 和 `terminal()` 中的 `source.writerFormatVersion()` 调用改为 `source.formatVersion()`。

### `core/src/main/java/org/apache/iceberg/TrackedFileStruct.java` (+6/-135 lines, 净减)

**修改目的**：同步更新 struct 实现中的字段名。

**工作逻辑**：将 `writerFormatVersion` 字段和相关方法重命名为 `formatVersion`。该文件的大量删减可能同时包含了其他清理工作。

### 测试文件（TestTrackedFile.java、TestTrackedFileAdapters.java、TestTrackedFileBuilder.java、TestTrackedFileStruct.java）

**修改目的**：同步更新测试中对方法的调用。

**工作逻辑**：将测试中所有 `writerFormatVersion()` 调用改为 `formatVersion()`，builder 的 `.writerFormatVersion(...)` 调用改为 `.formatVersion(...)`。

## 总结

这次提交将 TrackedFile 的 `writer_format_version` 重命名为 `format_version`，使字段名更准确地反映其语义（文件的格式版本而非写入器版本）。字段 ID 保持不变确保向后兼容。这是 TrackedFile 抽象（在 #16769 中引入）的命名完善工作。
