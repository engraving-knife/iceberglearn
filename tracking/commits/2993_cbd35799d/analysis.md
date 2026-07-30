# 提交 2993：Encryption: Simplify Hive key handling and add transaction tests (#14752)

## 提交信息

- **序号**：2993 / 4088
- **哈希**：cbd35799dc70e989528b1c14d640fe91cdafa52d
- **短哈希**：cbd35799d
- **日期**：2025-12-10
- **作者**：Sreesh Maheshwar
- **提交说明**：Encryption: Simplify Hive key handling and add transaction tests (#14752)
- **PR/Issue**：#14752

## 总体目的

Iceberg 在 Hive Metastore 后端下支持表级加密，加密密钥（DEK）的管理通过 `HiveTableOperations` 协调：表的 metadata 中持久化已提交的加密密钥列表，而 `EncryptionManager` 在运行时可能因为文件追加等操作新增尚未提交到 metadata 的密钥。原先的实现用两个 `Optional<List<EncryptedKey>>` 字段——`encryptedKeysFromMetadata`（来自最新 metadata）与 `encryptedKeysPending`（运行时新增、尚未提交）——分别维护，并在构建 `EncryptionManager` 时把两者合并。

这种"双列表 + Optional"的设计存在几个问题：1）状态分散，需要手动协调两个列表，易出错；2）`Optional<List>` 的语义冗余（既可能为 empty Optional，也可能是空 List）；3）在事务（Transaction）场景下，尤其是并发追加/替换事务时，密钥的合并与重新创建 `EncryptionManager` 的逻辑不够清晰，容易导致密钥丢失或重复。

本提交将双列表合并为单一的 `List<EncryptedKey> encryptedKeys`（初始化为 `List.of()`，加载时转为可变的 `LinkedList`），把来自 metadata 的密钥与运行时新增的密钥统一放入该列表，简化状态管理。同时新增针对事务场景（普通追加事务、并发追加事务、并发替换事务）的测试，验证密钥处理在并发提交下的正确性。测试还顺手把原先误用的 `catalog` 字段统一改为 `validationCatalog`，保证加密表测试用验证 catalog 而非被测 catalog 做断言。

## 如何达成设计目的

在 `HiveTableOperations` 中用单一可变列表替代两个 Optional 列表：`refreshFromMetadata` 路径加载 metadata 密钥时直接装入该列表，并把 `EncryptionManager` 中存在但不在 metadata 的密钥追加进同一列表；`getEncryptionManager` 直接用该列表构造 `EncryptionManager`。新增的事务测试通过 `validationCatalog` 操控表，构造并发追加/替换场景，断言最终数据文件数量符合预期。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java` (+30/-27 lines, 实际净 -3 但重构幅度大)

**修改目的**：将双 Optional 列表合并为单一可变列表，简化密钥状态管理。

**工作逻辑**：

字段层面：移除 `encryptedKeysFromMetadata` 与 `encryptedKeysPending` 两个 `Optional<List<EncryptedKey>>`，替换为单个 `private List<EncryptedKey> encryptedKeys = List.of();`。

`getEncryptionManager()`：原本需要从两个 Optional 中 `ifPresent(keys::addAll)` 合并，现直接 `EncryptionUtil.createEncryptionManager(encryptedKeys, encryptionProperties, keyManagementClient)`，逻辑一目了然。

`refreshFromMetadata` 路径（加载 HMS 加密配置时）：原本 `encryptedKeysFromMetadata = Optional.ofNullable(current().encryptionKeys())`，并在 `encryptionManager != null` 时新建 `encryptedKeysPending = Optional.of(new LinkedList())`，再把 EM 中不在 metadata 的密钥加入 pending。新逻辑改为：`encryptedKeys = Optional.ofNullable(current().encryptionKeys()).map(Lists::newLinkedList).orElseGet(Lists::newLinkedList)`，得到一个可变列表；若 `encryptionManager != null`，则把 EM 中 keyId 不在 metadata 的密钥直接 `encryptedKeys.add(keyFromEM)` 加入同一列表；不再维护 pending 概念，也不再有 `else { encryptedKeysPending = Optional.empty() }` 分支。这样无论是否已有 EM，`encryptedKeys` 始终是一个统一的、可变的、包含所有已知密钥的列表。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestTableEncryption.java` (+64/-3 lines)

**修改目的**：补充事务场景测试并修正 catalog 用法。

**工作逻辑**：

1. 将 `testRefresh` 与原 `testTransaction` 中的 `catalog.initialize` / `catalog.loadTable` 统一改为 `validationCatalog.initialize` / `validationCatalog.loadTable`，确保用验证 catalog 做断言（被测 catalog 可能是 Spark session catalog，状态不同步）。

2. 拆分原 `testTransaction` 为两个测试：
   - `testAppendTransaction`：标准追加事务，加载表 → 取当前数据文件 → 开事务追加其中一个文件 → 提交，断言文件数 +1。
   - `testConcurrentAppendTransactions`：模拟并发追加。开事务 A 追加文件后，**在提交事务 A 之前**用另一次 `validationCatalog.loadTable(tableIdent).newFastAppend().appendFile(...).commit()` 直接提交一个追加，然后再提交事务 A，断言最终文件数 = 原数量 + 2。这验证了在并发追加下密钥与 commit 重试逻辑能正确工作。

3. 新增 `testConcurrentReplaceTransactions`（参考 `CatalogTests#testConcurrentReplaceTransactions`）：先构造第二个 replace 事务（`secondReplace`）并追加文件但暂不提交；再构造并提交第一个 replace 事务（`firstReplace`）；最后提交 `secondReplace`。由于 replace 是全量替换，最终表应只保留 `secondReplace` 写入的文件，断言 `currentDataFiles(afterSecondReplace)` 大小为 1。该测试覆盖了并发 replace 在加密表场景下的 commit 冲突重试与密钥重建路径。

## 总结

本提交通过将 `HiveTableOperations` 中分散的双 Optional 密钥列表合并为单一可变列表，显著简化了加密密钥的状态管理，降低了事务（尤其并发追加/替换事务）下密钥丢失或重复的风险。配套新增的三组事务测试（普通追加、并发追加、并发替换）有效覆盖了加密表在并发提交下的正确性，并将测试中的 catalog 用法修正为验证 catalog，整体提升了 Hive 加密路径的健壮性与可测试性。
