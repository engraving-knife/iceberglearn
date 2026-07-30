# 提交 3266：Spark 4.1: Simplify handling of metadata columns (#15297)

## 提交信息

- **序号**：3266 / 4088
- **哈希**：4c2e60d1541fb37d6ea6be31b2ea2cf733a087c3
- **短哈希**：4c2e60d15
- **日期**：2026-02-16
- **作者**：Anton Okolnochyi
- **提交说明**：Spark 4.1: Simplify handling of metadata columns (#15297)
- **PR/Issue**：#15297

## 总体目的

本提交针对 Spark 4.1 适配器中元数据列（metadata columns）的处理逻辑进行重构与简化。原先在 `SparkScanBuilder` 中，元数据列的投影计算逻辑高度耦合且命名混乱：用 `schema` 字段同时承担"被裁剪的数据投影"和"最终返回给 reader 的 schema（含元数据列）"两种语义，方法名 `schemaWithMetadataColumns()` 与 `calculateMetadataSchema()` 之间夹带了一段内联的、使用 `AtomicInteger` 配合 `ImmutableSet` 的字段 ID 冲突重分配逻辑，使代码可读性差，且关键的重分配算法无法被复用。

具体要解决的问题是：当用户请求 `_partition` 这类元数据列时，分区元数据列的字段 ID 是一个嵌套结构类型，其内部字段 ID 可能与表已用字段 ID 冲突。原代码在 `SparkScanBuilder` 内部用一段 lambda 表达式实现冲突 ID 的重分配，无法在 Spark 4.1 后续要做的"扫描与 compaction 分离"等改造中被其它路径复用。此外，原 `pruneColumns` 还通过 `Stream` 过滤字段并重复迭代 `requestedSchema.fields()` 两次（一次裁剪、一次收集元数据列名），实现较为啰嗦。

重构后，将元数据字段 ID 重分配的核心算法上移到 `api` 模块的 `TypeUtil.reassignConflictingIds(...)`，使其成为可被任意引擎复用的通用工具；同时把 `SparkScanBuilder` 内部字段重命名（`schema` → `projection`，`metaColumns` → `metaFieldNames`，且改为 `Set` 以天然去重），方法拆分为职责清晰的小方法（`prune`、`projectionWithMetadataColumns`、`calculateMetadataSchema`、`metaFields`、`findPartitionField`、`allUsedFieldIds`），并让 `projectionWithMetadataColumns()` 从 private 提升为 `protected`，便于子类（如 staged scan）复用。

## 如何达成设计目的

整体设计是"上提通用算法 + 下沉专用逻辑到小方法"。涉及四个文件：在 `api` 模块新增 `TypeUtil.reassignConflictingIds` 及其内部实现类 `ReassignConflictingIds`，并给 `Schema` 增加一个便捷构造器；在 Spark 4.1 模块新增 `SparkSchemaUtil.toStructType(List<StructField>)` 便捷方法，再在 `SparkScanBuilder` 中把原先的 `schema`/`metaColumns` 字段重命名并改类型，拆分大方法为多个单一职责的小方法，所有原本调用 `schemaWithMetadataColumns()` 的位置改为调用 `projectionWithMetadataColumns()`。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Schema.java` (+4/-0 lines)

**修改目的**：新增一个仅指定 `GetID`、不带 identifier field 的便捷构造器。

**工作逻辑**：新构造器 `Schema(List<NestedField> columns, TypeUtil.GetID getId)` 转调既有的 `Schema(int schemaId, List<NestedField> columns, Set<Integer> identifierFieldIds, GetID getId)`，传入默认 `DEFAULT_SCHEMA_ID` 与空 `ImmutableSet.of()`。这是为了让 `SparkScanBuilder.calculateMetadataSchema()` 在构造仅含元数据字段且需要重分配 ID 的 schema 时写法更简洁，不必显式传 identifier 集合。

### `api/src/main/java/org/apache/iceberg/types/TypeUtil.java` (+46/-0 lines)

**修改目的**：把"重分配冲突字段 ID"的算法从 Spark 适配器上提到 API 层，供所有引擎复用。

**工作逻辑**：新增静态方法 `reassignConflictingIds(Set<Integer> conflictingIds, Set<Integer> allUsedIds)`，返回一个实现了 `GetID` 接口的 `ReassignConflictingIds` 实例。该内部类持有三个字段：`conflictingIds`（需要被重分配的 ID 集合，典型场景下即分区元数据列内嵌结构字段的所有 ID）、`allUsedIds`（已被占用的 ID 集合）、`nextId`（用于生成新 ID 的 `AtomicInteger`）。`get(int oldId)` 方法对位于 `conflictingIds` 中的旧 ID 调用 `nextAvailableId()` 生成新 ID；`nextAvailableId()` 自增取候选 ID 并跳过任何在 `allUsedIds` 中已占用的 ID。该方法配套有详细 Javadoc 说明其在合并 schema 时避免字段 ID 冲突的用途。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkSchemaUtil.java` (+5/-0 lines)

**修改目的**：新增由 `List<StructField>` 构造 `StructType` 的便捷方法。

**工作逻辑**：新增 `toStructType(List<StructField> fields)`，将列表转为数组后构造 `StructType`。在 `SparkScanBuilder.pruneColumns` 中先用普通 `List` 收集非元数据字段，再用此方法一次构造 `StructType`，避免原先通过 `Stream.of(...).filter(...).toArray(StructField[]::new)` 的啰嗦写法。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+71/-83 lines)

