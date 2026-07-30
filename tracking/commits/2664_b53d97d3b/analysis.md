# 提交 2664：[Core] Add mergeAppendTest to ensure consist distribution of data files in manifests (#14111)

## 提交信息

- **序号**：2664 / 4088
- **哈希**：b53d97d3bf14f0c334e5af488959ba01d2c6e53e
- **短哈希**：b53d97d3b
- **日期**：2025-09-19 14:10:43 -0700
- **作者**：Hongyue/Steve Zhang
- **提交说明**：[Core] Add mergeAppendTest to ensure consist distribution of data files in manifests (#14111)
- **PR/Issue**：#14111

## 总体目的

本提交新增了一个测试 `testAddManyFilesWithConsistentOrdering`，用于验证 PR #13411 中实现的"文件在 manifest 列表中保持传入顺序"的保证。PR #13411 修改了 Iceberg 的 merge append 逻辑，使得当追加大量数据文件时，文件在 manifest 中的分布和顺序与传入顺序一致——即文件按照传入顺序依次填入各 manifest，每个 manifest 填满 `MIN_FILE_GROUP_SIZE` 个文件后才开始下一个。

这个测试的目的是确保这一排序保证不会在未来的代码变更中被意外破坏。测试通过追加 `multiplier * MIN_FILE_GROUP_SIZE` 个文件（本测试中 multiplier=3），然后验证生成的多个 manifest 中文件的分布与传入顺序完全一致。

## 如何达成设计目的

通过在 `TestMergeAppend` 中新增测试方法，并在 `TestBase` 中添加辅助方法来简化重复值的迭代器创建：

1. **TestBase 辅助方法**：添加 `statusesRepeat`、`dataSeqsRepeat`、`fileSeqsRepeat`、`idsRepeat` 四个方法，用于创建重复相同值指定次数的迭代器，简化测试中验证 manifest 内容的代码。
2. **测试方法**：创建 `testAddManyFilesWithConsistentOrdering`，追加 3 倍 `MIN_FILE_GROUP_SIZE` 个文件，验证生成的 3 个 manifest 分别包含按顺序的第 1/3、2/3、3/3 的文件。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestBase.java` (+17/-0 lines)

**修改目的**：添加用于创建重复值迭代器的辅助方法。

**工作逻辑**：新增四个静态辅助方法，每个方法使用 `Iterators.limit(Iterators.cycle(Collections.singletonList(value)), count)` 模式创建一个重复指定值 `count` 次的迭代器：
- `statusesRepeat(ManifestEntry.Status status, int count)`：重复 manifest 条目状态
- `dataSeqsRepeat(Long value, int count)`：重复数据序列号
- `fileSeqsRepeat(Long value, int count)`：重复文件序列号
- `idsRepeat(Long value, int count)`：重复快照 ID

同时导入了 `java.util.Collections`。

### `core/src/test/java/org/apache/iceberg/TestMergeAppend.java` (+50/-0 lines)

**修改目的**：新增测试验证大量文件追加时 manifest 中的顺序一致性。

**工作逻辑**：
- 测试 `testAddManyFilesWithConsistentOrdering`：
  - 设置 `multiplier = 3`，获取 `SnapshotProducer.MIN_FILE_GROUP_SIZE` 作为每个 manifest 的文件组大小
  - 生成 `multiplier * groupSize` 个数据文件，交替分配到两个分区（`ordinal % 2`）
  - 通过 `table.newAppend()` 追加所有文件并提交
  - 验证生成的 manifest 数量为 `multiplier`（即 3 个）
  - 对每个 manifest 调用 `validateManifest` 验证：
    - 第 1 个 manifest 包含第 0 到 groupSize-1 的文件
    - 第 2 个 manifest 包含第 groupSize 到 groupSize*2-1 的文件
    - 第 3 个 manifest 包含第 groupSize*2 到 groupSize*3-1 的文件
  - 每个 manifest 中的所有条目具有相同的数据序列号（1L）、文件序列号（1L）、快照 ID 和状态（ADDED），且文件顺序与传入顺序一致

## 总结

本提交新增了一个回归测试，用于确保大量文件追加时数据文件在 manifest 中的分布和顺序与传入顺序一致。这一保证由 PR #13411 实现。测试通过追加 3 倍最小文件组大小的文件，验证 3 个 manifest 分别按顺序包含各三分之一的文件。同时在 TestBase 中添加了简化重复值迭代器创建的辅助方法。这是一个纯测试提交，不涉及生产代码变更。
