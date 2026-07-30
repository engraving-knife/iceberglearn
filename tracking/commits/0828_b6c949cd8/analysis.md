# 提交 0828：Core, Spark: Calling rewrite_position_delete_files fails on tables with more than 1k columns (#10020)

## 提交信息
- **序号**：0828 / 4088
- **哈希**：b6c949cd86c372a87a4d43a557c19dd310af80d8
- **短哈希**：b6c949cd8
- **日期**：2024-06-12
- **作者**：Szehon Ho
- **提交说明**：Core, Spark: Calling rewrite_position_delete_files fails on tables with more than 1k columns (#10020)
- **PR/Issue**：#10020

## 总体目的

修复一个 Bug：当 Iceberg 表的列数超过 1000 时，调用 `rewrite_position_delete_files`（重写位置删除文件，Spark Action）会失败。

### Bug 根因

Iceberg 的 PartitionSpec 中，分区字段 ID（partition field ID）从常量 `PARTITION_DATA_ID_START = 1000` 开始递增（见 `api/src/main/java/org/apache/iceberg/PartitionSpec.java` 第 65 行）。也就是说，无论表的列数多少，分区字段 ID 总是从 1000 开始占用 ID 空间。

而 `PositionDeletesTable.calculateSchema()` 构建元数据表 schema 时，会同时引入两类字段的 ID：

1. **`row` 字段**：类型为 `table().schema().asStruct()`，即把原始表的整张 schema 嵌入为一个嵌套字段（field id = `Integer.MAX_VALUE - 103`）。这意味着原始表的所有列 ID 都作为嵌套字段出现在 PositionDeletesTable 的 schema 树中。
2. **`partition` 字段**：类型为 `Partitioning.partitionType(table)`，这是一个 struct，其嵌套字段的 ID 就是分区字段 ID（1000+）。

当原始表的列数超过 1000 时，列 ID 就会占用 1000、1001、1002… 等位置；而分区字段 ID 也是 1000、1001… 这两者在同一个 schema 的 ID 命名空间内发生冲突。例如某分区字段 ID = 1000，而原始表也存在列 ID = 1000，二者都出现在 PositionDeletesTable 的 schema 树中。Iceberg 校验 schema 时（如 `TypeUtil.indexById` 等遍历），会因为字段 ID 重复而抛错，进而 `rewritePositionDeletes` Action 失败。

### 修复思路

不改变原始表的列 ID 也不改变 manifest 文件中已写入的分区字段 ID（保持向后兼容），而是在 **PositionDeletesTable 这个元数据表的 schema 内部**对分区字段 ID 进行重新分配（reassign），使其避开原始表已占用的所有 ID；同时记录原 ID ↔ 新 ID 的双向映射，以便在需要使用「manifest 中按原始 ID 写入的分区数据」时反向还原。这种「只重分配元数据表内部 ID、不动原始表与磁盘格式」的方式既修复了冲突，又不影响存储兼容性。

## 如何达成设计目的

整个修复围绕「在 Schema 构造时支持按需重分配字段 ID」展开，分四层落地：

1. **API 层（Schema/TypeUtil/Types）**：在 `Schema` 构造函数新增 `TypeUtil.GetID` 参数；新增 `AssignIds` 这个 `CustomOrderSchemaVisitor` 实现按 `GetID` 函数遍历并重分配所有字段（含嵌套 struct/list/map）的 ID；`Schema` 同时维护 `idsToReassigned`（原→新）和 `idsToOriginal`（新→原）两张映射表对外暴露。`Types.NestedField` 新增 `withFieldId(int)` 用于改写单个字段 ID。

2. **PositionDeletesTable 元数据表**：在 `calculateSchema()` 中确定要重分配的字段 ID 集合（即所有分区字段 ID），然后用一个 `AtomicInteger nextId` 在所有「已使用 ID」（含原始表所有 schema 的列 ID + 元数据表本身 ID）之外寻找可用 ID，构造 `GetID` 函数交给 `Schema` 构造器。这样 PositionDeletesTable 的 schema 内部分区字段 ID 不再与任何已有列 ID 冲突。

3. **PartitionSpec 双视图**：新增 `PartitionSpec.rawPartitionType()` 返回**原始**分区字段 ID 的 struct（用于读取 manifest 文件中按原始 ID 写入的分区数据）；原有的 `partitionType()` 在元数据表上会返回**重分配后**的 ID（用于元数据表自身的 spec/扫描）。`BaseMetadataTable.transformSpec()` 改为读 `idsToReassigned` 把分区 spec 中的 field ID 同步重映射为新 ID，与元数据表 schema 保持一致。

4. **ManifestReader 与 Spark Action**：`ManifestReader` 改用 `spec.rawPartitionType()` 读取磁盘上的分区数据，避免因为 manifest 中仍是原始 ID 而读错；Spark `RewritePositionDeleteFilesSparkAction`（v3.3/v3.4/v3.5 三个版本一致修改）改用 `Partitioning.partitionType(deletesTable)`（元数据表）来获取分区类型，而不是 `Partitioning.partitionType(table)`（原始表），从而使用重分配后无冲突的 ID。

### ID 冲突解决的工作流程

修复后调用 `rewrite_position_delete_files` 的关键流程：

- Spark Action 调用 `MetadataTableUtils.createMetadataTableInstance(table, POSITION_DELETES)` 创建 `PositionDeletesTable`。
- 构造时 `calculateSchema()` 走重分配逻辑：扫描原始表所有 schema 收集「已使用 ID」集合 `allUsedIds`；对每个分区字段 ID 找一个不在 `allUsedIds` 中的新 ID；`Schema` 构造器内部用 `AssignIds` visitor 遍历替换分区 struct 内的字段 ID，并填充 `idsToReassigned` / `idsToOriginal`。
- Spark Action 用 `Partitioning.partitionType(deletesTable)` 拿到的是已重分配的分区类型，使用其字段 ID 做 `groupByPartition` 等分组操作时不再与列 ID 冲突。
- 实际读取 manifest 中的分区数据时，`ManifestReader` 通过 `spec.rawPartitionType()` 取回**原始 ID** 的分区类型，磁盘上的分区数据按原始 ID 读取后通过 `BaseMetadataTable.transformSpec()` 在 spec 层面映射到重分配 ID，保证读出的数据与元数据表 schema 对齐。

## 修改详情

### `.palantir/revapi.yml`
**修改目的**：声明 1.5.0 的二进制兼容性破例。

**工作逻辑**：因 `Types.NestedField` 新增构造函数和 `withFieldId` 方法，revapi（API 兼容性检查工具）会报 `java.class.defaultSerializationChanged`。该条目将该 break 在 1.5.0 版本下标记为「可接受」，理由是「new Constructor added」。这是 Iceberg 在引入 API 增强时的常规做法。

### `api/src/main/java/org/apache/iceberg/types/AssignIds.java`（新增）
**修改目的**：实现按 `GetID` 函数遍历并重分配 type 树中所有字段 ID 的 visitor。

**工作逻辑**：`AssignIds extends TypeUtil.CustomOrderSchemaVisitor<Type>`。核心方法 `struct(...)`：先对本层 struct 的每个 `NestedField` 调用 `idFor(field.fieldId())` 得到新 ID（先于递归子节点分配，保证 ID 顺序与字段顺序一致），然后递归调用子节点的 `future.get()` 拿到子类型，最后用 `Types.NestedField.optional/required(newId, name, type, doc)` 重建字段。`list` 和 `map` 同样先调用 `idFor` 重分配元素/键/值的 ID，再递归子类型。`primitive` 直接返回。这样一次 visit 即可生成结构相同、ID 全部按 `GetID` 重分配的新 type 树。

### `api/src/main/java/org/apache/iceberg/types/TypeUtil.java`
**修改目的**：暴露 `assignIds(Type, GetID)` 静态方法与 `GetID` 函数式接口。

**工作逻辑**：新增 public 方法 `assignIds` 委托给 `TypeUtil.visit(type, new AssignIds(getId))`。新增内部接口 `GetID { int get(int oldId); }`，与已有 `AssignFreshIds` 用的接口签名不同——`GetID` 接收旧 ID 作为参数，便于按映射表进行定向重分配。

### `api/src/main/java/org/apache/iceberg/Schema.java`
**修改目的**：在 Schema 构造时支持按 `GetID` 重分配字段 ID，并维护原↔新 ID 双向映射。

**工作逻辑**：
- 新增 4 个构造函数重载，均接 `TypeUtil.GetID` 参数（与现有无 `GetID` 的构造函数并存，保证向后兼容）。
- 新增两个 transient 字段：`idsToReassigned`（原 ID → 新 ID）和 `idsToOriginal`（新 ID → 原 ID）。
- 在最完整构造函数中调用 `reassignIds(columns, getID)`：若 `getID == null` 直接返回原 columns；否则用 `TypeUtil.assignIds(StructType.of(columns), oldId -> {...})` 遍历替换，lambda 中对每个 `oldId` 计算 `newId`，若二者不同就同时填入两张映射表，最后返回新字段列表给 `StructType.of(finalColumns)`。
- 对外暴露 `idsToReassigned()` 和 `idsToOriginal()` 两个查询方法（空时返回 `Collections.emptyMap()`）。

### `api/src/main/java/org/apache/iceberg/types/Types.java`
**修改目的**：为 `NestedField` 增加 `withFieldId(int newId)` 方法。

**工作逻辑**：`public NestedField withFieldId(int newId) { return new NestedField(isOptional, newId, name, type, doc); }`，保留 optional/required、name、type、doc 属性，仅替换 ID。供 `PartitionSpec.rawPartitionType()` 等处使用。

### `api/src/main/java/org/apache/iceberg/PartitionSpec.java`
**修改目的**：新增 `rawPartitionType()` 方法返回 manifest 中实际写入的原始 ID 分区类型。

**工作逻辑**：新增 transient 字段 `lazyRawPartitionType` 做惰性缓存。`rawPartitionType()` 逻辑：若 `schema.idsToOriginal().isEmpty()`（schema 没有重分配），直接返回 `partitionType()`；否则基于 `partitionType()` 的字段，用 `f.withFieldId(schema.idsToOriginal().get(f.fieldId()))` 把每个分区字段 ID 反向还原为原始 ID，组装新 struct 返回。双重检查锁保证线程安全。

### `core/src/main/java/org/apache/iceberg/PositionDeletesTable.java`
**修改目的**：在 `calculateSchema()` 中对分区字段 ID 做去冲突重分配。

**工作逻辑**：
1. 先把 PositionDeletesTable 的所有列（`DELETE_FILE_PATH`、`DELETE_FILE_POS`、`DELETE_FILE_ROW_FIELD`、`PARTITION_COLUMN`、`SPEC_ID_COLUMN`、`FILE_PATH_COLUMN`）放入 `columns` 列表。
2. 计算 `currentlyUsedIds` = 这些 PositionDeletesTable 自身字段的 ID 集合。
3. 计算 `allUsedIds` = `currentlyUsedIds` ∪ 原始表所有 schema 的字段 ID 集合（用 `Sets::union` 归约）。
4. `idsToReassign` = `partitionType.fields()` 的所有分区字段 ID。
5. 用 `AtomicInteger nextId` 从 1 开始递增，跳过 `allUsedIds` 中已占用的 ID，作为分区字段 ID 的新值。
6. 构造 `Schema` 时传入 `GetID` lambda：若 `oldId` 不在 `idsToReassign` 中则原样返回；否则取 `nextId.incrementAndGet()` 并跳过冲突，直到找到未占用 ID。Schema 构造器会自动调用 `reassignIds` 完成替换并填充映射表。

注意：`if (!partitionType.fields().isEmpty())` 后续逻辑保持不变（即分区表返回 result；非分区表用 `TypeUtil.selectNot` 去掉空 partition 列）。

### `core/src/main/java/org/apache/iceberg/BaseMetadataTable.java`
**修改目的**：让元数据表的 `transformSpec` 在构造 PartitionSpec 时使用重分配后的 field ID。

**工作逻辑**：原 `transformSpec` 直接 `builder.add(field.fieldId(), field.fieldId(), field.name(), Transforms.identity())`。新版先取 `metadataTableSchema.idsToReassigned()`，然后对每个 `PartitionField`：`int newFieldId = reassignedFields.getOrDefault(field.fieldId(), field.fieldId())`，再用 `newFieldId` 同时作为 sourceId 和 fieldId 添加到 builder。这样元数据表的 partition spec 与其 schema 内的重分配 ID 对齐，校验通过。

### `core/src/main/java/org/apache/iceberg/ManifestReader.java`
**修改目的**：读取 manifest 文件时使用「原始 ID」的分区类型。

**工作逻辑**：将 `this.fileSchema = new Schema(DataFile.getType(spec.partitionType()).fields())` 改为 `... spec.rawPartitionType() ...`。原因：manifest 文件中存储的分区数据是按**原始分区字段 ID**（1000+）写入的，如果用重分配后的 ID 构造 `fileSchema`，会导致读出的分区数据 ID 与 fileSchema 不匹配。改用 `rawPartitionType()` 后，读出的数据按原始 ID 解析，再由上层通过 `transformSpec` / 映射表对齐到元数据表 schema。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScans.java`
**修改目的**：新增 `testPositionDeletesManyColumns` 测试，并修正若干已有测试中 partitionType 的获取方式。

**工作逻辑**：
- 三处把 `Partitioning.partitionType(table)` 改为 `positionDeletesTable.spec().partitionType()`，使用元数据表自身的分区类型（即重分配后的 ID）来读取/校验分区值。
- 一处把 `taskPartitionStruct.get(1, Integer.class)` 改为 `get(0, ...)`，原因是去掉分区字段重分配后，元数据表 spec 中分区字段位置发生变化（重分配后无插入字段，原先偏移的位置变成 0）。
- 新增 `testPositionDeletesManyColumns`：往表里加 2001 列（ID 进入 1000+ 区段），写 2 个数据文件 + 2 个 position delete 文件，断言 `TypeUtil.indexById(positionDeletesTable.schema().asStruct()).size() == 2010`（说明无 ID 冲突，所有字段 ID 都被正确收录），并验证扫描能正常返回 2 个 PositionDeletesScanTask。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScansWithPartitionEvolution.java`
**修改目的**：修正分区演化测试中分区类型与字段位置的预期。

**工作逻辑**：与 `TestMetadataTableScans` 类似，把 `Partitioning.partitionType(table)` 改为 `positionDeletesTable.spec().partitionType()`；`get(1, Integer.class)` 改为 `get(0, ...)`；并修正 `posDeleteTask.spec().fields().get(0).fieldId()` 的期望为 `partitionType.fields().get(0).fieldId()`（即元数据表自身的 partitionType）。

### `spark/v3.3|v3.4|v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java`
**修改目的**：使用元数据表的 partitionType 进行分组，并复用同一个 deletesTable 实例。

**工作逻辑**：三个 Spark 版本的修改完全一致：
1. `planFileGroups()` 中将原本在 `planFiles()` 内部创建的 `deletesTable` 提到外层，作为参数传入 `planFiles(deletesTable)`，避免重复创建实例。
2. `partitionType` 的来源从 `Partitioning.partitionType(table)`（原始表）改为 `Partitioning.partitionType(deletesTable)`（PositionDeletesTable 元数据表），使用重分配后无冲突的分区字段 ID 进行 `groupByPartition` 分组。
3. `planFiles()` 方法签名改为 `planFiles(Table deletesTable)`，去掉内部 `MetadataTableUtils.createMetadataTableInstance` 调用。

### `spark/v3.3|v3.4|v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java`
**修改目的**：新增 `testRewriteManyColumns` 测试覆盖超千列表的 rewrite 场景。

**工作逻辑**：三个 Spark 版本测试代码一致。构造 1010 列的表（列 ID 0~1009，与分区字段 ID 1000 冲突），用 `bucket("id", 2)` 分区；写 4 行数据（2 个数据文件）+ 对应 position delete（2 个 delete 文件）；调用 `SparkActions.get(spark).rewritePositionDeletes(table)` 并设置 `REWRITE_ALL=true` 强制重写；断言重写后仍为 2 个 delete 文件、文件已本地排序、结果统计正确、序列号正确、记录数与删除记录数与重写前一致。

## 小结
- **成效**：彻底修复了「列数 > 1000 时 rewrite_position_delete_files 失败」的 Bug。通过在 PositionDeletesTable 元数据表内部对分区字段 ID 做去冲突重分配，并维护原↔新 ID 映射，使得元数据表 schema 中不再有 ID 冲突；同时通过 `rawPartitionType()` 保留对 manifest 文件中原始 ID 分区数据的兼容读取。修复方式对原始表 schema、磁盘格式、manifest 文件均无侵入，向后兼容。
- **影响范围**：核心 API 层（`Schema`、`TypeUtil`、`Types`、`PartitionSpec`、新增 `AssignIds` 类）+ Core 层（`PositionDeletesTable`、`BaseMetadataTable`、`ManifestReader`）+ Spark v3.3/v3.4/v3.5 三个版本的 `RewritePositionDeleteFilesSparkAction`。新增了 Schema 构造重载与 `GetID` 接口，对调用方透明（默认 getID=null 走原路径）。受影响行为主要是 PositionDeletesTable 元数据表的 schema 内部 ID 分配策略，以及依赖它的 PositionDeletes 扫描与 rewritePositionDeletes Action。其他元数据表（FilesTable、ManifestsTable 等）未受影响，因为它们不嵌入原始表 schema。
- **回迁注意事项**：
  1. 这是一次跨 API/Core/Spark 的较大改动，回迁到 1.4.x 时需整套回迁，不能只取部分文件。尤其 `Schema` 新构造函数、`AssignIds`、`TypeUtil.GetID`、`Types.NestedField.withFieldId`、`PartitionSpec.rawPartitionType()` 必须同时回迁，否则编译/运行时会缺方法。
  2. `BaseMetadataTable.transformSpec` 与 `ManifestReader` 的改动是配套的：前者让元数据表 spec 使用新 ID，后者让磁盘读使用原 ID，二者必须同时回迁，否则会读写不一致。
  3. 三个 Spark 版本（v3.3/v3.4/v3.5）的 `RewritePositionDeleteFilesSparkAction` 改动相同，1.4.x 若已停止维护某个 Spark 版本（如 v3.3）则可跳过对应版本；但需确认 1.4.x 支持的所有 Spark 版本都同步修改。
  4. `.palantir/revapi.yml` 中针对 1.5.0 的兼容性破例条目，回迁到 1.4.x 时版本号需调整为 1.4.x 对应的下一版本号（如 1.5.0 → 1.4.x 的下一个 minor），否则 revapi 检查在 1.4.x 上下文仍会报错。
  5. 测试中 `get(1, ...)` → `get(0, ...)` 的位置调整依赖重分配后的字段顺序，回迁时若 1.4.x 的 PositionDeletesTable 字段顺序与 main 不同，需重新核对测试预期。
  6. 回迁后建议跑一遍 `testPositionDeletesManyColumns` 与 `testRewriteManyColumns` 两个新增测试，验证修复在 1.4.x 上的有效性。
