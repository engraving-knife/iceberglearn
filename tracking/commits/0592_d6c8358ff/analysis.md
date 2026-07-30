# 提交 0592：API, Core: Support manifest encryption

## 提交信息

- **序号**：0592 / 4088
- **哈希**：d6c8358ff26957c9234580addb03a0db1e441c4d
- **短哈希**：d6c8358ff
- **日期**：2024-03-13（Wed Mar 13 19:48:09 2024 +0200）
- **作者**：ggershinsky <ggershinsky@users.noreply.github.com>
- **提交说明**：API, Core: Support manifest encryption (#8252)
- **PR/Issue**：#8252

## 总体目的

本提交为 Iceberg 引入对 manifest 文件加密的能力，补齐了表元数据加密的最后一块拼图。

在改动之前，Iceberg 的数据文件（data file）和删除文件（delete file）已经可以通过 `EncryptionManager` 进行透明加解密，但 manifest 文件（描述数据文件清单的 Avro 文件）以及 manifest list（snap-*.avro，包含多个 manifest 引用）仍以明文存储。这意味着即使数据文件被加密，攻击者仍能从 manifest 中读取：

- 数据文件的路径、分区值、文件大小；
- 列级 metrics（min/max/null count 等），可能泄露数据分布与取值范围；
- 数据文件之间的关联关系与快照演进历史。

本提交通过让 `ManifestWriter` 走与数据文件相同的 `EncryptedOutputFile` 加密管线，使 manifest 文件也能被透明加密；同时在 manifest list 的 Avro schema 中加入 `KEY_METADATA` 字段，将每个 manifest 的加密密钥元数据持久化到 manifest list 中，便于读取时根据元数据解密对应的 manifest。

## 如何达成设计目的

整体设计遵循 Iceberg 既有的"加密输出文件 + 加密管理器"架构，主要思路是把 manifest 写入路径上原本使用 `OutputFile` 的位置替换为 `EncryptedOutputFile`，并把密钥元数据透传到最终的 `ManifestFile` 元数据中。

具体实现路径如下：

1. **API 层扩展 `PositionOutputStream`**：新增 `storedLength()` 方法，返回实际存储的字节长度。对加密流（如 AES-GCM），写入字节流的位置（`getPos()`）与底层存储的字节数可能不同（加密头、认证标签等开销），因此需要单独方法获取真实存储长度。默认实现回退到 `getPos()`，保证对未加密流的兼容。

2. **核心写入路径改用 `EncryptedOutputFile`**：
   - `SnapshotProducer` 新增 `newManifestOutputFile()` 方法，通过 `EncryptingFileIO.combine(ops.io(), ops.encryption())` 把 FileIO 与 EncryptionManager 组合成一个加密感知的 FileIO，再调用 `newEncryptingOutputFile(location)` 生成 `EncryptedOutputFile`。原 `newManifestOutput()` 标记为 `@Deprecated`（计划在 1.7.0 移除）。
   - `ManifestFiles.write()` 与 `writeDeleteManifest()` 新增接受 `EncryptedOutputFile` 的重载；原 `OutputFile` 重载通过 `EncryptedFiles.plainAsEncryptedOutput()` 包装为空密钥的 `EncryptedOutputFile` 后委托给新重载，保持向后兼容。
   - `ManifestWriter` 构造函数从 `OutputFile` 改为 `EncryptedOutputFile`，提取底层 `encryptingOutputFile()` 用于实际写入，并缓存 `keyMetadataBuffer`（来自 `file.keyMetadata().buffer()`）。在 `toManifestFile()` 中将该 buffer 作为 key metadata 写入 `GenericManifestFile`，使 manifest list 能记录每个 manifest 的解密信息。

3. **拷贝 manifest 路径同步更新**：`FastAppend`、`BaseRewriteManifests`、`MergingSnapshotProducer` 中的 `copyManifest` 方法都改为：
   - 输入侧用 `ops.io().newInputFile(manifest)`（传入 ManifestFile 对象，而非 `manifest.path()`），让 FileIO 根据该 manifest 的 key metadata 决定是否解密；
   - 输出侧用 `newManifestOutputFile()` 替代 `newManifestOutput()`，得到 `EncryptedOutputFile`。

4. **manifest list schema 增加 KEY_METADATA 字段**：
   - `V1Metadata.MANIFEST_FILE_SCHEMA` 与 `V2Metadata.MANIFEST_FILE_SCHEMA` 都加入 `ManifestFile.KEY_METADATA` 字段，使 manifest list 的 Avro schema 能序列化每个 manifest 的密钥元数据。
   - `V1Metadata.IndexedManifestEntry` / `ManifestFileWrapper` 暴露 `keyMetadata()` 读取，并在 `get(pos)` switch 中加入 case 11 返回 `keyMetadata()`，使 Avro 写入器能正确序列化该字段。

5. **修复加密流的长度计算**：`AvroFileAppender.length()` 从 `stream.getPos()` 改为 `stream.storedLength()`，确保加密 manifest 文件的长度字段记录的是实际密文长度而非写入流的位置。`AesGcmOutputStream` 重写 `storedLength()` 委托给目标流的 `storedLength()`，将真实存储长度向上传递。

6. **新增测试 `TestManifestEncryption`**：使用 `EncryptionTestHelpers.createEncryptionManager()` 构造 AES-GCM 加密管理器，覆盖 V1 写、V2 写、V2 delete 写三种场景。测试先验证用 `PlaintextEncryptionManager` 读取加密 manifest 会抛出 `InvalidAvroMagicException`（因为 Avro magic 字节也被加密了），再用正确的 `ENCRYPTION_MANAGER` 读取并校验文件条目内容与 metrics 完全一致。

## 修改详情

### `api/src/main/java/org/apache/iceberg/io/PositionOutputStream.java`

**修改目的**：为加密流提供获取真实存储长度的能力。

**工作逻辑**：新增 `storedLength()` 方法，默认实现返回 `getPos()`。对加密流（如 AES-GCM），写入流的位置（明文已写字节数）与底层存储的字节数（含加密头/尾的密文字节数）不同，子类需重写此方法返回密文长度。这是后续 `AvroFileAppender.length()` 正确计算加密 manifest 文件长度的基础。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`

**修改目的**：为 manifest 写入提供加密感知的输出文件工厂方法。

**工作逻辑**：
- 新增 `newManifestOutputFile()`：先用 `ops.metadataFileLocation()` 拼出 manifest 文件路径（与原 `newManifestOutput()` 相同的命名规则），再通过 `EncryptingFileIO.combine(ops.io(), ops.encryption())` 组合 FileIO 与 EncryptionManager，调用 `newEncryptingOutputFile(location)` 得到 `EncryptedOutputFile`。`EncryptingFileIO` 会根据 `EncryptionManager` 决定是否真正加密：若表配置了加密，会调用 `EncryptionManager.encrypt(outputFile)` 生成加密输出文件；否则返回包装了空密钥的 `EncryptedOutputFile`。
- 将原 `newManifestOutput()` 标记为 `@Deprecated`（计划 1.7.0 移除），保留以兼容子类。
- `newManifestWriter` 与 `newDeleteManifestWriter` 改用 `newManifestOutputFile()`。

### `core/src/main/java/org/apache/iceberg/ManifestFiles.java`

**修改目的**：让 manifest 写入 API 接受加密输出文件。

**工作逻辑**：
- 新增 `write(formatVersion, spec, EncryptedOutputFile, snapshotId)` 重载，按 formatVersion 选择 `V1Writer` / `V2Writer`。
- 原 `write(formatVersion, spec, OutputFile, snapshotId)` 改为委托给新重载，通过 `EncryptedFiles.plainAsEncryptedOutput(outputFile)` 把 `OutputFile` 包装为空密钥的 `EncryptedOutputFile`，保持向后兼容。
- `writeDeleteManifest` 同样新增 `EncryptedOutputFile` 重载，原 `OutputFile` 重载委托。
- `copyAppendManifest` 与 `copyRewriteManifest` 的 `outputFile` 参数类型从 `OutputFile` 改为 `EncryptedOutputFile`，内部 `copyManifestInternal` 同步更新。这两个方法用于把已存在的 manifest 拷贝到新位置（FastAppend / RewriteManifests 场景），同样需要支持加密。

### `core/src/main/java/org/apache/iceberg/ManifestWriter.java`

**修改目的**：把加密元数据写入 manifest 文件清单条目。

**工作逻辑**：
- 构造函数从 `(spec, OutputFile, snapshotId)` 改为 `(spec, EncryptedOutputFile, snapshotId)`。
  - `this.file = file.encryptingOutputFile()`：取出底层 `OutputFile` 用于实际写入（加密在更底层完成）。
  - `this.writer = newAppender(spec, this.file)`：用底层 OutputFile 创建 Avro appender。
  - `this.keyMetadataBuffer = (file.keyMetadata() == null) ? null : file.keyMetadata().buffer()`：缓存加密密钥元数据的 ByteBuffer。
- `toManifestFile()` 把 `keyMetadataBuffer` 作为最后一个参数传给 `GenericManifestFile` 构造器（原来传 `null`），使 manifest list 能记录每个 manifest 的解密信息。
- `V1Writer`、`V2Writer`、`V2DeleteWriter` 构造函数签名同步更新。

### `core/src/main/java/org/apache/iceberg/V1Metadata.java` 与 `core/src/main/java/org/apache/iceberg/V2Metadata.java`

**修改目的**：在 manifest list 的 Avro schema 中加入 KEY_METADATA 字段。

**工作逻辑**：
- `V1Metadata.MANIFEST_FILE_SCHEMA` 末尾加入 `ManifestFile.KEY_METADATA`（第 11 个字段，索引 10 之后）。
- `V2Metadata.MANIFEST_FILE_SCHEMA` 同样加入 `ManifestFile.KEY_METADATA`。
- `V1Metadata.ManifestFileWrapper` 暴露 `keyMetadata()` 委托给 wrapped 对象。
- `V1Metadata.IndexedManifestEntry.get(pos)` 的 switch 加入 `case 11: return keyMetadata();`，让 Avro 写入器能序列化该字段。
- V2 路径通过 `ManifestFile` 接口直接读取 `keyMetadata()`，无需在 wrapper 中显式声明。

### `core/src/main/java/org/apache/iceberg/avro/AvroFileAppender.java`

**修改目的**：修正加密 manifest 文件的长度计算。

**工作逻辑**：`length()` 方法从 `stream.getPos()` 改为 `stream.storedLength()`。`getPos()` 返回写入流的位置（明文已写字节数），而加密流的真实存储字节数（密文长度）可能与位置不同。manifest list 中记录的 manifest 长度字段必须为真实存储长度，否则读取端在尝试按 length 范围读取时会出错。

### `core/src/main/java/org/apache/iceberg/encryption/AesGcmOutputStream.java`

**修改目的**：让 AES-GCM 加密流正确报告存储长度。

**工作逻辑**：重写 `storedLength()`，委托给 `targetStream.storedLength()`。AES-GCM 输出流把数据加密后写入 targetStream，因此真实存储长度就是 targetStream 的存储长度（targetStream 通常为底层文件输出流，其 `storedLength()` 默认等于 `getPos()`）。

### `core/src/main/java/org/apache/iceberg/encryption/EncryptedFiles.java`

**修改目的**：为不加密场景提供兼容包装。

**工作逻辑**：新增 `plainAsEncryptedOutput(OutputFile)` 静态工厂方法，返回 `new BaseEncryptedOutputFile(encryptingOutputFile, EncryptionKeyMetadata.EMPTY)`。该方法把一个普通 `OutputFile` 包装为带空密钥元数据的 `EncryptedOutputFile`，用于让旧的 `ManifestFiles.write(..., OutputFile, ...)` 重载能复用新的加密路径而不实际加密。

### `core/src/main/java/org/apache/iceberg/FastAppend.java`、`core/src/main/java/org/apache/iceberg/BaseRewriteManifests.java`、`core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`

**修改目的**：在拷贝 manifest 时使用加密感知的输入/输出。

**工作逻辑**：三处的 `copyManifest` 方法做相同改动：
- 输入侧：`ops.io().newInputFile(manifest.path())` 改为 `ops.io().newInputFile(manifest)`，让 FileIO 根据 manifest 自带的 key metadata 决定是否解密。这是 `FileIO` 接口已有的重载（接受 `ManifestFile`），加密感知的 FileIO 会读取 manifest 的 key metadata 并调用 `EncryptionManager.decrypt(...)`。
- 输出侧：`newManifestOutput()` 改为 `newManifestOutputFile()`，得到 `EncryptedOutputFile`。
- 调用 `ManifestFiles.copyAppendManifest` / `copyRewriteManifest` 时传入新的 `EncryptedOutputFile`。

### `core/src/test/java/org/apache/iceberg/TestManifestEncryption.java`（新增）

**修改目的**：覆盖 manifest 加密的端到端测试。

**工作逻辑**：
- 用 `EncryptionTestHelpers.createEncryptionManager()` 构造 AES-GCM 加密管理器。
- 用例 `testV1Write` / `testV2Write` / `testV2WriteDelete` 分别用 V1/V2 manifest 写、V2 delete manifest 写。
- 写入流程：`ENCRYPTION_MANAGER.encrypt(manifestFile)` 得到 `EncryptedOutputFile`，调用 `ManifestFiles.write(formatVersion, SPEC, encryptedManifest, SNAPSHOT_ID)` 写入 `DATA_FILE` / `DELETE_FILE`，关闭后 `writer.toManifestFile()` 得到 `ManifestFile`。
- 读取校验：先用 `EncryptingFileIO.combine(FILE_IO, PlaintextEncryptionManager.instance())`（明文管理器）读取，断言抛出 `RuntimeIOException` 且 cause 为 `InvalidAvroMagicException`——证明 manifest 确实被加密（Avro magic 字节不可读）。再用 `EncryptingFileIO.combine(FILE_IO, ENCRYPTION_MANAGER)`（正确密钥）读取，校验 entry 的 status/snapshotId/sequence number 以及 data file 的 path/format/partition/metrics 等字段全部符合预期。

## 小结

本提交为 Iceberg manifest 文件引入透明加密能力，是表元数据加密闭环的关键一环。通过让 manifest 写入走与数据文件相同的 `EncryptedOutputFile` 管线，并在 manifest list schema 中持久化每个 manifest 的密钥元数据，实现了端到端的加密 manifest 读写。

**影响范围**：
- API 层：`PositionOutputStream` 新增 `storedLength()` 方法，第三方实现的 `PositionOutputStream` 子类无需立即重写（默认回退到 `getPos()`），但若用于加密流则需要重写以返回真实存储长度。
- 核心层：manifest 写入、拷贝、manifest list 序列化路径全部更新；manifest list 的 Avro schema 增加了 `KEY_METADATA` 字段（V1 与 V2 都加）。
- 兼容性：旧 `OutputFile` 重载通过 `plainAsEncryptedOutput()` 包装后委托给新路径，不破坏既有调用方；`SnapshotProducer.newManifestOutput()` 标记为 `@Deprecated`，计划 1.7.0 移除。
- 行为变化：对未启用加密的表，行为完全不变（走空密钥路径）；对启用加密的表，新生成的 manifest 会自动加密，manifest list 会记录 key metadata。

**回迁到 1.4.x 的注意事项**：
1. **schema 变更需谨慎**：V1/V2 manifest list schema 增加 `KEY_METADATA` 字段是 Avro schema 演进。Avro 默认对新增字段做向后兼容（旧 reader 读新文件，新字段被视为缺失/默认 null），但需确认 1.4.x 中 manifest list 的 reader/writer 版本协商逻辑与 main 一致，否则可能出现旧版本读不了新 manifest list 的情况。
2. **依赖项**：本提交依赖 `EncryptingFileIO`、`EncryptedOutputFile`、`EncryptionKeyMetadata` 等类，这些在 1.4.x 中应已存在（数据文件加密早已支持）。回迁前需确认这些类的 API 与本提交使用的版本一致。
3. **PositionOutputStream.storedLength()**：若 1.4.x 中有自定义的 `PositionOutputStream` 实现（如第三方 IO 模块），升级后需要确认其在加密场景下 `storedLength()` 的语义正确。
4. **测试**：`TestManifestEncryption` 依赖 `EncryptionTestHelpers.createEncryptionManager()`，回迁时需同步引入该测试辅助类。
5. **API 兼容**：本提交新增了 `ManifestFiles.write(..., EncryptedOutputFile, ...)` 重载并保留旧重载，不破坏二进制兼容；但 `SnapshotProducer.newManifestOutput()` 标记 `@Deprecated`，若 1.4.x 子类重写了该方法需注意未来升级路径。
