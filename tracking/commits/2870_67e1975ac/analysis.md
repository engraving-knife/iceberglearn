# 提交 2870：Flink: Fix writeDataFiles with hardcoded formatVersion (#14570)

## 提交信息

- **序号**：2870 / 4088
- **哈希**：67e1975ac65e2690fc9599d0f5a91c2f0563e337
- **短哈希**：67e1975ac
- **日期**：2025-11-12 14:10:58 +0100
- **作者**：GuoYu
- **提交说明**：Flink: Fix writeDataFiles with hardcoded formatVersion (#14570)
- **PR/Issue**：#14570

## 总体目的

在 Flink 的 Iceberg sink 中，`FlinkManifestUtil.writeDataFiles` 方法在写入数据清单（manifest）文件时，硬编码使用了格式版本 2（`FORMAT_V2 = 2`）。这意味着无论实际的 Iceberg 表使用的是哪个格式版本（v1 或 v2），写入的数据清单都会被强制标记为 v2 格式。

这会导致一个严重的问题：当表本身是 v1 格式时，写入 v2 格式的清单文件会造成版本不一致，可能导致读取该表的引擎在解析清单时出现兼容性问题或错误。正确的做法是使用表实际的格式版本来写入清单文件，确保清单文件与表定义保持一致。

此修改跨三个 Flink 版本（v1.20、v2.0、v2.1）同步修复了这一问题。

## 如何达成设计目的

核心思路是将硬编码的 `FORMAT_V2` 常量替换为从表元数据中动态获取的 `formatVersion` 参数。具体步骤：

1. 删除 `FlinkManifestUtil` 类中硬编码的 `FORMAT_V2 = 2` 常量。
2. 修改 `writeDataFiles` 方法签名，增加 `int formatVersion` 参数，将其传递给 `ManifestFiles.write()`。
3. 在调用 `writeDataFiles` 的地方（`writeDataFiles` 内部的提交逻辑），从已有的 `formatVersion` 变量中传入该值。
4. 在测试代码中，使用 `TableUtil.formatVersion(table)` 获取表的实际格式版本并传入。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkManifestUtil.java` (+8/-4 lines)

**修改目的**：移除硬编码的格式版本，改用表实际格式版本。

**工作逻辑**：删除了 `FORMAT_V2 = 2` 常量，`writeDataFiles` 方法新增 `formatVersion` 参数，直接传给 `ManifestFiles.write(formatVersion, spec, outputFile, DUMMY_SNAPSHOT_ID)`。在内部调用处（`dataManifest = writeDataFiles(...)`），将方法作用域内已有的 `formatVersion` 变量传入。该变量在 `writeDataFiles(OutputFile, PartitionSpec, List<DataFile>, int)` 的调用者中已经存在。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkManifest.java` (+2/-1 lines)

**修改目的**：更新测试以适配新方法签名。

**工作逻辑**：测试中调用 `FlinkManifestUtil.writeDataFiles` 时，增加 `TableUtil.formatVersion(table)` 参数，确保测试使用表的实际格式版本。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkManifestUtil.java` (+8/-4 lines)

与 v1.20 版本完全相同的修改。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkManifest.java` (+2/-1 lines)

与 v1.20 版本完全相同的修改。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkManifestUtil.java` (+8/-4 lines)

与 v1.20 版本完全相同的修改。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkManifest.java` (+2/-1 lines)

与 v1.20 版本完全相同的修改。

## 总结

该提交修复了 Flink sink 中数据清单文件格式版本硬编码为 v2 的 bug，确保清单文件的格式版本与表实际定义的格式版本一致。这避免了 v1 表错误地写入 v2 清单文件导致的潜在兼容性问题。修复同时应用于 Flink 1.20、2.0 和 2.1 三个版本分支，保证了不同 Flink 版本下的一致行为。
