# 提交 0698：Flink: Don't fail to serialize IcebergSourceSplit when there is too many delete files

## 提交信息
- **序号**：0698 / 4088
- **哈希**：8136463bd92dabe4c9b677c0d09a30664d584166
- **短哈希**：8136463bd
- **日期**：2024-04-18
- **作者**：Ahmet DAL
- **提交说明**：Flink: Don't fail to serialize IcebergSourceSplit when there is too many delete files (#9464)
- **PR/Issue**：#9464

## 总体目的

本提交修复 Flink Iceberg Source 在序列化 `IcebergSourceSplit`（Iceberg 源 split）时，当单个 split 包含大量 delete 文件（删除文件）导致序列化失败的 Bug。

**背景与 Bug 成因**：

Flink 的 Source API 依赖 `SimpleVersionedSerializer` 在 JobManager 与 TaskManager 之间、以及 checkpoint 持久化时序列化/反序列化 split。Iceberg 的 `IcebergSourceSplitSerializer` 负责将 `IcebergSourceSplit` 转为字节数组。其序列化流程大致为：
1. 写入 `fileOffset`（int）、`recordOffset`（long）、`taskCount`（int）
2. 对 split 内的每个 `FileScanTask`，通过 `FileScanTaskParser.toJson` 转为 JSON 字符串
3. 对每个 JSON 字符串调用 `out.writeUTF(taskJson)` 写入

**问题根源**：Java/Flink 的 `DataOutputSerializer.writeUTF(String)` 方法遵循 Java 的"修改版 UTF-8"编码规范，其长度前缀使用 **2 个字节（unsigned short，最大 65535）** 来记录 UTF-8 编码后的字节长度。这意味着单个 `writeUTF` 调用最多只能写入 65535 字节的字符串。当字符串的 UTF-8 编码长度超过 65535 字节时，会抛出 `UTFDataFormatException`（或类似异常）。

当 Iceberg 表启用了行级删除（row-level delete，如 position delete 或 equality delete），并且积累了大量 delete 文件时，单个 `FileScanTask` 的 JSON 表示会变得非常大——因为它需要包含该数据文件关联的所有 delete 文件的元数据。当 delete 文件数量足够多（例如数千个），单个 `FileScanTaskParser.toJson` 产生的 JSON 字符串超过 64KB，`writeUTF` 就会抛出异常，导致整个 split 序列化失败，进而导致 Flink 作业无法 checkpoint 或无法分发 split，作业直接失败。

**修复目标**：使 `IcebergSourceSplit` 的序列化能够支持超过 64KB 的单个 task JSON 字符串，从而在大量 delete 文件的场景下不再失败。

## 如何达成设计目的

修复采用"引入新的序列化版本（V3）+ 自定义长字符串读写方法"的策略：

1. **新增 `SerializerHelper` 工具类**：实现 `writeLongUTF` 和 `readLongUTF` 方法，其功能与 `DataOutputSerializer.writeUTF` / `DataInputDeserializer.readUTF` 类似，但长度前缀使用 **4 字节 int（最大约 2GB）** 而非 2 字节 short，从而支持远超 64KB 的字符串。这是核心修复。

2. **引入序列化版本 V3**：在 `IcebergSourceSplit` 中新增 `serializeV3` / `deserializeV3` 方法，V3 与 V2 的唯一区别在于写入/读取 task JSON 时使用 `SerializerHelper.writeLongUTF` / `readLongUTF` 而非 `writeUTF` / `readUTF`。通过版本号区分，保证旧版本（V1、V2）序列化的数据仍能正确反序列化，实现向后兼容。

3. **升级默认版本**：将 `IcebergSourceSplitSerializer` 的 `VERSION` 常量从 2 提升到 3，使新写入的 split 使用 V3 格式；在 `deserialize` 的 switch 中新增 case 3 分支。

