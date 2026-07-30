# 提交 3888：Core: extract parallel manifest write logic out of SnapshotProducer (#16730)

## 提交信息

- **序号**：3888 / 4088
- **哈希**：f5b206167d2ff174cd38f2ec21c5336d333b6fb1
- **短哈希**：f5b206167
- **日期**：2026-06-15 20:41:50 -0700
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core: extract parallel manifest write logic out of SnapshotProducer (#16730)
- **PR/Issue**：#16730

## 总体目的

将 `SnapshotProducer` 中的并行 manifest 写入逻辑提取到 `ManifestFiles` 工具类中。这是一个重构提交，目的是改善代码组织和可复用性。

`SnapshotProducer` 是 Iceberg 核心中负责生成快照的抽象基类，包含了数据文件和删除文件的 manifest 写入逻辑。其中并行写入逻辑（将文件分组后并行写入多个 manifest）是一个通用的能力，原本内嵌在 `SnapshotProducer` 的私有方法中，无法被其他需要类似功能的代码复用。

通过将这部分逻辑提取到 `ManifestFiles` 工具类，使其成为可复用的静态方法，降低了 `SnapshotProducer` 的职责负担，并为未来其他场景（如并行 manifest 重写）复用该逻辑奠定基础。

## 如何达成设计目的

将 `SnapshotProducer` 中的 `writeManifests` 私有方法和 `divide` 私有静态方法移动到 `ManifestFiles` 类中，重命名为 `writeParallel`（包级可见）和 `divide`（私有）。`SnapshotProducer` 的 `writeDataManifests` 和 `writeDeleteManifests` 方法改为调用 `ManifestFiles.writeParallel`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestFiles.java` (+60/-0 lines)

**修改目的**：新增并行 manifest 写入能力。

**工作逻辑**：
新增 `writeParallel` 静态方法（包级可见）和 `divide` 私有辅助方法：

```java
static <F> List<ManifestFile> writeParallel(
    Collection<F> files,
    int parallelism,
    ExecutorService writePool,
    Function<List<F>, List<ManifestFile>> writeFunc) {
  List<List<F>> groups = divide(files, parallelism);

  // Pair each group with their index so results can be reassembled in input order.
  List<Pair<Integer, List<F>>> groupsWithIndex = Lists.newArrayList();
  for (int i = 0; i < groups.size(); i++) {
    groupsWithIndex.add(Pair.of(i, groups.get(i)));
  }

  AtomicReferenceArray<List<ManifestFile>> results = new AtomicReferenceArray<>(groups.size());

  Tasks.foreach(groupsWithIndex)
      .stopOnFailure()
      .throwFailureWhenFinished()
      .executeWith(writePool)
      .run(
          indexedGroup -> {
            int index = indexedGroup.first();
            List<F> group = indexedGroup.second();
            results.set(index, writeFunc.apply(group));
          });

  ImmutableList.Builder<ManifestFile> builder = ImmutableList.builder();
  for (int i = 0; i < results.length(); i++) {
    builder.addAll(results.get(i));
  }
  return builder.build();
}

private static <T> List<List<T>> divide(Collection<T> collection, int groupCount) {
  List<T> list = Lists.newArrayList(collection);
  int groupSize = IntMath.divide(list.size(), groupCount, RoundingMode.CEILING);
  return Lists.partition(list, groupSize);
}
```

工作流程：
1. `divide` 方法将文件集合按 `groupCount` 向上取整分成若干组
2. 为每组配对索引，使用 `AtomicReferenceArray` 保证线程安全的结果收集
3. 通过 `Tasks` 工具并行执行各组写入，`stopOnFailure` 确保失败时停止
4. 最终按原始顺序收集所有 manifest 结果

同时新增了相关 import（`RoundingMode`、`Collection`、`List`、`ExecutorService`、`AtomicReferenceArray`、`Function`、`Lists`、`IntMath`、`Pair`、`Tasks`）。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (+7/-46 lines)

**修改目的**：移除内嵌的并行写入逻辑，改为委托给 `ManifestFiles.writeParallel`。

**工作逻辑**：
1. `writeDataManifests` 方法改为：
```java
protected List<ManifestFile> writeDataManifests(
    Collection<DataFile> files, Long dataSeq, PartitionSpec spec) {
  int groupCount = manifestWriterCount(writePoolParallelism, files.size());
  return ManifestFiles.writeParallel(
      files, groupCount, writePool(), group -> writeDataFileGroup(group, dataSeq, spec));
}
```

2. `writeDeleteManifests` 方法改为：
```java
protected List<ManifestFile> writeDeleteManifests(
    Collection<DeleteFile> files, PartitionSpec spec) {
  int groupCount = manifestWriterCount(writePoolParallelism, files.size());
  return ManifestFiles.writeParallel(
      files, groupCount, writePool(), group -> writeDeleteFileGroup(group, spec));
}
```

3. 删除了原有的 `writeManifests` 私有方法和 `divide` 私有静态方法
4. 移除了不再需要的 import（`AtomicReferenceArray`、`Function`、`ImmutableList`、`Pair`）
5. 修正了 javadoc 中的笔误："can be used to concurrently" -> "can be used concurrently"

## 总结

将 `SnapshotProducer` 中的并行 manifest 写入逻辑提取到 `ManifestFiles` 工具类，命名为 `writeParallel`。这是一个改善代码组织的重构提交，降低了 `SnapshotProducer` 的复杂度，使并行写入能力可被复用，同时保持原有行为不变。提取后的方法通过函数式接口接收写入逻辑，实现了通用性与类型安全性的平衡。
