# 提交 0699：Flink: port #9464 to v1.17 and v1.19

## 提交信息
- **序号**：0699 / 4088
- **哈希**：1f8cad3c71850645f6656df652df523a5d8108a7
- **短哈希**：1f8cad3c7
- **日期**：2024-04-18
- **作者**：Elkhan Dadash（Co-authored-by: Elkhan Dadashov）
- **提交说明**：Flink: port #9464 to v1.17 and v1.19 (#10177)
- **PR/Issue**：#10177（移植自 #9464）

## 总体目的

本提交将前一个提交（#9464，即 0698 `8136463bd`）中修复的 Flink Iceberg Source split 序列化 Bug 修复，从 `flink/v1.18` 目录移植（backport）到 `flink/v1.17` 和 `flink/v1.19` 两个 Flink 版本目录。

**背景**：Iceberg 项目为每个支持的 Flink 大版本（1.17、1.18、1.19 等）维护独立的源代码目录（如 `flink/v1.17/flink/...`、`flink/v1.19/flink/...`）。这些目录中的 split 序列化相关代码是各自独立维护的副本（而非共享代码），因此一个修复需要分别应用到每个 Flink 版本目录。

**Bug 回顾**：`IcebergSourceSplitSerializer` 使用 Java 的 `writeUTF` 序列化单个 `FileScanTask` 的 JSON 字符串，而 `writeUTF` 的 2 字节长度前缀限制单字符串最大 65535 字节。当 split 包含大量 delete 文件时，单个 task JSON 超过此限制导致序列化失败。

**目标**：使 v1.17 和 v1.19 目录的 Iceberg Source split 序列化也支持超过 64KB 的 task JSON，与 v1.18 的修复保持一致，确保所有受支持的 Flink 版本都不再受此 Bug 影响。

## 如何达成设计目的

移植策略与原修复（#9464）完全一致，仅将相同的代码变更应用到 v1.17 和 v1.19 两个目录的对应文件：

1. 在两个目录的 `IcebergSourceSplit.java` 中引入 `serialize(int version)` / `deserialize(..., version)` 私有方法，新增 `serializeV3` / `deserializeV3`，并通过 `writeTaskJson` / `readTaskJson` 按版本选择写入/读取方式（V2 用 `writeUTF`/`readUTF`，V3 用 `SerializerHelper.writeLongUTF`/`readLongUTF`）。
2. 在两个目录的 `IcebergSourceSplitSerializer.java` 中将 `VERSION` 从 2 升为 3，`serialize` 改调 `serializeV3`，`deserialize` switch 新增 case 3。
3. 在两个目录新增 `SerializerHelper.java`（与 v1.18 完全相同的 206 行实现）。
4. 在两个目录的 `SplitHelpers.java` 中新增 `equipSplitsWithMockDeleteFiles` 测试辅助方法。
5. 在两个目录的 `TestIcebergSourceSplitSerializer.java` 中新增 `testV3WithTooManyDeleteFiles` 测试。

两个目录的修改内容字节级一致（diff 中可见 v1.17 和 v1.19 的对应文件 `index` 哈希完全相同，如 `IcebergSourceSplit.java` 两目录均为 `e4bfbf145`->`44e37afcf`），表明这三个 Flink 版本目录的 split 序列化代码在修复前是同步的。

## 修改详情

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/split/IcebergSourceSplit.java`
### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/split/IcebergSourceSplit.java`
**修改目的**：为 v1.17 和 v1.19 引入 V3 序列化版本，支持超过 64KB 的 task JSON 字符串。

**工作逻辑**：与 0698 中 v1.18 的修改完全相同——抽取 `serialize(int version)` / `deserialize(serialized, caseSensitive, version)` 私有方法，新增 `serializeV3` / `deserializeV3`，通过 `writeTaskJson` / `readTaskJson` 按版本号分发：V2 走 `out.writeUTF` / `in.readUTF`，V3 走 `SerializerHelper.writeLongUTF` / `readLongUTF`。保留 `serializedBytesCache` 缓存。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/split/IcebergSourceSplitSerializer.java`
### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/split/IcebergSourceSplitSerializer.java`
**修改目的**：将 v1.17 和 v1.19 的默认序列化版本升级到 3。

**工作逻辑**：`VERSION` 常量 `2` -> `3`；`serialize()` 改调 `split.serializeV3()`；`deserialize()` switch 新增 `case 3` 分支调用 `IcebergSourceSplit.deserializeV3(serialized, caseSensitive)`，保留 case 1、case 2 兼容旧格式。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/split/SerializerHelper.java`（新增）
### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/split/SerializerHelper.java`（新增）
**修改目的**：为 v1.17 和 v1.19 提供支持超长字符串的 UTF 读写工具。

**工作逻辑**：与 v1.18 版本完全相同的 206 行实现。`writeLongUTF` 用 4 字节 int 长度前缀替代 `writeUTF` 的 2 字节 short 前缀，支持最大约 2GB 的字符串；`readLongUTF` 对应读取并按修改版 UTF-8 解码。注释说明 Flink 1.20 原生支持后可移除此 helper。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/SplitHelpers.java`
### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/SplitHelpers.java`
**修改目的**：为 v1.17 和 v1.19 测试提供构造大量 mock delete 文件 split 的辅助方法。

**工作逻辑**：新增 `equipSplitsWithMockDeleteFiles`，用 Mockito `spy` 包装 `CombinedScanTask`，用 `FileMetadata.deleteFileBuilder` 创建指定数量的 mock `DeleteFile`，通过 `BaseFileScanTask` 重建带 delete 文件的 `FileScanTask`，`doReturn` 替换 spy 的 tasks。与 v1.18 实现一致。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/split/TestIcebergSourceSplitSerializer.java`
### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/split/TestIcebergSourceSplitSerializer.java`
**修改目的**：为 v1.17 和 v1.19 新增 V3 大量 delete 文件场景的往返测试。

**工作逻辑**：新增 `testV3WithTooManyDeleteFiles()` 调用 `serializeAndDeserializeV3(1, 1, 5000)`，构造 1 split、1 数据文件、5000 mock delete 文件，执行 `serializeV3` -> `deserializeV3` 往返并断言一致性。

## 小结
- **成效**：成功将 #9464 的序列化修复移植到 v1.17 和 v1.19 两个 Flink 版本目录，使三个受支持的 Flink 版本（1.17、1.18、1.19）均不再受大量 delete 文件导致序列化失败的问题影响。
- **影响范围**：影响 `flink/v1.17` 和 `flink/v1.19` 模块的 Iceberg Source split 序列化路径。与 0698（v1.18）合计覆盖全部三个受支持 Flink 版本。
- **回迁到 1.4.x 的注意事项**：
  - 此提交本身已是"移植"性质。1.4.x 分支若支持这些 Flink 版本目录，需确认三个目录（v1.17/v1.18/v1.19）的对应文件都已回迁（0698 + 0699 配合）。
  - 序列化版本 V2->V3 升级具有方向性：新代码可读旧格式，旧代码不可读 V3。回迁后避免回滚。
  - 三个目录的修复代码应保持一致以简化维护；若 1.4.x 的目录结构与 main 不同（如某些版本目录不存在），需相应调整。
  - 若 1.4.x 还支持 v1.20 及以上 Flink 版本，需注意 `SerializerHelper` 注释提示的 Flink 1.20 原生长 UTF 支持，可选择性简化。
