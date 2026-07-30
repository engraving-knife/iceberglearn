# 提交 0142：Spark 3.5: Fix rewriting manifests for evolved unpartitioned V1 tables (#9015)

## 提交信息

- **序号**：0142 / 4088
- **哈希**：942988744e96e8c133a7fcce267e93942ce556ec
- **短哈希**：942988744
- **日期**：2023-11-09 13:43:24 -0800
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.5: Fix rewriting manifests for evolved unpartitioned V1 tables (#9015)
- **PR/Issue**：#9015

## 总体目的

这个提交修复了 Spark 3.5 模块下 `RewriteManifestsSparkAction` 在处理"演化后的非分区 V1 表"（evolved unpartitioned V1 tables）时的一处判定 bug。Iceberg 的分区规格（`PartitionSpec`）在演化过程中可能发生字段移除：当 V1 表原先按某列分区，随后通过 `updateSpec().removeField(...)` 把分区字段移除后，`PartitionSpec` 仍保留一个 `fields()` 列表项，但该项的 `transform()` 是 `void`，因此表实际上变成"非分区表"（`isUnpartitioned()` 返回 `true`）。然而原代码用 `spec.fields().size() < 1` 来判断是否走非分区的重写路径，这种"演化后"的 spec 仍有 1 个 field（虽然 transform 为 void），于是会被误判为"分区表"，走 `writeManifestsForPartitionedTable` 分支。这会导致重写后的 manifest 不能正确反映该表已无有效分区的事实，可能产生错误的分区值处理或 manifest 写入异常。

修复方式是把判定条件从 `spec.fields().size() < 1` 改为 `spec.isUnpartitioned()`。`isUnpartitioned()` 会综合考虑 fields 的实际 transform 是否为 void，正确识别"演化后的非分区表"。这样所有真正的非分区表（无论是否经历过分区演化）都会走 `writeManifestsForUnpartitionedTable`，按文件路径聚类而非按分区聚类写入 manifest。

这个修复对 V1 表的 manifest 重写正确性很重要：V1 表在分区演化上比 V2 表有更多遗留约束，演化后字段以 void transform 保留是 V1 的特殊行为，原代码没有覆盖到这种"看似有字段实则非分区"的情况。

## 如何达成设计目的

整体设计非常聚焦：在生产代码 `RewriteManifestsSparkAction.java` 中仅修改一行判定条件；在测试代码 `TestRewriteManifestsAction.java` 中新增一个端到端测试用例 `testRewriteLargeManifestsEvolvedUnpartitionedV1Table`，构造"V1 表先按 `c3` 分区、再移除 `c3` 字段"的演化场景，验证重写后 manifest 被正确切分；同时把 `newDataFile` 辅助方法重构为可复用的 `newDataFileBuilder`，新增支持 `StructLike` 分区值的重载，以适配演化后分区值为 null 的写入需求。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

**修改目的**：修正非分区表的判定条件，使演化后的非分区 V1 表走正确的重写路径。

**工作逻辑**：在 `rewriteManifests` 流程中，根据 spec 选择写入策略的那一行：

```java
// 修改前
if (spec.fields().size() < 1) {
  newManifests = writeManifestsForUnpartitionedTable(manifestEntryDF, targetNumManifests);
} else {
  newManifests = writeManifestsForPartitionedTable(manifestEntryDF, targetNumManifests);
}
// 修改后
if (spec.isUnpartitioned()) {
  newManifests = writeManifestsForUnpartitionedTable(manifestEntryDF, targetNumManifests);
} else {
  newManifests = writeManifestsForPartitionedTable(manifestEntryDF, targetNumManifests);
}
```

`spec.fields().size() < 1` 只看字段数量，忽略了 V1 表分区演化后字段仍存在但 transform 为 void 的情况；`spec.isUnpartitioned()` 则正确返回 true 当且仅当所有字段都是 void transform（或字段数为 0）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`

**修改目的**：新增针对"演化后非分区 V1 表"的 manifest 重写测试，并重构数据文件构造辅助方法以支持 null 分区值。

**工作逻辑**：

1. **新增测试 `testRewriteLargeManifestsEvolvedUnpartitionedV1Table`**：
   - 用 `assumeThat(formatVersion).isEqualTo(1)` 限定为 V1 表测试。
   - 创建一个按 `c3` identity 分区的表。
   - 执行 `table.updateSpec().removeField("c3").commit()` 移除分区字段。
   - 断言 `table.spec().fields()` 仍有 1 个元素，但所有字段的 transform 都是 void（`allMatch(field -> field.transform().isVoid())`），即演化后的"非分区表"。
   - 用 `newDataFile(table, TestHelpers.Row.of(new Object[] {null}))` 构造 1000 个分区值为 null 的数据文件，写入一个 manifest 并 append 到表。
   - 把表的 `MANIFEST_TARGET_SIZE_BYTES` 设为原 manifest 长度的一半，强制重写时切分为多个 manifest。
   - 执行 `SparkActions.get().rewriteManifests(table).rewriteIf(manifest -> true)...execute()`。
   - 断言：重写了 1 个 manifest，新增了至少 2 个 manifest（因为目标大小减半），且新增 manifest 位置合法；重写后表当前快照有至少 2 个 manifest。这验证了演化后非分区表能正确走 `writeManifestsForUnpartitionedTable` 路径并按大小切分。

2. **重构 `newDataFile` 辅助方法**：原 `newDataFile(Table table, String partitionPath)` 方法直接构造并返回 `DataFile`。重构后抽出 `newDataFileBuilder(Table table)` 返回 `DataFiles.Builder`（不设分区、不 build），原 `newDataFile(Table, String partitionPath)` 改为调用 builder 再 `withPartitionPath` 后 build；新增 `newDataFile(Table table, StructLike partition)` 重载，调用 builder 再 `withPartition` 后 build。这样测试既可以用字符串分区路径（常规分区表），也可以用 `StructLike`（演化后分区值为 null 的场景，`TestHelpers.Row.of(new Object[] {null})`）。

3. **新增导入**：`StructLike`、`TestHelpers` 用于构造 null 分区值。

## 小结

本提交通过把 `RewriteManifestsSparkAction` 中判定非分区表的条件从 `spec.fields().size() < 1` 改为 `spec.isUnpartitioned()`，修复了 V1 表分区演化（移除字段）后 manifest 重写走错路径的 bug，确保演化后的非分区表正确按非分区方式重写 manifest。
