# 提交 3418：Flink: Add branch support to RewriteDataFiles maintenance task (#15672)

## 提交信息

- **序号**：3418 / 4088
- **哈希**：367a0ba3fc94fbb294bacd2681dc3c97ccf898ad
- **短哈希**：367a0ba3fc
- **日期**：2026-03-19 16:46:51 +0100
- **作者**：Maximilian Michels
- **提交说明**：Flink: Add branch support to RewriteDataFiles maintenance task (#15672)
- **PR/Issue**：#15672

## 总体目的

为 Flink 的 RewriteDataFiles 维护任务添加分支（branch）支持。此前数据文件重写只在主分支上执行，现在允许用户指定目标分支，使规划器从指定分支的快照读取数据，提交也指向该分支。这对于使用分支进行 WAP（Write-Audit-Publish）工作流的用户尤为重要，可以在分支上独立进行数据压缩。

## 如何达成设计目的

1. 在 `RewriteDataFiles.Builder` 中新增 `branch` 字段（默认为 `main` 分支）和 `branch(String)` 方法
2. 在 `DataFileRewritePlanner` 中从指定分支获取快照而非使用 `currentSnapshot()`
3. 使用 `BinPackRewriteFilePlanner` 的带分支参数的构造函数
4. 在 `DataFileRewriteCommitter` 中将分支传递给 commit manager
5. `FlinkRewriteDataFilesCommitManager` 将分支传递给父类构造函数
6. `PlannedGroup` 携带分支信息

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (+20/-2 lines)

**修改目的**：在 Builder 中添加分支配置。

**工作逻辑**：
- 新增 `private String branch = SnapshotRef.MAIN_BRANCH` 字段
- 新增 `branch(String newBranch)` 方法，设置要压缩的分支
- 将 branch 传递给 `DataFileRewritePlanner` 和 `DataFileRewriteCommitter`

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (+24/-4 lines)

**修改目的**：从指定分支读取快照进行规划。

**工作逻辑**：
- 新增 `branch` 字段和构造函数参数
- 将 `table.currentSnapshot()` 替换为 `table.snapshot(branch)`，从指定分支获取快照
- 使用 `new BinPackRewriteFilePlanner(table, filter, snapshot.snapshotId(), false)` 替代 `new BinPackRewriteFilePlanner(table, filter)`
- `PlannedGroup` 新增 `branch` 字段和 `branch()` 方法

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewriteCommitter.java` (+19/-3 lines)

**修改目的**：将分支传递给提交管理器。

**工作逻辑**：
- 新增 `branch` 字段和构造函数参数
- `FlinkRewriteDataFilesCommitManager` 构造函数新增 `branch` 参数，传递给父类 `RewriteDataFilesCommitManager` 构造函数
- 使用 `ImmutableMap.of()` 作为空属性映射

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+5/-1 lines)

**修改目的**：将分支信息传递到维护任务。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewriteRunner.java` (+2/-1 lines)

**修改目的**：适配 PlannedGroup 的分支字段。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/RewriteUtil.java` (+4/-1 lines)

**修改目的**：适配 PlannedGroup 的分支字段。

### 测试文件 (+various lines)

**修改目的**：新增分支支持测试并更新现有测试的构造函数调用。

- `TestRewriteDataFiles.java`：新增分支重写测试
- `TestDataFileRewritePlanner.java`：更新构造函数调用并新增分支测试
- `TestDataFileRewriteCommitter.java`：更新构造函数调用
- `TestDataFileRewriteRunner.java`：更新构造函数调用

## 总结

本提交为 Flink 的 RewriteDataFiles 维护任务添加了分支支持，允许用户指定目标分支进行数据文件压缩。规划器从指定分支的快照读取数据，提交也指向该分支。这对 WAP 工作流场景很有用，可以在分支上独立进行数据压缩。
