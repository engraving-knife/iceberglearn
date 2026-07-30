# 提交 0950：Core: Support appending files with different specs (#9860)

## 提交信息

- **序号**：0950 / 4088
- **哈希**：5f970a839674f68a4b2f07cdca012ab4a15566c0
- **短哈希**：5f970a839
- **日期**：2024-07-18 19:27:06 -0400
- **作者**：Farooq Qaiser <fqaiser94@gmail.com>
- **提交说明**：Core: Support appending files with different specs (#9860)
- **PR/Issue**：#9860

## 总体目的

Iceberg core 模块的 `MergingSnapshotProducer` 是 `MergeAppend`、`OverwriteData`、`RowDelta` 等写入操作的基础抽象类，负责在生成新快照时收集新增数据文件、写出数据 manifest 并提交。在该提交之前，单次写入操作内部用一个标量字段 `dataSpec` 记录"本次写入所用的分区规格"，并通过 `setDataSpec(DataFile)` 强制约束：一旦第一个文件确定了 `dataSpec`，后续追加的任何文件必须属于同一个 `specId`，否则抛出 `ValidationException("Invalid data file, expected spec id: %d")`。

这意味着**一次 append/merge 操作只能包含来自同一个分区规格的数据文件**。然而在实际场景中，用户可能在一次批处理中同时写入按旧 spec 分区的文件与按新 spec 分区的文件（例如表刚做过 `updateSpec` 演进分区策略，新旧分区的文件同时产出），希望一次性提交。旧约束会强制用户拆成多次提交，既低效又破坏原子性。

本提交（PR #9860）移除"单 spec"约束，让 `MergingSnapshotProducer` 内部按 `PartitionSpec` 分组保存新增数据文件，并在写出 manifest 时**为每个 spec 分别生成一个 manifest**（因为一个 manifest 在 Iceberg 协议上只属于一个分区规格）。这样一次写入即可包含多个 spec 的文件，每个 spec 的文件落到各自的 manifest 中，符合 Iceberg 表元数据对 manifest 与 spec 一一对应的要求。

## 如何达成设计目的

整体设计思路是把"单 spec 假设"换成"多 spec 容器"，并把写出逻辑从"一个 writer 写所有文件"改成"按 spec 分组、每组一个 writer"。

具体做法：
1. **数据结构替换**：将 `List<DataFile> newDataFiles` 替换为 `Map<PartitionSpec, List<DataFile>> newDataFilesBySpec`，按 spec 分组保存新增文件；移除标量 `dataSpec` 字段与 `setDataSpec()` 方法。
2. **`add(DataFile)` 改造**：通过 `ops.current().spec(file.specId())` 查找文件所属 spec，校验 spec 存在后，用 `computeIfAbsent` 把文件加入对应 spec 的列表。`addedFilesSummary` 也按文件自身的 spec 累计。
3. **查询入口分裂**：保留 `dataSpec()` 但改为"仅当恰好只有一个 spec 时返回该 spec，否则抛 `IllegalStateException`"，以保持对单 spec 调用方的语义；新增 `dataSpecs()` 返回所有 spec 集合；新增 `addedDataFilesBySpec()` 暴露分组视图；`addedDataFiles()` 改为把分组扁平化。
4. **manifest 写出按 spec 循环**：`newDataFilesAsManifests()` 遍历 `newDataFilesBySpec` 的每个 entry，对每个 spec 创建独立的 `RollingManifestWriter`，把该 spec 下的文件写入，再将产出的 manifest 累加到缓存列表。
5. **缓存字段可变而非置空**：`cachedNewDataManifests` 由"可空引用、用 `null` 表示空"改为 `LinkedList`，用 `isEmpty()`/`clear()` 管理，简化空值判断与清理逻辑。

测试侧在 `TestMergeAppend` 中新增两个用例：一个验证一次 append 同时追加两个不同 spec 的文件会产生两个 manifest（每个 spec 一个），且各 manifest 内文件正确；另一个验证当存在多个 spec 时调用 `dataSpec()` 会抛出带特定消息的 `IllegalStateException`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`

**修改目的**：解除单 spec 约束，使一次写入可包含多个分区规格的数据文件，并按 spec 分别写出 manifest。

**工作逻辑**：

- **字段与导入**：新增 `import java.util.stream.Collectors;` 与 `import ... ImmutableMap;`。把 `private final List<DataFile> newDataFiles = Lists.newArrayList();` 改为 `private final Map<PartitionSpec, List<DataFile>> newDataFilesBySpec = Maps.newHashMap();`；删除 `private PartitionSpec dataSpec;` 字段及构造函数中的 `this.dataSpec = null;`；把 `cachedNewDataManifests` 从 `private List<ManifestFile> cachedNewDataManifests = null;` 改为 `private final List<ManifestFile> cachedNewDataManifests = Lists.newLinkedList();`。

- **`dataSpec()` / 新增 `dataSpecs()`**：
  ```java
  protected PartitionSpec dataSpec() {
    Set<PartitionSpec> specs = dataSpecs();
    Preconditions.checkState(
        specs.size() == 1,
        "Cannot return a single partition spec: data files with different partition specs have been added");
    return specs.iterator().next();
  }

  protected Set<PartitionSpec> dataSpecs() {
    Set<PartitionSpec> specs = newDataFilesBySpec.keySet();
    Preconditions.checkState(
        !specs.isEmpty(), "Cannot determine partition specs: no data files have been added");
    return ImmutableSet.copyOf(specs);
  }
  ```
  `dataSpec()` 保持对单 spec 调用方兼容，但多 spec 时显式抛异常而非默默返回其中一个；`dataSpecs()` 是新的多 spec 入口。

- **`addedDataFiles()` 与新增 `addedDataFilesBySpec()`**：
  ```java
  protected List<DataFile> addedDataFiles() {
    return ImmutableList.copyOf(
        newDataFilesBySpec.values().stream().flatMap(List::stream).collect(Collectors.toList()));
  }

  protected Map<PartitionSpec, List<DataFile>> addedDataFilesBySpec() {
    return ImmutableMap.copyOf(newDataFilesBySpec);
  }
  ```

- **`addsDataFiles()`**：判断条件由 `!newDataFiles.isEmpty()` 改为 `!newDataFilesBySpec.isEmpty()`。

- **`add(DataFile)`**：原 `setDataSpec(file)` 调用替换为按 specId 查找 spec、校验非空、`computeIfAbsent` 分组追加：
  ```java
  PartitionSpec fileSpec = ops.current().spec(file.specId());
  Preconditions.checkArgument(
      fileSpec != null,
      "Cannot find partition spec %s for data file: %s",
      file.specId(),
      file.path());
  addedFilesSummary.addedFile(fileSpec, file);
  hasNewDataFiles = true;
  List<DataFile> newDataFiles =
      newDataFilesBySpec.computeIfAbsent(fileSpec, ignored -> Lists.newArrayList());
  newDataFiles.add(file);
  ```

- **删除 `setDataSpec(DataFile)`**：原方法在 `dataSpec == null` 时设置、在 specId 不一致时抛 `ValidationException` 的逻辑整段移除，由上面的分组逻辑取代。

- **`cleanUncommittedAppends` / `prepareNewDataManifests` / `newDataFilesAsManifests`**：把对 `cachedNewDataManifests` 的 `!= null` 判断统一改为 `!isEmpty()`，把 `= null` 赋值改为 `.clear()`；`prepareNewDataManifests` 中 `!newDataFiles.isEmpty()` 改为 `!newDataFilesBySpec.isEmpty()`。

- **`newDataFilesAsManifests()` 核心**：把原来"创建一个 writer 写所有文件"改为遍历 `newDataFilesBySpec`、每个 spec 创建独立 writer：
  ```java
  if (cachedNewDataManifests.isEmpty()) {
    newDataFilesBySpec.forEach(
        (dataSpec, newDataFiles) -> {
          try {
            RollingManifestWriter<DataFile> writer = newRollingManifestWriter(dataSpec);
            try {
              if (newDataFilesDataSequenceNumber == null) {
                newDataFiles.forEach(writer::add);
              } else {
                newDataFiles.forEach(f -> writer.add(f, newDataFilesDataSequenceNumber));
              }
            } finally {
              writer.close();
            }
            this.cachedNewDataManifests.addAll(writer.toManifestFiles());
            this.hasNewDataFiles = false;
          } catch (IOException e) {
            throw new RuntimeIOException(e, "Failed to close manifest writer");
          }
        });
  }
  ```
  每个 spec 的文件被写入各自的滚动 manifest writer，产出的多个 manifest 累加到 `cachedNewDataManifests`，最终随快照提交。manifest 的 `partitionSpecId` 自然对应该 spec，下游读取与计划任务能正确按 spec 解析。

### `core/src/test/java/org/apache/iceberg/TestMergeAppend.java`

**修改目的**：验证多 spec 追加的正确性，以及 `dataSpec()` 在多 spec 场景下的失败行为。

**工作逻辑**：新增两个 `@TestTemplate` 用例：

- `testEmptyTableAppendFilesWithDifferentSpecs`：先对表 `updateSpec().addField("id").commit()` 演进出一个新 spec，使表拥有两个 spec；构造一个新 spec 下的文件 `fileNewSpec`（分区路径 `data_bucket=0/id=0`），与原 spec 下的 `FILE_A` 一起通过 `table.newAppend().appendFile(FILE_A).appendFile(fileNewSpec)` 提交。断言：产生 2 个 manifest（每个 spec 一个），按 `partitionSpecId` 过滤后分别验证每个 manifest 中包含对应 spec 的那个文件，且数据/文件序列号、snapshotId、`Status.ADDED` 均符合预期。V1/V2 分别校验 lastSequenceNumber。

- `testDataSpecThrowsExceptionIfDataFilesWithDifferentSpecsAreAdded`：构造同样的多 spec 追加场景，但调用 `((MergeAppend) table.newAppend().appendFile(FILE_A).appendFile(fileNewSpec)).dataSpec()`，断言抛出 `IllegalStateException` 且消息为 `"Cannot return a single partition spec: data files with different partition specs have been added"`，验证 `dataSpec()` 在多 spec 时的显式失败语义。

测试还引入了 `import java.util.Objects;` 与 `import ... ImmutableMap;` 用于按 specId 映射预期文件并做断言。

## 小结

- **成效**：解除了 `MergingSnapshotProducer` 单次写入只能包含单一分区规格文件的限制，使一次 append/merge/delta 操作可同时提交多个 spec 的文件，每个 spec 的文件落到独立 manifest；同时保留了对单 spec 调用方的兼容（`dataSpec()` 在多 spec 时显式报错而非静默返回）。
- **影响范围**：核心 `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`（约 102 行变更）及其测试 `core/src/test/java/org/apache/iceberg/TestMergeAppend.java`（新增 88 行）。该改动影响所有继承 `MergingSnapshotProducer` 的写入路径（`MergeAppend`、`OverwriteData`、`RowDelta` 等），以及任何调用 `dataSpec()` / `addedDataFiles()` 的下游子类。
- **回迁到 1.4.x 的注意事项**：这是一次功能性增强，回迁价值较高，但需谨慎评估：
  1. **行为兼容性**：原先会被 `setDataSpec()` 以 `ValidationException` 拒绝的"多 spec 同提交"输入，回迁后会变为成功提交——这是有意的语义放宽，但若 1.4.x 下游有依赖该异常做分支处理的代码需一并调整；
  2. **`dataSpec()` 抛异常类型变化**：单 spec 场景行为不变，但多 spec 场景由原先在 `add()` 阶段抛 `ValidationException` 改为在 `dataSpec()` 调用时抛 `IllegalStateException`，调用时机与异常类型均变化，需确认 1.4.x 子类无人在 `add()` 后立即捕获 `ValidationException`；
  3. **manifest 数量变化**：原本一次写入多 spec 文件会失败，现在会成功并产生多个 manifest，下游读取与统计逻辑需能处理多 manifest 情形（Iceberg 本就支持，风险低）；
  4. 建议回迁时一并回迁两个测试用例，确保 1.4.x 上多 spec 行为与 main 一致；同时检查 1.4.x 上 `MergingSnapshotProducer` 与 main 的其他差异（例如后续是否还有相关重构），避免 cherry-pick 冲突。
