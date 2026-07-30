# 提交 2671：Manifest list encryption (#7770)

## 提交信息

- **序号**：2671 / 4088
- **哈希**：11f3dcb3011fe5e37a4ec57fbc7a0c859ad5f24e
- **短哈希**：11f3dcb30
- **日期**：2025-09-22 08:46:34 -0700
- **作者**：ggershinsky（与 Ryan Blue 共同开发）
- **提交说明**：Manifest list encryption (#7770)
- **PR/Issue**：#7770

## 总体目的

本提交为 Apache Iceberg 实现了清单列表文件（manifest list）的加密能力，填补了 Iceberg 加密体系中一个重要的安全缺口。

在此提交之前，Iceberg 的加密体系覆盖了数据文件（data files）和清单文件（manifest files），但清单列表文件（即快照中指向所有 manifest 文件的 Avro 文件，又称为 snapshot file）一直以明文存储。清单列表虽然不包含实际的行数据，但它包含表的元数据信息——例如每个 manifest 的路径、分区统计、文件计数、行数等，这些信息在敏感场景下同样需要保护。一个未加密的 manifest list 暴露了表的物理布局和规模信息，削弱了端到端加密的承诺。

该实现遵循 Iceberg 规范中关于 manifest list 加密的要求：使用与数据文件相同的 AES-GCM 流式加密算法加密 manifest list 文件内容；同时引入"密钥加密密钥"（Key Encryption Key, KEK）机制，用 KEK 加密 manifest list 的数据密钥元数据（key metadata），并将加密后的密钥元数据与一个 manifest list key ID 一起存储在快照（snapshot）对象中。读取时通过 KEK 解密出 manifest list 的数据密钥，再用该数据密钥解密 manifest list 文件本身。

此外，本提交还为 `StandardKeyMetadata` 增加了 `file_length` 字段。AES-GCM 流式加密需要明文长度信息来防御文件截断攻击（truncation attack），此前 manifest 文件在 `toManifestFile()` 时会写入 writer 的长度，但 manifest list 的路径缺乏同样的机制，本次一并补齐。

## 如何达成设计目的

整体设计分为以下几个层次配合：

1. **API 层新增 `ManifestListFile` 接口**：抽象出 manifest list 文件的位置、加密密钥 ID 和密钥元数据解密能力，使 `FileIO` 能够以统一方式处理明文和加密的 manifest list。
2. **`FileIO` / `EncryptingFileIO` 扩展**：在 `FileIO` 接口增加 `newInputFile(ManifestListFile)` 默认方法；`EncryptingFileIO` 重写该方法，根据是否存在 `encryptionKeyID` 决定是否解密。
3. **写入路径改造**：`ManifestListWriter` 接收 `EncryptionManager`，在写入时若为 `StandardEncryptionManager` 则加密 manifest list 输出，并通过 `toManifestListFile()` 产出包含密钥 ID 的 `ManifestListFile`。
4. **KEK 机制**：`StandardEncryptionManager` 内部维护一个 KEK（用 table key 通过 KMS wrap），所有 manifest list 的数据密钥都用该 KEK 加密；引入 Caffeine 缓存避免重复调用 KMS unwrap。
5. **快照集成**：`SnapshotProducer` 将 `writer.toManifestListFile().encryptionKeyID()` 写入快照的 `key-id` 字段；`BaseSnapshot` 读取时用该 key ID 构造 `BaseManifestListFile` 交给 `FileIO` 解密。
6. **密钥元数据格式升级**：`StandardKeyMetadata` schema v1 增加 `file_length` 字段，`AesGcmInputFile` 支持显式传入加密文件长度。

## 修改详情

### `api/src/main/java/org/apache/iceberg/ManifestListFile.java` (+34/-0 lines, 新增)

**修改目的**：定义 manifest list 文件的公共接口。

**工作逻辑**：新接口包含三个方法：`location()` 返回文件路径；`encryptionKeyID()` 返回加密密钥 ID（为 null 表示未加密）；`decryptKeyMetadata(EncryptionManager)` 使用表的加密管理器解密并返回 manifest list 的密钥元数据 `ByteBuffer`。该接口与已有的 `ManifestFile` 接口平行，便于 `FileIO` 以多态方式处理。

### `api/src/main/java/org/apache/iceberg/io/FileIO.java` (+10/-0 lines)

**修改目的**：为 `FileIO` 增加读取 manifest list 文件的能力。

**工作逻辑**：新增默认方法 `newInputFile(ManifestListFile)`。默认实现会校验 `encryptionKeyID()` 必须为 null（即不支持加密），否则抛出异常并提示使用 `EncryptingFileIO`。这保证了非加密的 FileIO 实现无需修改即可继续工作，而加密场景必须由 `EncryptingFileIO` 处理。

### `api/src/main/java/org/apache/iceberg/encryption/EncryptingFileIO.java` (+13/-1 lines)

**修改目的**：实现加密 manifest list 文件的解密读取。

**工作逻辑**：重写 `newInputFile(ManifestListFile)`：若 `encryptionKeyID()` 不为 null，则先调用 `manifestList.decryptKeyMetadata(em)` 解密出数据密钥元数据，再调用 `newDecryptingInputFile(path, keyMetadata)` 返回解密后的 InputFile；否则回退到普通的 `newInputFile(path)`。同时移除了一个关于加密文件长度是否正确的旧 TODO 注释（因为本次提交通过 `file_length` 字段解决了该问题）。

### `core/src/main/java/org/apache/iceberg/BaseManifestListFile.java` (+49/-0 lines, 新增)

**修改目的**：提供 `ManifestListFile` 的标准实现。

**工作逻辑**：持有 `location` 和 `encryptionKeyID` 两个字段，实现 `Serializable`。`decryptKeyMetadata` 委托给 `EncryptionUtil.decryptManifestListKeyMetadata()` 完成实际解密。该类是包级可见的，供 `ManifestListWriter` 和 `BaseSnapshot` 内部使用。

### `core/src/main/java/org/apache/iceberg/BaseSnapshot.java` (+4/-1 lines)

**修改目的**：在读取 manifest list 时传入密钥 ID 以支持解密。

**工作逻辑**：原先 `ManifestLists.read(fileIO.newInputFile(manifestListLocation))` 直接用路径构造 InputFile；现在改为 `ManifestLists.read(fileIO.newInputFile(new BaseManifestListFile(manifestListLocation, keyId)))`，利用快照自身记录的 `keyId` 字段构造 `BaseManifestListFile`，使 `FileIO` 能据此判断是否需要解密。

### `core/src/main/java/org/apache/iceberg/ManifestListWriter.java` (+54/-2 lines)

**修改目的**：让 manifest list 写入器支持加密并产出 `ManifestListFile`。

**工作逻辑**：构造函数新增 `EncryptionManager` 参数。若该 manager 是 `StandardEncryptionManager`，则调用其 `encrypt(file)` 得到 `NativeEncryptionOutputFile`，从中获取加密后的 OutputFile 和 key metadata；否则直接使用原始 file 且 key metadata 为 null。新增 `toManifestListFile()` 方法：在 writer 关闭后，若有 key metadata 且包含 encryption key，则调用 `copyWithLength(writer.length())` 把文件长度写入 metadata，再调用 `standardEncryptionManager.addManifestListKeyMetadata()` 用 KEK 加密该 metadata 并得到 manifest list key ID，最终返回带 key ID 的 `BaseManifestListFile`；否则返回 key ID 为 null 的版本。V1/V2/V3/V4 四个内部 Writer 类的构造函数都相应增加了 `EncryptionManager` 参数。

### `core/src/main/java/org/apache/iceberg/ManifestLists.java` (+21/-3 lines)

**修改目的**：在工厂方法中传递 `EncryptionManager`。

**工作逻辑**：`write()` 方法签名增加 `EncryptionManager encryptionManager` 参数，并透传给各版本 Writer 构造函数。这是 API 适配层，本身逻辑不变。

### `core/src/main/java/org/apache/iceberg/ManifestWriter.java` (+18/-2 lines)

**修改目的**：让 manifest 文件写入也利用 `file_length` 字段防止截断攻击。

**工作逻辑**：将字段类型从 `ByteBuffer keyMetadataBuffer` 改为 `EncryptionKeyMetadata keyMetadata`（更抽象）。在 `toManifestFile()` 中，若 key metadata 是 `NativeEncryptionKeyMetadata`，则调用 `copyWithLength(length())` 把 manifest 文件长度写入 metadata 后再取 buffer；否则直接取 buffer。这与 manifest list 的长度处理方式保持一致。

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java` (+9/-0 lines)

**修改目的**：表路径重写工具也需正确传递加密管理器。

**工作逻辑**：在重写 manifest list 时，判断 FileIO 是否为 `EncryptingFileIO`，是则取其 `encryptionManager()`，否则使用 `PlaintextEncryptionManager.instance()`，并传入 `ManifestLists.write()`。保证路径重写不会意外丢失加密能力。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (+3/-1 lines)

**修改目的**：将 manifest list 的 key ID 写入快照。

**工作逻辑**：`ManifestLists.write()` 调用增加 `ops.encryption()` 参数；在创建快照时，原先传入 `null` 的 key-id 字段改为传入 `writer.toManifestListFile().encryptionKeyID()`，使快照 JSON 中记录 manifest list 的加密密钥 ID，供后续读取使用。

### `core/src/main/java/org/apache/iceberg/encryption/StandardEncryptionManager.java` (+129/-12 lines)

**修改目的**：实现 KEK 机制和 manifest list 密钥元数据的加解密。

**工作逻辑**：这是本提交最核心的改动。
- 引入内部类 `TransientEncryptionState`，封装不可序列化的 KMS 客户端、加密密钥映射表 `Map<String, EncryptedKey>` 和基于 Caffeine 的 `LoadingCache`（1 小时过期），用于缓存已 unwrap 的密钥，避免对 KMS 的重复调用。
- `KEY_ENCRYPTION_KEY_ID` 常量标识每实例唯一的 KEK。`keyEncryptionKeyID()` 方法惰性创建 KEK：生成新数据密钥、通过 KMS wrap、存入缓存和映射表。
- `addManifestListKeyMetadata(NativeEncryptionKeyMetadata)`：生成随机 manifest list key ID，用 KEK 通过 `EncryptionUtil.encryptManifestListKeyMetadata()` 加密 manifest list 的数据密钥元数据，封装为 `BaseEncryptedKey`（encryptedById 指向 KEK ID）存入映射表，返回 key ID。
- `encryptedByKey(String)` 和 `encryptedKeyMetadata(String)`：读取时根据 manifest list key ID 查找对应的 KEK（从缓存获取已解密的 KEK）和已加密的密钥元数据。
- 旧的 `wrapKey` / `unwrapKey` 方法标记为 `@Deprecated`（将在 2.0 移除），改为通过 `transientState` 访问 KMS。
- 解密 InputFile 时，将 `keyMetadata().fileLength()` 传给 `AesGcmInputFile`。

### `core/src/main/java/org/apache/iceberg/encryption/StandardKeyMetadata.java` (+43/-7 lines)

**修改目的**：在密钥元数据中增加文件长度字段。

**工作逻辑**：Schema v1 从两个字段（`encryption_key`、`aad_prefix`）扩展为三个字段，新增 optional 的 `file_length`（Long）。新增 `fileLength()` getter 和 `copyWithLength(long)` 方法（返回带长度的新实例，原实例不变）。Avro 的 `put`/`get` 方法增加 case 2 处理 `fileLength`。复制构造函数改为基于 `toCopy` 并允许用新长度覆盖。`getSchema()` 改为直接返回静态 `AVRO_SCHEMA_V1`，移除了实例字段 `avroSchema`。

### `core/src/main/java/org/apache/iceberg/NativeEncryptionKeyMetadata.java` (+17/-0 lines)

**修改目的**：在接口层声明文件长度能力。

**工作逻辑**：新增 `fileLength()` 默认方法（抛出 `UnsupportedOperationException`）和 `copyWithLength(long)` 默认方法（同样抛出异常），由 `StandardKeyMetadata` 实现。这使 `ManifestListWriter` 和 `ManifestWriter` 能以接口方式调用，而不必强制转换为具体类型。

### `core/src/main/java/org/apache/iceberg/encryption/AesGcmInputFile.java` (+24/-7 lines)

**修改目的**：支持显式传入加密文件长度。

**工作逻辑**：原先 `plaintextLength` 用 long（-1 表示未初始化），现改为 `Long`（null 表示未初始化）；新增 `encryptedLength` 字段。新增构造函数重载，接受 `Long length` 参数。`encryptedLength()` 私有方法在 `encryptedLength` 为 null 时回退到 `sourceFile.getLength()`。`getLength()` 和 `newStream()` 都改用 `encryptedLength()` 而非直接调用 `sourceFile.getLength()`，这样即使 InputFile 未跟踪长度也能正确解密。

### `core/src/main/java/org/apache/iceberg/encryption/EncryptionUtil.java` (+46/-0 lines)

**修改目的**：提供 manifest list 密钥元数据的加解密工具方法。

**工作逻辑**：
- `decryptManifestListKeyMetadata(ManifestListFile, EncryptionManager)`：校验 manager 是 `StandardEncryptionManager`，从中取出 KEK（`encryptedByKey`）和已加密的密钥元数据（`encryptedKeyMetadata`），用 KEK 构造 `AesGcmDecryptor`，以 manifest list key ID 的 UTF-8 字节作为 AAD（附加认证数据）解密，返回明文密钥元数据 buffer。
- `encryptManifestListKeyMetadata(ByteBuffer key, String keyId, EncryptionKeyMetadata)`：用 KEK 构造 `AesGcmEncryptor`，以 key ID 字节为 AAD 加密密钥元数据。使用 key ID 作为 AAD 将每个 manifest list 的密钥元数据与自己的 ID 绑定，增强认证强度。

### `.palantir/revapi.yml` (+5/-0 lines)

**修改目的**：接受 `EncryptingFileIO` 的二进制兼容性变更。

**工作逻辑**：在 1.10.0 版本下新增一条 `java.class.defaultSerializationChanged` 的 accepted break，理由是"New method for Manifest List reading"。因为 `EncryptingFileIO` 新增了方法，其默认序列化 UID 发生变化，需要 revapi 放行。

### `core/src/jmh/java/org/apache/iceberg/ManifestReadBenchmark.java` (+9/-2 lines) 和 `ManifestWriteBenchmark.java` (+2/-0 lines)

**修改目的**：适配 `ManifestLists.write()` 新签名。

**工作逻辑**：基准测试中调用 `ManifestLists.write()` 时传入 `PlaintextEncryptionManager.instance()`，保持明文写入以维持基准测试的可比性。

### `core/src/test/java/org/apache/iceberg/TestManifestListEncryption.java` (+146/-0 lines, 新增)

**修改目的**：验证 manifest list 加密的端到端正确性。

**工作逻辑**：使用 `EncryptingFileIO.combine(io, ENCRYPTION_MANAGER)` 构造加密 FileIO，写入一个 manifest list 后：先用普通 `outputFile.toInputFile()` 读取，断言抛出 `InvalidAvroMagicException`（证明确实被加密）；再用 `encryptingFileIO.newInputFile(manifestListFile)` 读取，断言能正确解密并还原 manifest 的所有字段（路径、长度、spec ID、序列号、文件计数、行数等）。

### `core/src/test/java/org/apache/iceberg/TestMetadataUpdateParser.java` (+9/-1 lines)、`TestSnapshotJson.java` (+10/-2 lines)、`TestTableMetadata.java` (+9/-1 lines)

**修改目的**：适配 `ManifestLists.write()` 新签名。

**工作逻辑**：在测试中调用 `ManifestLists.write()` 时增加 `PlaintextEncryptionManager.instance()` 参数。`TestSnapshotJson` 还移除了对 `allManifests` 的相等性断言（因为加密后 manifest 引用比较会失败）。

### `core/src/test/java/org/apache/iceberg/encryption/EncryptionTestHelpers.java` (+1/-1 lines)

**修改目的**：移除测试加密管理器对 format-version=2 的硬编码。

**工作逻辑**：删除 `tableProperties.put(TableProperties.FORMAT_VERSION, "2")` 一行，使测试加密管理器可用于各格式版本的测试。

### `core/src/test/java/org/apache/iceberg/hadoop/TestCatalogUtilDropTable.java` (+4/-0 lines)

**修改目的**：为 mock FileIO 增加对 `newInputFile(ManifestListFile)` 的桩。

**工作逻辑**：在 mock FileIO 时，新增对 `newInputFile(ManifestListFile)` 方法的 thenAnswer 桩，委托给真实 wrapped FileIO 处理，避免 drop table 测试中因新方法未被 mock 而返回 null。

### `core/src/test/java/org/apache/iceberg/TestManifestListVersions.java` (+2/-0 lines) 和 `TestManifestWriterVersions.java` (+1/-0 lines)

**修改目的**：适配新签名的小幅调整。

## 总结

本提交是 Iceberg 加密体系的一次重要安全增强，实现了 manifest list 文件的端到端加密。核心设计包括：引入 `ManifestListFile` 抽象、在 `StandardEncryptionManager` 中实现基于 KEK 的密钥元数据加密与 Caffeine 缓存、在快照中记录 manifest list key ID、为密钥元数据增加 `file_length` 字段以防御 AES-GCM 截断攻击。改动涉及 API 层（新增接口和 FileIO 方法）、core 层（写入器、快照、加密管理器、密钥元数据格式）和测试层，是一次跨模块的完整功能落地。该功能使 Iceberg 的加密覆盖范围从数据文件和 manifest 文件扩展到 manifest list，闭合了元数据层面的信息泄露缺口。该 PR 历经多轮评审和重构（提交信息中可见大量迭代），由 ggershinsky 主导、Ryan Blue 共同完成。
