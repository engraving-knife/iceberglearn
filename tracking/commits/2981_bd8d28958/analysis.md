# 提交 2981：Hive: Metadata integrity check for encrypted tables (#14685)

## 提交信息

- **序号**：2981 / 4088
- **哈希**：bd8d2895847bdce71b119f4cb7a83b2e1a58ea73
- **短哈希**：bd8d2895
- **日期**：2025-12-08
- **作者**：Adam Szita
- **提交说明**：Hive: Metadata integrity check for encrypted tables (#14685)
- **PR/Issue**：#14685

## 总体目的

Iceberg 表的元数据（`metadata.json`）存储在底层文件系统上，而 Hive 集成中表的加密密钥 ID（`encryption.table-key`）等可信属性存储在 Hive Metastore（HMS）中。当表启用加密时，存在一类安全威胁：攻击者可以替换文件系统上的 `metadata.json` 文件——例如用一份旧的 metadata 文件覆盖当前的，把表"回滚"到旧状态、绕过加密或读取到不应可见的数据。由于加密密钥 ID 来自可信的 HMS，但 metadata 文件本身来自可能不可信的存储层，仅靠原先的"检查 metadata 中的加密属性与 HMS 中的是否一致"不足以发现整份 metadata 文件被掉包的篡改。

本提交的目的就是为加密的 Hive 表引入元数据完整性校验：在每次提交时，对当前 `TableMetadata` 计算 SHA-256 哈希，并以一个新的 HMS 表属性 `metadata_hash` 存储（仅存储在 HMS，不写入 metadata 文件本身）。在每次刷新（refresh）加载表时，重新对从存储层读到的 metadata 计算哈希，与 HMS 中记录的 `metadata_hash` 比对；若不一致则抛出异常，阻止使用被篡改的 metadata。由于哈希只对启用加密的表生成，未加密表行为不变；对于历史遗留的加密表（HMS 中尚无 `metadata_hash`），则回退到原先的"加密属性一致性检查"并打印警告，保证平滑过渡。

## 如何达成设计目的

整体思路分三部分：

1. 在 `core` 模块新增一个通用的 `HashWriter`——一个 `java.io.Writer` 实现，内部用 `MessageDigest` 对流式写入的字节做增量哈希，不保存明文。这样可以让 `TableMetadataParser.toJson(metadata, jsonGenerator)` 在序列化 metadata 时直接把 JSON 输出流式喂给 `HashWriter` 计算哈希，避免把整份 JSON 先序列化成字符串再哈希的内存开销，尤其对大表 metadata 友好。

2. 在 `HMSTablePropertyHelper` 中新增 `setMetadataHash`（提交时计算并写入 HMS 参数）与 `verifyMetadataHash`（刷新时校验）两个静态方法，内部都通过私有的 `hashOf(metadata)` 用 `HashWriter` + SHA-256 计算。`setMetadataHash` 仅在 metadata 含 `ENCRYPTION_TABLE_KEY` 时才计算并写入，未加密表不写。

3. 在 `HiveTableOperations` 中：提交路径 `doCommit` 重构为先把最终 `TableMetadata`（无论是直接用入参 `metadata`，还是经加密 builder 重建的版本）确定下来再写盘、再调用 `HMSTablePropertyHelper.updateHmsTableForIcebergTable`（后者内部会调 `setMetadataHash`）；刷新路径 `refresh` 从 HMS 读取 `metadata_hash`，并调用改名后的 `checkIntegrityForEncryption`——若有 `metadata_hash` 则做完整哈希校验，否则回退到旧的加密属性一致性检查并打警告日志。

涉及文件包括 `core` 模块的 `BaseMetastoreTableOperations`（新增常量）、新增的 `HashWriter` 及其测试，`hive-metastore` 模块的 `HMSTablePropertyHelper`、`HiveTableOperations`、`TestHiveCatalog`，以及 `spark/v4.0` 模块的 `TestTableEncryption`（端到端篡改测试）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseMetastoreTableOperations.java` (+1/-0 lines)

**修改目的**：定义新的 HMS 表属性键名常量 `metadata_hash`。

**工作逻辑**：
新增 `public static final String METADATA_HASH_PROP = "metadata_hash";`，与既有的 `METADATA_LOCATION_PROP`、`PREVIOUS_METADATA_LOCATION_PROP` 并列。该常量被 Hive 模块在读写 HMS 表参数时引用，用于存储/读取 metadata 的 SHA-256 哈希。

### `core/src/main/java/org/apache/iceberg/util/HashWriter.java` (+78/-0 lines, 新文件)

**修改目的**：提供一个流式哈希 `Writer`，使 metadata 序列化时能增量计算哈希而无需把整份 JSON 缓存为字符串。

**工作逻辑**：
`HashWriter extends Writer`，构造时接收算法名（如 `"SHA-256"`）和 `Charset`，内部创建 `MessageDigest` 与 `CharsetEncoder`。`write(char[] cbuf, int off, int len)` 把字符通过 `encoder.encode` 转为字节后调用 `digest.update(byteBuffer)` 做增量更新；`flush()` 空实现；`close()` 仅置 `isClosed` 标志。核心方法 `getHash()` 调用 `digest.digest()` 计算最终哈希并返回 `byte[]`，同时置 `isClosed=true`，之后任何 `write`/`getHash` 调用都会被 `ensureNotClosed()` 拦截抛 `IllegalStateException("HashWriter is closed.")`。这样它可以作为 `JsonGenerator` 的输出目标，让 `TableMetadataParser.toJson(metadata, generator)` 在写出 JSON 的同时完成哈希计算。

### `core/src/test/java/org/apache/iceberg/util/TestHashWriter.java` (+75/-0 lines, 新文件)

**修改目的**：验证 `HashWriter` 的增量哈希计算结果与一次性哈希一致，且关闭后不可再用。

**工作逻辑**：
`testIncrementalHashCalculation` 用 `spy` 包装 `HashWriter`，构造一个含 300 个属性的 `TableMetadata`（足够大，使 JSON 生成器分多次写出），通过 `JsonUtil.factory().createGenerator(hashWriter)` + `TableMetadataParser.toJson(tableMetadata, generator)` 流式写入。先 `verify(hashWriter, times(3)).write(...)` 确认分多次写入触发了多次增量哈希，`generator.flush()` 后变为 4 次。然后用 `MessageDigest.getInstance("SHA-256").digest(TableMetadataParser.toJson(tableMetadata).getBytes(UTF_8))` 计算一次性哈希作为期望值，断言 `hashWriter.getHash()` 与之相等，并断言再次 `getHash()` 抛出 "HashWriter is closed." 异常。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HMSTablePropertyHelper.java` (+39/-1 lines)

**修改目的**：实现 metadata 哈希的写入与校验逻辑。

**工作逻辑**：
新增 import（`JsonGenerator`、`IOException`、`StandardCharsets`、`NoSuchAlgorithmException`、`Arrays`、`Base64`、`TableMetadataParser`、`HashWriter`）。在 `updateHmsTableForIcebergTable` 流程中调用 `setMetadataHash(metadata, parameters)`，与设置 schema、storage handler 等并列。

- `setMetadataHash(TableMetadata metadata, Map<String,String> parameters)`：仅当 `parameters` 含 `TableProperties.ENCRYPTION_TABLE_KEY` 时，调用 `hashOf(metadata)` 计算哈希，Base64 编码后写入 `parameters.put(METADATA_HASH_PROP, ...)`。未加密表不写哈希。
- `verifyMetadataHash(TableMetadata metadata, String metadataHashFromHMS)`：调用 `hashOf(metadata)` 得到当前哈希，Base64 解码 HMS 中的哈希，用 `Arrays.equals` 比较；不一致则抛 `RuntimeException`，提示 "The current metadata file %s might have been modified. Hash of metadata loaded from storage differs from HMS-stored metadata hash." 并附 metadata 文件位置。
- `private static byte[] hashOf(TableMetadata tableMetadata)`：try-with-resources 创建 `HashWriter("SHA-256", UTF_8)`，用 `JsonUtil.factory().createGenerator(hashWriter)` + `TableMetadataParser.toJson(tableMetadata, generator)` + `generator.flush()` 流式序列化并计算哈希，返回 `hashWriter.getHash()`；捕获 `NoSuchAlgorithmException`/`IOException` 转为 `RuntimeException`。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java` (+37/-9 lines)

**修改目的**：在刷新时校验 metadata 哈希，在提交时使用统一的最终 `tableMetadata` 以便正确计算哈希。

**工作逻辑**：
- `refresh()`：从 HMS 表参数中读取 `metadataHashFromHMS = table.getParameters().get(METADATA_HASH_PROP)`；在 `tableKeyIdFromHMS != null`（加密表）分支，把原来的 `checkEncryptionProperties(tableKeyIdFromHMS, dekLengthFromHMS)` 改为 `checkIntegrityForEncryption(tableKeyIdFromHMS, dekLengthFromHMS, metadataHashFromHMS)`。
- `doCommit()`：重构——原本在加密分支直接 `writeNewMetadataIfRequired(newTable, builder.build())`，非加密分支 `writeNewMetadataIfRequired(newTable, metadata)`。现在先确定 `final TableMetadata tableMetadata`：加密分支 `tableMetadata = builder.build()`，非加密分支 `tableMetadata = metadata`；然后统一 `newMetadataLocation = writeNewMetadataIfRequired(newTable, tableMetadata)`。后续 `hiveEngineEnabled`、`lockObject`、`newHmsTable`、`storageDescriptor`、属性差集、`HMSTablePropertyHelper.updateHmsTableForIcebergTable`、`checkCommitStatusStrict`/`checkCommitStatus` 等所有原先引用 `metadata` 的位置统一改为 `tableMetadata`。这样保证写盘的 metadata 与传给 `HMSTablePropertyHelper`（从而计算哈希）的 metadata 是同一份，避免加密重建后的 metadata 与写盘版本不一致导致哈希对不上。
- `checkIntegrityForEncryption(...)`（原 `checkEncryptionProperties` 改名并扩展）：先取 `TableMetadata metadata = current()`；若 `StringUtils.isNotEmpty(metadataHashFromHMS)`，调用 `HMSTablePropertyHelper.verifyMetadataHash(metadata, metadataHashFromHMS)` 做完整哈希校验并 return；否则打 `LOG.warn` 提示"因 HMS 中无 metadata hash 跳过完整完整性校验，回退到基于加密属性的检查"，再执行原先的加密属性一致性检查（对比 `ENCRYPTION_TABLE_KEY` 与 `ENCRYPTION_DEK_LENGTH`）。这保证了向后兼容——旧表无 `metadata_hash` 时不会校验失败，而是降级并告警。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java` (+27/-0 lines)

**修改目的**：单元测试 `setMetadataHash` 在加密/未加密表下的行为。

**工作逻辑**：
新增参数化测试 `testMetadataHashing(@ValueSource booleans)`：构造 `hiveTblProperties`，加密分支放入 `ENCRYPTION_TABLE_KEY="key_id"`；构造一个 `TableMetadata`，调用 `HMSTablePropertyHelper.setMetadataHash(tableMetadata, hiveTblProperties)`。加密时断言取出的 `METADATA_HASH_PROP` 是合法 Base64，并调用 `verifyMetadataHash` 应通过；未加密时断言该属性为 null（即不写哈希）。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestTableEncryption.java` (+30/-0 lines)

**修改目的**：端到端验证 metadata 文件被篡改后刷新表会抛出完整性校验异常。

**工作逻辑**：
新增 import（`Configuration`、`ChecksumFileSystem`、`FileSystem`、`Path`、`HasTableOperations`、`TableMetadata`、`mockito Iterables`）。新增测试 `testMetadataTamperproofing`：加载表拿到当前 `TableMetadata` 与其前一份 metadata 文件路径；用 `ChecksumFileSystem` 手动实施文件系统篡改——删除当前 metadata 文件及其 CRC 校验文件，把前一份 metadata 文件 rename 覆盖到当前 metadata 文件位置（模拟把表"回滚"到旧 metadata）；然后 `catalog.loadTable(tableIdent)` 应抛出异常，消息包含 "The current metadata file %s might have been modified. Hash of metadata loaded from storage differs from HMS-stored metadata hash."。这验证了端到端的篡改检测能力。

## 总结

该提交为加密的 Hive 表引入了基于 SHA-256 的元数据完整性校验：提交时在 HMS 中存储 `metadata_hash`，刷新时比对，从而能检测出存储层 metadata 文件被掉包/篡改的安全威胁。核心机制是新增的流式 `HashWriter`（让 metadata JSON 序列化与哈希计算一次流式完成，避免大表内存开销）配合 `HMSTablePropertyHelper` 的写入/校验方法与 `HiveTableOperations` 的提交/刷新流程改造。同时通过"无哈希则回退到旧加密属性检查并告警"保证了对历史加密表的向后兼容。改动覆盖 core、hive-metastore、spark 三个模块，并配有单元测试与端到端篡改测试，显著提升了加密 Hive 表的安全姿态。
