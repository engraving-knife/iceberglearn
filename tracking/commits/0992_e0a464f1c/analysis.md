# 提交 0992：Flink: improve snapshot compatibility check by comparing projected sort schema in SortKeySerializer. also add unit tests for serializer snapshot. (#10794)

## 提交信息

- **序号**：0992 / 4088
- **哈希**：e0a464f1cfd81823940501ecb9f0c88905981ee1
- **短哈希**：e0a464f1c
- **日期**：2024-07-29（Mon Jul 29 15:34:32 2024 -0700）
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: improve snapshot compatibility check by comparing projected sort schema in SortKeySerializer. also add unit tests for serializer snapshot. (#10794)
- **PR/Issue**：#10794

## 总体目的

Iceberg 的 Flink sink 模块提供了 `SortKeySerializer`，用于在 Flink 的 shuffle 阶段序列化/反序列化 `SortKey`（排序键）。Flink 的类型序列化框架要求每个 `TypeSerializer` 配套一个 `TypeSerializerSnapshot`，用于在作业从 savepoint 恢复或升级时判断新旧序列化器的 schema 是否兼容。`SortKeySerializer.SortKeySerializerSnapshot.resolveSchemaCompatibility(...)` 就是承担这一判断的核心方法。

改动前的兼容性检查逻辑存在一个过于严格的问题：它会比较"新旧序列化器所关联的表的完整 schema"是否写兼容。这意味着，只要用户对表做了任何与排序字段无关的 schema 演进（例如新增一个普通列、删除一个不参与排序的列），整个序列化器就会被判定为不兼容，从而导致作业无法从 savepoint 平滑恢复，被迫全量重启。这在实际生产中是不必要的限制——排序 shuffle 只关心参与排序的字段，非排序字段的增删并不会影响 `SortKey` 的序列化布局。

本提交的目的是放宽这一限制：只对"参与排序的字段所对应的子 schema"进行写兼容性比较，非排序字段的变更不再触发不兼容判定。同时，作者补充了完整的序列化器快照单元测试（`TestSortKeySerializerSnapshot`），覆盖兼容与不兼容的多种场景，弥补此前该模块缺少快照测试的空白。

## 如何达成设计目的

实现思路是利用 Iceberg 已有的 `TypeUtil.project(Schema, Set<Integer>)` 工具方法，将新旧 schema 都"投影"到只包含排序字段（`SortField.sourceId()`）的子 schema 上，再调用 `CheckCompatibility.writeCompatibilityErrors(...)` 做写兼容性检查。如果排序字段的类型没有破坏性变更，就返回 `compatibleAsIs`；否则返回 `incompatible`。

由于 Iceberg 同时维护 Flink 1.17、1.18、1.19 三个版本的绑定模块，且 Flink 在不同版本中 `resolveSchemaCompatibility` 的方法签名有所不同（1.17/1.18 接收 `TypeSerializer<SortKey> newSerializer`，1.19 接收 `TypeSerializerSnapshot<SortKey> oldSerializerSnapshot`），因此改动在三个模块中分别落地，但核心逻辑一致。1.19 版本还顺手删除了不再被调用的旧 `resolveSchemaCompatibility` 静态工具方法。

## 修改详情

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java` 与 `flink/v1.18/flink/.../SortKeySerializer.java`

**修改目的**：放宽快照兼容性检查，使其只比较排序字段对应的子 schema。

**工作逻辑**：在 `SortKeySerializerSnapshot.resolveSchemaCompatibility(TypeSerializer<SortKey> newSerializer)` 中：
- 先通过 `newSerializer.snapshotConfiguration()` 拿到新序列化器的快照，校验新旧 `sortOrder` 是否一致（排序顺序变化仍判为不兼容）；
- 收集当前 `sortOrder` 中所有 `SortField.sourceId` 组成 `sortFieldIds`；
- 用 `TypeUtil.project(...)` 将当前快照的 `schema` 和新快照的 `schema` 都投影到这些排序字段上，得到 `sortSchema` 与 `newSortSchema`；
- 调用 `CheckCompatibility.writeCompatibilityErrors(sortSchema, newSortSchema)`，若返回的错误列表为空则 `compatibleAsIs`，否则 `incompatible`。

同时新增了 `java.util.Set`、`java.util.stream.Collectors`、`org.apache.iceberg.types.TypeUtil` 的 import。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java`

**修改目的**：同上，但适配 Flink 1.19 的快照 API，并清理无用代码。

**工作逻辑**：1.19 中方法签名为 `resolveSchemaCompatibility(TypeSerializerSnapshot<SortKey> oldSerializerSnapshot)`，即 `this` 是新快照、参数是旧快照。逻辑与 1.17/1.18 对称：投影新旧 schema 到排序字段后做写兼容检查，得到 `oldSortSchema` 与 `sortSchema` 比较。此外删除了不再被外部调用的静态方法 `resolveSchemaCompatibility(Schema readSchema, Schema writeSchema)`，并把 `VisibleForTesting` 的 import 一并移除；方法内增加注释 `// Sort order should be identical`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSortKeySerializerSnapshot.java` 与 v1.18、v1.19 对应文件（新增）

**修改目的**：为 `SortKeySerializer` 的快照兼容性逻辑补充单元测试，此前该模块没有快照测试。

**工作逻辑**：新增测试类覆盖以下场景（三份文件内容一致）：
- `testRestoredSerializer`：通过 `roundTrip`（writeSnapshot → readSnapshot）模拟快照序列化往返，验证恢复出的序列化器能正确反序列化 `SortKey`；
- `testSnapshotIsCompatibleWithSameSortOrder`：相同 schema 与 sort order 应判为 `compatibleAsIs`；
- `testSnapshotIsCompatibleWithRemoveNonSortField` / `testSnapshotIsCompatibleWithAddNonSortField`：删除/新增非排序字段时，因只比较排序字段子 schema，仍判为兼容——这正是本提交要修复的核心场景；
- `testSnapshotIsIncompatibleWithIncompatibleSchema`：把排序字段 `str` 类型从 String 改为 Long，应判为不兼容；
- `testSnapshotIsIncompatibleWithAddSortField` / `testSnapshotIsIncompatibleWithRemoveSortField` / `testSnapshotIsIncompatibleWithSortFieldsOrderChange`：增加、删除排序字段或调整排序字段顺序，均应判为不兼容。

`roundTrip` 辅助方法参考自 Flink 的 `AvroSerializerSnapshotTest`，用 `DataOutputSerializer`/`DataInputDeserializer` 完成快照的二进制往返。

## 小结

- **成效**：放宽了 `SortKeySerializer` 的快照兼容性判定，使非排序字段的 schema 演进不再阻塞作业从 savepoint 恢复；同时补齐了此前缺失的序列化器快照单元测试，覆盖兼容/不兼容共 7 种场景。
- **影响范围**：仅涉及 Flink 1.17/1.18/1.19 三个绑定模块的 `sink/shuffle` 子包，共 6 个文件（3 个主代码修改 + 3 个新增测试），无公共 API 变更。
- **回迁到 1.4.x 的注意事项**：该改动是 bug fix 性质的兼容性改进，逻辑独立、风险低，适合回迁。回迁时需确认 1.4.x 分支对应的 Flink 绑定模块版本（1.4.x 通常包含 1.17/1.18/1.19），并按各版本的方法签名差异分别处理；1.19 版本删除旧 `resolveSchemaCompatibility` 静态方法的清理可一并带上。需注意配套引入的新测试依赖 `Fixtures`、`assertj` 等已在模块中存在，无额外依赖问题。
