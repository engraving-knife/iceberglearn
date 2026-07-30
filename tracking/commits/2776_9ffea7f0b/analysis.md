# 提交 2776：Hotfix: adapt HMS tests to HiveTableOperations ctor (add KeyManagementClient) (#14384)

## 提交信息

- **序号**：2776 / 4088
- **哈希**：9ffea7f0b263f09de2cc966c96dbab5c7b7fc0d2
- **短哈希**：9ffea7f0b
- **日期**：2025-10-20 20:12:44 -0700
- **作者**：Huaxin Gao
- **提交说明**：Hotfix: adapt HMS tests to HiveTableOperations ctor (add KeyManagementClient) (#14384)
- **PR/Issue**：#14384

## 总体目的

本提交是一个热修复（hotfix），用于适配 2774（Encryption integration and test #13066）引入的 `HiveTableOperations` 构造器变更。

2774 提交在 `HiveTableOperations` 构造器中新增了 `KeyManagementClient` 参数，以支持 Hive Catalog 的加密功能。当时已更新了 `TestHiveCommitLocks` 中的构造器调用（传入 `null`），但遗漏了 `TestHiveCommits` 中的一处构造器调用。这导致测试编译失败。

本提交修复了这个遗漏，在 `TestHiveCommits.testFirstHiveCommitWithLockSetting` 方法中的 `HiveTableOperations` 构造器调用处补上 `null` 参数（表示不使用 KMS）。

值得注意的是，`testFirstHiveCommitWithLockSetting` 方法本身是在 2771（Hive: Fix lock selection during table creation）中新增的，而 2774 在合并时可能未与 2771 的变更完全同步，导致这处构造器调用未被更新。

## 如何达成设计目的

在 `TestHiveCommits.java` 的 `testFirstHiveCommitWithLockSetting` 方法中，`HiveTableOperations` 构造器调用的参数列表中，在 `catalog.newTableOps(newTableIdentifier).io()` 和 `catalog.name()` 之间插入 `null`，对应新增的 `KeyManagementClient` 参数。

## 修改详情

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCommits.java` (+1/-0 lines)

**修改目的**：适配 `HiveTableOperations` 构造器新增的 `KeyManagementClient` 参数。

**工作逻辑**：在 `testFirstHiveCommitWithLockSetting` 方法中创建 `HiveTableOperations` 时，在 `io()` 参数和 `catalog.name()` 参数之间补上 `null`，表示该测试不使用加密/KMS 功能。

## 总结

本提交是一个简单的热修复，补上了 2774 加密集成提交中遗漏的一处测试代码适配。这体现了多 PR 并行开发时构造器签名变更容易导致遗漏的问题。修复后 `TestHiveCommits` 能正确编译和运行。
