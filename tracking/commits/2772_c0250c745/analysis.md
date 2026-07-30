# 提交 2772：Hive: Fix lock selection during table creation to respect table properties (#14236)

## 提交信息

- **序号**：2772 / 4088
- **哈希**：c0250c745d29ddcf892a06294a35019762f51686
- **短哈希**：c0250c745
- **日期**：2025-10-20 12:37:47 +0200
- **作者**：s-sanjay
- **提交说明**：Hive: Fix lock selection during table creation to respect table properties (#14236)
- **PR/Issue**：#14236

## 总体目的

本提交修复 Hive 表创建时锁选择逻辑的一个 bug。

在 Iceberg 的 Hive 集成中，`HiveTableOperations` 在提交时会根据表属性（table properties）决定使用何种锁机制。例如 `HIVE_LOCK_ENABLED` 属性为 `false` 时使用 `NoLock`，否则使用 `MetastoreLock`。`lockObject(TableMetadata)` 方法会读取传入的元数据中的表属性来决定锁类型。

问题出在 `commit()` 方法中：`HiveLock lock = lockObject(base)`。其中 `base` 是当前表的基础元数据。但在创建新表时，`base` 为 `null`（因为表还不存在，没有之前的元数据）。当 `base` 为 null 时，`lockObject` 无法读取表属性，因此会使用默认的锁机制（`MetastoreLock`），忽略了用户在建表时设置的 `HIVE_LOCK_ENABLED=false` 属性。

这意味着即使用户明确禁用了 Hive 锁，新表创建时仍然会使用 Hive Metastore 锁，可能导致不必要的锁竞争或死锁问题。

## 如何达成设计目的

修复方式非常简洁：将 `lockObject(base)` 改为 `lockObject(base != null ? base : metadata)`。当 `base` 为 null（新表创建）时，使用 `metadata`（新表的元数据，包含用户设置的表属性）作为锁选择的依据。这样 `lockObject` 就能读取到 `HIVE_LOCK_ENABLED` 等属性，正确选择锁类型。

同时新增两个测试验证修复：
1. `TestHiveCommits.testFirstHiveCommitWithLockSetting`：直接测试 `HiveTableOperations.commit(null, metadata)` 时锁的选择是否尊重表属性。
2. `TestHiveTable.testCreateTableEndToEnd`：通过 catalog 端到端测试建表流程中锁的选择。

两个测试都参数化测试三种场景：`lockEnabled=true`、`lockEnabled=false`、`lockEnabled=null`（不设置该属性），分别验证锁类型为 `MetastoreLock`、`NoLock`、`MetastoreLock`（默认）。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java` (+1/-1 lines)

**修改目的**：修复新表创建时锁选择使用 null base 的问题。

**工作逻辑**：将第 146 行的 `HiveLock lock = lockObject(base);` 改为 `HiveLock lock = lockObject(base != null ? base : metadata);`。当 base 为 null（首次提交/建表）时传入 metadata，使 `lockObject` 能从新表元数据中读取 `HIVE_LOCK_ENABLED` 等属性来决定锁类型。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCommits.java` (+52/-0 lines)

**修改目的**：新增测试验证首次 Hive 提交时锁选择是否尊重表属性。

**工作逻辑**：新增 `testFirstHiveCommitWithLockSetting(Boolean lockEnabled)` 参数化测试（`@ValueSource(booleans = {true, false})` + `@NullSource`）。创建一个新的 `HiveTableOperations`，用 spy 捕获 `lockObject(metadata)` 返回的锁对象，然后调用 `commit(null, metadata)` 模拟新表创建。断言：`lockEnabled=false` 时锁类型为 `NoLock.class`，其余情况为 `MetastoreLock.class`。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveTable.java` (+61/-0 lines)

**修改目的**：新增端到端测试验证通过 catalog 建表时锁选择是否正确。

**工作逻辑**：新增 `testCreateTableEndToEnd(Boolean lockEnabled)` 参数化测试。通过 spy catalog 拦截 `newTableOps` 调用，再 spy `HiveTableOperations` 捕获 `lockObject` 返回值。然后通过 `spyCatalog.createTable(...)` 建表（传入带 `HIVE_LOCK_ENABLED` 的属性），验证捕获的锁类型符合预期。

## 总结

本提交修复了一个影响新表创建的锁选择 bug：当 base 为 null 时，`lockObject` 无法读取表属性导致忽略用户的 `HIVE_LOCK_ENABLED=false` 设置，强制使用 MetastoreLock。修复方式简洁有效——在 base 为 null 时回退使用新表 metadata。配套的两个参数化测试全面覆盖了 `true`/`false`/`null` 三种属性设置场景，从直接操作和端到端两个层面验证修复。