**修改目的**：重构元数据列投影逻辑，使职责清晰并为后续 Spark 4.1 改造提供可复用的 protected 入口。

**工作逻辑**：
- 字段重命名与改类型：`schema` 改为 `projection`（语义更准确——它只是被裁剪后的数据投影，并非最终返回 schema）；`metaColumns`（`List<String>`）改为 `metaFieldNames`（`Set<String>`，使用 `Sets.newLinkedHashSet()`），天然去重，省去原先 `distinct()` 调用。构造器、`withBaseFilters`、`withAggregates`、`pruneColumns`、`buildIcebergBatchScan`、`buildChangelogScan`、`buildMetadataScan`、`buildBatchScan`、`buildCopyScan` 等所有原先引用 `schema` 的位置统一改为 `projection`。
- `pruneColumns(StructType requestedSchema)` 简化：单次遍历 `requestedSchema.fields()`，元数据字段名加入 `metaFieldNames`，数据字段加入 `dataFields`，再通过新 `prune(...)` 私有方法调用 `SparkSchemaUtil.prune`。原先要两次 `Stream.of(requestedSchema.fields())` 遍历。
- 大方法 `schemaWithMetadataColumns()` 与 `calculateMetadataSchema(...)` 拆分为多个小方法：
  - `projectionWithMetadataColumns()`（由 private 提升为 `protected`，便于子类复用）：返回 `TypeUtil.join(projection, calculateMetadataSchema())`，即数据投影 join 元数据 schema。
  - `calculateMetadataSchema()`：取 `metaFields()`，找到分区字段，若不存在分区字段直接 `new Schema(metaFields)`；若存在则用 `TypeUtil.reassignConflictingIds(partitionFieldIds, allUsedFieldIds())` 构造重分配函数，并 `new Schema(metaFields, getId)`（用新构造器）。
  - `metaFields()`：把 `metaFieldNames` 映射为 `Types.NestedField`。
  - `findPartitionField(List<Types.NestedField>)`：在元数据字段中找 ID 等于 `MetadataColumns.PARTITION_COLUMN_ID` 的字段。
  - `allUsedFieldIds()`：遍历 `table.schemas().values()`，用 `TypeUtil.getProjectedIds(...)` 平铺收集所有已用字段 ID。原先用 `Stream.reduce(..., Sets::union)` 比较啰嗦。

整个改造使 Spark 4.1 适配器的元数据列处理代码量更小、更易读，且冲突 ID 重分配这一关键算法成为 API 层公共能力。

## 总结

本提交通过把字段 ID 冲突重分配算法上移至 API 层 `TypeUtil`，并把 `SparkScanBuilder` 中混杂的元数据列投影逻辑拆分为命名清晰、职责单一的小方法，显著降低了 Spark 4.1 适配器在处理元数据列时的认知复杂度，同时为后续"扫描与 compaction 分离"等 Spark 4.1 改造（如 3267 提交）提供了可被继承复用的 `protected projectionWithMetadataColumns()` 入口。这是一次纯重构，行为不变，但为后续 Spark 4.1 工作铺平道路。
