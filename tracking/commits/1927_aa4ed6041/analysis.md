# 提交 1927：Core: Enhance TestRemoveSnapshots (#12662)

## 提交信息

- **序号**：1927 / 4088
- **哈希**：aa4ed6041b2e4555e222eb587d5055be163f362c
- **短哈希**：aa4ed6041
- **日期**：2025-03-27 13:02:06 +0100
- **作者**：Manu Zhang
- **提交说明**：Core: Enhance TestRemoveSnapshots (#12662)
- **PR/Issue**：#12662

## 总体目的

`TestRemoveSnapshots` 是 Iceberg Core 模块测试快照过期（expire snapshots）逻辑的核心测试类。该类中大量测试用例需要在连续多次 append 提交之间保证"后一次提交的时间戳严格大于前一次"，以便后续用 `expireOlderThan(timestamp)` 精确控制过期边界。原代码在几乎每个提交后都用同一段手写的忙等循环来等待时间推进：

```java
long t1 = System.currentTimeMillis();
while (t1 <= table.currentSnapshot().timestampMillis()) {
  t1 = System.currentTimeMillis();
}
```

这段代码在十几个测试方法中重复出现，既冗长又易错。本提交对测试做清理增强：

1. 用既有的工具方法 `waitUntilAfter(long timestampMillis)` 替换所有重复的忙等循环，该方法会阻塞直到当前时间严格晚于给定时间戳，并返回新的时间值，调用更简洁、语义更清晰。
2. 删除未使用的变量（如多处 `long t0 = ...`、未被引用的 `firstSnapshotId`）。
3. 删除冗余的 `.toString()` 调用（`FILE_A.location()` 已返回 `String`，无需再 `toString()`）。

这是一次纯测试代码质量改进，不改变被测产品代码，也不改变测试覆盖的语义。

## 如何达成设计目的

在 `TestRemoveSnapshots.java` 中：
- 把每个 `long tN = System.currentTimeMillis(); while (tN <= ...timestampMillis()) { tN = System.currentTimeMillis(); }` 替换为 `long tN = waitUntilAfter(table.currentSnapshot().timestampMillis());`（若该 `tN` 后续未被使用，则直接 `waitUntilAfter(...);` 不赋值）。
- 删除从未读取的局部变量声明。
- 把 `assertThat(deletedFiles).contains(FILE_A.location().toString())` 改为 `.contains(FILE_A.location())`，去掉多余的 `toString()`。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java` (修改, +38/-133 lines)

**修改目的**：消除重复忙等循环、清理无用变量与冗余 toString。

**工作逻辑**：

涉及约 10+ 个测试方法（`testRetainLastWithExpireOlderThan`、`testRetainLastWithExpireById`、`testRetainNAvailableSnapshotsWithTransaction`、`testRetainLastWithTooFewSnapshots`、`testRetainLastWithTooFewSnapshotsAndTransaction`、`testRetainLastKeepsExpiringSnapshot`、`testExpireOlderThanMultipleCalls`、`testRetainLastMultipleCalls`、`testExpireOlderThanWithDeleteMultipleSpec`、`testRetainLastWithMultipleSpecs`、`testRewriteExpiry` 等），统一替换：

```java
// before
long t3 = System.currentTimeMillis();
while (t3 <= table.currentSnapshot().timestampMillis()) {
  t3 = System.currentTimeMillis();
}
// after
long t3 = waitUntilAfter(table.currentSnapshot().timestampMillis());
```

对于后续未使用的 `tN`（如 `t1`、`t2` 仅用于等待），改为：

```java
waitUntilAfter(table.currentSnapshot().timestampMillis());
```

删除未使用变量：`long t0 = System.currentTimeMillis();`（多处，从未读取）、部分未被引用的 `firstSnapshotId`。

冗余 `toString()` 清理：

```java
// before
assertThat(deletedFiles).contains(FILE_A.location().toString());
// after
assertThat(deletedFiles).contains(FILE_A.location());
```

`FileScanTask.location()` / `DataFile.location()` 返回类型本就是 `String`，无需再调 `toString()`。

## 总结

本提交对 `TestRemoveSnapshots` 测试类做代码清理：用工具方法 `waitUntilAfter` 替代散落各处的手写忙等循环，删除未使用变量与冗余 `toString()`，使测试代码更简洁、可读性更好、更易维护，不改变测试语义与被测产品代码。
