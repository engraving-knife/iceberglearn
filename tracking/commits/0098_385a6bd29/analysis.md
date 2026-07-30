# 提交 0098：Core: Ignore split offsets array when split offset is past file length (#8925)

## 提交信息

- **序号**：0098 / 4088
- **哈希**：385a6bd296e551bdfdab48d2a291d3516f999f5a
- **短哈希**：385a6bd29
- **日期**：2023-10-28
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Ignore split offsets array when split offset is past file length (#8925)
- **PR/Issue**：#8925

## 总体目的

本提交修复 `BaseFile` 中 split offsets 损坏检测不一致导致任务切分规划仍使用损坏偏移的缺陷，确保当文件 split offsets 末尾超出文件大小时，扫描任务规划一致地忽略这些偏移、回退到按固定大小切分。

背景：Iceberg 在 manifest 中为每个数据文件记录 `splitOffsets`（如 Parquet row group 起始偏移数组），用于扫描规划时按格式内部块边界切分任务，避免在 row group 中间断开带来的额外读取开销。然而偏移数据可能因写入异常或元数据损坏而不可靠——典型表现是最后一个 split offset 大于等于文件实际大小（`fileSizeInBytes`），这意味着偏移数组与文件实际内容不匹配。

`BaseFile` 提供两个访问口径：
- 公开的 `splitOffsets()` 返回 `List<Long>`，已被加入"末尾偏移超过文件大小时返回 null"的损坏检测；
- 包级可见的 `splitOffsetArray()` 返回 `long[]`，**修改前直接返回原始数组，不做任何损坏检查**。

问题在于扫描规划的核心入口 `BaseContentScanTask.split()` 在判断如何切分时，对 `BaseFile` 实例优先调用 `splitOffsetArray()`（注释说明是为避免装箱开销）。因此即使公开 `splitOffsets()` 因损坏返回 null，`splitOffsetArray()` 仍会返回损坏数组，`split()` 随后用它构造 `OffsetsAwareSplitScanTaskIterator`。该迭代器在计算最后一个 split 大小时执行 `parentTaskLength - offsets[lastIndex]`，当 `offsets[lastIndex] > parentTaskLength` 时结果为负数，会产生非法的（负长度）子任务，进而导致扫描异常或读取越界。

本提交将损坏检测统一抽取为私有方法 `hasWellDefinedOffsets()`，让 `splitOffsets()` 与 `splitOffsetArray()` 共用同一判断，从而保证两个口径行为一致：偏移损坏时两者都返回 null，`BaseContentScanTask.split()` 据此回退到 `FixedSizeSplitScanTaskIterator` 按固定大小切分，避免使用损坏偏移。

## 如何达成设计目的

设计思路是"统一损坏判断、消除两套口径的不一致"。具体做法：
1. 抽取一个私有方法 `hasWellDefinedOffsets()`，封装三个条件：`splitOffsets != null`、长度非 0、且最后一个偏移严格小于 `fileSizeInBytes`。
2. `splitOffsets()` 与 `splitOffsetArray()` 都改为先调用 `hasWellDefinedOffsets()`，成立才返回数据，否则返回 null。
3. 这样 `BaseContentScanTask.split()` 中无论走哪个口径，损坏偏移都会被识别为 null，统一回退到固定大小切分路径。

同时新增单元测试 `testTaskGroupPlanningCorruptedOffset`，构造一个末尾偏移（12）超过文件大小（10）的数据文件，验证 `planTaskGroups` 规划后每个子任务的 `splitOffsets()` 为 null，且任务数等于按 1 字节切分 10 字节文件的 10 个任务——直接证明损坏偏移被忽略、回退到固定大小切分。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseFile.java`

**修改目的**：让 `splitOffsetArray()` 与公开 `splitOffsets()` 采用同一损坏检测逻辑，避免扫描规划误用损坏偏移。

**工作逻辑**：

修改前（简化）：

```java
@Override
public List<Long> splitOffsets() {
  if (splitOffsets == null || splitOffsets.length == 0) {
    return null;
  }
  // 末尾偏移超过文件大小则视为损坏
  if (splitOffsets[splitOffsets.length - 1] >= fileSizeInBytes) {
    return null;
  }
  return ArrayUtil.toUnmodifiableLongList(splitOffsets);
}

long[] splitOffsetArray() {
  return splitOffsets;   // 直接返回原始数组，无损坏检查
}
```

修改后（简化）：

```java
@Override
public List<Long> splitOffsets() {
  if (hasWellDefinedOffsets()) {
    return ArrayUtil.toUnmodifiableLongList(splitOffsets);
  }
  return null;
}

long[] splitOffsetArray() {
  if (hasWellDefinedOffsets()) {
    return splitOffsets;
  }
  return null;
}

private boolean hasWellDefinedOffsets() {
  // 末尾偏移超过文件大小则视为损坏，不应使用
  return splitOffsets != null
      && splitOffsets.length != 0
      && splitOffsets[splitOffsets.length - 1] < fileSizeInBytes;
}
```

关键变化：
- 新增私有方法 `hasWellDefinedOffsets()` 集中三个判断条件（非 null、非空、末尾偏移严格小于文件大小），原本散落在 `splitOffsets()` 内的两段 if 合并为单一谓词。
- `splitOffsets()` 与 `splitOffsetArray()` 现在共享同一谓词，行为一致：损坏时都返回 null。
- 注意判断从 `>=` 改为 `<` 的正向表达：`hasWellDefinedOffsets` 为真要求末尾偏移严格小于文件大小，等价于原 `>= fileSizeInBytes` 即损坏的语义。
- 修复了 `splitOffsetArray()` 之前完全不检查损坏的漏洞——这是 bug 根因，因为 `BaseContentScanTask.split()` 优先调用此方法。

由于 `BaseContentScanTask.split()` 中逻辑为"若 `splitOffsetArray()` 返回非 null 且严格递增则用 `OffsetsAwareSplitScanTaskIterator`，否则用 `FixedSizeSplitScanTaskIterator`"，修复后损坏偏移会触发 null 分支，安全回退到固定大小切分。

### `core/src/test/java/org/apache/iceberg/util/TestTableScanUtil.java`

**修改目的**：验证损坏偏移场景下任务规划正确忽略偏移并回退到固定大小切分。

**工作逻辑**：

新增测试 `testTaskGroupPlanningCorruptedOffset`：

1. **构造损坏文件**：用 `DataFiles.builder(SPEC)` 构造一个 10 字节的数据文件，split offsets 为 `[2L, 12L]`——末尾偏移 12 超过文件大小 10，模拟损坏。

   ```java
   DataFile dataFile =
       DataFiles.builder(TableTestBase.SPEC)
           .withPath("/path/to/data-a.parquet")
           .withFileSizeInBytes(10)
           .withPartitionPath("data_bucket=0")
           .withRecordCount(1)
           .withSplitOffsets(ImmutableList.of(2L, 12L))
           .build();
   ```

2. **构造扫描任务**：用 `BaseFileScanTask` 包装该文件，配置 `ResidualEvaluator`。

3. **规划并断言**：调用 `TableScanUtil.planTaskGroups(..., splitSize=1, lookback=1, openFileCost=0)`，遍历产出的 `ScanTaskGroup`，对每个子任务断言：
   - `taskDataFile.splitOffsets()` 为 null（损坏偏移被忽略）；
   - 任务总数等于 10（10 字节文件按 1 字节切分得 10 个任务），证明回退到了 `FixedSizeSplitScanTaskIterator` 而非使用损坏的偏移数组。

   ```java
   // 10 tasks since the split offsets are ignored and there are 1 byte splits for a 10 byte file
   Assertions.assertThat(taskCount).isEqualTo(10);
   ```

该测试直接复现了修复前的 bug 场景：若 `splitOffsetArray()` 仍返回损坏数组，规划会尝试按 `[2, 12]` 切分 10 字节文件，最后一个 split 长度为 `10 - 12 = -2`，产生非法任务；修复后则正常回退。

新增 import 包括 `BaseFileScanTask`、`DataFiles`、`PartitionSpecParser`、`SchemaParser`、`TableTestBase`、`Expressions`、`ResidualEvaluator` 等。

## 小结

本提交通过抽取统一的 `hasWellDefinedOffsets()` 谓词并让 `splitOffsetArray()` 共用，修复了扫描任务规划绕过损坏检测误用损坏 split offsets 的缺陷，是 Iceberg 在面对元数据损坏时保证扫描规划健壮性的重要修复。
