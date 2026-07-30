# 提交 2803：Auto rotation of key encryption keys (#14396)

## 提交信息

- **序号**：2803 / 4088
- **哈希**：d1a518f8457f4debd2698b8eda1d7a5ce04c21e8
- **短哈希**：d1a518f84
- **日期**：2025-10-29 09:14:05 -0700
- **作者**：ggershinsky
- **提交说明**：Auto rotation of key encryption keys (#14396)
- **PR/Issue**：#14396

## 总体目的

本提交为 Iceberg 加密机制实现密钥加密密钥（KEK, Key Encryption Key）的自动轮换功能。

在 Iceberg 的加密架构中，数据加密密钥（DEK）用于加密实际数据（如 manifest list），而密钥加密密钥（KEK）用于加密/包装 DEK。KEK 由 KMS（密钥管理服务）通过表主密钥（tableKeyId）包装生成。之前，`StandardEncryptionManager` 使用一个固定的 KEK（ID 为 `KEY_ENCRYPTION_KEY_ID`），这个 KEK 永不过期，存在安全风险。

根据 NIST SP 800-57 标准（PART 1 REV. 5, section 5.3.6.7.b），密钥加密密钥的最大生命周期为 2 年（730 天）。长期使用同一 KEK 会增加密钥被破解或泄露的风险。本提交实现了 KEK 的自动轮换：当 KEK 超过 2 年生命周期时，自动生成新的 KEK，后续的 DEK 加密使用新 KEK。

这与之前的提交 2670（Manifest list encryption）和 2774（Encryption integration test）是同一加密功能系列的一部分。2670 实现了 manifest list 加密的基础机制，本提交在其基础上增加了 KEK 自动轮换能力，2800 修复了 Hive 事务中 KEK 丢失的相关 bug。

## 如何达成设计目的

整体设计包括以下几个方面：

1. **KEK 生命周期管理**：每个 KEK 创建时记录时间戳（`KEY_TIMESTAMP` 属性），当 KEK 年龄超过 730 天时自动创建新 KEK。
2. **KEK 查找逻辑重构**：`keyEncryptionKeyID()` 方法从原来简单的"如果不存在就创建"改为遍历所有密钥，找到未过期的 KEK；如果都过期了则创建新的。
3. **KEK ID 生成**：不再使用固定 ID `KEY_ENCRYPTION_KEY_ID`，改为使用 `generateKeyId()` 生成唯一 ID，支持多个 KEK 共存。
4. **时间戳防篡改**：将 KEK 时间戳用作 AES-GCM 加密的附加认证数据（AAD），防止时间戳被篡改以绕过轮换机制。
5. **测试时间偏移**：添加 `testTimeShift` 字段和 `setTestTimeShift` 方法，允许测试模拟时间流逝来验证轮换逻辑。

## 修改详情

### `core/src/main/java/org/apache/iceberg/encryption/StandardEncryptionManager.java` (+40/-30 lines)

**修改目的**：实现 KEK 自动轮换核心逻辑。

**工作逻辑**：
- **常量修改**：移除固定的 `KEY_ENCRYPTION_KEY_ID` 常量，新增 `KEY_ENCRYPTION_KEY_LIFESPAN_MS`（730 天，依据 NIST 标准）和 `KEY_TIMESTAMP` 属性键。
- **`keyEncryptionKeyID()` 方法重构**：从 package-private 改为 package-private（供测试使用），逻辑改为：遍历所有加密密钥，找到 `encryptedById` 等于 `tableKeyId`（即 KEK）且未过期的密钥返回其 ID；如果没有未过期的 KEK，则通过 KMS 创建新 KEK，附带 `KEY_TIMESTAMP` 属性。
- **时间偏移**：新增 `testTimeShift` 字段和 `currentTimeMillis()` 方法（`System.currentTimeMillis() + testTimeShift`），用于测试模拟时间流逝。
- **`encryptedByKey()` 方法简化**：改为直接从 `unwrappedKeyCache` 获取 KEK 的解包密钥（之前通过 `encryptedById` 间接查找）。
- **`addManifestListKeyMetadata()` 方法修改**：获取当前 KEK 的时间戳，将时间戳而非 manifest list key ID 作为 AES-GCM 加密的 AAD。

### `core/src/main/java/org/apache/iceberg/encryption/EncryptionUtil.java` (+20/-10 lines)

**修改目的**：修改 manifest list 密钥元数据的加密/解密逻辑，使用 KEK 时间戳作为 AAD。

**工作逻辑**：
- **`decryptManifestListKeyMetadata()` 方法**：从 manifest list 的加密密钥中提取 KEK ID，再从 KEK 的属性中获取时间戳。使用 KEK 时间戳作为 AES-GCM 解密的 AAD（之前使用 manifest list key ID）。添加前置条件检查确保 KEK 有时间戳。
- **`encryptManifestListKeyMetadata()` 方法**：参数从 `keyId` 改为 `keyTimestamp`，使用 KEK 时间戳作为 AES-GCM 加密的 AAD。注释说明这是为了防止时间戳篡改攻击。

### `core/src/test/java/org/apache/iceberg/TestManifestListEncryption.java` (+97/-12 lines)

**修改目的**：添加 KEK 轮换的深度测试。

**工作逻辑**：
- 重构 `testEncryption`（原 `testV2Write`）：每次创建新的 EncryptionManager 实例。
- 新增 `testKeyWrappingAndRotation` 测试：
  1. **初始写入**：验证初始有 2 个密钥（1 个 KEK + 1 个 MLK），KEK ID 一致。
  2. **30 天后写入**：验证 KEK 不轮换（同一 KEK ID），密钥增至 3 个（1 KEK + 2 MLK）。
  3. **800 天后写入**（累计超过 730 天）：验证 KEK 轮换（新 KEK ID），密钥增至 5 个（2 KEK + 3 MLK），新 MLK 由新 KEK 包装。

### `core/src/test/java/org/apache/iceberg/encryption/EncryptionTestHelpers.java` (+15/-0 lines)

**修改目的**：添加测试辅助方法。

**工作逻辑**：
- `keyEncryptionKeyID(EncryptionManager)`：获取当前 KEK ID（通过反射调用 package-private 方法）。
- `shiftEncryptionManagerTime(EncryptionManager, long)`：设置测试时间偏移，模拟时间流逝。

## 总结

本提交实现了密钥加密密钥（KEK）的自动轮换功能，依据 NIST SP 800-57 标准设定 730 天的最大生命周期。主要改动包括：重构 KEK 查找逻辑以支持多 KEK 共存和过期检查、为每个 KEK 记录时间戳、将时间戳用作 AES-GCM 加密的 AAD 以防篡改。测试通过模拟时间偏移验证了 30 天不轮换、800 天轮换的场景。这是 Iceberg 加密功能系列（2670 manifest list encryption、2774 encryption integration test、2800 Hive KEK 丢失修复）的重要补充，增强了加密机制的安全性。
