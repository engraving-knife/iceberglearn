# 提交 1561：API: Support removeUnusedSpecs in ExpireSnapshots (#10755)

## 提交信息

- **序号**：1561
- **哈希**：67e084c8d47db68c6135f9837b9cff6f88da0309
- **短哈希**：67e084c8d
- **日期**：2025-01-08（Wed Jan 8 02:26:46 2025 +0800）
- **作者**：advancedxy <807537+advancedxy@users.noreply.github.com>
- **共同作者**：Russell_Spitzer <rspitzer@apple.com>、Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：API: Support removeUnusedSpecs in ExpireSnapshots (#10755)
- **PR/Issue**：#10755

## 总体目的

本提交为 Iceberg 引入一项新的元数据治理能力——在 `ExpireSnapshots`（快照过期）流程中可顺带清理"已不再被任何保留快照引用的分区 spec"。这是社区长期诉求：随着表长期演进，用户会通过 `updateSpec` 多次新增/修改分区方案，旧的 spec 仍会留在 `TableMetadata` 中，导致 metadata 文件膨胀、解析开销与认知负担增大。在此之前 Iceberg 只能清理过期快照与文件，无法回收 spec 元数据；本提交填补了这一空白。

设计上选择把它放在 `ExpireSnapshots` 里而非独立 action，是因为"spec 是否仍被引用"本质上需要做快照可达性分析——只有引用该 spec 的快照全部过期后，spec 才能安全移除。这一判断逻辑与 `ExpireSnapshots` 已经在做的"快照可达性分析"完全一致，复用最自然。

为避免破坏既有行为，新能力以 opt-in 开关 `cleanExpiredMetadata(boolean)` 暴露，默认关闭；只有用户显式开启时才执行 spec 清理。同时本提交为该操作设计了完整的提交安全要求（`UpdateRequirements`）与 JSON 序列化（`MetadataUpdateParser`），保证在 REST Catalog 等场景下也能正确传输与校验。

提交里还包含一处关键的辅助重构：`IncrementalFileCleanup` 改用"过期前的 specsById"而非"过期后的 TableMetadata"来读取 manifest，避免在被删除的 spec 上读取 manifest 时出现 NPE——这是新功能落地必须修复的关联点。

## 如何达成设计目的

### 设计思路

1. **API 默认方法 + 实现覆盖**：在 `ExpireSnapshots` 接口新增 `default cleanExpiredMetadata(boolean)`，默认抛 `UnsupportedOperationException`，避免破坏既有实现；`RemoveSnapshots`（Iceberg 自带实现）覆盖该方法接收开关。
2. **新增 `MetadataUpdate.RemovePartitionSpecs`**：让"删除 spec"成为一等公民的元数据更新事件，可被 `TableMetadata.Builder` 应用、可被 commit 要求校验、可被序列化进 REST 协议。
3. **可达性分析**：在 `RemoveSnapshots.internalApply()` 中，对保留快照集合 `idsToRetain` 并行扫描所有 manifest，收集它们使用的 `partitionSpecId`，加上默认 spec id，作为"可达 spec 集合"；其余 spec 即可删除。
4. **保护默认 spec**：`TableMetadata.Builder.removeSpecs(...)` 显式断言 `!specIdsToRemove.contains(defaultSpecId)`，从底层兜底防止误删默认 spec。
5. **提交要求（commit requirements）**：在 `UpdateRequirements` 中对 `RemovePartitionSpecs` 注册两个 assertion——默认 spec id 未变、所有非 main 分支的 snapshot id 未变。这保证提交时如果其他 writer 改了默认 spec 或推了分支引用老快照，本次提交会快速失败而不是误删 spec。
6. **JSON 序列化**：在 `MetadataUpdateParser` 中注册 action 字符串 `remove-partition-specs` 与字段 `spec-ids`，支持读写往返。
7. **配套修复 `IncrementalFileCleanup`**：增量清理在扫描待删 manifest 时需要 `specsById` 来解析 manifest；如果继续用过期后的 `TableMetadata`（已删除 spec），就找不到对应 spec 报错。改用过期前的 `specsById` 解决该问题。

### 修改详情

#### `api/src/main/java/org/apache/iceberg/ExpireSnapshots.java`

**修改目的**：在公开 API 上暴露"清理过期元数据"开关。

**修改内容**：新增默认方法：

```java
default ExpireSnapshots cleanExpiredMetadata(boolean clean) {
  throw new UnsupportedOperationException(
      this.getClass().getName() + " doesn't implement cleanExpiredMetadata");
}
```

**工作逻辑**：默认抛异常避免破坏第三方 `ExpireSnapshots` 实现；同时 Javadoc 说明未来可扩展到 schema 等其他元数据。

#### `core/src/main/java/org/apache/iceberg/MetadataUpdate.java`

**修改目的**：定义"删除分区 spec"这一元数据更新类型。

**修改内容**：新增内部类 `RemovePartitionSpecs implements MetadataUpdate`，持有 `Set<Integer> specIds`，`applyTo(TableMetadata.Builder)` 调用 `metadataBuilder.removeSpecs(specIds)`。

**工作逻辑**：作为 commit 时 metadata 变更事件的载体，会被 `TableMetadata.Builder.changes` 记录、被 `UpdateRequirements` 解析、被 `MetadataUpdateParser` 序列化。

#### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：在 `Builder` 上提供 spec 删除能力并记录变更事件。

**修改内容**：新增 `Builder.removeSpecs(Iterable<Integer> specIds)`：

```java
Builder removeSpecs(Iterable<Integer> specIds) {
  Set<Integer> specIdsToRemove = Sets.newHashSet(specIds);
  Preconditions.checkArgument(
      !specIdsToRemove.contains(defaultSpecId), "Cannot remove the default partition spec");
  this.specs = specs.stream()
      .filter(s -> !specIdsToRemove.contains(s.specId()))
      .collect(Collectors.toList());
  changes.add(new MetadataUpdate.RemovePartitionSpecs(specIdsToRemove));
  return this;
}
```

**工作逻辑**：先校验不含默认 spec id；再用流式过滤掉待删 spec；最后把变更加入 `changes`，使其能被 `UpdateRequirements.forUpdateTable(...)` 检测到并附加 assertion。

#### `core/src/main/java/org/apache/iceberg/UpdateRequirements.java`

**修改目的**：为 `RemovePartitionSpecs` 注册提交安全要求。

**修改内容**：在 `forUpdateTable` 的 update 分发链中加入 `RemovePartitionSpecs` 分支；新增私有方法 `update(MetadataUpdate.RemovePartitionSpecs unused)`：
- 若尚未设置过 spec 要求，则要求 `AssertDefaultSpecID(base.defaultSpecId())`——确保 commit 时默认 spec 没被其他 writer 修改（否则可能删除新默认 spec 引用的旧 spec 而破坏表）。
- 对所有非 main 分支要求 `AssertRefSnapshotID(name, ref.snapshotId())`——确保分支引用的快照没变（否则可能误删仍被分支快照引用的 spec）。

**工作逻辑**：`UpdateRequirement` 是 Iceberg commit 协议中的"乐观锁断言"，REST 服务端会在 apply 之前校验所有 requirement。本提交让 `RemovePartitionSpecs` 与 `SetDefaultPartitionSpec` 共享 spec id 不变断言，但额外要求分支不变——因为分支可能引用老快照从而间接引用老 spec。

#### `core/src/main/java/org/apache/iceberg/MetadataUpdateParser.java`

**修改目的**：让 `RemovePartitionSpecs` 可在 REST 协议中传输。

**修改内容**：
- 新增 action 常量 `REMOVE_PARTITION_SPECS = "remove-partition-specs"` 与字段常量 `SPEC_IDS = "spec-ids"`。
- 在 `ACTIONS` map 中注册类到 action 的映射。
- 新增 `writeRemovePartitionSpecs(...)`：用 `JsonUtil.writeIntegerArray` 写出 spec id 数组。
- 新增 `readRemovePartitionSpecs(...)`：用 `JsonUtil.getIntegerSet` 读回 Set。
- 在 `write`/`read` 的 switch 中加入对应 case。

**工作逻辑**：JSON 形如 `{"action":"remove-partition-specs","spec-ids":[1,2,3]}`，与既有 metadata update 的序列化模式一致，便于 REST Catalog server 端解析并 apply。

#### `core/src/main/java/org/apache/iceberg/RemoveSnapshots.java`

**修改目的**：实现可达性分析与 spec 删除的核心逻辑。

**修改内容**：
- 新增 import `Collectors`。
- 新增字段 `private boolean cleanExpiredMetadata = false;`。
- 覆写 `cleanExpiredMetadata(boolean)`。
- 在 `internalApply()` 中，删除 snapshots 之后，若开关打开：

```java
if (cleanExpiredMetadata) {
  Set<Integer> reachableSpecs = Sets.newConcurrentHashSet();
  reachableSpecs.add(base.defaultSpecId());
  Tasks.foreach(idsToRetain)
      .executeWith(planExecutorService)
      .run(snapshot ->
          base.snapshot(snapshot).allManifests(ops.io()).stream()
              .map(ManifestFile::partitionSpecId)
              .forEach(reachableSpecs::add));
  Set<Integer> specsToRemove = base.specs().stream()
      .map(PartitionSpec::specId)
      .filter(specId -> !reachableSpecs.contains(specId))
      .collect(Collectors.toSet());
  updatedMetaBuilder.removeSpecs(specsToRemove);
}
```

**工作逻辑**：
- `idsToRetain` 是过期后仍保留的 snapshot id 集合（包括 main 与所有分支引用的）。
- 用 `Tasks.foreach(...).executeWith(planExecutorService)` 并行扫描每个保留快照的所有 manifest，把它们的 `partitionSpecId` 加入并发集合 `reachableSpecs`；同时把默认 spec id 也加入。
- 用集合差集计算 `specsToRemove`，调用 `removeSpecs` 写入 metadata builder。
- 整个过程只读 manifest 元数据（partitionSpecId 字段），不读取数据文件，开销低。
- TODO 注释提示后续可扩展到 schema 清理。

#### `core/src/main/java/org/apache/iceberg/IncrementalFileCleanup.java`

**修改目的**：修复增量清理在新功能下的潜在 NPE。

**修改内容**：把 `findFilesToDelete(...)` 的最后一个参数从 `TableMetadata current` 改为 `Map<Integer, PartitionSpec> specsById`；调用方由传 `afterExpiration` 改为传 `beforeExpiration.specsById()`；方法内部把 `current.specsById()` 改为直接用入参 `specsById`。

**工作逻辑**：增量清理会扫描待删 manifest 来找数据文件删除。这些 manifest 的 `partitionSpecId` 可能指向即将被删除的 spec，如果用过期后的 `TableMetadata`（已不含该 spec）来取 `specsById()`，`ManifestFiles.open(manifest, fileIO, specsById)` 会找不到 spec 抛 NPE。改用过期前的 `specsById`（仍含全部 spec）即可正确打开 manifest。这是一处与 spec 删除功能耦合的必要修复。

#### `core/src/test/java/org/apache/iceberg/TestMetadataUpdateParser.java`

**修改目的**：验证 `RemovePartitionSpecs` 的 JSON 往返。

**修改内容**：新增 `testRemovePartitionSpec`，断言 `{"action":"remove-partition-specs","spec-ids":[1,2,3]}` 与 `RemovePartitionSpecs(ImmutableSet.of(1,2,3))` 双向等价；在 `assertEquals` 中加入对应 case。

#### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java`

**修改目的**：端到端验证过期时 spec 删除行为。

**修改内容**：新增两个测试：
- `testRemoveSpecDuringExpiration`：建表→append→delete→`updateSpec` 加字段→append→过期，断言旧 spec 被删、只剩新 spec、相关 manifest 与数据文件被清理。
- `testRemoveSpecsDoesntRemoveDefaultSpec`：默认 spec 是 `data_bucket`，但用 unpartitioned 写一次再删除，过期后断言只删 unpartitioned spec，保留 `data_bucket` 默认 spec。

#### `core/src/test/java/org/apache/iceberg/TestUpdateRequirements.java`

**修改目的**：验证 commit requirements 的正确性。

**修改内容**：新增三个测试：
- `removePartitionSpec`：仅有 `RemovePartitionSpecs` 更新时，应产生 `AssertTableUUID` + `AssertDefaultSpecID` 两个要求。
- `testRemovePartitionSpecsWithBranch`：有非 main 分支时，应额外产生 `AssertRefSnapshotID` 要求。
- `testRemovePartitionSpecsFailure`：当 base 默认 spec id 与 updated 不一致时，validate 应抛 `CommitFailedException`。

#### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java`

**修改目的**：让所有 catalog 实现跑统一的"删除未用 spec"端到端测试。

**修改内容**：新增参数化测试 `testRemoveUnusedSpec(boolean withBranch)`：
- 建表→append→（可选）创建分支→两次 updateSpec→过期；断言过期后剩余 spec 集合符合预期。
- 参数化 `withBranch` 验证有分支时旧 spec 不会被误删（分支仍引用旧快照）。
- 由于位于 `CatalogTests` 基类，Hive、Jdbc、Nessie、REST、InMemory 等所有 catalog 实现都会跑该用例，保证一致性。

## 小结

- **成效**：Iceberg 现可通过 `table.expireSnapshots().cleanExpiredMetadata(true).commit()` 在过期快照的同时清理不再被任何保留快照引用的分区 spec，控制 metadata 膨胀；配套提供 commit requirements（默认 spec 不变 + 非主分支不变）与 JSON 序列化（`remove-partition-specs`），保障并发提交安全与 REST 协议兼容；同时修复 `IncrementalFileCleanup` 在 spec 被删后扫描 manifest 的潜在 NPE。
- **影响范围**：11 个文件，约 394 增 6 删；跨越 api、core 两个模块，包含公开 API 新增、元数据协议扩展、提交要求扩展与跨 catalog 测试基类新增。属于较大的功能增强，但默认关闭开关保证对既有用户零影响。
- **回迁到 1.4.x 的注意事项**：本提交是用户可见的功能增强（新 API + 新 metadata update action + 新 commit requirement 类型），跨多个模块与协议。回迁到 1.4.x 需要：
  1. 1.4.x 的 `ExpireSnapshots`、`MetadataUpdate`、`MetadataUpdateParser`、`UpdateRequirements`、`TableMetadata.Builder`、`RemoveSnapshots`、`IncrementalFileCleanup` 接口/类结构与 main 兼容；
  2. REST Catalog 协议在 1.4.x 与新版客户端/服务端之间需要协调——若 1.4.x 服务端不识别 `remove-partition-specs` action，会报错；
  3. 需一并回迁所有相关测试以保证行为正确。
  若 1.4.x 的 commit requirements 与 metadata update 框架与 main 已分叉较大，回迁成本较高。**建议仅当 1.4.x 用户群体明确需要此功能且上述基础设施兼容时才回迁**，否则可作为 main 独有能力。
