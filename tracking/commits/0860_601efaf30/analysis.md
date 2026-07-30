# 提交 0860：Flink: Fix the condition of `formatVersion` for skipping test cases (#10541)

## 提交信息
- **序号**：0860 / 4088
- **哈希**：601efaf309712eca21228f8949813133606a42b8
- **短哈希**：601efaf30
- **日期**：2024-06-20
- **作者**：Qishang Zhong <zhongqishang@gmail.com>
- **提交说明**：Flink: Fix the condition of `formatVersion` for skipping test cases (#10541)
- **PR/Issue**：#10541

## 总体目的

Iceberg 表格式分为 v1 和 v2 两代，v2 才引入了 equality-delete（等值删除）和 position-delete（位置删除）这两类 delete files。Flink sink 的 `TestIcebergFilesCommitter` 里有两个测试用例——`testDeleteFiles` 和 `testCommitTwoCheckpointsInSingleTxn`——专门验证 equality-delete 路径，因此只应在 `formatVersion >= 2` 的表上跑。这两个测试用 AssertJ 的 `assumeThat(formatVersion)` 机制跳过不满足条件的场景（被假设跳过不会算失败）。

但原有的跳过条件写错了，写成了：

```java
assumeThat(formatVersion)
    .as("Only support equality-delete in format v2 or later.")
    .isGreaterThan(2);
```

`isGreaterThan(2)` 的语义是「formatVersion 必须大于 2 才不跳过」，意味着只有 formatVersion = 3 或更高才执行——而 Iceberg 当前根本没有 v3，最常用的就是 v2。结果就是：这两个本应在 v2 表上跑的 equality-delete 测试，在 v2 表上反而被跳过了，造成 v2 路径在 Flink sink 端长期缺乏测试覆盖。本提交把条件改成 `isGreaterThan(1)`，让 v2 及以上版本的表都能正常执行这两个测试，恢复应有的测试覆盖。

## 如何达成设计目的

修复非常直接：把 `assumeThat(formatVersion).isGreaterThan(2)` 改成 `assumeThat(formatVersion).isGreaterThan(1)`。

逻辑修正依据：
- formatVersion = 1 → v1，不支持 delete files → 应跳过 → `1 > 1` 为 false，正确跳过。
- formatVersion = 2 → v2，支持 equality-delete → 应执行 → `2 > 1` 为 true，正确执行。
- formatVersion >= 3（未来） → 也应执行 → `n > 1` 为 true，正确执行。

修复后 v2 表（生产环境最主流的版本）会被纳入这两个 equality-delete 测试，覆盖 Flink sink 在 checkpoint 提交时处理 delete files 的关键路径。

修改同时落到 `flink/v1.17`、`flink/v1.18`、`flink/v1.19` 三个 Flink 版本目录下完全相同路径的 `TestIcebergFilesCommitter.java`——Iceberg 用版本目录机制为每个支持的 Flink 大版本维护一份代码副本，三个目录的测试文件结构完全一致，因此同一处修复需要在三个目录里同步落地。

## 修改详情

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergFilesCommitter.java`
**修改目的**：修正 `testDeleteFiles` 与 `testCommitTwoCheckpointsInSingleTxn` 两个测试用例的 `formatVersion` 跳过条件，让 v2 表能正常执行。
**工作逻辑**：将两处 `assumeThat(formatVersion).as("Only support equality-delete in format v2 or later.").isGreaterThan(2)` 中的 `isGreaterThan(2)` 改为 `isGreaterThan(1)`，使条件从「大于 2」修正为「大于 1」，从而让 formatVersion = 2 的表也满足条件、不再被跳过。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergFilesCommitter.java`
**修改目的**：与 v1.17 相同，修正两个测试用例的 `formatVersion` 跳过条件。
**工作逻辑**：与 v1.17 完全一致——同样两处 `isGreaterThan(2)` 改为 `isGreaterThan(1)`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergFilesCommitter.java`
**修改目的**：与 v1.17、v1.18 相同，修正两个测试用例的 `formatVersion` 跳过条件。
**工作逻辑**：与 v1.17、v1.18 完全一致——同样两处 `isGreaterThan(2)` 改为 `isGreaterThan(1)`。

## 小结
- **成效**：修正了 `TestIcebergFilesCommitter` 中 `testDeleteFiles` 与 `testCommitTwoCheckpointsInSingleTxn` 的跳过条件错误，让 v2 表（最主流版本）上的 equality-delete 测试真正被执行而不是被跳过，恢复 Flink sink 在 delete files 提交路径上的应有测试覆盖。这是一次「沉睡测试被唤醒」式的修复——既有测试早就写好，但因条件写错从未在 v2 上真正跑过。
- **影响范围**：仅影响 Flink v1.17、v1.18、v1.19 三个版本目录下的测试代码，不改动任何生产源代码或运行时行为。修复后 CI 在 v2 表上会真正执行这两个测试用例，有可能暴露之前被掩盖的潜在 bug（需要密切观察首批 CI 结果）。
- **回迁注意事项**：1.4.x 分支可直接 cherry-pick，三个 Flink 版本目录需同步修改。回迁后需重点关注：(a) 1.4.x 分支下 Flink 版本目录是否仍是 v1.17/v1.18/v1.19，如果版本不同（例如只有更老或更新的 Flink 版本），需要按相同模式在对应版本目录里同步修复；(b) 修复后首批 CI 跑这两个测试用例时是否真的能通过——之前从未在 v2 上跑过，可能潜藏 bug，跑挂了需要继续修；(c) 同时建议全局排查 1.4.x 分支下是否还有其它 `assumeThat(formatVersion).isGreaterThan(2)` 或类似写错的格式版本判断条件（例如 `isGreaterThanOrEqualTo(3)` 等），一并修正。
