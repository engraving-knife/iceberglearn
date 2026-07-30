# 提交 0465：Flink: backport #9547 to 1.17 and 1.16 for Adds the ability to read from a branch on the Flink Iceberg Source

## 提交信息

- **序号**：0465
- **完整哈希**：c34efa0687f74794619d744f875305401df11a33
- **短哈希**：c34efa068
- **日期**：2024-02-05 08:22:34 -0800
- **作者**：Rodrigo <rmenesespinillos@apple.com>
- **提交说明**：Flink: backport #9547 to 1.17 and 1.16 for Adds the ability to read from a branch on the Flink Iceberg Source
- **PR**：#9627（backport 自 #9547）

## 总体目的

本提交是把 PR #9547 的能力 backport 到 Flink 1.16 与 1.17 两个版本分支，使 Flink Iceberg Source 在流式（continuous/streaming）读取模式下支持从指定的 Iceberg 分支（branch）读取数据。在此之前，Flink 流式源对 branch 显式拒绝：`ScanContext`、`StreamingMonitorFunction` 中都有 `Preconditions.checkArgument(branch == null, ...)` 守卫，一旦用户在流式查询里带上 `'branch'='b1'` 之类的 hint，就会抛出 `IllegalArgumentException: Cannot scan table using ref b1 configured for streaming reader yet`。这意味着用户只能在批式读取里使用 branch，而流式增量消费无法跟踪某个分支上的新快照，限制了 branch 在 Flink 实时管线中的使用场景。

Iceberg 的 branch（结合 Nessie 或 Iceberg 自身 `manageSnapshots`）是支撑「可版本化的数据研发」「WAP（Write-Audit-Publish）」「分支试算」等模式的关键能力。流式源支持 branch 后，用户可以让 Flink 作业只消费某个分支上的增量提交，主分支与其他分支的写入互不干扰，从而把 branch 的能力从批处理扩展到流式 ETL 与实时数仓。

本提交移除了流式路径上对 branch 的禁用守卫，并在 `StreamingMonitorFunction` 与 `ContinuousSplitPlannerImpl` 的快照选取逻辑中加入「若指定了 branch 则取 `table.snapshot(branch)`，否则取 `table.currentSnapshot()`」的分支判断，使流式作业能正确跟踪指定分支的最新快照。同时为 v1.16 与 v1.17 两个模块分别新增/改写测试，覆盖从主分支读、从命名分支读、从两个分支并行读等场景。

## 如何达成设计目的

实现分三步：第一，在 `ScanContext` 的流式校验中删除「branch 必须为 null」的 `Preconditions` 守卫（保留对 tag 的禁用，因为 tag 是不可变的固定快照，不适合流式增量）。第二，在 `StreamingMonitorFunction`（旧版 SourceFunction API）的初始化校验和每次轮询取快照处，以及在新版 `ContinuousSplitPlannerImpl` 的 `discoverIncrementalSplits` 处，把原本直接 `table.currentSnapshot()` 改为根据 `scanContext.branch()` 是否为 null 选择 `table.snapshot(branch)` 或 `currentSnapshot()`。第三，新增/改写测试：在 `TestIcebergSourceContinuous` 中新增 `testReadingFromBranch` 覆盖「先全量后增量」地从分支读，并把 `TestStreamScanSql` 中原先断言「带 branch 流式查询会抛异常」的 `testConsumeFilesWithBranch` 改写为三个正向测试 `testConsumeFilesFromMainBranch`、`testConsumeFilesFromBranch`、`testConsumeFilesFromTwoBranches`，验证 SQL hint `'branch'='...'` 在流式查询中的正确行为。v1.16 与 v1.17 两个模块做对称的相同改动。

## 修改详情

下面按文件说明，v1.16 与 v1.17 同名文件的改动内容完全对称。

### flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/source/ScanContext.java
### flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/ScanContext.java

**修改目的**：解除流式读取对 branch 的禁用守卫，允许在流式 ScanContext 中配置 branch。

**工作逻辑**：
- 在流式相关校验块中，删除如下 `Preconditions.checkArgument`：
  ```java
  Preconditions.checkArgument(
      branch == null,
      String.format("Cannot scan table using ref %s configured for streaming reader yet", branch));
  ```
- 紧随其后对 `tag` 的守卫（`Cannot scan table using ref %s configured for streaming reader`）保留不变，因为 tag 是不可变快照，逻辑上不能用于流式增量。
- 删除后，流式 ScanContext 允许 `branch` 非 null，为后续在 monitor/planner 中根据 branch 选快照铺路。

### flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/source/StreamingMonitorFunction.java
### flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/StreamingMonitorFunction.java

**修改目的**：让旧版 `RichSourceFunction` 流式监控函数支持按 branch 跟踪最新快照，并移除对应的禁用校验。

**工作逻辑**：
- 在 `open`/初始化校验段中删除：
  ```java
  Preconditions.checkArgument(
      scanContext.branch() == null,
      "Cannot scan table using ref %s configured for streaming reader yet.");
  ```
  保留对 `START_SNAPSHOT_ID` 与 `START_TAG` 不能同时设置、以及 `table.currentSnapshot()` 非空的校验。
- 在每次轮询监控的核心方法中，把：
  ```java
  Snapshot snapshot = table.currentSnapshot();
  ```
  改为：
  ```java
  Snapshot snapshot =
      scanContext.branch() != null
          ? table.snapshot(scanContext.branch())
          : table.currentSnapshot();
  ```
- 这样当用户配置了 branch 时，监控函数会去取该分支当前指向的快照，而不是主分支的 currentSnapshot；后续 `if (snapshot != null && snapshot.snapshotId() != lastSnapshotId)` 的增量判断逻辑不变，从而能正确发现 branch 上的新快照并发起读取。

### flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousSplitPlannerImpl.java
### flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousSplitPlannerImpl.java

**修改目的**：让新版 Source（基于 `Source`/`SplitEnumerator` API）的增量切分规划器也支持按 branch 取当前快照。

**工作逻辑**：
- 在 `discoverIncrementalSplits(IcebergEnumeratorPosition lastPosition)` 中，把：
  ```java
  Snapshot currentSnapshot = table.currentSnapshot();
  ```
  改为：
  ```java
  Snapshot currentSnapshot =
      scanContext.branch() != null
          ? table.snapshot(scanContext.branch())
          : table.currentSnapshot();
  ```
- 后续对 `currentSnapshot == null`（空表）的 `Preconditions` 校验与增量切分逻辑保持不变。这样新版 Source 的 ContinuousSplitPlanner 在每次规划增量 splits 时，会基于指定 branch 的最新快照进行 diff，而不是主分支。

### flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceContinuous.java
### flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceContinuous.java

**修改目的**：为「从分支流式读取」能力新增端到端测试，并修复测试辅助方法以传递 branch。

**工作逻辑**：
- 新增 `@Test testReadingFromBranch()`，流程如下：
  1. 向主表追加 `batchBase`（2 条），记录为分支基线快照。
  2. 通过 `manageSnapshots().createBranch("b1", currentSnapshot().snapshotId()).commit()` 在当前快照上创建分支 b1。
  3. 向 b1 追加 `batch1`、`batch2`（各 2 条），分支累计应有 batchBase + batch1 + batch2 共 6 条。
  4. 构造 `ScanContext`：`streaming(true)`、`monitorInterval(10ms)`、`startingStrategy(TABLE_SCAN_THEN_INCREMENTAL)`、`useBranch("b1")`。
  5. 启动流并 `waitForResult(iter, 6)`，断言结果与 `branchExpectedRecords`（6 条）一致——验证「先全量扫描分支基线，再增量读取分支新快照」。
  6. 继续向 b1 追加 `batch3`、`batch4`，分别 `waitForResult(iter, 2)` 并断言——验证持续增量消费分支新提交。
  7. 关闭流后，再构造一个不带 branch 的 ScanContext 从主分支读，`waitForResult(iter, 2)` 应只返回 `batchBase`；再向主分支追加 `batchMain2`，应只返回 `batchMain2`——验证主分支与命名分支相互隔离，互不串数据。
- 同时在私有辅助方法 `createStream(scanContext)` 内部构造 FlinkSource 时，新增 `.branch(scanContext.branch())`，把测试 ScanContext 中的 branch 透传给实际 Source，保证测试路径与生产路径一致。

### flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java
### flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java

**修改目的**：把原先「断言带 branch 流式查询抛异常」的测试改写为正向验证「SQL hint 指定 branch 进行流式查询」的多个场景，并新增辅助方法支持向指定 branch 写入数据。

**工作逻辑**：
- 新增 import `org.apache.iceberg.SnapshotRef`。
- 重载 `insertRows(String partition, String branch, Table table, Row... rows)`，把原先只写主分支的版本改为委托给带 branch 的版本（默认 `SnapshotRef.MAIN_BRANCH`）。在带 branch 版本中，根据是否有 partition 调用 `appender.appendToTable(partition, branch, records)` 或 `appender.appendToTable(branch, records)`，把数据写到指定分支。
- 新增 `insertRowsInBranch(String branch, Table table, Row... rows)`，便捷地向命名分支写数据。
- 删除原 `testConsumeFilesWithBranch()`（它仅断言流式 + branch 抛 `IllegalArgumentException`）。
- 新增三个 `@TestTemplate`：
  - `testConsumeFilesFromMainBranch()`：先向主分支写 row1/row2，创建 b1，向 b1 写 row3/row4；然后从主分支流式读。断言首次返回 row1/row2（起始快照 exclusive 语义下的初始全量），再向主分支写 row5/row6、row7，断言增量返回对应行。验证主分支读取不受 b1 写入影响。
  - `testConsumeFilesFromBranch()`：先向主分支写 row1/row2，创建 b1；启动带 `'branch'='b1'` 的流式查询；断言首次返回 row1/row2（分支基线），再向 b1 写 row3/row4，断言增量返回 row3/row4。验证从命名分支的「全量 + 增量」读取。
  - `testConsumeFilesFromTwoBranches()`：创建 b1、b2，分别向各自写 2 条；分别启动带 `'branch'='b1'`、`'branch'='b2'` 的流式查询，断言各自只返回各自分支的数据；再向各分支追加 1 条，断言各自增量正确。验证两个分支互不干扰。
- 三个测试在结束后都调用 `result.getJobClient().ifPresent(JobClient::cancel)` 清理作业。
- 注意：v1.16 与 v1.17 的该文件改动基本一致，v1.17 的 `TestStreamScanSql.java` 行数略多（140 vs 142），主要因起始 index 不同导致上下文行差异，逻辑内容对称。

## 小结

本提交把 PR #9547 的「Flink 流式源支持从 branch 读取」能力 backport 到 v1.16 与 v1.17。核心是移除流式路径上对 branch 的禁用守卫，并在监控函数与增量切分规划器的快照选取处加入按 branch 取快照的分支判断。测试侧把一个原「断言抛异常」的负向测试改写为三个正向测试，覆盖从主分支读、从命名分支读、从两个分支并行读等场景，并新增 `TestIcebergSourceContinuous.testReadingFromBranch` 端到端验证「全量 + 增量」地从分支消费。改动在两个 Flink 版本模块中完全对称，使 branch 能力从批处理扩展到流式 ETL 与实时数仓场景。