4. **新增测试**：添加 `testV3WithTooManyDeleteFiles` 测试，构造包含 5000 个 mock delete 文件的 split，验证 V3 序列化/反序列化能正确往返。同时在 `SplitHelpers` 中新增 `equipSplitsWithMockDeleteFiles` 辅助方法用于构造带大量 mock delete 文件的测试 split。

**序列化版本兼容性设计**：Flink 的 `SimpleVersionedSerializer` 在序列化时会将版本号写入字节流头部，反序列化时根据版本号选择对应的反序列化逻辑。因此：
- 旧版本（V1、V2）写入的 checkpoint 中的 split 仍可被新代码正确反序列化（switch 中保留 case 1、case 2）
- 新代码新写入的 split 使用 V3，旧代码无法反序列化 V3（这通常不是问题，因为升级后不会回滚到旧版本）

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/split/IcebergSourceSplit.java`
**修改目的**：重构序列化/反序列化逻辑，引入 V3 版本支持长字符串。

**工作逻辑**：
- 将原 `serializeV2()` 的实现抽取为私有的 `serialize(int version)` 方法，`serializeV2()` 和新增的 `serializeV3()` 分别委托调用 `serialize(2)` / `serialize(3)`。
- 新增 `writeTaskJson(DataOutputSerializer out, String taskJson, int version)`：根据版本号选择写入方式——V2 调用 `out.writeUTF(taskJson)`，V3 调用 `SerializerHelper.writeLongUTF(out, taskJson)`。
- 同样地将 `deserializeV2()` 抽取为 `deserialize(serialized, caseSensitive, version)`，新增 `deserializeV3()`。
- 新增 `readTaskJson(DataInputDeserializer in, int version)`：V2 用 `in.readUTF()`，V3 用 `SerializerHelper.readLongUTF(in)`。
- 序列化主循环中对每个 `FileScanTask` 调用 `writeTaskJson(out, taskJson, version)`，反序列化循环中调用 `readTaskJson(in, version)`。
- 保留了 `serializedBytesCache` 缓存机制不变。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/split/IcebergSourceSplitSerializer.java`
**修改目的**：将默认序列化版本从 2 升级到 3，并注册 V3 反序列化分支。

**工作逻辑**：
- `VERSION` 常量从 `2` 改为 `3`。
- `serialize()` 方法改为调用 `split.serializeV3()`（原为 `serializeV2()`）。
- `deserialize()` 的 switch 语句新增 `case 3: return IcebergSourceSplit.deserializeV3(serialized, caseSensitive);`，保留 case 1、case 2 以兼容旧数据。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/split/SerializerHelper.java`（新增）
**修改目的**：提供支持超长字符串（突破 64KB 限制）的 UTF 读写工具方法。

**工作逻辑**：
- `writeLongUTF(DataOutputSerializer out, String str)`：
  1. 先遍历字符串计算 UTF-8 编码后的总字节长度 `utflen`（通过 `getUTFBytesSize` 判断每个 char 占 1/2/3 字节），用 `long` 累加以防溢出。
  2. 校验 `utflen` 不超过 `Integer.MAX_VALUE` 及 `Integer.MAX_VALUE - 4`（留出 4 字节前缀空间）。
  3. 用 `out.writeInt((int) utflen)` 写入 **4 字节长度前缀**（关键差异：`writeUTF` 用 2 字节）。
  4. 调用 `writeUTFBytes` 写入 UTF-8 编码字节，编码规则遵循 Java 修改版 UTF-8：U+0001~U+007F 占 1 字节，U+0000 或 U+0080~U+07FF 占 2 字节，其余占 3 字节。
- `readLongUTF(DataInputDeserializer in)`：
  1. `int utflen = in.readInt()` 读取 4 字节长度前缀。
  2. 分配 `byte[utflen]` 和 `char[utflen]` 缓冲区，`readFully` 读入字节。
  3. 按修改版 UTF-8 规则解码：先快速处理 ASCII 段（首字节 <=127），再处理多字节序列（2 字节、3 字节），校验续字节的 `10xxxxxx` 前缀，发现畸形输入抛 `UTFDataFormatException`。
- 该类注释明确说明：Flink 1.20 起 `DataOutputSerializer` 将原生支持类似能力（FLINK-34228），届时可移除此 helper。
- `SerializerHelper` 实现 `Serializable`，因其被 `IcebergSourceSplit`（可序列化）间接引用。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/SplitHelpers.java`
**修改目的**：新增测试辅助方法，构造带大量 mock delete 文件的 split。

