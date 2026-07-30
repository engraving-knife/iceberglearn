# 提交 0244：Flink: switch to use SortKey for data statistics (#9212)

## 提交信息

- **序号**：0244 / 4088
- **哈希**：beb41b649c47ec8bf677127b3cba1bd9690e1aac
- **短哈希**：beb41b649
- **日期**：2023-12-08 12:57:44 -0800
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: switch to use SortKey for data statistics (#9212)
- **PR/Issue**：#9212

## 总体目的

Iceberg Flink sink 的 shuffle 机制（`flink/sink/shuffle` 包）通过 `DataStatistics` 收集数据分布信息，由 coordinator 聚合后指导 sink 写入时的数据分布与局部排序，从而在写入 Iceberg 表时生成符合 `SortOrder` 的数据文件，提升下游查询的数据跳过能力。在本次改动之前，统计的"键"是 Flink 的 `RowData`：算子通过一个 `KeySelector<RowData, RowData>` 从输入行提取键，然后以 `RowData` 作为 map 的 key 进行计数和序列化。

这种以 `RowData` 为键的设计存在根本性的局限。第一，`RowData` 不能直接表达 `SortOrder` 中带 transform 的字段（例如 `bucket(x, 16)`、`truncate(s, 2)`、`hour(ts)` 等），因为 `KeySelector` 只能截取原始列值，无法应用 transform，导致统计的键与实际写入时排序所用的"排序键"语义不一致。第二，`RowData` 作为 map key 时其相等性 / hashCode 依赖 Flink 的 `RowDataSerializer` 配套实现，且 `RowData` 对象常被复用，直接作为 map key 容易产生别名问题。第三，统计键的序列化绑死在 `RowDataSerializer` 上，无法体现 Iceberg 侧的 schema / sortOrder 演进。

Iceberg core 已经在更早的 StructTransform / SortKey 系列工作中引入了 `SortKey`（`org.apache.iceberg.SortKey`）这一抽象：它绑定一个 `Schema` 与 `SortOrder`，通过 `wrap(StructLike)` 把行数据包装成排序键，内部对每个 `SortField` 应用其 `transform` 得到变换后的值，并支持 `copy()`、`get(i, class)`、`set(i, value)` 等访问接口。本提交把 Flink 数据统计的键类型从 `RowData` 整体切换到 `SortKey`，使统计的键能精确表达 `SortOrder`（含 transform）的语义，并为后续基于分布感知的 sink shuffle 打好基础。

## 如何达成设计目的

整体设计是沿着数据流的方向把"键"的类型从 `RowData` 替换为 `SortKey`：

1. `DataStatistics<D, S>` 接口的 `add` 入参从 `RowData` 改为 `SortKey`，并把泛型 `D extends DataStatistics` 修正为 `D extends DataStatistics<D, S>`（自类型递归，便于 merge 返回具体子类型）。
2. `MapDataStatistics` 把内部 `Map<RowData, Long>` 改为 `Map<SortKey, Long>`，并在 `add` 中对入参 `SortKey` 做 `copy()` 再放入 map（因为输入 `SortKey` 是复用对象）。
3. `DataStatisticsOperator` 不再持有 `KeySelector<RowData, RowData>`，改为持有 `RowDataWrapper` 与一个可复用的 `SortKey` 实例；`processElement` 时把 `RowData` 包装成 `StructLike`，调用 `sortKey.wrap(struct)` 复用同一个 `SortKey` 对象再交给统计。
4. 新增 `SortKeySerializer`：实现 Flink 的 `TypeSerializer<SortKey>`，按 `SortOrder` 中每个 `SortField` 的 transform 结果类型逐字段序列化，并实现 `TypeSerializerSnapshot` 以支持 checkpoint 恢复时的 schema 兼容性检查。
5. `MapDataStatisticsSerializer` 把内部 `MapSerializer<RowData, Long>` 换成 `MapSerializer<SortKey, Long>`，工厂方法从 `fromKeySerializer(RowDataSerializer)` 改为 `fromSortKeySerializer(SortKeySerializer)`。
6. 同步改造一批测试，并新增 `TestSortKeySerializerBase` / `TestSortKeySerializerPrimitives` / `TestSortKeySerializerNestedStruct` / `TestMapDataStatistics` 覆盖新序列化器与统计行为。

## 修改详情

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatistics.java`

**修改目的**：把统计接口的键类型从 `RowData` 切换为 `SortKey`，并修正泛型自类型。

**工作逻辑**：

- import 从 `org.apache.flink.table.data.RowData` 改为 `org.apache.iceberg.SortKey`。
- 接口声明由 `interface DataStatistics<D extends DataStatistics, S>` 改为 `interface DataStatistics<D extends DataStatistics<D, S>, S>`，使 `D` 成为真正的自类型（F-bounded），这样 `merge(D other)` 等方法能返回 / 接受具体子类型而非裸 `DataStatistics`。
- `add(RowData key)` 改为 `add(SortKey sortKey)`，注释由"Add data key ... generate from data by applying key selector"改为"Add row sortKey to data statistics."，反映键的来源从 key selector 变为 sort key 包装。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapDataStatistics.java`

**修改目的**：把内部统计 map 的键类型换为 `SortKey`，并处理 `SortKey` 复用对象导致别名问题。

**工作逻辑**：

- 类声明由 `DataStatistics<MapDataStatistics, Map<RowData, Long>>` 改为 `DataStatistics<MapDataStatistics, Map<SortKey, Long>>`，字段与构造方法的 `Map<RowData, Long>` 同步改为 `Map<SortKey, Long>`。
- `add` 方法由简单的 `statistics.merge(key, 1L, Long::sum)` 改为先 `containsKey` 判断：若已存在则 `merge` 累加；若不存在则调用 `sortKey.copy()` 克隆一份再放入 map。这是因为 `DataStatisticsOperator` 中的 `SortKey` 是单实例复用的（`sortKey.wrap(struct)` 每次只改写内部状态），若直接把同一个对象作为 key 放入 map，后续 wrap 会改写已存入的 key，破坏 map 不变式。`copy()` 产生独立快照，规避别名。这是从 `RowData`（也是复用对象）迁移到 `SortKey` 时必须保留的语义——此前用 `RowData` 作 key 时 Flink 的 `RowDataSerializer` 在序列化路径上间接避免了别名，但作为内存 map 的 key 直接复用是不安全的，新实现显式 copy。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOperator.java`

**修改目的**：用 `RowDataWrapper` + 复用 `SortKey` 取代 `KeySelector`，使统计键能表达 `SortOrder`。

**工作逻辑**：

- 移除 `KeySelector<RowData, RowData> keySelector` 字段与对应 import；新增 `RowDataWrapper rowDataWrapper` 与 `SortKey sortKey` 字段，并 import `Schema`、`SortKey`、`SortOrder`、`StructLike`、`FlinkSchemaUtil`、`RowDataWrapper`。
- 构造方法签名由 `(operatorName, KeySelector<RowData, RowData>, gateway, serializer)` 改为 `(operatorName, Schema schema, SortOrder sortOrder, gateway, serializer)`，内部 `this.rowDataWrapper = new RowDataWrapper(FlinkSchemaUtil.convert(schema), schema.asStruct())`（注意 `FlinkSchemaUtil.convert(schema)` 返回 Flink `RowType`，`schema.asStruct()` 是 Iceberg `StructType`，`RowDataWrapper` 桥接二者），`this.sortKey = new SortKey(schema, sortOrder)`。
- `processElement` 由 `RowData key = keySelector.getKey(record); localStatistics.add(key);` 改为 `StructLike struct = rowDataWrapper.wrap(record); sortKey.wrap(struct); localStatistics.add(sortKey);`。这里 `rowDataWrapper.wrap(record)` 把 Flink `RowData` 包装成 Iceberg `StructLike`，`sortKey.wrap(struct)` 则把该 `StructLike` 包装成排序键——内部会按 `SortOrder` 的每个 `SortField` 应用 `transform` 取变换后的值。`sortKey` 是单实例复用，`MapDataStatistics.add` 内部会 `copy()`，所以这里无需克隆。同时把 `throws Exception` 去掉（不再抛受检异常）。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java`（新文件）

**修改目的**：为 `SortKey` 提供自定义的 Flink `TypeSerializer`，使统计状态可被 checkpoint 序列化与恢复。

**工作逻辑**：

这是本提交最核心的新增类，共 353 行。设计要点如下：

- 持有 `Schema schema`、`SortOrder sortOrder`、`int size`（= `sortOrder.fields().size()`）以及 `Types.NestedField[] transformedFields`。构造时遍历 `sortOrder.fields()`，对每个 `SortField` 取其 `sourceId` 对应的 `NestedField`，再用 `sortField.transform().getResultType(sourceField.type())` 计算变换后的结果类型，组装成新的 `NestedField`（保留原 field id / optional / name / doc，仅替换 type）。这样序列化时按"变换后类型"读写，与 `SortKey` 内部存储的值类型一致。
- `transient SortKey sortKey` + `lazySortKey()`：用于 `deserialize` 时复用一个 `SortKey` 实例（`lazySortKey().copy()` 再填充），声明 `transient` 是因为 `TypeSerializer` 实例本身可能被序列化传输，而 `SortKey` 不一定可序列化。
- `serialize(SortKey record, DataOutputView target)`：按 `size` 逐字段，依据 `transformedFields[i].type().typeId()` 选择写入方式。覆盖 `BOOLEAN`、`INTEGER/DATE`、`LONG/TIME/TIMESTAMP`、`FLOAT`、`DOUBLE`、`STRING`（`writeUTF`）、`UUID`（拆成两个 long）、`FIXED/BINARY`（先写长度再写字节）、`DECIMAL`（写 unscaled 的字节 + scale）。对 `STRUCT/MAP/LIST` 抛 `UnsupportedOperationException`，并注释说明 `SortKey` 的 transform 是扁平 struct，不含 list/map。
- `deserialize(DataInputView)` 与 `deserialize(SortKey reuse, DataInputView)`：与 serialize 对称地逐字段读取并通过 `reuse.set(i, value)` 填充。`deserialize()` 无参版本用 `lazySortKey().copy()` 作为容器（注释说 copy 比新建略快），再调用 `deserialize(reuse, source)`。
- `copy(SortKey from)` 委托给 `from.copy()`（Iceberg `SortKey` 自带深拷贝）；`copy(from, reuse)` 不复用直接 `copy(from)`；`createInstance()` 返回 `new SortKey(schema, sortOrder)`；`getLength()` 返回 -1（变长）；`isImmutableType()` 返回 false。
- `equals` 比较 `schema.asStruct()` 与 `sortOrder`；`hashCode` = `schema.asStruct().hashCode() * 31 + sortOrder.hashCode()`。
- `snapshotConfiguration()` 返回内部类 `SortKeySerializerSnapshot`（实现 `TypeSerializerSnapshot<SortKey>`）：`writeSnapshot` 把 `SchemaParser.toJson(schema)` 与 `SortOrderParser.toJson(sortOrder)` 写出；`readSnapshot` 读回 JSON 并 `SortOrderParser.fromJson(...).bind(schema)` 重新绑定；`resolveSchemaCompatibility` 用 `CheckCompatibility.writeCompatibilityErrors(readSchema, writeSchema)` 检查写兼容性（读 schema 必须能容纳写 schema 的所有字段），无错则 `compatibleAsIs()`，否则 `incompatible()`；`restoreSerializer()` 用恢复出的 schema / sortOrder 重建 `SortKeySerializer`。这保证 checkpoint 恢复时若 schema 演进违反写兼容性会被检测出来，而不是静默错乱。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapDataStatisticsSerializer.java`

**修改目的**：把内部 map 序列化器的键类型从 `RowData` 换成 `SortKey`，并调整工厂方法。

**工作逻辑**：

- 类签名、字段、工厂方法、`duplicate`、`createInstance`、`copy`、`serialize`、`deserialize`、`snapshotConfiguration` 等所有涉及泛型 `DataStatistics<MapDataStatistics, Map<RowData, Long>>` 的位置统一替换为 `DataStatistics<MapDataStatistics, Map<SortKey, Long>>`。
- 工厂方法由 `fromKeySerializer(TypeSerializer<RowData> keySerializer)` 改为 `fromSortKeySerializer(TypeSerializer<SortKey> sortKeySerializer)`，内部 `new MapSerializer<>(sortKeySerializer, LongSerializer.INSTANCE)`。
- `createInstance()` 的返回类型由泛化的 `DataStatistics<...>` 收窄为具体 `MapDataStatistics`，更精确。
- `copy(DataStatistics obj)` 中把 `TypeSerializer<RowData>` 改为 `TypeSerializer<SortKey>`，循环里 `keySerializer.copy(entry.getKey())` 仍然成立（`SortKeySerializer.copy` 委托给 `SortKey.copy()`）。
- `createOuterSerializerWithNestedSerializers` 中把 `MapSerializer<RowData, Long>` 改为 `MapSerializer<SortKey, Long>`。
- `snapshotConfiguration` 的泛型与内部 `MapDataStatisticsSerializerSnapshot` 的 `CompositeTypeSerializerSnapshot` 参数同步更新。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsUtil.java`

**修改目的**：清理不再需要的 `@SuppressWarnings("unchecked")`。

**工作逻辑**：

`deserializeAggregatedStatistics` 方法签名本身泛型已自洽（`<D extends DataStatistics<D, S>, S>`），此前因接口泛型未做 F-bound 而需要抑制 unchecked 警告；本提交修正接口泛型后该注解不再需要，故移除。仅此一行改动。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestMapDataStatistics.java`（新文件）

**修改目的**：覆盖 `MapDataStatistics` 在 `SortKey` 复用场景下的计数正确性。

**工作逻辑**：

- 用 `TestFixtures.SCHEMA` 与 `SortOrder.builderFor(...).asc("data").build()` 构造 `SortKey` 与 `RowDataWrapper`。
- `testAddsAndGet`：复用同一个 `GenericRowData reusedRow` 与同一个 `SortKey`，连续 6 次 `sortKey.wrap(rowWrapper.wrap(reusedRow))` 后 `dataStatistics.add(sortKey)`，分别传入 `a/b/c/b/a/b`。这模拟了算子真实场景——`SortKey` 单实例复用，每次 `wrap` 改写内部状态。最后断言统计 map 为 `{a:2, b:3, c:1}`。该测试直接验证了 `MapDataStatistics.add` 中 `copy()` 的必要性：若不 copy，所有 key 会指向最后一个值 `b`，统计结果会错乱。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSortKeySerializerBase.java`（新文件）、`TestSortKeySerializerPrimitives.java`（新文件）、`TestSortKeySerializerNestedStruct.java`（新文件）

**修改目的**：基于 Flink `SerializerTestBase` 系统化验证 `SortKeySerializer` 的序列化、反序列化、快照兼容性。

**工作逻辑**：

- `TestSortKeySerializerBase` 继承 Flink 的 `SerializerTestBase<SortKey>`，声明三个抽象方法 `schema()`、`sortOrder()`、`rowData()`，由子类提供具体 schema/sortOrder/row。`createSerializer()` 返回 `new SortKeySerializer(schema(), sortOrder())`；`getTestData()` 通过 `RowDataWrapper` 把 `rowData()` 包装成 `SortKey` 返回。`SerializerTestBase` 会自动跑一组标准用例（copy、serialize/deserialize 往返、duplicate、snapshot 等）。
- `TestSortKeySerializerPrimitives` 用 `DataGenerators.Primitives` 生成包含 boolean/int/string/uuid/timestamp 等原始类型与嵌套类型的 schema，`sortOrder()` 组合了 `asc`、`bucket`、`truncate`、`hour`、`day` 等多种 transform 与 `SortDirection`/`NullOrder`，全面覆盖 `SortKeySerializer.serialize` 的各个 type 分支。
- `TestSortKeySerializerNestedStruct` 则针对含嵌套 struct 的 schema 验证（注释里说明 binary 字段因 `HeapByteBuffer` 相等性问题未纳入，对应 `serialize` 中 `FIXED/BINARY` 分支在测试中被规避）。

### 其余测试文件（`TestAggregatedStatistics.java`、`TestAggregatedStatisticsTracker.java`、`TestDataStatisticsCoordinator.java`、`TestDataStatisticsCoordinatorProvider.java`、`TestDataStatisticsOperator.java`）

**修改目的**：跟随产品代码把测试中的键类型从 `RowData` 迁移到 `SortKey`。

**工作逻辑**：

这些文件的改动模式一致：

- 不再构造 `GenericRowData` / `RowDataSerializer` / `RowType.of(VarCharType)`，改为构造 `Schema` + `SortOrder` + `SortKey` + `SortKeySerializer`。
- 通过 `sortKey.copy()` 得到独立 key，再用 `key.set(0, "a")` 设值（`SortKey.set` 接受 transform 后的值类型），取代此前的 `GenericRowData.of(StringData.fromString("a"))`。
- 序列化器构造由 `MapDataStatisticsSerializer.fromKeySerializer(new RowDataSerializer(...))` 改为 `MapDataStatisticsSerializer.fromSortKeySerializer(new SortKeySerializer(schema, sortOrder))`。
- `DataStatisticsOperator` 的测试构造由传 `KeySelector` 改为传 `Schema` + `SortOrder`。
- 断言中以 `SortKey` 作为 map key 查询，替代原 `RowData`。

`TestDataStatisticsOperator` 还验证了算子端到端：输入 `RowData` 后算子内部用 `RowDataWrapper` + `SortKey` 生成统计，输出仍为原始 record（`DataStatisticsOrRecord.fromRecord`），行为不变但统计键语义已升级。

## 小结

本提交把 Flink sink shuffle 数据统计的键类型从 `RowData` 整体切换到 Iceberg `SortKey`，使统计键能精确表达含 transform 的 `SortOrder` 语义，并配套实现 `SortKeySerializer` 与快照兼容性检查、处理复用对象的 copy 别名问题，为分布感知的 sink 写入与 checkpoint 恢复打下了类型安全的基础。
