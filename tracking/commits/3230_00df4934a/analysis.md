# 提交 3230：Core, Spark 4.1: Fix querying equality deletes with schema evolution (#15268)

## 提交信息

- **序号**：3230 / 4088
- **哈希**：00df4934a6b66c9f36025f45eae1fd2f7588f71f
- **短哈希**：00df4934a
- **日期**：2026-02-09
- **作者**：Anton Okolnychyi
- **提交说明**：Core, Spark 4.1: Fix querying equality deletes with schema evolution (#15268)
- **PR/Issue**：#15268

## 总体目的

本提交修复了当表 schema 发生演进（schema evolution）后查询 equality delete（等值删除）文件时的正确性问题。在 Iceberg 中，equality delete 文件通过字段 ID 引用被删除的列，并在扫描时利用这些字段的边界（lower/upper bounds）进行删除文件过滤优化。然而，当表的 schema 发生演进（如类型提升 int→long、列添加/删除等）后，原有代码使用 partition spec 关联的 schema 来查找字段定义，而非使用最新或正确的 schema 版本。

具体问题在于 `DeleteFileIndex.EqualityDeleteFile` 内部通过 `spec.schema().findField(id)` 查找等值删除字段。`PartitionSpec` 关联的 schema 可能是旧的 schema 版本，导致获取的字段类型与当前表 schema 不一致。例如，字段从 `IntegerType` 提升为 `LongType` 后，使用旧 schema 查找字段会得到 `IntegerType`，而删除文件中的边界值可能是 `LongType`，造成类型不匹配，边界转换失败，最终导致删除文件被错误地跳过或错误地匹配，查询结果不正确。

此提交通过引入跨 schema 的字段索引机制，确保查找等值删除字段时使用正确的字段定义（优先采用高 schema ID 的版本以处理类型提升），同时重构了 Spark reader 层中传递 `tableSchema` 的方式——移除了在各 reader 构造器中显式传递 `tableSchema` 参数的做法，改为使用基于 `Table` 对象的延迟字段查找机制 `FieldLookup`，避免在 Spark executor 序列化时携带不必要的 schema 信息。

## 如何达成设计目的

整体设计分三个层面：在 API 层，新增 `Schema.indexFields(Collection<Schema>)` 静态方法，将多个 schema 的字段按 ID 索引，高 schema ID 优先（用于类型提升）；在 Core 层，`DeleteFileIndex.Builder` 新增 `schemasById()` 方法接受 schema 映射，构建 `fieldLookup` 函数传给 `EqualityDeletes` 和 `EqualityDeleteFile`，替换原先的 `spec.schema().findField(id)`；在 Spark 层，移除 `BaseReader` 及其子类中的 `tableSchema` 参数，新增 `FieldLookup` 内部类通过 `table.schema()` 优先查找、延迟加载历史 schema 字段，同时各 scan 类（`DataScan`、`DataTableScan`、`BaseDistributedDataScan` 等）向 `DeleteFileIndex` 传递 schema 信息，`DeleteFilter` 也改用 `fieldLookup` 函数替代 `tableSchema`。此外移除了 `SparkInputPartition` 和 `SparkBatch` 中不再需要的 `branch` 字段。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Schema.java` (+31/-0 lines)

**修改目的**：新增跨 schema 的字段索引方法。

**工作逻辑**：
新增静态方法 `indexFields(Collection<Schema> schemas)`，当只有一个 schema 时直接返回其 `lazyIdToField()`；当有多个 schema 时，通过 `sortAndDeduplicate` 按 `schemaId` 排序去重后，依次将每个 schema 的字段映射 `putAll` 到结果 map 中。由于按 schema ID 升序遍历，后处理（高 ID）的 schema 会覆盖先前的字段定义，从而实现"高 schema ID 优先"的语义，正确处理类型提升场景。`sortAndDeduplicate` 使用 `TreeSet` + `Comparator.comparingInt(Schema::schemaId)` 实现。

### `api/src/test/java/org/apache/iceberg/TestSchema.java` (+95/-0 lines)

**修改目的**：测试 `indexFields` 方法的各种场景。

**工作逻辑**：
新增 4 个测试用例：`testIndexFieldsSingleSchema` 验证单一 schema 的字段索引；`testIndexFieldsHigherSchemaIdTakesPrecedence` 验证高 schema ID 的字段定义优先（同一字段 ID 在 schema2 中为 `IntegerType` 且 required，在 schema1 中为 `StringType` 且 optional，结果取 schema2）；`testIndexFieldsDuplicateSchemaIds` 验证相同 schema ID 去重（保留先加入的）；`testIndexFieldsNestedSchema` 验证嵌套结构体字段也被正确索引。

### `core/src/main/java/org/apache/iceberg/DeleteFileIndex.java` (+52/-9 lines)

**修改目的**：使用 `fieldLookup` 替代 `spec.schema().findField(id)` 进行等值删除字段查找。

**工作逻辑**：
`Builder` 新增 `schemasById` 字段和对应 setter。`build()` 方法中调用 `Schema.indexFields(schemas())` 构建 `fieldsById` 映射，并获取 `fieldLookup = fieldsById::get` 函数。`schemas()` 方法优先使用 `schemasById`，否则回退到从 `specsById` 中提取 schema。`EqualityDeletes` 类构造器接受 `fieldLookup` 参数，`add()` 方法不再接收 `PartitionSpec` 参数，改用构造时传入的 `fieldLookup` 创建 `EqualityDeleteFile`。`EqualityDeleteFile` 内部将 `spec` 字段替换为 `fieldLookup`，在 `equalityFields()` 中使用 `fieldLookup.apply(id)` 替代 `spec.schema().findField(id)`，并新增 `Preconditions.checkArgument(field != null)` 校验。在 `convertedBounds` 中也直接使用已查找的 `field.type()` 而非重新查找。

### `core/src/main/java/org/apache/iceberg/BaseScan.java` (+4/-0 lines)

**修改目的**：为子类提供访问表 schema 映射的方法。

**工作逻辑**：
新增 `protected Map<Integer, Schema> schemas()` 方法，返回 `table.schemas()`，供各 scan 子类调用以传递给 `DeleteFileIndex`。

### `core/src/main/java/org/apache/iceberg/DataScan.java` (+1/-0 lines)

**修改目的**：向 `ManifestGroup` 传递 schema 映射。

**工作逻辑**：
在构建 `ManifestGroup` 时链式调用 `.schemasById(schemas())`。

### `core/src/main/java/org/apache/iceberg/DataTableScan.java` (+1/-0 lines)

**修改目的**：向 `ManifestGroup` 传递 schema 映射。

**工作逻辑**：
同 `DataScan`，在构建 `ManifestGroup` 时新增 `.schemasById(schemas())`。

### `core/src/main/java/org/apache/iceberg/BaseIncrementalAppendScan.java` (+1/-0 lines)

**修改目的**：向 `ManifestGroup` 传递 schema 映射。

**工作逻辑**：
同上，新增 `.schemasById(schemas())`。

### `core/src/main/java/org/apache/iceberg/IncrementalDataTableScan.java` (+1/-0 lines)

**修改目的**：向 `ManifestGroup` 传递 schema 映射。

**工作逻辑**：
同上，新增 `.schemasById(schemas())`。

### `core/src/main/java/org/apache/iceberg/BaseDistributedDataScan.java` (+1/-0 lines)

**修改目的**：向 `DeleteFileIndex` 传递 schema 映射。

**工作逻辑**：
在构建 `DeleteFileIndex` 时新增 `.schemasById(schemas())`。

### `core/src/main/java/org/apache/iceberg/ManifestGroup.java` (+5/-0 lines)

**修改目的**：转发 schema 映射到 `DeleteFileIndex.Builder`。

**工作逻辑**：
新增 `schemasById(Map<Integer, Schema>)` 方法，内部调用 `deleteIndexBuilder.schemasById(newSchemasById)` 并返回 `this` 以支持链式调用。

### `data/src/main/java/org/apache/iceberg/data/DeleteFilter.java` (+15/-4 lines)

**修改目的**：支持通过 `fieldLookup` 函数查找字段，替代 `tableSchema`。

**工作逻辑**：
新增接受 `Function<Integer, Types.NestedField> fieldLookup` 参数的构造器重载；原有接受 `Schema tableSchema` 的构造器改为委托调用 `tableSchema::findField`。`fileProjection` 静态方法签名从 `Schema tableSchema` 改为 `Function<Integer, Types.NestedField> fieldLookup`，内部 `tableSchema.asStruct().field(fieldId)` 改为 `fieldLookup.apply(fieldId)`，并新增空值校验。另一个仅接受 `tableSchema` 的构造器也改为委托 `tableSchema::findField`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/SparkDistributedDataScan.java` (+1/-0 lines)

**修改目的**：向 `DeleteFileIndex` 传递 schema 映射。

**工作逻辑**：
在构建 `DeleteFileIndex` 时新增 `.schemasById(schemas())`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java` (+40/-2 lines)

**修改目的**：移除 `tableSchema` 字段，引入 `FieldLookup` 实现延迟历史 schema 查找。

**工作逻辑**：
移除构造器的 `tableSchema` 参数和 `tableSchema` 字段。`SparkDeleteFilter` 构造时改为传递 `new FieldLookup(table)` 而非 `tableSchema`。新增内部类 `FieldLookup implements Function<Integer, Types.NestedField>`，优先从 `table.schema().findField(id)` 查找（当前 schema），未找到时延迟加载历史 schema 字段（`historicSchemaFields`，通过双重检查锁初始化），历史 schema 过滤掉当前 schema ID。这一设计考虑了 Spark executor 中 `Table` 对象是序列化广播的，获取历史 schema 可能耗时，因此使用延迟初始化并缓存。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseBatchReader.java` (+4/-3 lines) / `BaseRowReader.java` (+4/-3 lines)

**修改目的**：移除 `tableSchema` 参数。

**工作逻辑**：
构造器中移除 `Schema tableSchema` 参数，`super()` 调用中相应移除该参数。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BatchDataReader.java` (+4/-4 lines) / `ChangelogRowReader.java` (+4/-3 lines) / `PositionDeletesRowReader.java` (+6/-4 lines) / `RowDataReader.java` (+6/-4 lines) / `EqualityDeleteRowReader.java` (+3/-2 lines)

**修改目的**：移除各 reader 中对 `SnapshotUtil.schemaFor()` 的调用和 `tableSchema` 参数。

**工作逻辑**：
这些 reader 原来在便捷构造器中调用 `SnapshotUtil.schemaFor(partition.table(), partition.branch())` 获取 tableSchema 并传递给父类。移除该调用和 `tableSchema` 参数后，不再需要 `SnapshotUtil` 导入。`EqualityDeleteRowReader` 继承 `RowDataReader`，同样移除 `tableSchema` 参数。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java` (+3/-3 lines) / `SparkInputPartition.java` (+7/-6 lines) / `SparkMicroBatchStream.java` (+3/-3 lines)

**修改目的**：移除 `branch` 字段及其传递。

**工作逻辑**：
`SparkBatch` 移除了 `branch` 字段和 `readConf.branch()` 调用；`SparkInputPartition` 移除了 `branch` 字段、构造器参数和 `branch()` getter 方法；`SparkMicroBatchStream` 移除了 `branch` 字段和 `readConf.branch()` 调用。这些 `branch` 信息此前用于在各 reader 中调用 `SnapshotUtil.schemaFor()`，现已不再需要。

### `core/src/test/java/org/apache/iceberg/DeleteFileIndexTestBase.java` (+15/-7 lines)

**修改目的**：适配 `EqualityDeletes` 新的 API 签名。

**工作逻辑**：
测试中使用 `SCHEMA::findField` 作为 `fieldLookup`，`new EqualityDeletes(fieldLookup)` 替代 `new EqualityDeletes()`，`group.add(file)` 替代 `group.add(SPEC, file)`。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java` (+59/-11 lines)

**修改目的**：适配 reader 构造器变更并新增 schema 演进下的等值删除测试。

**工作逻辑**：
`EqualityDeleteRowReader` 构造调用移除 `tableSchema` 参数（原传 `null`）。`BatchDataReader` 构造调用移除 `dateTable.schema()` 参数。新增测试 `testEqualityDeleteWithSchemaEvolution`：先添加 `status` 列、写入含该列的数据、对 `status` 列发起等值删除（删除 `INACTIVE` 行）、然后删除 `status` 列，最后验证读取结果正确——3 条含 status 的记录中有 1 条被删除，剩余 2 条保留。这模拟了字段被删除后查询历史等值删除的场景。

### 其余测试文件（`TestHelpers.java`、`TestBaseReader.java`、`TestChangelogReader.java`、`TestPositionDeletesReader.java`）

**修改目的**：适配 reader 构造器签名变更（移除 `tableSchema` 参数），小改动。

## 总结

本提交通过引入跨 schema 的字段索引机制（`Schema.indexFields`）和统一的 `fieldLookup` 函数，修复了 schema 演进后等值删除查询的正确性问题。核心改动是将 `DeleteFileIndex` 中基于 partition spec schema 的字段查找替换为基于所有表 schema 的全局查找，高 schema ID 优先以正确处理类型提升。同时重构了 Spark reader 层，移除了显式传递 `tableSchema` 的模式，改为基于 `Table` 对象的延迟查找，减少了序列化开销。新增了完整的测试覆盖，包括 schema 演进下等值删除的场景验证。
