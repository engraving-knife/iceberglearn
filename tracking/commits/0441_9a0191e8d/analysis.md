# 提交 0441：Spark 3.4: Fix rewriting manifests for evolved unpartitioned V1 tables (#9599)

## 提交信息

- **序号**：0441
- **哈希**：9a0191e8de151864f27de00b30d6d1bae634a534
- **短哈希**：9a0191e8d
- **日期**：2024-02-01 09:36:42 -0800
- **作者**：Anton Okolnychyi <aokolnychyi@apple.com>
- **提交说明**：Spark 3.4: Fix rewriting manifests for evolved unpartitioned V1 tables (#9599)
- **PR/Issue**：#9599（backport #9015）

## 总体目的

本提交将上游 #9015 的修复 backport 到 Spark 3.4 模块，修复的是 `RewriteManifestsSparkAction` 在处理"演化后变为无分区的 V1 表"（evolved unpartitioned V1 tables）时错误地走分区表路径的 bug。

背景在于 V1 表的分区演化语义：V1 表不能真正删除分区字段，只能用 `void` transform 把字段标记为"已废弃"。也就是说，一个原本按 `c3` 分区的 V1 表，执行 `removeField("c3")` 之后，`spec.fields()` 仍然有一个字段，只是它的 `transform()` 是 `void`。从 Iceberg 内部语义看，这张表实际等价于无分区表——所有数据文件的分区值都是 `null`，写入和读取都按无分区处理。

原来的代码用 `spec.fields().size() < 1` 来判断是否走无分区表的写入路径。对于 V1 表演化掉分区字段的情况，`fields().size()` 仍然是 1，于是条件不成立，进入 `writeManifestsForPartitionedTable` 分支。这条分支会按分区字段对 manifest entry 做 redistribute，但分区字段是 void transform，Spark 侧拿到的分区值其实是 null， redistribute 的 key 也是 null，后续写入 manifest 时分区数据也是 null，会导致行为不符合预期（甚至可能抛错或产出语义错误的 manifest）。

修复的方法是把判断条件从"`spec.fields()` 为空"改成"`spec.isUnpartitioned()`"。`isUnpartitioned()` 的语义是当前没有"活跃的、非 void"分区字段，恰好覆盖了 V1 表演化掉分区字段这一情况，使这类表正确地走 `writeManifestsForUnpartitionedTable` 路径。

## 如何达成设计目的

实现上只改一行判断条件：`spec.fields().size() < 1` 替换为 `spec.isUnpartitioned()`。同时新增一个针对性的回归测试 `testRewriteLargeManifestsEvolvedUnpartitionedV1Table`：构造一个 V1 表，先按 `c3` 分区，再用 `updateSpec().removeField("c3")` 把它演化成"无分区"，写入 1000 个分区值为 null 的数据文件，然后调小 `MANIFEST_TARGET_SIZE_BYTES` 强制重写时拆分成多个 manifest，验证重写后 rewrittenManifests 为 1、addedManifests 至少为 2、且最终快照里 manifest 数也至少为 2。为了让测试可以构造 null 分区值的数据文件，还把原来 `newDataFile(Table, String partitionPath)` 拆成了 builder 形式，新增了 `newDataFile(Table, StructLike partition)` 重载，能够直接传 `TestHelpers.Row.of(new Object[] {null})` 作为分区值。

## 修改详情

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java

**修改目的**：修正无分区判断，使 V1 表演化掉分区字段后正确走无分区路径。

**工作逻辑**：在 `rewrite` 方法（约 179 行）里，原本根据 `spec.fields().size() < 1` 二选一：小于 1 走 `writeManifestsForUnpartitionedTable`，否则走 `writeManifestsForPartitionedTable`。改为 `spec.isUnpartitioned()`。`isUnpartitioned()` 内部判断的是 `fields().stream().noneMatch(f -> f.transform() != voidTransform && ...) `——只要没有"活跃非 void 分区字段"就返回 true。这样 V1 表分区字段被 `removeField` 演化成 void 之后也会被识别为无分区，走正确的 unpartitioned 写入路径。

### spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java

**修改目的**：新增针对"演化后无分区 V1 表"重写 manifest 的回归测试，并重构 `newDataFile` 工具方法以支持按 `StructLike` 构造分区为 null 的数据文件。

**工作逻辑**：

1. 新增 import：`StructLike`、`TestHelpers`，用于构造 `Row.of(new Object[] {null})` 形式的分区值。

2. 新增测试 `testRewriteLargeManifestsEvolvedUnpartitionedV1Table`：
   - 用 `assumeThat(formatVersion).isEqualTo(1)` 限定 V1 表（V2 表 removeField 行为不同，不在本测试范围）。
   - 建表时 `spec = identity("c3")`，启用 `SNAPSHOT_ID_INHERITANCE_ENABLED`，设置 `FORMAT_VERSION=1`。
   - `table.updateSpec().removeField("c3").commit()`，断言 `table.spec().fields()` 大小为 1 且 transform 全是 void，确认演化后的状态符合预期。
   - 生成 1000 个分区值为 `Row.of(null)` 的数据文件，写入一个 manifest 后通过 `newFastAppend().appendManifest(...)` 提交。
   - 取出当前快照唯一的 manifest，把 `MANIFEST_TARGET_SIZE_BYTES` 设为它长度的一半，强制重写时必须拆成至少 2 个新 manifest。
   - 调用 `SparkActions.get().rewriteManifests(table).rewriteIf(manifest -> true)...execute()`，断言 `rewrittenManifests` 大小为 1、`addedManifests` 大小 ≥ 2、并校验 manifest 位置；最后断言当前快照 manifest 数 ≥ 2。

3. 重构 `newDataFile`：
   - 原 `newDataFile(Table, String partitionPath)` 改为调用新的 builder：`return newDataFileBuilder(table).withPartitionPath(partitionPath).build();`
   - 新增 `newDataFile(Table, StructLike partition)`：`return newDataFileBuilder(table).withPartition(partition).build();`
   - 抽出 `newDataFileBuilder(Table)`：构造 `DataFiles.builder(table.spec())`，设置 path、fileSizeInBytes=10、recordCount=1，但不设置分区也不 build，留给上层用 `withPartitionPath` 或 `withPartition` 完成。这样既保持原 API 行为，又支持以 `StructLike` 形式指定 null 分区。

## 小结

这是一个一行核心修复 + 一个回归测试的 backport 提交。根因是 V1 表分区演化只把字段 transform 改成 void 而不删除字段，导致旧的 `fields().size() < 1` 判断失效；改用 `spec.isUnpartitioned()` 后，"分区字段全部为 void"的表也被正确识别为无分区表。测试侧通过把 `newDataFile` 重构成 builder 形式，覆盖了"分区值为 null"这一原本难以构造的场景。
