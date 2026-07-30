# 提交 2801：[Hive] Fix newly added encryption keys getting lost in transactions (#14427)

## 提交信息

- **序号**：2801 / 4088
- **哈希**：fa62ec1df2d4e56f06c18c42e96264abaf756deb
- **短哈希**：fa62ec1df
- **日期**：2025-10-28 09:50:23 -0700
- **作者**：Adam Szita
- **提交说明**：[Hive] Fix newly added encryption keys getting lost in transactions (#14427)
- **PR/Issue**：#14427

## 总体目的

本提交修复了 Hive 表操作中事务提交时新添加的加密密钥丢失的 bug。

在 Iceberg 的加密机制中，`EncryptionManager`（加密管理器）会管理数据加密密钥（DEK）和密钥加密密钥（KEK）。当执行文件追加（FileAppend）操作时，可能会生成新的加密密钥，这些密钥存储在加密管理器的临时状态中，等待在提交时写入表元数据。

问题出在 Hive 的事务提交流程中：提交事务会触发底层表操作的 `refresh()` 调用，而 `HiveTableOperations` 在 `refresh()` 时会重新创建加密管理器。重新创建时只从最新的元数据中加载密钥，导致加密管理器临时状态中新生成但尚未提交到元数据的密钥被丢弃。

这导致的直接后果是：事务内的追加操作生成的加密 manifest list 使用的加密密钥会丢失，使得这些文件无法被正确解密。这是与加密功能（如 Manifest list encryption）相关的严重数据完整性问题。

## 如何达成设计目的

核心思路是在 `refresh()` 时保留加密管理器中"待提交"（pending）的密钥，在重新创建加密管理器时将这些密钥一并纳入：

1. 将原来的 `encryptedKeysFromMetadata` 字段改为 `Optional` 类型。
2. 新增 `encryptedKeysPending` 字段，存储加密管理器中存在但元数据中尚不存在的密钥。
3. 在 `refresh()` 时，从当前加密管理器中提取所有密钥，与元数据中的密钥比较，差集即为 pending 密钥。
4. 在创建加密管理器时，合并元数据密钥和 pending 密钥。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java` (+39/-4 lines)

**修改目的**：修复事务提交时加密密钥丢失的问题。

**工作逻辑**：
- **字段修改**：将 `encryptedKeysFromMetadata` 改为 `Optional<List<EncryptedKey>>`，新增 `encryptedKeysPending` 字段（`Optional<List<EncryptedKey>>`），用于存储加密管理器中尚未提交到元数据的密钥。
- **加密管理器创建逻辑**（`encryptionManager()` 方法）：创建加密管理器时，合并 `encryptedKeysFromMetadata` 和 `encryptedKeysPending` 两个列表的密钥，确保新创建的加密管理器包含所有密钥。
- **refresh 逻辑**（`refreshFromMetadata()` 方法）：从元数据加载密钥到 `encryptedKeysFromMetadata`。如果当前已有加密管理器，则提取其所有密钥，与元数据密钥比较，不在元数据中的密钥被视为 pending 密钥，保存到 `encryptedKeysPending`。然后强制重新创建加密管理器（置空 `encryptingFileIO` 和 `encryptionManager`），新创建时会合并元数据密钥和 pending 密钥。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestTableEncryption.java` (+20/-0 lines)

**修改目的**：添加事务内加密追加操作的测试。

**工作逻辑**：新增 `testTransaction` 测试：加载加密表，创建事务，在事务内执行追加操作并提交事务，验证数据文件数量正确增加。这个测试覆盖了之前会丢失加密密钥的场景。

## 总结

本提交修复了 Hive 表操作中事务提交时新添加的加密密钥丢失的严重 bug。根因是 `refresh()` 重新创建加密管理器时只从元数据加载密钥，丢弃了临时状态中的 pending 密钥。修复方案是在 refresh 时提取 pending 密钥并在重建加密管理器时合并。这与 Iceberg 的加密功能（如 Manifest list encryption）密切相关，确保事务内的加密操作不会丢失密钥。
