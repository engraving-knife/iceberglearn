# 提交分析：Core: Support Avro file encryption with AES GCM streams

## 提交信息

- 哈希: bb50ab97d377476bfa3a743448e4afabe619e764
- 短哈希: bb50ab97d
- 日期: 2024-01-16 09:55:06 -0800
- 作者: ggershinsky
- 说明: Core: Support Avro file encryption with AES GCM streams (#9436)

## 总体目的

本次提交为 Iceberg 的 Avro 文件格式接入此前已存在的 AES-GCM 流式加密能力，使 Avro 数据文件、删除文件与通用写入路径都能像 Parquet/ORC 一样受到 `EncryptionManager` 的透明保护。在本次提交之前，`Avro.write(EncryptedOutputFile)`、`Avro.writeData(EncryptedOutputFile)`、`Avro.writeDeletes(EncryptedOutputFile)` 三个入口都通过 `Preconditions.checkState(file.keyMetadata() == null || file.keyMetadata() == EncryptionKeyMetadata.EMPTY, "Avro encryption is not supported")` 显式拒绝携带密钥元数据的输出文件——也就是说 Avro 之前是 Iceberg 中唯一不支持加密的主流格式。本次提交删除这三处守卫，Avro 写入路径即可正常接受 `EncryptedOutputFile`，从而把"加密"这件事统一收敛到 `OutputFile` 抽象层面，与格式无关。

加密机制本身并非本次新造：Iceberg 在 `core/src/main/java/org/apache/iceberg/encryption` 包中已经实现了完整的 AES-GCM 流式加解密栈——`AesGcmOutputFile`/`AesGcmOutputStream` 负责写入侧，`AesGcmInputFile`/`AesGcmInputStream` 负责读取侧，`Ciphers` 提供底层原语与流格式常量。其流格式为：先写文件头（小端序 magic `AGS1` + 4 字节明文块大小，共 8 字节），随后将明文按 1MB 切块，每块独立用 AES-GCM 加密并以 [Nonce(12B) | 密文 | GCM-Tag(16B)] 的布局写出。每块的 AAD 由"文件级 AAD 前缀 + 块序号"拼接而成（见 `Ciphers.streamBlockAAD`），既提供机密性也提供完整性，并防止块重排与块替换攻击。Nonce 由 `SecureRandom` 随机生成 12 字节，保证 GCM 在同一密钥下 Nonce 不重复。

之所以这一机制天然适合 Avro，是因为 Avro 文件本身就是顺序字节流（容器格式 OCF + 内部 block），而 Iceberg 的 AES-GCM 流设计本身就支持随机定位（每块独立可解密，`AesGcmInputStream` 可 seek/skip 到任意块）——这点对 Avro 的 split 读取（即 `Avro.read(...).split(start, length)`）至关重要。Parquet/ORC 走的是"格式内嵌加密"路线（列级加密、footer 加密），而 Avro 走的是"文件级流加密"路线：`EncryptionManager.encrypt(out)` 返回的 `EncryptedOutputFile.encryptingOutputFile()` 实际上是一个 `AesGcmOutputFile` 包装器，Avro writer 向其 `PositionOutputStream` 写明文字节，加密层透明地写出密文字节；读取时 `EncryptionManager.decrypt(encryptedInput)` 返回 `AesGcmInputFile` 包装器，Avro reader 读到的明文字节由底层按需解密。Avro 完全无需感知加密的存在——这正是删除守卫即可生效的原因。

本次提交同时新增了对应的测试基础设施与端到端测试 `TestEncryptedAvroFileSplit`，验证加密 Avro 文件的全文件读取、split 读取、`_pos` 元数据列读取以及 EOF 边界 split 等场景。

## 如何达成设计目的

实现分为"放开限制"与"补齐测试"两部分。放开限制：删除 `Avro.java` 中 `write`/`writeData`/`writeDeletes` 三个静态工厂方法里对 `file.keyMetadata()` 的非空校验，直接将 `file.encryptingOutputFile()`（即已包装好的 `AesGcmOutputFile` 或未加密的原 `OutputFile`）交给后续 builder。由于加密层在 `OutputFile`/`PositionOutputStream` 抽象之下工作，Avro 的 `FileAppender` 写出的明文字节会被透明加密，无需 Avro 侧做任何改动。补齐测试：新增 `TestEncryptedAvroFileSplit`、`EncryptionTestHelpers`、`UnitestKMS` 三个测试类，覆盖"写入加密 Avro → 多种方式读取并校验"的端到端路径。`UnitestKMS` 继承 `MemoryMockKMS`，用两个 16 字节主密钥（`keyA`/`keyB`）模拟 KMS；`EncryptionTestHelpers.createEncryptionManager()` 通过 `EncryptionUtil.createEncryptionManager(tableProperties, kmsClient)` 构造一个使用 `UnitestKMS`、表密钥名为 `keyA`、`format-version=2` 的加密管理器；测试用例通过 `ENCRYPTION_MANAGER.encrypt(out)` 获得加密输出，写完后再用 `EncryptedFiles.encryptedInput(out.toInputFile(), eOut.keyMetadata())` + `ENCRYPTION_MANAGER.decrypt(...)` 构造解密输入，最后调用 `Avro.read(file).split(start, length).project(projection)` 验证读取结果。

## 修改详情

### core/src/main/java/org/apache/iceberg/avro/Avro.java

**修改目的**：移除 Avro 写入路径上对加密的显式拒绝，使 Avro 支持 AES-GCM 文件加密。

**工作逻辑**：在 `write(EncryptedOutputFile file)`、`writeData(EncryptedOutputFile file)`、`writeDeletes(EncryptedOutputFile file)` 三个静态工厂方法中，分别删除如下守卫：

```
Preconditions.checkState(
    file.keyMetadata() == null || file.keyMetadata() == EncryptionKeyMetadata.EMPTY,
    "Avro encryption is not supported");
```

删除后方法体仅保留 `return new WriteBuilder/DataWriteBuilder/DeleteWriteBuilder(file.encryptingOutputFile());`。`encryptingOutputFile()` 返回的对象由 `EncryptionManager` 决定：若表配置了加密，则返回 `AesGcmOutputFile`（其 `create()`/`createOrOverwrite()` 返回 `AesGcmOutputStream`，把明文字节按 1MB 分块 GCM 加密后写入底层流，并先写 `AGS1` 文件头）；若未配置加密，则原样返回底层 `OutputFile`。Avro 的 writer 始终只看到明文 API，加密对格式层透明。读取侧同样无需改动——`Avro.read(InputFile)` 接收的 `InputFile` 由 `EncryptionManager.decrypt` 返回的 `AesGcmInputFile` 包装，按需解密块并支持 `split(start, length)`。

### core/src/test/java/org/apache/iceberg/avro/TestEncryptedAvroFileSplit.java

**修改目的**：新增端到端测试，验证加密 Avro 文件在多种读取场景下的正确性。

**工作逻辑**：
- 静态准备：定义 SCHEMA（`id: long`, `data: string`），通过 `EncryptionTestHelpers.createEncryptionManager()` 拿到一个基于 `UnitestKMS` 的 `EncryptionManager`，常量 `NUM_RECORDS = 100_000`（足够大以触发多块加密，验证 split 跨块）。
- `@BeforeEach writeDataFile()`：用 `Files.localOutput(temp.toFile())` 创建本地输出，`ENCRYPTION_MANAGER.encrypt(out)` 得到 `EncryptedOutputFile eOut`；通过 `Avro.write(eOut).set(AVRO_COMPRESSION, "uncompressed").createWriterFunc(DataWriter::create).schema(SCHEMA).overwrite().build()` 写入 10 万条随机记录（`id` 递增、`data` 为随机 UUID），并存入 `expected`；然后用 `EncryptedFiles.encryptedInput(out.toInputFile(), eOut.keyMetadata())` + `ENCRYPTION_MANAGER.decrypt(...)` 得到解密 `InputFile file`，供后续测试读取。
- `testSplitDataSkipping()`：把文件按中点 split 成前后两半，分别用 `Avro.read(file).createReaderFunc(DataReader::create).split(start, length).project(SCHEMA).build()` 读取，断言两半均非空、总数等于期望、记录顺序与期望逐条相等。验证加密流支持 split 读取（依赖 `AesGcmInputStream` 的随机 seek 能力）。
- `testPosField()`：用包含 `MetadataColumns.ROW_POSITION` 的 projection 全文件读取，断言每条记录的 `_pos` 等于其索引、`id`/`data` 与期望匹配。验证加密文件下 `_pos` 元数据列仍能正确生成。
- `testPosFieldWithSplits()`：与上一用例相同，但 split 成两半后分别读取并校验 `_pos`、`id`、`data`。验证 split 场景下 `_pos` 仍连续正确。
- `testPosWithEOFSplit()`：从文件末尾倒数 10 字节起读取长度 10 的 split，断言读到 0 条记录。验证 EOF 边界 split 不会读出半条记录或越界。
- `readAvro(InputFile, Schema, long, long)` 是测试用的统一读取入口。

### core/src/test/java/org/apache/iceberg/encryption/EncryptionTestHelpers.java

**修改目的**：提供构造测试用 `EncryptionManager` 的公共辅助方法，供加密相关测试复用。

**工作逻辑**：`createEncryptionManager()` 内部创建两个 properties Map：
- catalogProperties：`CatalogProperties.ENCRYPTION_KMS_IMPL = UnitestKMS.class.getCanonicalName()`，告诉 `EncryptionUtil` 用哪个 KMS 实现。
- tableProperties：`TableProperties.ENCRYPTION_TABLE_KEY = UnitestKMS.MASTER_KEY_NAME1`（即 `keyA`）、`TableProperties.FORMAT_VERSION = "2"`（加密要求 v2 表）。
最后调用 `EncryptionUtil.createEncryptionManager(tableProperties, EncryptionUtil.createKmsClient(catalogProperties))`，由 Iceberg 内部根据表属性装配出完整的加密管理器（包含 DEK 生成、KEK 包装、KMS 解包等链路），KMS 端用 `UnitestKMS` 在内存中模拟主密钥加解包。该 helper 让 Avro 加密测试与未来的其他加密测试无需各自重写加密管理器装配代码。

### core/src/test/java/org/apache/iceberg/encryption/UnitestKMS.java

**修改目的**：提供一个内存级 KMS mock，用于测试环境下的密钥管理。

**工作逻辑**：继承 `MemoryMockKMS`（Iceberg 已有的内存 KMS 基类），在 `initialize(Map<String, String> properties)` 中把两个主密钥注册到 `masterKeys`：`MASTER_KEY_NAME1="keyA"` → `MASTER_KEY1="0123456789012345".getBytes(UTF-8)`、`MASTER_KEY_NAME2="keyB"` → `MASTER_KEY2="1123456789012345".getBytes(UTF-8)`。两个主密钥均为 16 字节（AES-128）。KMS 的作用是在 Iceberg 加密链路中包装/解包数据密钥（DEK）：写入时 `EncryptionManager` 生成随机 DEK，用 KMS 提供的主密钥包装后存入 `EncryptionKeyMetadata`；读取时 KMS 用主密钥解包出 DEK，再由 `AesGcmOutputFile`/`AesGcmInputFile` 用于 AES-GCM 加解密。`UnitestKMS` 让这一切在测试中无需真实 KMS 服务即可运行。

## 小结

本次提交通过删除 `Avro.java` 中三处 `Preconditions.checkState(..., "Avro encryption is not supported")` 守卫，使 Avro 数据文件、删除文件与通用写入路径正式支持 Iceberg 既有的 AES-GCM 流式加密（`AesGcmOutputFile`/`AesGcmInputFile`/`Ciphers` 已实现，分块 1MB、随机 Nonce、块序号 AAD 防重放）。加密在 `OutputFile`/`InputFile` 抽象层透明工作，Avro 格式层无需改动，并天然支持 split 读取。同时新增 `TestEncryptedAvroFileSplit` 端到端测试与 `EncryptionTestHelpers`、`UnitestKMS` 测试基础设施，覆盖全文件读取、split 读取、`_pos` 列、EOF 边界等场景，确保加密 Avro 与未加密 Avro 行为一致。
