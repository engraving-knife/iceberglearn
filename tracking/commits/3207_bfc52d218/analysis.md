# 提交 3207：Core: Fix compute_table_stats failures with concurrent writes (#15148)

## 提交信息

- **序号**：3207 / 4088
- **哈希**：bfc52d218adf9fc0229601055c2238f580bcac3a
- **短哈希**：bfc52d218
- **日期**：2026-02-05
- **作者**：hemanthboyina
- **提交说明**：Core: Fix compute_table_stats failures with concurrent writes (#15148)
- **PR/Issue**：#15148

## 总体目的

`compute_table_stats`（计算表统计信息）过程会扫描数据生成统计文件（如 puffin 格式的 ndv/bloom-filter 等），随后通过 `SetStatistics`（即 `UpdateStatistics` 的实现）把这些统计文件提交到表的元数据中。统计计算往往耗时较长，而在此期间表上很可能发生并发写入——例如另一个作业提交了新的快照。一旦有并发写入，`SetStatistics.commit()` 拿到的 `base` 元数据就变成了过期版本，`ops.commit(base, newMetadata)` 会因为 base 与当前元数据不一致而抛出 `CommitFailedException`，导致整个统计过程前功尽弃、无法落地。

原 `commit()` 实现是一次性无重试的提交：`TableMetadata base = ops.current();` 取一次快照，`internalApply(base)` 计算新元数据后直接 `ops.commit(base, newMetadata)`。这与 Iceberg 中绝大多数 `UpdateXXX` 操作不同——后者普遍采用 `Tasks.foreach(ops).retry(...).exponentialBackoff(...)` 的乐观锁重试模式来应对并发修改。`SetStatistics` 缺少这一保护，因此在并发写场景下稳定性很差。

本提交为 `SetStatistics.commit()` 补上标准化的乐观提交重试机制：以表属性配置的 `COMMIT_NUM_RETRIES`、`COMMIT_MIN_RETRY_WAIT_MS`、`COMMIT_MAX_RETRY_WAIT_MS`、`COMMIT_TOTAL_RETRY_TIME_MS` 为参数，做指数退避重试，仅对 `CommitFailedException` 重试；每次重试前先 `taskOps.refresh()` 获取最新元数据，再重新 `internalApply` 并提交。这样统计文件的写入就能在并发写入下自动重试成功，不再因一次 base 过期而整体失败。

## 如何达成设计目的

整体思路是把 `commit()` 由"单次提交"改造为"`Tasks` 重试框架包裹的循环提交"，与 Iceberg 其它写操作保持一致。涉及两个文件：`SetStatistics.java` 是核心修复，`TestSetStatistics.java` 新增两个测试覆盖并发修改与重试成功的场景。关键是每次重试都重新 `refresh()` 并重新 `internalApply`，保证基于最新元数据推导统计集合，从而安全地完成提交。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SetStatistics.java` (+27/-3)

**修改目的**：为统计信息提交加入乐观锁重试，抵御并发写入导致的提交失败。

**工作逻辑**：
新增了一组 import：表属性常量 `COMMIT_MAX_RETRY_WAIT_MS(_DEFAULT)`、`COMMIT_MIN_RETRY_WAIT_MS(_DEFAULT)`、`COMMIT_NUM_RETRIES(_DEFAULT)`、`COMMIT_TOTAL_RETRY_TIME_MS(_DEFAULT)`，以及 `CommitFailedException` 和 `Tasks` 工具类。

原 `commit()` 三行实现被替换为标准的 `Tasks` 重试模板：

```
Tasks.foreach(ops)
    .retry(ops.current().propertyAsInt(COMMIT_NUM_RETRIES, COMMIT_NUM_RETRIES_DEFAULT))
    .exponentialBackoff(min, max, total, 2.0)
    .onlyRetryOn(CommitFailedException.class)
    .run(taskOps -> {
        TableMetadata base = taskOps.refresh();
        TableMetadata updated = internalApply(base);
        taskOps.commit(base, updated);
    });
```

重试次数与退避参数全部从 `ops.current()` 的表属性读取，使用各自的默认值兜底，退避倍率为 `2.0`（指数退避）。`.onlyRetryOn(CommitFailedException.class)` 确保只在乐观锁冲突时重试，其它异常正常抛出。循环体内用 `taskOps.refresh()`（而非原来的 `ops.current()`）取得每次尝试时的最新元数据——这是重试能成功的关键：`refresh()` 会向 `TableOperations` 拉取最新已提交状态，使 `base` 重新对齐；随后 `internalApply(base)` 基于最新状态重新推导统计文件集合（移除已不存在快照对应的统计文件、加入新统计文件），再 `taskOps.commit(base, updated)` 提交。这样即便第一次因并发写入失败，重试时也会基于最新元数据重算并提交成功。

### `core/src/test/java/org/apache/iceberg/TestSetStatistics.java` (+50/-0)

**修改目的**：验证并发修改下的重试行为与重试最终成功。

**工作逻辑**：
新增 `setStatisticsRetryWithConcurrentModification()`：构造一个自定义 `TestTables.TestTableOperations`，在其 `commit()` 首次被调用时先执行一次 `table.newFastAppend().appendFile(FILE_B).commit()` 模拟并发写入（使 base 过期），随后再调用 `super.commit(base, metadata)` 必然抛出 `CommitFailedException`。测试断言经过重试后统计文件最终被成功设置，`readMetadata().statisticsFiles()` 恰好包含该 `statisticsFile`。

新增 `setStatisticsRetrySuccess()`：通过 `ops.failCommits(2)` 让前两次提交失败、第三次成功，走标准 `table.updateStatistics().setStatistics(statisticsFile).commit()` 路径，断言重试后统计文件正确落地。两者共同覆盖"真实并发修改"与"通用提交失败注入"两类重试触发场景。

## 总结

本提交补齐了 `SetStatistics` 缺失的乐观锁重试机制，使其与 Iceberg 其它写操作一致。`compute_table_stats` 在长时间运行期间极易遇到并发写入，原先一次失败即整体失败；改造后能在 `CommitFailedException` 时按表配置做指数退避重试，每次重新 `refresh()` 并重算统计集合，从而在并发写场景下稳健落地统计文件，显著提升了表统计维护的可靠性。
