# 提交 0418：Flink: Adds the ability to read from a branch on the Flink Iceberg Source (#9547)

## 提交信息

- **序号**：0418
- **哈希**：3be1939afc01c5032590cf31074389bc1b320141
- **短哈希**：3be1939af
- **日期**：Mon Jan 29 17:14:34 2024 -0800
- **作者**：Rodrigo <rmenesespinillos@apple.com>
- **提交说明**：Flink: Adds the ability to read from a branch on the Flink Iceberg Source (#9547)
- **PR/Issue**：#9547

## 总体目的

Iceberg 的分支（branch）和标签（tag）机制允许在同一张表上维护多个独立的快照演进线，常用于诸如 WAP（Write-Audit-Publish）、A/B 测试、可审计的历史回放等场景。在批式读取路径上，Flink Iceberg Source 早已支持通过 `branch`/`tag` 选项读取指定 ref 的快照数据；但**流式读取路径长期以来显式拒绝了 branch 配置**——`ScanContext` 和 `StreamingMonitorFunction` 中均带有 `Preconditions.checkArgument(branch == null, "Cannot scan table using ref ... configured for streaming reader yet")` 这样的硬约束，遇到分支配置就直接抛 `IllegalArgumentException`。这意味着用户无法对一个分支做增量消费，分支上后续提交的数据流不进 Flink 流作业，分支功能在 Flink 流式场景下形同虚设。

本提交的目的就是把这条"尚未支持"的限制去掉，让 Flink 流式 Source 真正具备读取指定分支增量数据的能力。具体来说，当用户配置了 `branch` 选项时，流式 Source 在每轮增量发现（continuous split discovery）和流监控（streaming monitor）时，不再无脑地取 `table.currentSnapshot()`（即 main 分支的最新快照），而是改用 `table.snapshot(branch)` 取该分支当前指向的快照，从而把分支上的快照演进纳入流式消费范围。这是 Flink Iceberg Source 对 Iceberg 分支能力的一次重要功能补齐，使分支在批流两种模式下都能被一致地读取。

值得注意的是，提交保留了 `tag == null` 的检查（`ScanContext` 中），这是因为 tag 指向的是一个不可变的快照点，没有"增量"可言，流式读取 tag 在语义上没有意义，所以 tag 的限制依然合理；本次只放开了 branch 这条线，设计上非常克制和聚焦。

## 如何达成设计目的

实现路径相当精炼，核心思路是**在所有"取当前快照"的代码点把无条件的 `table.currentSnapshot()` 替换为"按 branch 选择"的取快照逻辑**：若 `scanContext.branch()` 非空则用 `table.snapshot(branch)`，否则回退到 `table.currentSnapshot()`。同时移除 `ScanContext` 和 `StreamingMonitorFunction` 中针对 branch 的硬性禁止校验。这种"取快照处做分支感知"的最小改动，让原本为 main 分支设计的增量发现逻辑（基于 `lastSnapshotId` 做差量）天然适用于任意分支——因为 Iceberg 表的快照图本身是有向无环的，分支只是 main 之外的一条独立演进线，`table.snapshot(branch)` 返回的就是该分支最新的 head，差量逻辑无需任何改动即可工作。配套地，提交新增了三个流式测试用例，分别覆盖分支读取、main 与分支隔离、以及多分支并发读取的场景。

## 修改详情

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/ScanContext.java

**修改目的**：移除流式读取路径上对 branch 的硬性禁止校验，允许在流式模式下配置 branch。

**工作逻辑**：在 `ScanContext` 的构建校验逻辑中，原本有一段 `Preconditions.checkArgument(branch == null, "Cannot scan table using ref %s configured for streaming reader yet", branch)`，本提交将这段整段删除（5 行）。保留紧随其后的 `tag == null` 校验不动，体现"放开 branch、保留 tag"的设计取舍——tag 是不可变快照点，流式增量消费无意义；而 branch 是可演进的，自然支持增量。删除该校验后，构建流式 `ScanContext` 时配置 `branch` 不再抛异常，为后续流式分支读取扫清障碍。

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/StreamingMonitorFunction.java

**修改目的**：让旧的 `SourceFunction` 风格的流式监控函数（legacy streaming source）在每轮扫描时能取到指定分支的最新快照，而不是固定取 main 的 currentSnapshot。

**工作逻辑**：
- 在 `open` 方法的参数校验段，删除了 `Preconditions.checkArgument(scanContext.branch() == null, "Cannot scan table using ref %s configured for streaming reader yet.")` 这段对 branch 的禁止检查，与 `ScanContext` 的改动保持一致。
- 在每轮监控扫描的核心方法（`monitorAndForwardSplits` 逻辑）中，原本是 `Snapshot snapshot = table.currentSnapshot();`，改为：
  ```java
  Snapshot snapshot =
      scanContext.branch() != null
          ? table.snapshot(scanContext.branch())
          : table.currentSnapshot();
  ```
  这样当配置了 branch 时，每次刷新表后取的是该分支当前的 head 快照，后续通过 `snapshot.snapshotId() != lastSnapshotId` 的差量判断就能发现分支上的新提交，并把对应的 splits 推送给下游。

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousSplitPlannerImpl.java

**修改目的**：让新版的基于 `Source` API 的连续枚举器（ContinuousEnumerator）在做增量 split 发现时也能感知 branch，取分支上的最新快照。

**工作逻辑**：在 `discoverIncrementalSplits(IcebergEnumeratorPosition lastPosition)` 方法中，把 `Snapshot currentSnapshot = table.currentSnapshot();` 改为：
```java
Snapshot currentSnapshot =
    scanContext.branch() != null
        ? table.snapshot(scanContext.branch())
        : table.currentSnapshot();
```
这是新版 Flink Source API 路径上的对应改动，和 `StreamingMonitorFunction` 中的改动是平行的——两者分别是 legacy 和新版的流式 Source，都需要在"取当前快照"这一点上做分支感知。后续的 `currentSnapshot == null`（空表）判断和基于 `lastPosition` 的差量计算逻辑完全复用，无需改动。

### flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceContinuous.java

**修改目的**：为新版 Source API 的流式分支读取能力新增端到端测试覆盖，验证分支读取、main/分支隔离、连续增量消费等行为。

**工作逻辑**：
- 新增 `testReadingFromBranch()` 测试：先向 main 追加一批基础数据（batchBase），然后基于当前快照创建分支 `b1`，再向 `b1` 追加两批数据（batch1、batch2）。构造一个 `streaming=true`、`startingStrategy=TABLE_SCAN_THEN_INCREMENTAL`、`useBranch("b1")` 的 `ScanContext`，启动流后先收到 6 条记录（batchBase + batch1 + batch2，因为 TABLE_SCAN_THEN_INCREMENTAL 会先全量扫描分支起点前的数据再增量），验证分支读取能看到分支创建前的快照数据；随后再向 `b1` 追加 batch3、batch4，分别验证能连续收到对应增量。最后再构造一个不指定 branch 的 `ScanContext` 读取 main，验证 main 上只能看到 batchBase 及其后续提交，证明分支与 main 的数据是隔离的。
- 在私有 `createStream` 方法中，构建 `FlinkSource` 时把 `scanContext.branch()` 透传给 `ScanContext.builder().branch(...)`，让测试用的流式 Source 能带上分支配置。

### flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java

**修改目的**：为通过 SQL hints 配置流式分支读取的场景（`/*+ OPTIONS('branch'='b1') */`）提供测试覆盖，并把原本"断言 branch 流式读取会抛异常"的反向测试改写为正向测试。

**工作逻辑**：
- 引入 `org.apache.iceberg.SnapshotRef`，扩展 `insertRows` 辅助方法：新增一个重载 `insertRows(String partition, String branch, Table table, Row... rows)`，把 branch 透传给 `GenericAppenderHelper.appendToTable`，原有的无 branch 版本改为以 `SnapshotRef.MAIN_BRANCH` 委托调用。同时新增 `insertRowsInBranch(branch, table, rows)` 便捷方法，用于向指定分支追加非分区数据。
- 把原 `testConsumeFilesWithBranch`（断言带 branch 的流式 SQL 会抛 `IllegalArgumentException`）拆分并改写为三个正向测试：
  - `testConsumeFilesFromMainBranch`：在 main 上插入数据后创建分支 `b1`，再向 `b1` 插入数据；不带 branch 选项流式读取 main，验证只能看到 main 上的数据（row1/row2 初始 + row5/row6/row7 后续），分支上的数据不会泄漏到 main 读取。
  - `testConsumeFilesFromBranch`：在 main 上插入数据后创建分支 `b1`，带 `'branch'='b1'` 选项流式读取，验证先收到 main 上已有的 row1/row2（分支继承自 main 当前快照），再向 `b1` 追加 row3/row4 后能收到对应增量。
  - `testConsumeFilesFromTwoBranches`：创建两个分支 `b1`、`b2`，各自独立插入数据，分别带 `'branch'='b1'`/`'branch'='b2'` 选项流式读取，验证每个分支只能看到自己演进线上的数据，互不干扰。这个测试充分体现了分支作为独立演进线的隔离性。

## 小结

这是一个聚焦而完整的功能补齐提交：用最小的代码改动（核心是三处 `table.currentSnapshot()` → `table.snapshot(branch)` 的条件化替换 + 两处校验移除）打通了 Flink 流式 Source 读取 Iceberg 分支的能力，配套三个 SQL 层和 Source API 层的端到端测试充分验证了分支读取、main/分支隔离、多分支并发等关键行为。改动体现了对 Iceberg 快照图模型的正确理解——分支只是另一条独立的快照演进线，增量发现的差量逻辑天然适用，无需为分支另起一套机制。同时设计上保持了克制：只放开 branch（可演进），保留 tag（不可变）的禁止，避免在语义不合理的场景引入意外行为。这一改动让 Flink 流式读取与 Iceberg 的分支能力对齐，为 WAP、分支级 CDC 消费等流式场景提供了基础支撑。
