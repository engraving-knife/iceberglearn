# 提交 0389：Spark 3.5: Propagate snapshot properties in compaction (#9449)

## 提交信息

- **序号**：0389
- **哈希**：008d1731cb0d9c2dfbcab640633651dc920953ea
- **短哈希**：008d1731c
- **日期**：2024-01-19（Fri Jan 19 03:35:48 2024 +0800）
- **作者**：advancedxy <xianjin@apache.org>
- **提交说明**：Spark 3.5: Propagate snapshot properties in compaction (#9449)
- **PR/Issue**：#9449

## 总体目的

这个提交修复了 Spark 3.5 上 compaction 类 action（`rewrite_data_files` 与 `rewrite_position_deletes`）的一个"用户设置被吞掉"问题。Iceberg 的 action API 提供 `snapshotProperty(key, value)` 方法，让用户在执行重写时给即将产生的快照注入自定义 summary 属性——典型用途是打审计标签（"rewritten-by=governance-job"）、关联业务流水号、记录重写参数等。这些属性应该出现在 `Snapshot.summary()` 里，与 Iceberg 内部产生的 `added-files`、`deleted-files`、`total-data-files` 等指标并列。

修复前，Spark 3.5 的 `RewriteDataFilesSparkAction` 与 `RewritePositionDeleteFilesSparkAction` 虽然从父类 `BaseSnapshotUpdateSparkAction` 继承了 `snapshotProperty(...)` 的入口，但这些用户属性只被存到 action 内部的 `summary` map 里，**从未被传到 `RewriteDataFilesCommitManager` / `RewritePositionDeletesCommitManager`**，commit manager 在 `commit()` 时也从未调 `RewriteFiles.set(...)` 把它们写入快照。结果：用户调 `snapshotProperty("key","value")` 后，新快照的 summary 里完全没有这对键值，调用仿佛从未发生。这是一处 API 承诺与实现脱节的缺陷。

第二层意图是统一两类 compaction 的行为：data files compaction 与 position deletes compaction 都应该把用户 snapshot properties 透传到最终快照。提交同时给两条路径都加上同样的传递链路与测试，确保两条路径行为一致。

第三层是工程整洁性：commit manager 此前签名不含 snapshotProperties，本次通过新增重载构造器（而非破坏性修改）注入，保持旧调用点兼容；并在 Spark action 基类暴露一个 `commitSummary()` 取出不可变副本，避免外部直接修改内部 map。

## 如何达成设计目的

实现链路是"两端打通"：

1. **核心层 commit manager 接受 snapshotProperties**：`RewriteDataFilesCommitManager` 与 `RewritePositionDeletesCommitManager` 各新增一个重载构造器，多接一个 `Map<String, String> snapshotProperties` 字段；旧的 3 参/1 参构造器委托新构造器并传 `ImmutableMap.of()`，保持兼容。在 `commit()` 内、`rewrite.commit()` 之前，遍历 snapshotProperties 调 `rewrite.set(k, v)`，让 `RewriteFiles` API 把它们写进即将提交的 snapshot summary。
2. **Spark 层把 summary 喂给 commit manager**：`BaseSnapshotUpdateSparkAction` 暴露 `protected Map<String,String> commitSummary()` 返回内部 `summary` 的不可变副本；两个 Spark action 在创建 commitManager 时把 `commitSummary()` 作为参数传入。
3. **测试固化**：两个测试类各加 `testSnapshotProperty`，断言自定义 `key=value` 进了 `Snapshot.summary()`，同时验证 Iceberg 内部的 commit metrics（`SnapshotSummary.*_PROP`）没被覆盖丢失。

## 修改详情

### core/src/main/java/org/apache/iceberg/actions/RewriteDataFilesCommitManager.java

**修改目的**：让 data files compaction 的提交阶段把用户 snapshot properties 写入快照 summary。

**工作逻辑**：
- import 新增 `java.util.Map` 与 `ImmutableMap`。
- 新增字段 `private final Map<String, String> snapshotProperties;`。
- 原 3 参构造器 `RewriteDataFilesCommitManager(Table, long, boolean)` 改为委托新 4 参构造器，传入 `ImmutableMap.of()`（即"无自定义属性"），保持向后兼容。
- 新增 4 参构造器 `RewriteDataFilesCommitManager(Table, long startingSnapshotId, boolean useStartingSequenceNumber, Map<String,String> snapshotProperties)`，赋值四个字段。
- 关键改动在 `commit()` 内：在 `rewrite.rewriteFiles(rewrittenDataFiles, addedDataFiles)` 之后、`rewrite.commit()` 之前，插入一行 `snapshotProperties.forEach(rewrite::set);`——即把每个用户属性用 `RewriteFiles.set(key, value)` 注册到 pending 的 snapshot update 上。`RewriteFiles.set` 是 Iceberg 核心 API 把属性写入 snapshot summary 的标准入口，所以这一行就是修复的核心。`rewrite.commit()` 触发后，这些属性就和内部 metrics 一起落到新快照的 summary 里。

### core/src/main/java/org/apache/iceberg/actions/RewritePositionDeletesCommitManager.java

**修改目的**：让 position deletes compaction 的提交阶段同样透传用户 snapshot properties。

**工作逻辑**：
- import 新增 `java.util.Map` 与 `ImmutableMap`。
- 新增字段 `private final Map<String, String> snapshotProperties;`。
- 原 1 参构造器 `RewritePositionDeletesCommitManager(Table)` 改为委托新 2 参构造器，传 `ImmutableMap.of()`，保持兼容。
- 新增 2 参构造器 `(Table, Map<String,String> snapshotProperties)`，赋值三个字段（`table`、`startingSnapshotId = table.currentSnapshot().snapshotId()`、`snapshotProperties`）。
- `commit()` 内、`rewriteFiles.commit()` 之前，插入 `snapshotProperties.forEach(rewriteFiles::set);`。与 data files 路径对称，使用同一套 `RewriteFiles.set` API。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/BaseSnapshotUpdateSparkAction.java

**修改目的**：在 Spark action 基类暴露一个安全的"取出 commit summary 不可变副本"方法，供子类把用户 snapshot properties 喂给 commit manager。

**工作逻辑**：
- import 新增 `ImmutableMap`（已有 `Maps`）。
- 新增方法：
  ```java
  protected Map<String, String> commitSummary() {
    return ImmutableMap.copyOf(summary);
  }
  ```
  `summary` 是父类 / 本类内部维护的 `Map<String,String>`（用户通过 `snapshotProperty(k,v)` 累积进来）。返回 `ImmutableMap.copyOf` 而非原 map，是为了防止 commitManager 或下游 action 持有的引用意外回写到 action 状态；同时也避免并发修改。设计上这是一个"只读快照"导出口。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java

**修改目的**：在创建 `RewriteDataFilesCommitManager` 时把 `commitSummary()` 一起传进去。

**工作逻辑**：
- `commitManager(long startingSnapshotId)` 由 `new RewriteDataFilesCommitManager(table, startingSnapshotId, useStartingSequenceNumber)` 改为：
  ```java
  return new RewriteDataFilesCommitManager(
      table, startingSnapshotId, useStartingSequenceNumber, commitSummary());
  ```
  即把 action 当前累积的所有 snapshot properties 通过新 4 参构造器注入 commit manager。`commitManager` 是 `@VisibleForTesting` 的工厂方法，所有 rewrite 路径最终都通过它拿 commitManager，所以一处改动覆盖全部分支。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java

**修改目的**：在创建 `RewritePositionDeletesCommitManager` 时把 `commitSummary()` 一起传进去。

**工作逻辑**：
- `commitManager()` 由 `new RewritePositionDeletesCommitManager(table)` 改为 `new RewritePositionDeletesCommitManager(table, commitSummary())`。与 data files 路径对称。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java

**修改目的**：验证 `rewrite_data_files` 执行后，用户 `snapshotProperty("key","value")` 出现在新快照 summary 里，且 Iceberg 自身的 commit metrics 没被覆盖。

**工作逻辑**：
- import 新增 `org.apache.iceberg.SnapshotSummary`。
- 新增测试 `testSnapshotProperty`：
  1. `createTable(4)` 建表造数据；
  2. `basicRewrite(table).snapshotProperty("key", "value").execute()` 执行重写并注入一对自定义属性；
  3. `assertThat(table.currentSnapshot().summary()).containsAllEntriesOf(ImmutableMap.of("key", "value"))` 验证自定义属性已写入；
  4. 构造一个 keys 数组，包含 `SnapshotSummary.ADDED_FILES_PROP`、`DELETED_FILES_PROP`、`TOTAL_DATA_FILES_PROP`、`CHANGED_PARTITION_COUNT_PROP`，断言 `summary().containsKeys(...)` 这些内部 metrics 仍存在——这覆盖了"用户属性会不会冲掉内部指标"的隐含风险。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java

**修改目的**：验证 `rewrite_position_deletes` 执行后，用户 snapshot properties 同样出现在新快照 summary 里，且 position-delete 相关 metrics 不丢失。

**工作逻辑**：
- import 新增 `org.apache.iceberg.SnapshotSummary`。
- 新增测试 `testSnapshotProperty`：
  1. `createTableUnpartitioned(2, SCALE)` 建表，`TestHelpers.dataFiles(table)` 拿数据文件，`writePosDeletesForFiles(...)` 写位置删除，断言 data/delete 文件各 2 个；
  2. `SparkActions.get(spark).rewritePositionDeletes(table).snapshotProperty("key","value").option(SizeBasedFileRewriter.REWRITE_ALL, "true").execute()` 执行重写并注入属性；
  3. 断言 `summary()` 含 `key=value`；
  4. 断言 `summary()` 含一组 position-delete 专属 metrics keys：`ADDED_DELETE_FILES_PROP`、`ADDED_POS_DELETES_PROP`、`CHANGED_PARTITION_COUNT_PROP`、`REMOVED_DELETE_FILES_PROP`、`REMOVED_POS_DELETES_PROP`、`TOTAL_DATA_FILES_PROP`、`TOTAL_DELETE_FILES_PROP`。这一组比 data files 测试更丰富，因为 position-delete 路径要同时统计删除文件相关的多类指标，验证它们都没被覆盖。

## 小结

这是一个"补齐 API 承诺"的修复：`snapshotProperty(...)` 入口早已存在，但 Spark 3.5 的 compaction 实现没有把它送到 commit 阶段。修复模式很轻量——核心层给 commit manager 加重载构造器并补一行 `forEach(rewrite::set)`，Spark 层把 summary 通过新方法 `commitSummary()` 透传过去，外加两条对称的测试。改动小但价值明确：让 compaction 产出的快照可以携带用户自定义审计/治理标签，且不破坏 Iceberg 自身 metrics。同时通过 `ImmutableMap.copyOf` 与重载构造器两个手段保持了 API 向后兼容，没有给旧调用方造成破坏。
