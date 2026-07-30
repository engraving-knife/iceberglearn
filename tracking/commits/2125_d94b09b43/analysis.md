# 提交分析：Core: Support incremental compute for partition stats

## 提交信息

| 字段 | 值 |
|------|-----|
| 提交号 | 2125 |
| 短哈希 | `d94b09b43` |
| 完整哈希 | `d94b09b4314df114e63279f04ead29f0933615f4` |
| 作者 | Ajantha Bhat |
| 邮箱 | ajanthabhat@gmail.com |
| 日期 | 2025-05-14 19:42:15 2025 +0530 |
| 提交信息 | Core: Support incremental compute for partition stats (#12629) |

## 总体目的

本提交为 Iceberg 的分区统计（partition stats）计算添加了增量计算支持。此前，每次计算分区统计时都需要扫描表的所有 manifest 文件进行全量计算，对于大表来说非常耗时。本提交通过利用之前的分区统计文件，只计算自上次统计以来的增量变化并与旧统计合并，显著提高了计算效率。如果不存在之前的统计文件，则回退到全量计算。

## 设计目的的实现方式

1. **查找之前的统计文件**：新增 `latestStatsFile` 方法，在表的快照祖先链中查找最近的分区统计文件。

2. **增量计算差异**：新增 `computeStatsDiff` 方法，计算两个快照之间的 manifest 差异，只处理新增的 manifest 文件。

3. **合并增量与历史统计**：新增 `computeAndMergeStatsIncremental` 方法，读取旧统计文件、计算增量差异、将增量统计合并到旧统计中。

4. **处理删除条目的增量逻辑**：新增 `deletedEntryForIncrementalCompute` 方法，在增量模式下减去已删除文件的计数器（而非仅更新快照信息）。

5. **区分新增和已存在条目**：在增量模式下，只处理 `ADDED` 状态的 live 条目（`EXISTING` 状态已在之前的统计中包含）。

## 修改详情

### 1. 修改 `PartitionStats.java`

**文件**：`core/src/main/java/org/apache/iceberg/PartitionStats.java`

**修改内容**：

#### 新增 `deletedEntryForIncrementalCompute` 方法

```java
void deletedEntryForIncrementalCompute(ContentFile<?> file, Snapshot snapshot) {
    Preconditions.checkArgument(specId == file.specId(), "Spec IDs must match");
    switch (file.content()) {
      case DATA:
        this.dataRecordCount -= file.recordCount();
        this.dataFileCount -= 1;
        this.totalDataFileSizeInBytes -= file.fileSizeInBytes();
        break;
      case POSITION_DELETES:
        this.positionDeleteRecordCount -= file.recordCount();
        this.positionDeleteFileCount -= 1;
        break;
      case EQUALITY_DELETES:
        this.equalityDeleteRecordCount -= file.recordCount();
        this.equalityDeleteFileCount -= 1;
        break;
      default:
        throw new UnsupportedOperationException("Unsupported file content type: " + file.content());
    }
    if (snapshot != null) {
      updateSnapshotInfo(snapshot.snapshotId(), snapshot.timestampMillis());
    }
}
```

**目的和工作逻辑**：在增量计算模式下，当遇到已删除的 manifest 条目时，需要从之前的统计中减去该文件的计数（记录数、文件数、文件大小），因为该文件之前被统计过但现在已被删除。这与全量模式下的 `deletedEntry`（仅更新快照信息）不同。

#### 弃用标记

将 `liveEntry`、`deletedEntry`、`appendStats` 方法标记为 `@Deprecated`，注释说明将在 1.11.0 中降低可见性为 package-private。

**目的**：这些方法原本是 public 的，但只应该在 `PartitionStatsHandler` 内部使用。标记为弃用以提醒外部调用者不要依赖这些方法。

### 2. 修改 `PartitionStatsHandler.java`

**文件**：`core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java`

这是本提交的核心修改，涉及多处新增和重构：

#### 2a. 修改 `computeAndWriteStatsFile(Table, long)` 方法

```java
// 修改前：
Collection<PartitionStats> stats = computeStats(table, snapshot);

// 修改后：
PartitionStatisticsFile statisticsFile = latestStatsFile(table, snapshot.snapshotId());
if (statisticsFile == null) {
    stats = computeStats(table, snapshot, file -> true, false /* incremental */).values();
} else {
    stats = computeAndMergeStatsIncremental(table, snapshot, partitionType, statisticsFile);
}
```

**目的**：在计算统计时先查找之前的统计文件。如果找到则使用增量计算，否则回退到全量计算。

#### 2b. 新增 `computeAndMergeStatsIncremental` 方法

**目的和工作逻辑**：
1. 读取之前的统计文件，将旧统计放入 `statsMap`
2. 调用 `computeStatsDiff` 计算自上次统计以来的增量
3. 将增量统计合并到旧统计中（使用 `appendStats`）

#### 2c. 新增 `latestStatsFile` 方法

**目的和工作逻辑**：遍历快照的祖先链，查找最近的包含分区统计文件的快照。返回该统计文件；如果找不到则返回 null（触发全量计算回退）。

#### 2d. 新增 `computeStatsDiff` 方法

**目的和工作逻辑**：
1. 使用 `SnapshotUtil.ancestorIdsBetween` 获取两个快照之间的快照 ID 集合
2. 创建 manifest 谓词，只匹配这些快照中的 manifest
3. 调用 `computeStats` 进行增量计算

#### 2e. 重构 `computeStats` 方法

新增 `predicate` 和 `incremental` 参数：
- `predicate`：用于过滤 manifest 文件（全量模式不过滤，增量模式只处理差异 manifest）
- `incremental`：控制条目处理逻辑

**增量模式下的条目处理逻辑**：
- Live 条目中 `ADDED` 状态：正常调用 `liveEntry` 添加计数
- Live 条目中 `EXISTING` 状态：跳过（已在之前的统计中包含）
- Deleted 条目：调用 `deletedEntryForIncrementalCompute` 减去计数

#### 2f. 重构辅助方法

- `collectStats` → `collectStatsForManifest`：新增 `incremental` 参数
- `mergeStats` → `mergePartitionMap`：重命名并简化
- `openManifest`：内联到 `collectStatsForManifest` 中

### 3. 修改测试文件

#### `PartitionStatsHandlerTestBase.java`
新增增量计算相关的测试基础设施。

#### `TestOrcPartitionStatsHandler.java`
新增 ORC 格式的增量计算测试。

#### `TestRewriteDataFilesProcedure.java`
新增测试验证数据文件重写后增量计算的正确性。

#### `TestRewriteManifestsProcedure.java`
新增测试验证 manifest 重写后增量计算的正确性。

## 总结

本提交为 Iceberg 分区统计计算添加了增量计算支持，显著提高了大表的统计计算效率。核心变更包括：

1. **增量计算逻辑**：查找之前的统计文件 → 计算快照差异 → 合并增量与历史统计
2. **新增 `deletedEntryForIncrementalCompute`**：在增量模式下从计数中减去已删除文件的统计
3. **区分条目状态**：增量模式下只处理 `ADDED` 状态的 live 条目，跳过 `EXISTING` 状态
4. **全量回退**：如果找不到之前的统计文件，自动回退到全量计算
5. **API 弃用标记**：将 `PartitionStats` 的公共方法标记为弃用，计划在 1.11.0 中降低可见性

共修改 6 个文件，新增 349 行，删除 44 行。
