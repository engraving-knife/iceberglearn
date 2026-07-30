# 提交 1801：Core: Print un-pretty metadata files (#12318)

## 提交信息

- **序号**：1801 / 4088
- **哈希**：465e063bd179cea376a308d0991e1df6f257ae44
- **短哈希**：465e063bd
- **日期**：2025-02-28 11:32:12 +0100
- **作者**：Ian Streeter
- **提交说明**：Core: Print un-pretty metadata files (#12318)
- **PR/Issue**：#12318

## 总体目的

Iceberg 在写入表元数据（TableMetadata）和视图元数据（ViewMetadata）到 JSON 文件时，此前调用了 `generator.useDefaultPrettyPrinter()`，即以“美化打印（pretty print）”格式输出 JSON。美化打印会在键值之间添加换行和缩进，使文件便于人类阅读，但会显著增加文件体积。

对于大型表，元数据文件可能包含大量快照、文件清单等信息，美化打印带来的额外空格和换行会导致文件体积膨胀，增加存储成本和读取时的解析开销（尤其当元数据文件被 Gzip 压缩时，冗余空白会降低压缩效率或增加解压后体积）。本提交移除美化打印，改为输出紧凑（un-pretty）的单行 JSON，以减小元数据文件体积、提升读写效率。

## 如何达成设计目的

通过在 `TableMetadataParser` 和 `ViewMetadataParser` 的 `write` 方法中删除 `generator.useDefaultPrettyPrinter()` 调用，使 JSON 输出变为紧凑格式。两个文件的修改方式完全一致，各删除一行。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java`（修改, -1 lines）

**修改目的**：让表元数据 JSON 文件以紧凑格式输出。

**工作逻辑**：在 `write(TableMetadata metadata, OutputStream stream)` 方法中，创建 `JsonGenerator` 后原本调用 `generator.useDefaultPrettyPrinter()` 来启用美化打印。删除该行后，generator 默认输出紧凑 JSON（无多余空白和换行），后续 `toJson(metadata, generator)` 写出的内容即为单行紧凑格式。

### `core/src/main/java/org/apache/iceberg/view/ViewMetadataParser.java`（修改, -1 lines）

**修改目的**：让视图元数据 JSON 文件以紧凑格式输出。

**工作逻辑**：与表元数据相同的处理方式。在 `write` 方法中删除 `generator.useDefaultPrettyPrinter()` 调用，使视图元数据以紧凑 JSON 格式输出。

## 小结

- **成效**：表和视图的元数据 JSON 文件改为紧凑格式输出，减小文件体积，降低存储和解析开销。
- **影响范围**：涉及 `core` 模块的 `TableMetadataParser` 和 `ViewMetadataParser`，影响所有新写入的元数据文件格式。读取侧不受影响（JSON 解析器对紧凑和美化格式均可解析）。向后兼容：已有的美化格式元数据文件仍可正常读取。
- **回迁到 1.4.x 的注意事项**：建议回迁。改动简单且向后兼容，能减小 1.4.x 上元数据文件体积。无前置依赖。需注意：回迁后新写入的元数据文件变为紧凑格式，若 1.4.x 上有工具或脚本依赖美化格式的人工可读性（如直接 `cat` 查看），需调整为使用 JSON 格式化工具查看。通常元数据文件本就以工具读取为主，影响可忽略。
