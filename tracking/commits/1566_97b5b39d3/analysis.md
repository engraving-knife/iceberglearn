# 提交 1566：Core: Allow adding files to multiple partition specs in FastAppend (#11771)

## 提交信息

- **序号**：1566
- **哈希**：97b5b39d3cdc699ddcdc8928aecea38f4d797362
- **短哈希**：97b5b39d3
- **日期**：2025-01-10（Fri Jan 10 09:53:26 2025 -0800）
- **作者**：Anurag Mantripragada <amantripragada@apple.com>
- **提交说明**：Core: Allow adding files to multiple partition specs in FastAppend (#11771)
- **PR/Issue**：#11771

## 总体目的

Iceberg 表支持多个 partition spec 共存（通过 `ALTER TABLE ... ADD PARTITION FIELD` 演进 spec）。每个 `DataFile` 都带有 `specId`，标识它属于哪个 spec。当用户在一次 `AppendFiles` 操作中追加多个文件、且这些文件分属不同 spec 时，理应为每个 spec 单独写一个 manifest（manifest 与 spec 一一绑定，`ManifestFile.partitionSpecId()` 标识其所属 spec）。

但 `FastAppend` 此前的实现是：在构造时抓取 `ops().current().spec()`（即当前默认 spec）保存到 `spec` 字段，所有追加的文件都丢进同一个 `DataFileSet newFiles`，写 manifest 时统一用那个单一 spec 调用 `writeDataManifests(newFiles, spec)`。这意味着如果某个文件的 `specId` 不是当前 spec，它会被写入一个 partitionSpecId 不匹配的 manifest，导致：
- manifest 的 `partitionSpecId` 与文件实际 `specId` 不一致；
- 文件的 partition 数据按错误 spec 解读，可能造成读取错误或校验失败。

本提交修复该缺陷，使 `FastAppend` 支持在一次提交中追加来自多个 spec 的文件——按 `file.specId()` 分桶，每个 spec 单独写一个 manifest。

## 如何达成设计目的

1. **数据结构改造**：删除 `spec` 字段（构造时不再抓取默认 spec）和 `newFiles`（单一集合）；新增 `Map<Integer, DataFileSet> newDataFilesBySpec` 按 specId 分桶；`newManifests` 从 `List<ManifestFile>`（可空引用）改为非空 `ArrayList`，用 `clear()` 代替 `= null`。
2. **`appendFile(DataFile)`**：通过新增的 `spec(int specId)` 方法（`ops().current().spec(specId)`）按文件 `specId` 查找 spec；先校验 spec 存在（`Preconditions.checkArgument(spec != null, ...)`），再用 `computeIfAbsent` 取/建对应的 `DataFileSet`，加入文件并更新 `summaryBuilder.addedFile(spec, file)`（用文件实际 spec 而非默认 spec）。
3. **`writeNewManifests()`**：遍历 `newDataFilesBySpec`，对每个 `(specId, dataFiles)` 调用 `writeDataManifests(dataFiles, spec(specId))`，把生成的 manifest 累加到 `newManifests`。判定条件从 `newManifests == null` 改为 `newManifests.isEmpty()`；清理从 `= null` 改为 `clear()`。
4. **`prepareCommit`/`updatePreviousManifests` 中的清理**：把 `this.newManifests = null` 改为 `this.newManifests.clear()`（一致性）。
5. **测试**：新增 `testEmptyTableFastAppendFilesWithDifferentSpecs`，对空表先 `updateSpec().addField("id")` 演进到第二个 spec，然后 `newFastAppend().appendFile(FILE_A).appendFile(fileNewSpec)` 一次提交两个不同 spec 的文件，断言生成 2 个 manifest（每个 spec 一个），并按 specId 验证每个 manifest 的内容与文件归属。
6. **CatalogTests 修复**：原有一个测试在 `updateSpec.commit()` 之前用 `updateSpec.apply()` 拿"提议的 spec"构造数据文件，但 commit 后真正的 specId 可能与提议不同（spec 演进时 id 由 TableMetadata 分配）。改为 `create.table().spec()` 在 commit 之后取实际 spec，保证文件 `specId` 与表内 spec 一致——这与本次修复配套，否则测试本身就会触发新加入的校验。

### 修改详情

#### `core/src/main/java/org/apache/iceberg/FastAppend.java`
**修改目的**：让 FastAppend 按 specId 分桶写 manifest。

**关键变更**：
- 删除 `private final PartitionSpec spec;` 与构造函数中的 `this.spec = ops().current().spec();`。
- `private final DataFileSet newFiles = DataFileSet.create();` 改为 `private final Map<Integer, DataFileSet> newDataFilesBySpec = Maps.newHashMap();`。
- `private List<ManifestFile> newManifests = null;` 改为 `private final List<ManifestFile> newManifests = Lists.newArrayList();`。
- `appendFile`：
  ```java
  PartitionSpec spec = spec(file.specId());
  Preconditions.checkArgument(spec != null,
      "Cannot find partition spec %s for data file: %s", file.specId(), file.location());
  DataFileSet dataFiles =
      newDataFilesBySpec.computeIfAbsent(spec.specId(), ignored -> DataFileSet.create());
  if (dataFiles.add(file)) {
    this.hasNewFiles = true;
    summaryBuilder.addedFile(spec, file);
  }
  ```
- 新增 `private PartitionSpec spec(int specId) { return ops().current().spec(specId); }`。
- `writeNewManifests` 改为遍历 map：
  ```java
  if (hasNewFiles && !newManifests.isEmpty()) {
    newManifests.forEach(file -> deleteFile(file.path()));
    newManifests.clear();
  }
  if (newManifests.isEmpty() && !newDataFilesBySpec.isEmpty()) {
    newDataFilesBySpec.forEach((specId, dataFiles) ->
        newManifests.addAll(writeDataManifests(dataFiles, spec(specId))));
    hasNewFiles = false;
  }
  ```
- `prepareCommit` 中 `this.newManifests = null;` → `this.newManifests.clear();`。

#### `core/src/test/java/org/apache/iceberg/TestFastAppend.java`
**修改目的**：覆盖一次 FastAppend 追加多 spec 文件的场景。

**关键内容**：`testEmptyTableFastAppendFilesWithDifferentSpecs` 演进到 2 个 spec，追加 `FILE_A`（原 spec）和 `fileNewSpec`（新 spec），断言 snapshot 包含 2 个 manifest，分别按 `partitionSpecId` 过滤后验证各自包含正确的数据文件、sequence、status。同时验证 V1 表 last sequence 仍为 0、V2 表为 1。

#### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java`
**修改目的**：修复一个会在新校验下失败的现有测试。

**关键变更**：把 `PartitionSpec newSpec = updateSpec.apply();`（commit 前的提议）移到 `updateSpec.commit()` 之后改为 `PartitionSpec newSpec = create.table().spec();`（commit 后的实际 spec），保证后续用 `newSpec` 构造的 `DataFile` 的 `specId` 与表内真实 spec 一致。

## 小结

- **成效**：`FastAppend` 现在能正确处理一次提交中包含多个 spec 文件的场景，每个 spec 单独生成 manifest，避免 manifest `partitionSpecId` 与文件 `specId` 不匹配的隐患。同时新增了对未知 `specId` 的显式校验，提升错误信息的可诊断性。
- **影响范围**：仅 `FastAppend.java`（核心 append 路径）与测试。其他 append 实现（如 `MergeOnReadAppend`、`OverwriteFiles`）不受影响。API 无变更。
- **回迁到 1.4.x 的注意事项**：这是一个正确性 bug 修复，影响多 spec 表的数据完整性。1.4.x 若包含 `FastAppend` 同样实现，建议回迁以避免在 spec 演进场景下写出错误 manifest。回迁范围小（一个文件的核心逻辑改造 + 配套测试修复），冲突风险低。**建议回迁**。
