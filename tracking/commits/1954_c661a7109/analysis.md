# 提交 1954：Core, Hive: Double check commit status in case of commit conflict for NoLock (#12637)

## 提交信息

- **序号**：1954 / 4088
- **哈希**：c661a71091e496393c743ddd879d9e1a0f2747b2
- **短哈希**：c661a7109
- **日期**：2025-04-02 14:34:32 +0200
- **作者**：Rui Li
- **提交说明**：Core, Hive: Double check commit status in case of commit conflict for NoLock (#12637)
- **PR/Issue**：#12637

## 总体目的

在 Hive Metastore 表提交场景下，当禁用 Hive 锁（`HIVE_LOCK_ENABLED=false`，即 NoLock 模式）时，HMS 客户端可能会因为网络抖动等原因对一次已经成功的 alter_table 操作进行重试，从而触发 "The table has been modified. The parameter value for key 'metadata_location' is ..." 这种异常。原代码在捕获到该异常时，直接把它当作并发修改并抛出 `CommitFailedException`，但实际上第一次 alter 可能已经成功提交了元数据，导致误判提交失败。

本提交的目的就是在 NoLock 模式下，当遇到这种"表已被修改"异常时，做一次严格的提交状态二次校验（strict check）：如果新 metadata location 出现在表的当前或历史 metadata location 中，说明本次提交其实成功了；只有确认不在历史中时才判定为真正的并发修改失败并抛 `CommitFailedException`。

为了支持这种区分，本提交在 core 层引入了 `checkCommitStatusStrict` 方法（找不到 location 时返回 `FAILURE`），与原有 `checkCommitStatus`（找不到时返回 `UNKNOWN`，因为可能还有 pending 重试会成功）区分开。Hive 侧在 "table has been modified" 异常分支调用 strict 版本进行二次确认。

## 如何达成设计目的

设计上把"提交状态检查"拆成两种语义：

1. **`checkCommitStatus`（非严格）**：当新 metadata location 不在当前/历史 location 中时返回 `UNKNOWN`。语义是"可能还有 pending 重试会成功，不能确定失败"。用于一般性异常（无法判断成败）的场景。
2. **`checkCommitStatusStrict`（严格）**：当新 metadata location 不在当前/历史 location 中时返回 `FAILURE`。语义是"可以确定没有 pending 重试能再成功，因此可安全判定失败"。用于 "table has been modified" 这种明确表示不会再有 pending 重试成功的场景。

`checkCommitStatus` 内部实现改为：先调用 `checkCommitStatusStrict`，若返回 `FAILURE` 则转成 `UNKNOWN` 返回（保持原有非严格语义），否则直接返回 strict 结果。这样两者复用同一套重试检查逻辑，只在最终找不到 location 时的返回值不同。

在 `HiveTableOperations.commit` 的异常处理中：
- 对于 "table has been modified" 异常：调用 `checkCommitStatusStrict`；若为 `FAILURE` 才抛 `CommitFailedException`（真正并发修改），否则按 SUCCESS/UNKNOWN 继续后续逻辑。
- 对于其他异常：保持原逻辑，调用非严格 `checkCommitStatus` 并记录 error 日志。

此外还更新了测试用的 derby schema（将部分 CLOB 列改为 VARCHAR(32672)），以便测试能稳定触发 HMS 的 "table has been modified" 行为，并新增 `testMultipleAlterTableForNoLock` 测试覆盖 NoLock 下 alter_table 被重复调用的情况。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseMetastoreOperations.java` (修改, +48/-6 lines)

**修改目的**：引入严格版提交状态检查，区分"可能还有 pending 重试"与"确定失败"。

**工作逻辑**：
- 原 `checkCommitStatus` 改为委托新方法 `checkCommitStatusStrict`，并在后者返回 `FAILURE` 时转成 `UNKNOWN` 返回（保持原非严格语义）。
- 新增 `checkCommitStatusStrict`：复用原有的重试检查循环（按 `COMMIT_NUM_STATUS_CHECKS` 次数重试调用 `commitStatusSupplier`），若新 location 在当前/历史中则 `SUCCESS`，否则设为 `FAILURE`（而非原来的 UNKNOWN）。
- 更新两方法的 Javadoc 说明返回语义差异。

### `core/src/main/java/org/apache/iceberg/BaseMetastoreTableOperations.java` (修改, +38/-8 lines)

**修改目的**：在表操作层暴露严格版检查方法。

**工作逻辑**：
- 原 `checkCommitStatus(newMetadataLocation, config)` 改为直接调用 `BaseMetastoreOperations.checkCommitStatus`（去掉 `CommitStatus.valueOf(...name())` 转换，直接用返回值）。
- 新增 `checkCommitStatusStrict(newMetadataLocation, config)`，委托给 `BaseMetastoreOperations.checkCommitStatusStrict`。
- 更新 Javadoc：非严格版找不到时返回 UNKNOWN，严格版返回 FAILURE。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java` (修改, +42/-13 lines)

**修改目的**：在 NoLock 模式下对 "table has been modified" 异常做严格二次校验，避免误判。

**工作逻辑**：
在 `commit` 的 `catch (Throwable e)` 分支中重构：
- 若异常消息包含 "The table has been modified. The parameter value for key '...metadata_location' is ..."：调用 `checkCommitStatusStrict`。注释说明 HMS 客户端可能错误重试已成功操作从而触发此异常，且此异常意味着不会再有 pending 请求能成功，因此用 strict 模式安全判定。若返回 `FAILURE` 才抛 `CommitFailedException`（真正并发修改），否则进入 switch 处理 SUCCESS/UNKNOWN。
- 其他异常（如 HIVE_LOCKS 表不存在）保持原 RuntimeException 处理。
- 其余异常：记录 error 日志后调用非严格 `checkCommitStatus`，再按 switch 处理。
- 移除了原先在捕获阶段就根据 "table has been modified" 直接抛 `CommitFailedException` 的逻辑。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCommitLocks.java` (修改, +33/-0 lines)

**修改目的**：新增 NoLock 模式下 alter_table 被重复调用的回归测试。

**工作逻辑**：新增 `testMultipleAlterTableForNoLock`：设置 `HIVE_LOCK_ENABLED=false`，用 Mockito spy 让 `alter_table_with_environmentContext` 调用两次 real 方法（模拟 HMS 客户端重复调用），执行 commit 后验证 alter_table 只成功一次（`times(1)`），并断言捕获到的异常消息包含 "The table has been modified. The parameter value for key '"。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveMetastore.java` (修改, +17/-2 lines)

**修改目的**：适配测试断言。

**工作逻辑**：调整相关测试以配合新的提交状态判定行为（具体为测试断言的调整）。

### `hive-metastore/src/test/resources/hive-schema-3.1.0.derby.sql` (修改, +5/-5 lines)

**修改目的**：调整测试用 derby schema 列类型以稳定复现 HMS 行为。

**工作逻辑**：将 `SERDE_PARAMS.PARAM_VALUE`、`COLUMNS_V2.TYPE_NAME`、`TABLE_PARAMS.PARAM_VALUE`、`SD_PARAMS.PARAM_VALUE`、`MV_CREATION_METADATA.TXN_LIST` 从 `CLOB` 改为 `VARCHAR(32672)`，使测试环境下的 HMS 行为更稳定可预测。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveMetastoreExtension.java` (修改, +1/-1 lines)

**修改目的**：适配测试扩展。

**工作逻辑**：小幅调整（与测试环境相关）。

## 总结

本提交针对 Hive NoLock 模式下 HMS 客户端错误重试已成功 alter_table 而触发的 "table has been modified" 异常，避免误判为提交失败。核心是在 core 层新增严格版 `checkCommitStatusStrict`（找不到 location 返回 FAILURE），Hive 侧在该异常分支用 strict 模式二次校验提交状态，仅当确认 metadata location 不在历史中时才抛 `CommitFailedException`。同时更新测试 derby schema 与新增回归测试。
