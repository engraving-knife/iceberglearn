# 提交 0264：Core: Add StandardEncryptionManager (#9277)

## 提交信息

- **序号**：0264 / 4088
- **哈希**：36ecab4601b34114224e10b579eaee9018e57344
- **短哈希**：36ecab460
- **日期**：2023-12-12 10:07:46 -0800
- **作者**：Ryan Blue
- **提交说明**：Core: Add StandardEncryptionManager (#9277)
- **PR/Issue**：#9277

## 总体目的

Iceberg 的加密能力长期处于半成品状态：`EncryptionManager` 接口、`EncryptedInputFile`/`EncryptedOutputFile` 抽象、`AesGcmInputFile`/`AesGcmOutputFile`（AES-GCM 流式加解密）以及 `KeyMetadata`（基于 Avro 单文件对象编码的密钥元数据序列化）都已存在，`PlaintextEncryptionManager` 也提供了无加密的占位实现；但是缺少一个真正基于信封加密（envelope encryption）的 `EncryptionManager` 实现：本地生成数据加密密钥（DEK），通过 KMS 主密钥包装/解包 DEK，并把 DEK 与 AAD（附加认证数据）以 `StandardKeyMetadata` 形式持久化到文件元数据中，再结合 `AesGcmOutputFile`/`AesGcmInputFile` 完成 AES-GCM 加解密。这导致 Iceberg 表层面没有标准的端到端加密路径，用户即便设置了 `encryption.key-id` 也不会被实际使用。

本提交通过新增 `StandardEncryptionManager` 来填补这一缺口，把 KMS 抽象（`KeyManagementClient`）、密钥元数据序列化（`StandardKeyMetadata`，由 `KeyMetadata` 重命名而来）、AES-GCM 流式加解密三者组装成一个完整的信封加密实现。配套地：

1. 新增 `EncryptionUtil`，提供 `createKmsClient(catalogProperties)` 与 `createEncryptionManager(tableProperties, kmsClient)` 两个工厂方法，把"从 catalog 属性反射构造 KMS 客户端"、"按表属性选择 Plaintext 或 Standard 加密管理器"这两步集中管理，便于上层 Catalog 调用。
2. 新增 catalog 级加密属性 `encryption.kms-type` / `encryption.kms-impl` 与表级加密属性 `encryption.key-id` / `encryption.data-key-length`（默认 16 字节）以及 `ENCRYPTION_AAD_LENGTH_DEFAULT = 16`，把加密配置统一到 `CatalogProperties` / `TableProperties` 上。
3. 把 `KeyMetadata` 重命名为 `StandardKeyMetadata` 并新增 `castOrParse(EncryptionKeyMetadata)` 静态方法，使其既能直接复用已解析的内存对象，也能从字节缓冲解析，避免重复反序列化。
4. 把 `PlaintextEncryptionManager` 改为单例（`instance()`），统一无加密场景的 `EncryptionManager` 实例获取路径，避免每次 `TableOperations.encryption()` 都 new 一个对象。
5. 在 `EncryptedOutputFile` 接口上新增 `plainOutputFile()` 默认方法，让加密管理器可以拿到原生未加密的 `OutputFile`，以便在 Parquet 等格式走原生加密模块时仍能写出底层文件。

这一提交对 Iceberg 演进的意义在于：它把加密从"有抽象无实现"推进到"有标准实现"，让 Parquet 数据文件的端到端信封加密成为开箱即用的能力，是 Iceberg 安全特性走向成熟的重要一步。

## 如何达成设计目的

整体设计思路是"以 `StandardEncryptionManager` 为核心，把 KMS、密钥元数据、AES-GCM 三个已有组件组装为完整的信封加密流水线"。`StandardEncryptionManager` 实现 `EncryptionManager` 接口的 `encrypt(OutputFile)` / `decrypt(EncryptedInputFile)` 两个方法：`encrypt` 返回一个 `StandardEncryptedOutputFile` 内部类，它持有原始 `plainOutputFile` 与 `dataKeyLength`，并按需懒加载生成 DEK + AAD（用 `SecureRandom`）、构造 `StandardKeyMetadata`、包装出 `AesGcmOutputFile`；`decrypt` 返回一个 `StandardDecryptedInputFile` 内部类，按需懒加载解析 `StandardKeyMetadata`（通过 `castOrParse`，避免重复反序列化）并构造 `AesGcmInputFile`。KMS 的 `wrapKey`/`unwrapKey` 通过 `KeyManagementClient` 完成，`StandardEncryptionManager` 持有 `kmsClient` 引用（`transient`，序列化后丢失，调用会抛 `IllegalStateException`，强制执行节点重新构造 KMS 客户端）。配置入口由 `EncryptionUtil` 提供：`createKmsClient` 通过 `encryption.kms-impl` 反射构造并 `initialize(properties)`；`createEncryptionManager` 根据表属性 `encryption.key-id` 是否存在选择 `PlaintextEncryptionManager`（无 key id 视为不加密）或 `StandardEncryptionManager`，并校验文件格式必须为 Parquet（当前加密仅支持 Parquet）。密钥元数据序列化沿用原 `KeyMetadata` 的 Avro 单文件对象编码方案（V1 schema 含 `encryption_key` 与 `aad_prefix`），仅做类名重命名与 `castOrParse` 增强。

## 修改详情

### `core/src/main/java/org/apache/iceberg/encryption/StandardEncryptionManager.java`（新文件）

**修改目的**：提供基于信封加密的标准 `EncryptionManager` 实现，串联 KMS、密钥元数据、AES-GCM 三者。

**工作逻辑**：

- 字段：`transient KeyManagementClient kmsClient`（不参与序列化，执行节点需重新注入）、`String tableKeyId`（表级主密钥 ID）、`int dataKeyLength`（DEK 长度，16/24/32）、`transient volatile SecureRandom lazyRNG`（懒加载的随机数生成器，`volatile` 保证多线程可见性）。
- 构造方法 `StandardEncryptionManager(String tableKeyId, int dataKeyLength, KeyManagementClient kmsClient)`：校验三者非空且 `dataKeyLength` 必须为 16/24/32（对应 AES-128/192/256），赋值字段。
- `encrypt(OutputFile plainOutput)`：返回 `new StandardEncryptedOutputFile(plainOutput, dataKeyLength)`。
- `decrypt(EncryptedInputFile encrypted)`：返回 `new StandardDecryptedInputFile(encrypted)`。
- `decrypt(Iterable<EncryptedInputFile> encrypted)`：批量解密仅对数据文件生效（Parquet 走原生加密模块，注释说明"Bulk decrypt is only applied to data files. Returning source input files for parquet."），实现为 `Iterables.transform(encrypted, this::decrypt)`，即对每个文件复用单文件 decrypt。
- `workerRNG()`：懒加载 `SecureRandom`，供 `StandardEncryptedOutputFile` 生成 DEK 与 AAD 使用。
- `wrapKey(ByteBuffer secretKey)` / `unwrapKey(ByteBuffer wrappedSecretKey)`：委托给 `kmsClient.wrapKey/unwrapKey(secretKey, tableKeyId)`；若 `kmsClient == null`（典型场景是对象被序列化分发后丢失），抛 `IllegalStateException("Cannot wrap key after called after serialization (missing KMS client)")`，强制重新构造。
- 内部类 `StandardEncryptedOutputFile implements EncryptedOutputFile`：
  - 持有 `plainOutputFile`、`dataKeyLength`、`lazyKeyMetadata`、`lazyEncryptingOutputFile`。
  - `keyMetadata()`：懒加载。生成 `byte[] fileDek = new byte[dataKeyLength]` 与 `byte[] aadPrefix = new byte[ENCRYPTION_AAD_LENGTH_DEFAULT]`（16 字节），用 `workerRNG().nextBytes(...)` 填充随机字节，构造 `new StandardKeyMetadata(fileDek, aadPrefix)`。这样每次写文件都生成全新的 DEK + AAD，符合信封加密"一文件一密钥"原则。
  - `encryptingOutputFile()`：懒加载。用 `keyMetadata().encryptionKey()` 和 `aadPrefix()` 构造 `new AesGcmOutputFile(plainOutputFile, dekBytes, aadBytes)`，把 AES-GCM 流式加密器包装在原始 `OutputFile` 之上。
  - `plainOutputFile()`：返回原始未加密的 `OutputFile`，供 Parquet 原生加密模块使用。
- 内部类 `StandardDecryptedInputFile implements InputFile`（静态私有）：
  - 持有 `encryptedInputFile`、`lazyKeyMetadata`、`lazyDecryptedInputFile`。
  - `keyMetadata()`：懒加载。调用 `StandardKeyMetadata.castOrParse(encryptedInputFile.keyMetadata())`：如果 key metadata 已经是 `StandardKeyMetadata` 实例则直接强转，否则从 `buffer()` 解析（避免重复反序列化）。注意这里**没有**调用 `unwrapKey`——因为 `StandardKeyMetadata` 中存的是**明文 DEK**（写入时由 `workerRNG` 本地生成，未被 KMS 包装；KMS 的 wrap/unwrap 仅在密钥需要持久化到外部不可信存储时才使用，本实现把明文 DEK 直接放在文件元数据中，依赖文件元数据的访问控制来保护）。这是一个值得注意的设计选择：当前的 `StandardEncryptionManager` 没有在写入时把 DEK wrap 后存入 metadata、读取时 unwrap，而是直接存明文 DEK。`wrapKey`/`unwrapKey` 方法存在但 `StandardEncryptedOutputFile`/`StandardDecryptedInputFile` 的代码路径上并未调用它们——这为后续把 DEK 包装后再持久化（即真正用 KMS 包装的密钥元数据）预留了入口，但当前提交中元数据仍是明文 DEK + AAD。
  - `decrypted()`：懒加载。用 `keyMetadata().encryptionKey()` 和 `aadPrefix()` 构造 `new AesGcmInputFile(encryptedInputFile.encryptedInputFile(), dekBytes, aadBytes)`。
  - `getLength()` / `newStream()` / `location()` / `exists()` 全部委托给 `decrypted()` 返回的 `AesGcmInputFile`，对外表现为一个普通 `InputFile`，调用方无感。

### `core/src/main/java/org/apache/iceberg/encryption/EncryptionUtil.java`（新文件）

**修改目的**：提供 KMS 客户端与 `EncryptionManager` 的工厂方法，集中配置入口。

**工作逻辑**：

- `createKmsClient(Map<String, String> catalogProperties)`：
  - 读取 `encryption.kms-type` 与 `encryption.kms-impl` 两个属性。
  - 校验二者不能同时设置（`Preconditions.checkArgument(kmsType == null || kmsImpl == null, "Cannot set both KMS type (%s) and KMS impl (%s)", ...)`）。
  - `kmsType` 暂时未支持（`// TODO: Add KMS implementations`），抛 `Unsupported KMS type` 异常。
  - 用 `DynConstructors.builder(KeyManagementClient.class).impl(kmsImpl).buildChecked()` 反射构造 KMS 客户端实例（要求实现类有 no-arg 构造方法），捕获 `NoSuchMethodException` 翻译为友好错误。
  - 调用 `kmsClient.initialize(catalogProperties)` 完成初始化，把 catalog 属性传入 KMS 实现，使其能建立与 KMS 服务的连接。
- `createEncryptionManager(Map<String, String> tableProperties, KeyManagementClient kmsClient)`：
  - 校验 `kmsClient` 非空。
  - 读取 `TableProperties.ENCRYPTION_TABLE_KEY`（即 `encryption.key-id`）。若为 null，视为不加密，返回 `PlaintextEncryptionManager.instance()` 单例。
  - 否则读取 `DEFAULT_FILE_FORMAT`（默认 `parquet`），校验必须是 Parquet（当前加密仅支持 Parquet 数据文件，抛 `UnsupportedOperationException` 否则）。
  - 读取 `ENCRYPTION_DEK_LENGTH`（默认 16），校验必须是 16/24/32。
  - 构造并返回 `new StandardEncryptionManager(tableKeyId, dataKeyLength, kmsClient)`。

### `core/src/main/java/org/apache/iceberg/encryption/StandardKeyMetadata.java`（由 `KeyMetadata.java` 重命名）

**修改目的**：把通用的 `KeyMetadata` 类名改为 `StandardKeyMetadata`，明确其属于"标准信封加密"实现；并增强复用入口。

**工作逻辑**：

- 文件由 `KeyMetadata.java` 重命名为 `StandardKeyMetadata.java`（`git mv`，相似度 80%），类名同步改为 `StandardKeyMetadata implements EncryptionKeyMetadata, IndexedRecord`。Avro schema 的命名空间从 `KeyMetadata.class.getCanonicalName()` 改为 `StandardKeyMetadata.class.getCanonicalName()`，影响 Avro schema 的全名，但因为 Iceberg 用版本号做 schema 路由（V1），不会破坏向后兼容性。
- 新增公开构造方法 `StandardKeyMetadata(byte[] key, byte[] aad)`：内部用 `ByteBuffer.wrap(...)` 包装后委托给私有构造方法，便于 `StandardEncryptedOutputFile` 直接用字节数组构造（避免无谓的 ByteBuffer 拷贝）。原 `KeyMetadata(ByteBuffer, ByteBuffer)` 改为 `private`，仅供 Avro 反射与 `copy()` 内部使用。
- 新增静态方法 `static StandardKeyMetadata castOrParse(EncryptionKeyMetadata keyMetadata)`：如果传入的对象本身是 `StandardKeyMetadata` 实例则直接强转返回；否则取其 `buffer()`，若为 null 抛 `IllegalStateException("Null key metadata buffer")`，否则调 `parse(buffer)` 反序列化。这让 `StandardDecryptedInputFile` 在 key metadata 已是 `StandardKeyMetadata` 时跳过反序列化，避免重复解析开销。
- `parse(ByteBuffer)`、`copy()`、`buffer()` 等方法签名相应改为 `StandardKeyMetadata`。

### `core/src/main/java/org/apache/iceberg/encryption/KeyMetadataEncoder.java` 与 `KeyMetadataDecoder.java`

**修改目的**：跟随 `KeyMetadata` → `StandardKeyMetadata` 重命名，更新编解码器的类型参数与文档。

**工作逻辑**：

- `KeyMetadataEncoder implements MessageEncoder<KeyMetadata>` 改为 `MessageEncoder<StandardKeyMetadata>`，内部 `DatumWriter<KeyMetadata>` 改为 `DatumWriter<StandardKeyMetadata>`，`encode(KeyMetadata datum, ...)` 改为 `encode(StandardKeyMetadata datum, ...)`，`KeyMetadata.supportedAvroSchemaVersions()` 改为 `StandardKeyMetadata.supportedAvroSchemaVersions()`，Javadoc 中 `KeyMetadata` 改为 `StandardKeyMetadata`。
- `KeyMetadataDecoder extends MessageDecoder.BaseDecoder<KeyMetadata>` 改为 `BaseDecoder<StandardKeyMetadata>`，内部 `Map<Byte, RawDecoder<KeyMetadata>>` 改为 `Map<Byte, RawDecoder<StandardKeyMetadata>>`，`decode(InputStream, KeyMetadata reuse)` 改为 `decode(InputStream, StandardKeyMetadata reuse)`，`KeyMetadata.supportedSchemaVersions()` / `supportedAvroSchemaVersions()` 调用同步改名。

### `core/src/main/java/org/apache/iceberg/encryption/PlaintextEncryptionManager.java`

**修改目的**：把 `PlaintextEncryptionManager` 改为单例，统一不加密场景的 `EncryptionManager` 获取路径。

**工作逻辑**：

- 新增 `private static final EncryptionManager INSTANCE = new PlaintextEncryptionManager()` 与 `public static EncryptionManager instance()` 静态工厂。
- 保留 public 无参构造方法但标记 `@Deprecated`（"will be removed in 1.6.0. use instance() instead"），向后兼容。
- `decrypt(EncryptedInputFile)` 的告警日志改为更准确的"File encryption key metadata is present, but no encryption has been configured."（原文是"but currently using PlaintextEncryptionManager"，更具体但不够清晰）。
- `encrypt(OutputFile rawOutput)` 从 `EncryptedFiles.encryptedOutput(rawOutput, (ByteBuffer) null)` 改为 `EncryptedFiles.encryptedOutput(rawOutput, EncryptionKeyMetadata.empty())`，用语义更清晰的 `empty()` 替代裸 `null`。

### `core/src/main/java/org/apache/iceberg/TableOperations.java`

**修改目的**：让默认 `encryption()` 返回单例而非每次 new。

**工作逻辑**：`default EncryptionManager encryption()` 实现从 `return new PlaintextEncryptionManager();` 改为 `return PlaintextEncryptionManager.instance();`，避免每次调用 `TableOperations.encryption()` 都创建新对象。

### `core/src/main/java/org/apache/iceberg/CatalogProperties.java`

**修改目的**：新增 catalog 级加密配置项。

**工作逻辑**：新增两个常量：
- `ENCRYPTION_KMS_TYPE = "encryption.kms-type"`：预留给内置 KMS 类型选择（当前未实现，`EncryptionUtil.createKmsClient` 会抛 Unsupported）。
- `ENCRYPTION_KMS_IMPL = "encryption.kms-impl"`：自定义 KMS 客户端实现类全名，`EncryptionUtil` 用反射构造。

### `core/src/main/java/org/apache/iceberg/TableProperties.java`

**修改目的**：新增表级加密配置项。

**工作逻辑**：新增三个常量：
- `ENCRYPTION_TABLE_KEY = "encryption.key-id"`：表级主密钥（KEK）ID，存在则启用加密，`EncryptionUtil.createEncryptionManager` 据此选择 Plaintext 或 Standard。
- `ENCRYPTION_DEK_LENGTH = "encryption.data-key-length"`：DEK 长度（字节数）。
- `ENCRYPTION_DEK_LENGTH_DEFAULT = 16`：默认 16 字节（AES-128）。
- `ENCRYPTION_AAD_LENGTH_DEFAULT = 16`：AAD 长度默认 16 字节，供 `StandardEncryptedOutputFile` 生成 AAD 时使用。

### `api/src/main/java/org/apache/iceberg/encryption/EncryptedOutputFile.java`

**修改目的**：在接口上暴露原生未加密的 `OutputFile`，支持 Parquet 等格式的原生加密模块。

**工作逻辑**：新增 default 方法 `plainOutputFile()`，默认抛 `UnsupportedOperationException("Not implemented")`。`StandardEncryptedOutputFile` 覆写该方法返回 `plainOutputFile` 字段。这个方法让上层在需要绕过 AES-GCM 包装、直接走格式原生加密（如 Parquet 模块加密）时，能拿到底层 `OutputFile`。

### `core/src/test/java/org/apache/iceberg/encryption/TestStandardKeyMetadataParser.java`（由 `TestKeyMetadataParser.java` 重命名）

**修改目的**：跟随类重命名，并验证 `StandardKeyMetadata` 的序列化/反序列化正确性。

**工作逻辑**：

- 文件由 `TestKeyMetadataParser.java` 重命名而来（相似度 84%）。
- `testParser`：用 `"0123456789012345"` 作为 16 字节 encryption key、`"1234567890123456"` 作为 16 字节 AAD，构造 `new StandardKeyMetadata(encryptionKey.array(), aadPrefix.array())`，调 `metadata.buffer()` 序列化，再用 `StandardKeyMetadata.parse(serialized)` 反序列化，断言两次得到的 `encryptionKey()` 与 `aadPrefix()` 相等。
- `testUnsupportedVersion`：构造只含一个字节 `0x02` 的缓冲（V1 之外的版本号），断言 `StandardKeyMetadata.parse` 抛 `UnsupportedOperationException("Cannot resolve schema for version: 2")`。

## 小结

本提交通过新增 `StandardEncryptionManager` 及其配套工厂 `EncryptionUtil`、配置项与 `StandardKeyMetadata` 重命名，把 Iceberg 的加密能力从"有抽象无实现"推进到"有标准信封加密实现"，使 Parquet 数据文件的端到端 AES-GCM 加解密 + KMS 密钥管理成为开箱即用的能力，是 Iceberg 加密特性走向成熟的关键一步。
