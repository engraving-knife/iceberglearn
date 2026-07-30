# 提交 2775：Encryption integration and test (#13066)

## 提交信息

- **序号**：2775 / 4088
- **哈希**：30ca573fe4a7fbbe86a7ca8bd2c0fcab5c025c13
- **短哈希**：30ca573fe
- **日期**：2025-10-20 18:39:10 -0700
- **作者**：ggershinsky
- **提交说明**：Encryption integration and test (#13066)
- **PR/Issue**：#13066

## 总体目的

本提交是 Iceberg 加密功能在 Hive Catalog 中的集成实现，是之前加密相关工作（如 2670 Manifest list encryption）的延续和整合。

在此之前，Iceberg 的加密框架（`StandardEncryptionManager`、`EncryptionUtil`、`EncryptingFileIO` 等）已存在于 core 模块中，但 Hive Catalog 尚未集成加密功能。Hive Metastore（HMS）作为表元数据的可信来源，天然适合存储加密密钥 ID（table key ID），而 Iceberg 的 metadata JSON 文件则存储详细的加密密钥信息（encrypted data keys）。

本提交的核心安全设计理念是：**table key ID 必须从可信的 catalog 服务（HMS）获取，而非从不可信的存储（metadata JSON 文件）读取**。因为 metadata 文件可能被篡改——例如攻击者可以删除 table key 参数并清除现有快照，使后续写入器生成未加密文件。通过从 HMS 读取 key ID 并与 metadata 文件中的值进行交叉验证，可以检测篡改行为。

具体而言，本提交实现了：(1) `HiveCatalog` 初始化 KMS 客户端并传递给 `HiveTableOperations`；(2) `HiveTableOperations` 在 refresh 时从 HMS 读取加密属性并验证与 metadata 一致，在 commit 时将新生成的加密密钥写入 metadata；(3) `StandardEncryptionManager` 支持从 table metadata 中的预存密钥初始化；(4) 提供完整的 Spark 端到端加密测试。

## 如何达成设计目的

整体设计分为四个层面：

1. **加密管理器增强**：`StandardEncryptionManager` 新增接受 `List<EncryptedKey>` 参数的构造器，使其能从 table metadata 中的预存密钥初始化 `TransientEncryptionState`。新增 `encryptionKeys()` 方法暴露当前加密密钥。`EncryptionUtil.createEncryptionManager()` 改为 public 并传入 keys。新增 `EncryptionUtil.encryptionKeys()` 从 EncryptionManager 提取密钥。

2. **HiveCatalog 集成**：`HiveCatalog` 在初始化时检查 `ENCRYPTION_KMS_IMPL` 属性，若存在则创建 KMS 客户端。在 `newTableOps()` 中将 KMS 客户端传递给 `HiveTableOperations`。在 `close()` 中关闭 KMS 客户端。

3. **HiveTableOperations 加密逻辑**：
   - `io()` 方法：当 tableKeyId 非空时返回 `EncryptingFileIO`（组合原始 FileIO 和 EncryptionManager），否则返回原始 FileIO。
   - `encryption()` 方法：根据 tableKeyId 和 KMS 客户端创建 `StandardEncryptionManager`，传入从 metadata 获取的加密密钥。
   - `doRefresh()`：从 HMS 表参数读取 `ENCRYPTION_TABLE_KEY` 和 `ENCRYPTION_DEK_LENGTH`，与 metadata 文件中的值交叉验证（防篡改），然后更新加密状态。
   - `doCommit()`：从 metadata 提取加密属性，若使用 `StandardEncryptionManager`，则将新生成的加密密钥（通过 `EncryptionUtil.encryptionKeys()` 获取）写入 metadata JSON。
   - `temp()` 方法：为未提交的 metadata 创建临时 TableOperations，支持加密场景下的临时表操作。
   - 禁止删除加密表的 key：在 commit 时检测 `ENCRYPTION_TABLE_KEY` 是否在 removedProps 中，若是则抛出异常。

4. **测试**：新增 `TestTableEncryption` 和 `TestCTASEncryption` 两个 Spark 端到端测试，使用 `UnitestKMS` 模拟 KMS，验证加密表的建表、查询、刷新、插入删除、key 删除保护、数据文件直接读取（应失败）、manifest 加密等场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseMetastoreTableOperations.java` (+1/-1 lines)

**修改目的**：将 `metadataFileLocation` 方法从 private 改为 protected，允许子类访问。

**工作逻辑**：`HiveTableOperations.temp()` 方法中需要调用 `metadataFileLocation` 来定位未提交 metadata 的文件位置，因此需要提升可见性。

### `core/src/main/java/org/apache/iceberg/encryption/EncryptionUtil.java` (+12/-2 lines)

**修改目的**：暴露加密管理器创建和密钥提取的公共 API。

**工作逻辑**：`createEncryptionManager` 改为 `public`，新增 `keys` 参数传给 `StandardEncryptionManager`。新增 `encryptionKeys(EncryptionManager em)` 方法，检查 em 是 `StandardEncryptionManager` 后调用其 `encryptionKeys()` 返回当前加密密钥映射。

### `core/src/main/java/org/apache/iceberg/encryption/StandardEncryptionManager.java` (+38/-3 lines)

**修改目的**：支持从 table metadata 预存密钥初始化加密管理器，并暴露当前密钥。

**工作逻辑**：`TransientEncryptionState` 构造器新增 `List<EncryptedKey> keys` 参数，若 keys 非空则将其转化为 `BaseEncryptedKey` 放入 `encryptionKeys` 映射。新增接受 keys 的 `StandardEncryptionManager` 构造器，旧构造器标记 `@Deprecated` 并委托新构造器（传 `List.of()`）。新增 `encryptionKeys()` 方法返回 `transientState.encryptionKeys`（序列化后 transientState 为 null 时抛异常）。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java` (+20/-2 lines)

**修改目的**：在 HiveCatalog 中初始化 KMS 客户端并传递给表操作。

**工作逻辑**：新增 `KeyManagementClient keyManagementClient` 字段。在 `initialize()` 中检查 `CatalogProperties.ENCRYPTION_KMS_IMPL`，若存在则通过 `EncryptionUtil.createKmsClient()` 创建。`newTableOps()` 将 keyManagementClient 传给 `HiveTableOperations` 构造器。新增 `close()` 方法关闭 KMS 客户端。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java` (+190/-2 lines)

**修改目的**：在 Hive 表操作中集成加密功能，包括加密文件 IO、密钥管理和防篡改验证。

**工作逻辑**：
- 新增 `KeyManagementClient keyManagementClient` 字段和构造器参数，以及 `encryptionManager`、`encryptingFileIO`、`tableKeyId`、`encryptionDekLength`、`encryptedKeysFromMetadata` 等加密相关字段。
- `io()`：tableKeyId 非空时懒初始化 `EncryptingFileIO.combine(fileIO, encryption())` 并返回，否则返回原始 fileIO。
- `encryption()`：懒创建 `EncryptionManager`。tableKeyId 非空时用 `EncryptionUtil.createEncryptionManager(encryptedKeysFromMetadata, encryptionProperties, keyManagementClient)` 创建 `StandardEncryptionManager`；否则返回 `PlaintextEncryptionManager.instance()`。
- `doRefresh()`：从 HMS 表参数读取 `ENCRYPTION_TABLE_KEY` 和 `ENCRYPTION_DEK_LENGTH`（可信源），刷新 metadata 后调用 `checkEncryptionProperties()` 验证 HMS 值与 metadata 文件值一致（防篡改），然后更新 tableKeyId、encryptionDekLength、encryptedKeysFromMetadata，并重置 encryptionManager 和 encryptingFileIO。
- `doCommit()`：先调用 `encryptionPropsFromMetadata()` 提取加密属性，获取 EncryptionManager，若为 `StandardEncryptionManager` 则将新密钥通过 `builder.addEncryptionKey()` 写入 metadata，然后写 metadata 文件。在属性变更检查中，若 `ENCRYPTION_TABLE_KEY` 在 removedProps 中则抛异常禁止移除加密 key。
- `temp()`：创建匿名 `TableOperations` 实现，委托给外部 `HiveTableOperations` 的 `io()`、`encryption()`、`metadataFileLocation()` 等方法，支持未提交 metadata 的加密操作。
- `encryptionPropsFromMetadata()`：从 table properties 中提取 tableKeyId 和 encryptionDekLength（若尚未设置）。
- `checkEncryptionProperties()`：比较 HMS 中的加密 key ID 和 DEK length 与 metadata 文件中的值，不一致则抛异常（检测 metadata 篡改）。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCommitLocks.java` (+2/-0 lines)

**修改目的**：适配 `HiveTableOperations` 构造器新增的 `keyManagementClient` 参数。

**工作逻辑**：两处构造器调用中在 `io()` 和 `catalogName` 之间插入 `null`（表示不使用 KMS）。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestCTASEncryption.java` (+120 lines, 新文件)

**修改目的**：测试通过 CTAS（Create Table As Select）创建加密表的端到端流程。

**工作逻辑**：使用 HiveCatalog 配置并附加 `UnitestKMS` 作为 KMS 实现。先创建明文表并插入数据，再通过 CTAS 创建带 `'encryption.key-id'` 属性的加密表。测试：(1) `testSelect` 通过 Spark 能正常查询加密表数据；(2) `testDirectDataFileRead` 直接用 Parquet reader 读取数据文件应抛 `ParquetCryptoRuntimeException`（文件已加密）。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestTableEncryption.java` (+232 lines, 新文件)

**修改目的**：全面测试加密表的各种操作场景。

**工作逻辑**：使用 `UnitestKMS` 作为 KMS，创建带加密属性的表并插入数据。测试覆盖：(1) `testSelect` 正常查询；(2) `testRefresh` 刷新表元数据；(3) `testInsertAndDelete` 插入和删除操作；(4) `testKeyDelete` 验证不能通过 `ALTER TABLE UNSET TBLPROPERTIES` 删除加密 key（应报错 "Cannot remove key in encrypted table"）；(5) `testDirectDataFileRead` 直接读取数据文件应失败；(6) `testManifestEncryption` 验证 manifest 文件和 manifest list 文件已加密（检查 GCM 流 magic 字节头）。

## 总结

本提交是 Iceberg 加密功能在 Hive Catalog 中的完整集成，实现了端到端的表级加密。核心安全设计是从可信的 HMS 读取 table key ID 并与 metadata 文件交叉验证，防止篡改攻击。`HiveTableOperations` 通过 `EncryptingFileIO` 透明地加密数据文件和 manifest 文件，在 commit 时将新生成的加密密钥写入 metadata。`StandardEncryptionManager` 增强为支持从 metadata 预存密钥初始化。配套的两个 Spark 测试全面验证了加密表的建表（含 CTAS）、查询、刷新、增删、key 保护、文件加密等场景。注意此提交修改了 `HiveTableOperations` 构造器签名（新增 `KeyManagementClient` 参数），后续 2775 是适配此变更的热修复。本提交与 2670（Manifest list encryption）共同构成了 Iceberg 加密体系的重要组成部分。
