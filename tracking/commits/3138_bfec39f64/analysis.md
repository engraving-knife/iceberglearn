# 提交 3138：Make StandardEncryptionManager serializable (#14751)

## 提交信息

- **序号**：3138 / 4088
- **哈希**：bfec39f64666b8d49dc5061a6c5cfa96062f613a
- **短哈希**：bfec39f64
- **日期**：2026-01-20
- **作者**：Thomas Powell
- **提交说明**：Make StandardEncryptionManager serializable (#14751)
- **PR/Issue**：#14751

## 总体目的

Iceberg 的 `StandardEncryptionManager` 与各云厂商的 `KeyManagementClient`（AWS/Azure/GCP）需要在分布式引擎（Spark/Flink）中被序列化后发送到 executor 端执行读写时的加解密。然而此前 `StandardEncryptionManager` 名义上声称可序列化（声明了若干 `transient` 字段并加了“序列化后不可用”的守卫），但实际上并不可用：它持有一个 `final TransientEncryptionState transientState` 字段，而 `TransientEncryptionState` 内部聚合了不可序列化的 `KeyManagementClient`、Caffeine `LoadingCache` 与 `Map<String, EncryptedKey>`，整个对象图根本无法被 Java 序列化器正常写出；即便勉强写出，反序列化后 `wrapKey`/`unwrapKey`/`encryptionKeys()`/`keyEncryptionKeyID()`/`encryptedByKey()`/`addManifestListKeyMetadata()` 等方法都会因 `transientState == null` 直接抛出 “Cannot ... after serialization” 异常。这意味着在真正的分布式加密写入场景下，executor 端拿到的 manager 是残废的。

本提交的目标是让 `StandardEncryptionManager` 真正可序列化并在反序列化后功能完整。其核心思路是：让 `KeyManagementClient` 自身可序列化（把不可序列化的 SDK 客户端改为 `transient volatile`，按需从已保存的 `SerializableMap` 属性中懒重建），让 `BaseEncryptedKey`/`EncryptedKey` 可序列化（`ByteBuffer` 改为 `byte[]`、`properties` 用 `SerializableMap` 包装、接口 `extends Serializable`），让 `StandardEncryptionManager` 持有可序列化的 `kmsClient` 与 `encryptionKeys`，并把 Caffeine 缓存改为 `transient volatile` 懒重建（双检锁），从而移除所有“序列化后不可用”的守卫。同时为各 KMS 客户端与 `BaseEncryptedKey` 补齐序列化往返测试，并在 Spark v4.0/v4.1 加密测试中新增 `DROP TABLE PURGE` 后数据文件被物理删除的用例。

## 如何达成设计目的

整体设计分三层：① `api` 层让 `EncryptedKey extends Serializable`；② `core` 层把 `BaseEncryptedKey` 的不可序列化字段（`ByteBuffer`、原生 `Map`）替换为可序列化等价物（`byte[]`、`SerializableMap`），并重构 `StandardEncryptionManager` 去掉 `TransientEncryptionState`、改为字段级懒加载；③ 各云模块（aws/azure/gcp）把 `KeyManagementClient` 改造为“属性可序列化 + 客户端懒重建”模式，并使 `close()` 幂等。配套新增序列化往返测试与 Spark purge 功能测试。涉及文件分布在 `api`、`core`、`aws`、`azure`、`gcp`、`spark/v4.0`、`spark/v4.1` 模块。

## 修改详情

### `api/src/main/java/org/apache/iceberg/encryption/EncryptedKey.java` (+2/-1 lines)

**修改目的**：让 `EncryptedKey` 接口本身可序列化。

**工作逻辑**：
接口由 `public interface EncryptedKey` 改为 `public interface EncryptedKey extends Serializable`。这是整个改造的根基——所有实现该接口的密钥对象（如 `BaseEncryptedKey`）现在有了 `Serializable` 契约，使得 `StandardEncryptionManager` 中持有 `Map<String, EncryptedKey>` 能被整体序列化。

### `core/src/main/java/org/apache/iceberg/encryption/BaseEncryptedKey.java` (+6/-4 lines)

**修改目的**：让 `BaseEncryptedKey` 真正可序列化。

**工作逻辑**：
- `keyMetadata` 字段类型由 `ByteBuffer` 改为 `byte[]`。`ByteBuffer` 在 Java 序列化中行为不可靠（不同实现未必标记 `Serializable`，且 position/limit 等状态会被序列化），而 `byte[]` 天然可序列化。构造函数中以 `ByteBuffers.toByteArray(keyMetadata)` 转存，`encryptedKeyMetadata()` 改为 `ByteBuffer.wrap(keyMetadata)` 返回新视图，避免暴露内部数组。
- `properties` 用 `SerializableMap.copyOf(properties)` 包装，确保该 `Map` 是可序列化的不可变副本，避免上游传入不可序列化的 Map 实现。
- 字段 `keyId`、`encryptedById` 本就是 `String`，无需改动。

### `core/src/main/java/org/apache/iceberg/encryption/StandardEncryptionManager.java` (+45/-61 lines)

**修改目的**：重构 manager，使其真正可序列化且反序列化后功能完整。

**工作逻辑**：
- 删除内部类 `TransientEncryptionState` 与 `final transientState` 字段。原先该类把 `kmsClient`、`encryptionKeys`、`unwrappedKeyCache` 三个本应分别处理的字段打包成一个不可序列化对象，导致整个 manager 不可序列化。
- 新增可序列化字段 `Map<String, EncryptedKey> encryptionKeys`（构造时用 `SerializableMap.copyOf(Maps.newLinkedHashMap())` 建空表后逐个 put `BaseEncryptedKey`）与 `KeyManagementClient kmsClient`（依赖各 KMS 客户端自身可序列化）。
- `unwrappedKeyCache` 改为 `transient volatile LoadingCache<String, ByteBuffer>`，新增 `unwrappedKeyCache()` 方法用双检锁（先判空，再 `synchronized` 二次判空）懒重建 Caffeine 缓存，缓存加载函数仍调用 `kmsClient.unwrapKey(encryptionKeys.get(keyId).encryptedKeyMetadata(), tableKeyId)`。`lazyRNG` 仍为 `transient volatile SecureRandom` 懒加载。
- 构造函数改为直接赋值 `this.kmsClient = kmsClient` 并构建 `encryptionKeys`。
- 移除 `wrapKey`、`unwrapKey`、`encryptionKeys()`、`keyEncryptionKeyID()`、`encryptedByKey()`、`addManifestListKeyMetadata()` 中所有 `Preconditions.checkState(transientState != null, "...after serialization")` 守卫，并把这些方法中对 `transientState.xxx` 的访问替换为对 `kmsClient`、`encryptionKeys`、`unwrappedKeyCache()` 的直接访问。这样反序列化后 manager 仍能 wrap/unwrap、查找当前 KEK、按 ID 取加密密钥、新增 manifest list 密钥元数据。
- `keyEncryptionKeyID()` 在需要新建 KEK 时，把 `unwrappedKeyCache.put(...)` 改为 `unwrappedKeyCache().put(...)`，`encryptionKeys.put(...)` 直接写可序列化 Map。

### `aws/src/main/java/org/apache/iceberg/aws/AwsKeyManagementClient.java` (+38/-11 lines)

**修改目的**：让 AWS KMS 客户端可序列化。

**工作逻辑**：
- `kmsClient` 由普通字段改为 `transient volatile KmsClient`；`initialize(Map)` 不再立即构建客户端，而是保存 `allProperties = SerializableMap.copyOf(properties)`，同时解析并保存 `encryptionAlgorithmSpec`、`dataKeySpec`。
- 新增私有 `kmsClient()` 方法，用双检锁按需从 `allProperties` 经 `AwsClientFactories.from(allProperties).kms()` 重建 `KmsClient`。`wrapKey`/`generateDataKey`/`unwrapKey` 全部改调 `kmsClient()`。
- `close()` 用 `AtomicBoolean isResourceClosed` 做 `compareAndSet`，保证幂等关闭，避免反序列化/重复关闭场景下重复关闭或 NPE。
- 暴露包级私有 `encryptionAlgorithmSpec()`、`dataKeySpec()` getter，供序列化测试断言配置保留。

### `azure/src/main/java/org/apache/iceberg/azure/keymanagement/AzureKeyManagementClient.java` (+64/-23 lines)

**修改目的**：让 Azure Key Vault 客户端可序列化。

**工作逻辑**：
- `initialize(Map)` 仅保存 `allProperties = SerializableMap.copyOf(properties)`；原先在 `initialize` 中构建的 `KeyClient` 与 `KeyWrapAlgorithm` 被移入新的内部静态类 `ClientState`（持有 `KeyClient` 与 `KeyWrapAlgorithm`）。
- 字段 `transient volatile ClientState state`，通过 `state()` 双检锁按需构建：从 `allProperties` 构造 `AzureProperties`、`KeyClientBuilder`（设置 vaultUrl、credential），构建 `KeyClient` 并解析 `keyWrapAlgorithm`，封装为 `ClientState`。`wrapKey`/`unwrapKey` 经 `keyClient()`/`keyWrapAlgorithm()` 间接访问 `state()`。
- 由于 `ClientState` 是 `transient`，反序列化后会在首次访问时重建，Azure SDK 客户端无需自身可序列化。

### `gcp/src/main/java/org/apache/iceberg/gcp/GcpKeyManagementClient.java` (+57/-18 lines)

**修改目的**：让 GCP KMS 客户端可序列化。

**工作逻辑**：
- `kmsClient` 与 `closeableGroup` 改为 `transient volatile`；`initialize(Map)` 仅保存 `allProperties = SerializableMap.copyOf(properties)` 并重建 `closeableGroup`。
- 新增 `kmsClient()` 双检锁方法，把原先 `initialize` 中的构建逻辑（解析 `GCPProperties`、按 OAuth 凭证构建 `KeyManagementServiceSettings`、`KeyManagementServiceClient.create`、注册到 `closeableGroup`）搬入其中。原先构建失败抛 `RuntimeException`，现改为抛 `RuntimeIOException`（与 Iceberg 异常体系一致）。
- `close()` 用 `AtomicBoolean isResourceClosed` 幂等化，并在关闭前补 `closeableGroup.setSuppressCloseFailure(true)`，对 `closeableGroup` 为 null（反序列化后未初始化即关闭）的情况做防护。
- `wrapKey`/`unwrapKey` 改调 `kmsClient()`。

### `core/src/test/java/org/apache/iceberg/encryption/TestBaseEncryptedKeySerialization.java` (新增, +47 lines)

**修改目的**：验证 `BaseEncryptedKey` 的序列化往返。

**工作逻辑**：
参数化测试使用 `TestHelpers#serializers` 提供的多种 `RoundTripSerializer`（Java 序列化、Kryo 等），对 `new BaseEncryptedKey("a", ByteBuffer.wrap(keyBytes), "b", Map.of("test","value"))` 做往返序列化，断言反序列化后 `keyId()`、`encryptedById()`、`encryptedKeyMetadata()`、`properties()` 均与原对象相等，间接验证 `byte[]` 转换与 `SerializableMap` 包装的正确性。

### `aws/src/integration/java/org/apache/iceberg/aws/TestKeyManagementClient.java` (+30/-4 lines)

**修改目的**：验证 `AwsKeyManagementClient` 序列化后仍可工作。

**工作逻辑**：
新增 `testSerialization` 参数化测试（`@MethodSource("TestHelpers#serializers")`）：用特定算法与 key spec 初始化客户端，先断言配置 getter 返回预期值，再经 `roundTripSerializer.apply` 序列化往返得到 `result`，用 `result.wrapKey` 加密、用原客户端与 `result` 各自 `unwrapKey` 解密并断言一致，最后断言 `result` 的 `encryptionAlgorithmSpec()`/`dataKeySpec()` 配置保留。同时把一处 `new String("...").getBytes()` 简化为字面量。

### `gcp/src/integration/java/org/apache/iceberg/gcp/TestKeyManagementClient.java` (+21/-4 lines)

**修改目的**：验证 `GcpKeyManagementClient` 序列化往返。

**工作逻辑**：
与 AWS 测试对称，参数化序列化往返后断言 wrap/unwrap 仍正常工作。

### `azure/src/test/java/org/apache/iceberg/azure/keymanagement/TestAzureKeyManagementClient.java` (+25/-4 lines)

**修改目的**：验证 `AzureKeyManagementClient` 序列化往返。

**工作逻辑**：
参数化序列化往返后断言 `keyClient()` 懒重建可用，wrap/unwrap 行为正确。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestTableEncryption.java` (+19/-0 lines)

**修改目的**：补齐加密表 `DROP TABLE PURGE` 物理删除数据文件的端到端测试。

**工作逻辑**：
新增 `testDropTableWithPurge`：查询 `ALL_DATA_FILES` 元数据表得到数据文件路径，断言文件存在；执行 `DROP TABLE %s PURGE`；断言表已不存在，且此前所有数据文件在本地存储中均被物理删除。该测试在加密场景下验证 purge 路径，间接依赖可序列化的加密管理器在 Spark 任务中正确工作。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestTableEncryption.java` (+19/-0 lines)

**修改目的**：在 Spark 4.1 同步相同的 purge 测试。

**工作逻辑**：
与 v4.0 测试逐字相同，确保两个 Spark 版本都覆盖加密 + purge 路径。

## 总结

本提交系统性修复了 `StandardEncryptionManager` 及其依赖链的序列化缺陷：通过让 `EncryptedKey`/`BaseEncryptedKey` 可序列化、把各云 KMS 客户端改造为“属性可序列化 + SDK 客户端懒重建”模式、将 manager 内 Caffeine 缓存改为 transient 懒加载，使加密管理器在 Spark/Flink 分布式执行器上反序列化后仍能完整执行 wrap/unwrap 与密钥轮换，移除了此前所有“序列化后不可用”的运行时守卫，并以单元、集成与 Spark 端到端测试锁定该能力。