**工作逻辑**：
- 新增 `equipSplitsWithMockDeleteFiles(List<IcebergSourceSplit>, TemporaryFolder, int deleteFilesPerSplit)`：
  1. 对每个输入 split，用 Mockito `spy` 包装其 `CombinedScanTask`。
  2. 用 `FileMetadata.deleteFileBuilder` 创建指定数量的 mock `DeleteFile`（Parquet 格式、position delete、文件大小 1000、记录数 1000、路径指向临时文件）。
  3. 遍历原 split 的每个 `FileScanTask`，用 `BaseFileScanTask` 重新构造，将 mock delete 文件数组附加其上，schema 和 spec 通过 `SchemaParser`/`PartitionSpecParser` 序列化保留，`ResidualEvaluator` 用 unpartitioned 包装。
  4. 用 `doReturn(newFileScanTasks).when(combinedScanTask).tasks()` 替换 spy 的 tasks 返回。
  5. 用改造后的 task 重建 `IcebergSourceSplit` 加入结果列表。
- 注释提示：这些 delete 文件是 mock 的，调用方不应尝试真正读取它们。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/split/TestIcebergSourceSplitSerializer.java`
**修改目的**：新增 V3 序列化在大量 delete 文件场景下的往返测试。

**工作逻辑**：
- 新增 `testV3WithTooManyDeleteFiles()`：调用 `serializeAndDeserializeV3(1, 1, 5000)`，即 1 个 split、每 split 1 个数据文件、附加 5000 个 mock delete 文件。
- 私有方法 `serializeAndDeserializeV3`：先用 `createSplitsFromTransientHadoopTable` 创建真实 split，再用 `equipSplitsWithMockDeleteFiles` 装配 mock delete 文件，然后对每个 split 执行 `serializeV3()` -> `deserializeV3()` 往返，最后 `assertSplitEquals` 验证一致性。5000 个 delete 文件足以使单个 task JSON 远超 64KB，从而验证修复有效性。

## 小结
- **成效**：成功修复了 Flink Iceberg Source 在单个 split 包含大量 delete 文件时序列化失败的 Bug。通过引入 V3 序列化版本和 4 字节长度前缀的长字符串读写方法，从根本上突破了 `writeUTF` 的 64KB 限制。同时保持了与 V1、V2 旧格式的反序列化兼容。
- **影响范围**：仅影响 `flink/v1.18` 模块的 Iceberg Source split 序列化路径。影响所有使用 Flink 1.18 + Iceberg Source 且表存在大量 delete 文件（行级删除）的用户。对无 delete 文件或 delete 文件较少的场景无影响（V3 与 V2 行为等价，仅长度前缀宽度不同）。
- **回迁到 1.4.x 的注意事项**：
  - 此修复仅覆盖 `flink/v1.18`。1.4.x 分支若同时维护 v1.17、v1.19、v1.20 等 Flink 版本目录，需同步回迁到对应目录（参见后续提交 #10177 已为 v1.17/v1.19 完成移植）。
  - 序列化版本升级（V2->V3）具有方向性：新代码可读旧格式（V1/V2），但旧代码无法读 V3 格式。回迁后不应回滚到不支持 V3 的版本，否则从 V3 checkpoint 恢复会失败。
  - `SerializerHelper` 注释提到 Flink 1.20 原生支持长 UTF，1.4.x 若目标 Flink 版本为 1.20+，可考虑直接使用原生方法而简化此 helper；但为保持跨版本一致，保留 helper 亦可。
  - 测试依赖 Mockito 的 `spy`/`doReturn`，需确认 1.4.x 测试依赖中包含相应版本。
