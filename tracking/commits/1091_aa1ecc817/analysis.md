# 提交 1091：Flink: Maintenance - TableChange refactor (#10992)

## 提交信息

- **序号**：1091 / 4088
- **哈希**：aa1ecc817b6f11ad987cf56cdb207edb3079b614
- **短哈希**：aa1ecc817
- **日期**：2024-08-23 17:19:58 +0200
- **作者**：pvary
- **提交说明**：Flink: Maintenance - TableChange refactor (#10992)
- **PR/Issue**：#10992

## 总体目的

在 1085/1090 引入 `TriggerManager` 后，`TableChange` 事件只笼统地统计了"删除文件数/删除文件大小"，没有区分 position delete 与 equality delete，也没有记录删除记录数。但实际上这两类删除文件对 maintenance 触发策略的影响差异很大：position delete 通常由 `RewritePositionDeleteFiles` 处理，equality delete 由 `RewriteDeleteFiles` 处理，且触发条件应基于"记录数"而非仅"文件数/大小"才能更准确反映维护压力。

本提交对 Flink v1.20 模块的 `TableChange` 做一次重构：(1) 把删除文件统计拆分为 position delete 与 equality delete 两类，每类分别记录文件数与记录数；(2) 统一字段命名（`*Num`→`*Count`、`*Size`→`*SizeInBytes`），使语义更清晰；(3) 同步更新 `TriggerEvaluator` 的 Builder 与谓词，支持针对各类删除的细粒度触发条件；(4) 顺手清理 `JdbcLockFactory` 的代码（锁类改名并收为私有、移除冗余 catch、字段加 final、改进异常信息）；(5) 更新相关测试。

## 如何达成设计目的

1. **`TableChange` 字段重构**：由 `dataFileNum/deleteFileNum/dataFileSize/deleteFileSize/commitNum` 五字段改为 `dataFileCount/dataFileSizeInBytes/posDeleteFileCount/posDeleteRecordCount/eqDeleteFileCount/eqDeleteRecordCount/commitCount` 七字段。构造 `TableChange(Snapshot, FileIO)` 时按 `deleteFile.content()` 分流到 position/equality 两条计数路径，并累加 `recordCount()`。
2. **`TriggerEvaluator` 细化**：Builder 提供七个独立阈值（`dataFileCount`/`dataFileSizeInBytes`/`posDeleteFileCount`/`posDeleteRecordCount`/`eqDeleteFileCount`/`eqDeleteRecordCount`/`commitCount`）加 `timeout`，每个非空阈值生成一个谓词，`anyMatch` 触发。原来的 `fileNumber`（数据+删除文件总数）与 `fileSize`（总大小）这类粗粒度谓词被替换。
3. **`JdbcLockFactory` 清理**：内部锁类 `Lock` 改名为 `JdbcLock` 并收为 `private static class`，构造器改为 private；移除 `unlock()` 中冗余的 `catch (UncheckedSQLException e) { throw e; }`；`Type.key` 加 `final`；`isHeld` 的异常信息改为"Failed to check the state of the lock"。
4. **测试适配**：`TestMonitorSource` 与 `TestTriggerManager` 改用新字段名与 Builder 方法。

## 修改详情

### `flink/v1.20/.../maintenance/operator/TableChange.java`

**修改目的**：拆分删除文件统计、统一命名、增加记录数。

**工作逻辑**：
- 字段重命名与拆分：`dataFileNum`→`dataFileCount`、`dataFileSize`→`dataFileSizeInBytes`、`deleteFileNum/deleteFileSize` 拆为 `posDeleteFileCount/posDeleteRecordCount` 与 `eqDeleteFileCount/eqDeleteRecordCount`、`commitNum`→`commitCount`。
- 构造器改为包级（`TableChange(...)`），参数列表扩展为 7 个。
- `TableChange(Snapshot, FileIO)`：遍历 deleteFiles 时 `switch (deleteFile.content())`，`POSITION_DELETES` 累加 `posDeleteFileCount`/`posDeleteRecordCount`（用 `recordCount()`），`EQUALITY_DELETES` 累加 `eqDeleteFileCount`/`eqDeleteRecordCount`，未知类型抛 `IllegalArgumentException`。
- `empty()`/`copy()`/`merge()`/`equals()`/`hashCode()`/`toString()` 全部适配新字段；`merge` 把 7 个字段逐一相加。
- Builder 的方法名同步重命名（`dataFileCount`/`dataFileSizeInBytes`/`posDeleteFileCount`/`posDeleteRecordCount`/`eqDeleteFileCount`/`eqDeleteRecordCount`/`commitCount`）。
- 各 getter 重命名对应。

### `flink/v1.20/.../maintenance/operator/TriggerEvaluator.java`

**修改目的**：支持按细粒度指标配置触发条件。

**工作逻辑**：
- `check` 简化：移除 `try/catch` 包装（原来把异常包成 RuntimeException，现直接让异常传播），`anyMatch(p -> p.evaluate(...))`。
- Builder 字段改为七个细粒度阈值（`dataFileCount`/`dataFileSizeInBytes`/`posDeleteFileCount`/`posDeleteRecordCount`/`eqDeleteFileCount`/`eqDeleteRecordCount`/`commitCount`）加 `timeout`，Builder 方法改为 `public`。
- `build()`：每个非空阈值生成一个谓词，分别对 `TableChange` 对应 getter 做 `>=` 比较；`timeout` 谓词不变（`currentTimeMs - lastTimeMs >= timeout.toMillis()`）。原来合并数据+删除总数的 `fileNumber`/`fileSize` 谓词被取代。

### `flink/v1.20/.../maintenance/operator/JdbcLockFactory.java`

**修改目的**：锁类封装与代码清理。

**工作逻辑**：
- 内部 `public static class Lock` 改为 `private static class JdbcLock`，构造器由 `public` 改为 `private`；`createLock()`/`createRecoveryLock()` 返回 `new JdbcLock(...)`。
- `isHeld()` 的 `UncheckedSQLException` 信息由"Failed to get lock information for %s"改为"Failed to check the state of the lock %s"。
- `unlock()` 移除冗余的 `catch (UncheckedSQLException e) { throw e; }`（该 catch 只是重新抛出，无意义）。
- `Type` 枚举的 `key` 字段加 `final`。

### `flink/v1.20/.../maintenance/operator/TestMonitorSource.java`

**修改目的**：适配 `TableChange` 新 API。

**工作逻辑**：断言改为 `TableChange.builder().dataFileCount(1).dataFileSizeInBytes(size).commitCount(1).build()`；`commitNum()` 调用改为 `commitCount()`。

### `flink/v1.20/.../maintenance/operator/TestTriggerManager.java`

**修改目的**：适配 `TableChange` 与 `TriggerEvaluator` 新 API。

**工作逻辑**：约 182 行调整，把测试中构造 `TableChange` 与 `TriggerEvaluator` 的调用全部改为新字段名/方法名，并把原来基于 `fileNumber`/`deleteFileNumber` 的触发用例改为基于 `posDeleteFileCount`/`eqDeleteFileCount`/`posDeleteRecordCount`/`eqDeleteRecordCount` 等细粒度阈值。

## 小结

- **成效**：重构 `TableChange`，把笼统的删除文件统计拆分为 position/equality 两类并补充记录数；同步细化 `TriggerEvaluator` 触发条件，使维护调度能针对不同删除类型与记录压力分别设置阈值；顺手清理 `JdbcLockFactory`（锁类私有化、移除冗余代码、字段 final、异常信息优化）。
- **影响范围**：仅 `flink/v1.20` 模块 maintenance operator 包，5 个文件，329 行新增、172 行删除。属于已有新功能的内部重构（API 仍是 `@Internal`）。
- **回迁到 1.4.x 的注意事项**：不建议回迁。该重构依赖 1085/1090 引入的 maintenance operator 框架，1.4.x 不具备该框架，无法回迁。若 1.4.x 将来引入 maintenance 框架，应直接采用重构后的版本（即本提交的形态），而非先回迁旧版再重构。
