# 提交 1279：Core: Track data files by spec id instead of full PartitionSpec (#11323)

## 提交信息

- **序号**：1279 / 4088
- **哈希**：6b04a6d000019a6182eb6521d1e7e4124a0cd73b
- **短哈希**：6b04a6d00
- **日期**：2024-10-25（Fri Oct 25 16:25:23 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Track data files by spec id instead of full PartitionSpec (#11323)
- **PR/Issue**：#11323

## 总体目的

`MergingSnapshotProducer` 是 Iceberg core 中"合并型快照更新"（Append/Overwrite/Delete/ReplacePartitions）的核心基类，负责在 commit 时把新增的数据文件按其所属 `PartitionSpec` 分组、写入 manifest。它内部维护一个映射：

```java
private final Map<PartitionSpec, DataFileSet> newDataFilesBySpec = Maps.newHashMap();
```

以完整的 `PartitionSpec` 对象作为 Map 的 key。这带来两个隐患：

1. **HashMap key 语义风险**：`PartitionSpec` 的 `equals/hashCode` 是否按值比较、是否与"同一逻辑 spec"一致，会直接影响分组正确性。一旦 `PartitionSpec` 实现细节变化或不同实例不 `equals`，就可能出现"同一 spec 被分到多个桶"或"取回时 key 不匹配"的微妙 bug；
2. **冗余对象引用**：Map 实际只需要区分不同的 spec（按 `specId` 即可），但持有完整 `PartitionSpec` 对象引用既没必要也容易让代码产生"依赖 key 完整性"的隐式假设。

本提交将这个 Map 的 key 从 `PartitionSpec` 改为 `Integer`（spec id），用 spec id 作为唯一标识。同时顺手清理了 `dataSpec()` / `dataSpecs()` 两个方法的边界：原来的 `dataSpecs()` 方法（返回 `Set<PartitionSpec>`）不再有外部调用方，被合并/删除；`dataSpec()` 改为基于 spec id 集合做单元素校验后再通过 `spec(specId)` 反查 `PartitionSpec`。

## 如何达成设计目的

通过 4 处局部改动达成：

1. 字段类型从 `Map<PartitionSpec, DataFileSet>` 改为 `Map<Integer, DataFileSet>`，与已有的 `newDeleteFilesBySpec: Map<Integer, DeleteFileSet>` 保持一致风格（删除文件那边早已按 spec id 索引）；
2. `dataSpec()` 方法重写：原 `dataSpec()` 委托给 `dataSpecs()` 取 `Set<PartitionSpec>` 再校验 size==1；新版直接用 `newDataFilesBySpec.keySet()`（即 `Set<Integer>`）做"非空 + 单元素"校验，最后通过 `spec(int specId)`（私有方法，转发到 `ops.current().spec(specId)`）反查 `PartitionSpec` 返回；
3. 删除冗余的 `dataSpecs()` 方法（其语义已被 `dataSpec()` 内联）；
4. 写入数据文件与写 manifest 两处对 Map 的访问从 `spec` 改为 `spec.specId()`，写 manifest 时再用 `spec(specId)` 还原 `PartitionSpec` 供 `writeDataManifests` 使用。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`（修改，+9 -14 行）

**修改目的**：把 `newDataFilesBySpec` 的 key 从 `PartitionSpec` 改为 `Integer`（spec id），消除以完整对象作为 HashMap key 的风险，并与 `newDeleteFilesBySpec` 风格对齐。

**工作逻辑**（按 diff 顺序）：

1. **字段声明**：
   ```java
   // 旧
   private final Map<PartitionSpec, DataFileSet> newDataFilesBySpec = Maps.newHashMap();
   // 新
   private final Map<Integer, DataFileSet> newDataFilesBySpec = Maps.newHashMap();
   ```
   key 类型由 `PartitionSpec` 改为 `Integer`。

2. **`dataSpec()` 与 `dataSpecs()` 合并重写**：
   - 旧版：
     ```java
     protected PartitionSpec dataSpec() {
       Set<PartitionSpec> specs = dataSpecs();
       Preconditions.checkState(specs.size() == 1, "Cannot return a single partition spec: ...");
       return specs.iterator().next();
     }
     protected Set<PartitionSpec> dataSpecs() {
       Set<PartitionSpec> specs = newDataFilesBySpec.keySet();
       Preconditions.checkState(!specs.isEmpty(), "Cannot determine partition specs: ...");
       return ImmutableSet.copyOf(specs);
     }
     ```
   - 新版：
     ```java
     protected PartitionSpec dataSpec() {
       Set<Integer> specIds = newDataFilesBySpec.keySet();
       Preconditions.checkState(!specIds.isEmpty(), "Cannot determine partition specs: no data files have been added");
       Preconditions.checkState(specIds.size() == 1, "Cannot return a single partition spec: data files with different partition specs have been added");
       return spec(Iterables.getOnlyElement(specIds));
     }
     ```
   - 改动要点：把"非空校验"前移到 `dataSpec()` 自身（原在 `dataSpecs()` 中），并直接对 spec id 集合做单元素校验，最后通过 `spec(specId)`（私有方法，等价于 `ops.current().spec(specId)`）反查 `PartitionSpec`；`dataSpecs()` 方法被删除。`Iterables.getOnlyElement` 在 size==1 时安全取唯一元素。

3. **新增数据文件时的分组 key**：
   ```java
   // 旧
   newDataFilesBySpec.computeIfAbsent(spec, ignored -> DataFileSet.create());
   // 新
   newDataFilesBySpec.computeIfAbsent(spec.specId(), ignored -> DataFileSet.create());
   ```
   原来用 `PartitionSpec` 对象作为桶 key，现在用 `spec.specId()`（int）。

4. **写新数据 manifest 时的还原**：
   ```java
   // 旧
   newDataFilesBySpec.forEach((dataSpec, dataFiles) -> {
     List<ManifestFile> newDataManifests =
         writeDataManifests(dataFiles, newDataFilesDataSequenceNumber, dataSpec);
     ...
   });
   // 新
   newDataFilesBySpec.forEach((specId, dataFiles) -> {
     List<ManifestFile> newDataManifests =
         writeDataManifests(dataFiles, newDataFilesDataSequenceNumber, spec(specId));
     ...
   });
   ```
   lambda 入参由 `(dataSpec, dataFiles)` 改为 `(specId, dataFiles)`，在调用 `writeDataManifests` 时再通过 `spec(specId)` 反查 `PartitionSpec`（因为 `writeDataManifests` 仍需要完整 `PartitionSpec` 来写 manifest 头部）。

## 小结

- **成效**：`MergingSnapshotProducer` 内部对"新增数据文件按 spec 分组"的桶 key 从完整 `PartitionSpec` 改为 spec id（`Integer`），消除了"以可变/复杂对象作为 HashMap key"的潜在风险，使分组语义只依赖 spec id 这一无歧义标识；同时与删除文件侧的 `newDeleteFilesBySpec: Map<Integer, DeleteFileSet>` 风格对齐，并删除了不再被外部调用的 `dataSpecs()` 方法，简化 API 表面。
- **影响范围**：仅 `core` 模块的 `MergingSnapshotProducer.java` 一个文件、4 处局部改动（+9/-14 行）。`dataSpec()` 的对外契约不变（仍返回 `PartitionSpec`，仍在校验失败时抛 `IllegalStateException`），内部 `dataSpecs()` 被移除（外部无调用方，已 grep 验证）。对 commit 行为、manifest 写入结果无功能变化。
- **回迁到 1.4.x 的注意事项**：纯重构类变更，回迁安全。需注意：
  1. 回迁前确认 1.4.x 上的 `MergingSnapshotProducer` 是否还有调用 `dataSpecs()` 的地方（如某些自定义子类或上游 patch），若有需同步调整；
  2. `spec(int specId)` 私有方法必须存在（在父提交版本中已存在，转发到 `ops.current().spec(specId)`），回迁时若 1.4.x 上该方法签名不同需调整；
  3. 本提交是后续更大重构（把 `dataSpec` 改为 `setDataSpec` 显式赋值的字段，详见 main 分支后续提交）的中间步骤，1.4.x 若回迁此提交后还想跟后续重构，需要适配；
  4. 校验顺序变化：原 `dataSpec()` 先 size==1 再非空（依赖 `dataSpecs()` 的非空校验在前），新版先非空再 size==1，语义等价但异常信息略有差异，测试断言异常消息时需留意。
