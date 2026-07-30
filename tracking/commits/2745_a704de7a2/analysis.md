# 提交 2745：Flink: Backport move write from AppenderFactory to FileWriterFactory

## 提交信息

- **序号**：2745 / 4088
- **哈希**：a704de7a2b1c00cb125537436f0049fae73ac4b6
- **短哈希**：a704de7a2
- **日期**：2025-10-14 08:23:11 -0700
- **作者**：pvary
- **提交说明**：Flink: Backport move write from AppenderFactory to FileWriterFactory
- **PR/Issue**：#14325（backport #14271）

## 总体目的

这是提交 2743（PR #14271）的 backport 提交。原始 PR #14271 将 Flink sink 的写入路径从旧的 `AppenderFactory` 模式迁移到新的 `FileWriterFactory` 模式，但原始提交只修改了 `flink/v2.0` 和 `flink/v2.1` 版本（注：核心 IO 层的修改在原始 PR 中已包含，本 backport 不涉及核心层修改）。

本提交将该迁移 backport 到 `flink/v1.20` 和 `flink/v2.0` 两个 Flink 版本模块，确保所有受支持的 Flink 版本都采用统一的 `FileWriterFactory` 写入路径。注意 `flink/v2.0` 在原始 PR 中已被修改，此 backport 中 v2.0 的修改是补充性的（因为 v2.0 可能需要额外的调整）。

## 如何达成设计目的

将 PR #14271 的 Flink 层修改分别应用到 `flink/v1.20` 和 `flink/v2.0` 模块，修改内容与原始提交完全一致：
1. 将 `BaseDeltaTaskWriter`、`PartitionedDeltaWriter`、`UnpartitionedDeltaWriter` 的参数类型从 `FileAppenderFactory` 改为 `FileWriterFactory`
2. 标记 `FlinkAppenderFactory` 为 `@Deprecated`
3. 公开 `FlinkFileWriterFactory` 及其 Builder 的 API，新增 `writerProperties` 支持
4. 将 `RowDataTaskWriterFactory` 从使用 `FlinkAppenderFactory` 改为使用 `FlinkFileWriterFactory`
5. 适配测试工具类和测试用例

## 修改详情

### Flink v1.20 模块（12 个文件）和 Flink v2.0 模块（12 个文件）

每个模块的修改与原始提交 2743 中的 Flink 层修改完全一致：

#### `BaseDeltaTaskWriter.java` (+3/-3 lines)
**修改目的**：将构造参数从 `FileAppenderFactory` 改为 `FileWriterFactory`。

#### `FlinkAppenderFactory.java` (+5/-0 lines)
**修改目的**：标记为 `@Deprecated`，注明 1.11.0 起废弃，1.12.0 移除。

#### `FlinkFileWriterFactory.java` (+27/-18 lines)
**修改目的**：公开 API 并支持 writerProperties。
**工作逻辑**：类和 Builder 改为 public，构造函数改为 private，新增 `writerProperties` 字段和 Builder 方法。

#### `PartitionedDeltaWriter.java` (+3/-3 lines)
**修改目的**：将参数类型从 `FileAppenderFactory` 改为 `FileWriterFactory`。

#### `RowDataTaskWriterFactory.java` (+38/-28 lines)
**修改目的**：从 FlinkAppenderFactory 迁移到 FlinkFileWriterFactory。
**工作逻辑**：三种场景下将 `new FlinkAppenderFactory(...)` 替换为 `new FlinkFileWriterFactory.Builder(table)...build()`。

#### `UnpartitionedDeltaWriter.java` (+3/-3 lines)
**修改目的**：将参数类型从 `FileAppenderFactory` 改为 `FileWriterFactory`。

#### `SimpleDataUtil.java` (+69/-22 lines)
**修改目的**：测试工具类适配 FileWriterFactory。

#### 测试文件（TestCompressionSettings、TestFlinkManifest、TestIcebergCommitter、TestIcebergFilesCommitter、TestDynamicWriter）
**修改目的**：适配新的 FileWriterFactory 接口。

## 总结

本提交是 PR #14271（提交 2743）的 backport，将 Flink sink 写入路径从 `AppenderFactory` 迁移到 `FileWriterFactory` 的修改应用到 Flink v1.20 和 v2.0 版本。共修改 24 个文件（2 个模块 x 12 文件），修改内容与原始提交的 Flink 层修改完全一致。注意核心 IO 层（BaseTaskWriter 等）和 data 层（BaseFileWriterFactory）的修改已在原始 PR 中完成，本 backport 仅涉及 Flink 层。
