# 提交 1994：Core: Use OutputFile.location(), InputFile.location() in Error Messages

## 提交信息

- **序号**：1994 / 4088
- **哈希**：1e36faac9833df661f7d397f8a4c4554e4f121c7
- **短哈希**：1e36faac9
- **日期**：2025-04-14 18:04:21 +0200
- **作者**：Jordano Mark
- **提交说明**：Core: Use OutputFile.location(), InputFile.location() in Error Messages (#12755)
- **PR/Issue**：#12755

## 总体目的

本提交将 Iceberg 核心模块及 ORC、Parquet 模块中错误消息里对 `InputFile`/`OutputFile` 对象的直接字符串拼接，改为显式调用 `.location()` 方法获取文件路径字符串。

此前，在错误消息中直接使用 `file` 或 `outputFile` 对象时，会隐式调用其 `toString()` 方法。不同实现类的 `toString()` 可能返回不一致或不友好的字符串表示（例如返回对象哈希码或非路径格式的字符串），导致错误消息中显示的文件路径信息不准确或难以理解。通过统一使用 `.location()` 方法，确保错误消息始终显示文件的实际位置字符串，便于调试和问题排查。

这是一个涉及 9 个文件的横切式改进，覆盖了 Manifest 读写、表元数据解析、视图元数据解析、Avro/ORC/Parquet 文件读写等关键 I/O 路径。

## 如何达成设计目的

通过在所有错误消息格式化中，将直接传入 `file`/`outputFile` 对象改为传入 `file.location()`/`outputFile.location()`，确保字符串拼接时使用的是文件路径而非对象的 `toString()` 结果。修改集中在各模块的 catch 块和 Preconditions 校验中，不影响正常逻辑路径。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestListWriter.java` (修改, +9/-3 lines)

**修改目的**：修正 Manifest 列表写入器中三处错误消息的文件路径显示。

**工作逻辑**：
在三个内部类（对应不同版本的写入器）的 catch 块中，将 `"Failed to create snapshot list writer for path: %s", file` 改为 `"Failed to create snapshot list writer for path: %s", file.location()`。

### `core/src/main/java/org/apache/iceberg/ManifestReader.java` (修改, +3/-1 lines)

**修改目的**：修正 Manifest 读取器中格式判断失败的错误消息。

**工作逻辑**：
将 `Preconditions.checkArgument(format != null, "Unable to determine format of manifest: %s", file)` 改为使用 `file.location()`。

### `core/src/main/java/org/apache/iceberg/ManifestWriter.java` (修改, +15/-5 lines)

**修改目的**：修正 Manifest 写入器中五处错误消息的文件路径显示。

**工作逻辑**：
在五个 catch 块中统一将 `file` 改为 `file.location()`。

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java` (修改, +4/-2 lines)

**修改目的**：修正表元数据解析器中读写错误消息的文件路径显示。

**工作逻辑**：
在 JSON 写入和读取的 catch 块中，分别将 `outputFile` 改为 `outputFile.location()`，`file` 改为 `file.location()`。

### `core/src/main/java/org/apache/iceberg/avro/AvroIterable.java` (修改, +4/-2 lines)

**修改目的**：修正 Avro 迭代器中读取元数据和打开文件错误消息的文件路径显示。

**工作逻辑**：
在两个 catch 块中将 `file` 改为 `file.location()`。

### `core/src/main/java/org/apache/iceberg/view/ViewMetadataParser.java` (修改, +5/-2 lines)

**修改目的**：修正视图元数据解析器中读写错误消息的文件路径显示。

**工作逻辑**：
在 JSON 读取和写入的 catch 块中，分别将 `file` 和 `outputFile` 改为 `.location()` 调用。

### `orc/src/main/java/org/apache/iceberg/orc/OrcIterable.java` (修改, +2/-1 lines)

**修改目的**：修正 ORC 迭代器中获取行数据错误消息的文件路径显示。

**工作逻辑**：
在 catch 块中将 `file` 改为 `file.location()`。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetIO.java` (修改, +9/-3 lines)

**修改目的**：修正 Parquet I/O 工具类中三处创建输入/输出文件错误消息的路径显示。

**工作逻辑**：
在三个 catch 块中将 `file` 改为 `file.location()`。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetUtil.java` (修改, +2/-1 lines)

**修改目的**：修正 Parquet 工具类中读取文件页脚错误消息的路径显示。

**工作逻辑**：
在 catch 块中将 `file` 改为 `file.location()`。

## 总结

本提交是一个横切式改进，将 9 个文件中错误消息里对 `InputFile`/`OutputFile` 对象的直接引用统一改为 `.location()` 调用，确保错误消息中显示的是文件的实际路径字符串而非对象的不确定 `toString()` 结果，提升了错误诊断的可读性和一致性。
