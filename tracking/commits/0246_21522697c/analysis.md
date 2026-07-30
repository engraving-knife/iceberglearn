# 提交 0246：Flink: backport PR #9212 to 1.16 for switching to SortKey for data statistics

## 提交信息

- **序号**：0246 / 4088
- **哈希**：21522697c42cee3bedd758aa399b9530b001e30a
- **短哈希**：21522697c
- **日期**：2023-12-09 11:12:33 -0800
- **作者**：Steven Wu
- **提交说明**：Flink: backport PR #9212 to 1.16 for switching to SortKey for data statistics
- **PR/Issue**：#9212

## 总体目的

这个提交是把主线（main）PR #9212 的改动回移植（backport）到 Iceberg 的 Flink 1.16 模块。原 PR 的核心目标是：在 Flink sink 的数据分布统计（data statistics）模块中，把用作统计 key 的类型从 Flink 的 `RowData` 切换为 Iceberg 自身的 `SortKey`，以正确支持排序键中包含变换函数（transform）的场景。

之前的实现里，`DataStatistics` 用 `RowData` 作为 key 直接计数，但这只能反映"原始字段值"，无法表达 `SortOrder` 中带 `Transform`（如 `bucket`、`truncate`、`year` 等）的排序键。当用户配置带变换函数的 sort order 时，统计的 key 与实际 shuffle 用的 sort key 不一致，会导致数据分布统计失真，进而影响写文件时的 cluster/分布效果。

通过切换到 `SortKey`，统计模块可以直接复用 Iceberg SortOrder 的语义（包括 transform 结果类型），让统计 key 与写文件排序键完全对齐。这是 Iceberg Flink sink distribution write 功能演进的关键一步，为后续更准确的 range shuffle 和数据 clustering 打下基础。同时，本次 backport 还附带新增了独立的 `SortKeySerializer` 以及对应的序列化兼容性快照（snapshot），使得 SortKey 可以安全地在 Flink state 和 operator event 中序列化传输，支持 checkpoint 恢复。

由于是 backport 到 1.16 分支，改动只作用于 `flink/v1.16/flink/` 子模块，不影响其他 Flink 版本。

## 如何达成设计目的

整体设计思路是：以 Iceberg `SortKey` 替换 `RowData` 作为统计与序列化的核心类型，把"如何从一条 RowData 生成 sort key"这一逻辑从 `KeySelector` 改为在 `DataStatisticsOperator` 内部用 `RowDataWrapper` + `SortKey.wrap()` 直接生成。同时新增专用的 `SortKeySerializer`，按 sort order 中各字段 transform 后的结果类型进行逐字段二进制读写，并在 `MapDataStatisticsSerializer` 中替换为以 `SortKey` 为 map key 的版本。所有相关测试同步迁移到使用 `SortKey`，并新增针对 `MapDataStatistics` 和 `SortKeySerializer` 的单元测试覆盖。

## 修改详情

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatistics.java`

**修改目的**：把统计接口的 key 类型从 `RowData` 切换为 `SortKey`，并修正泛型自引用声明。

**工作逻辑**：`add` 方法签名由 `void add(RowData key)` 改为 `void add(SortKey sortKey)`；import 由 `org.apache.flink.table.data.RowData` 换为 `org.apache.iceberg.SortKey`。同时把泛型 `DataStatistics<D extends DataStatistics, S>` 修正为 `DataStatistics<D extends DataStatistics<D, S>, S>`，使子类泛型自引用正确（让 `MapDataStatistics` 能正确实现 `DataStatistics<MapDataStatistics, ...>`）。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java`

**修改目的**：在 coordinator 端做小幅清理与告警抑制，配合新泛型签名。

**工作逻辑**：为 `sendDataStatisticsToSubtasks` 增加 `@SuppressWarnings("FutureReturnValueIgnored")`（该处故意不等待 Future），并把 `gateways[subtaskIndex].size() > 0` 改写为 `!gateways[subtaskIndex].isEmpty()`。这些是配合类型切换带来的代码 lint 调整，不影响行为。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOperator.java`

**修改目的**：把 operator 内部生成 sort key 的方式从外部 `KeySelector` 改为内置 `RowDataWrapper` + `SortKey`，让 operator 直接产出符合 SortOrder 语义的 sort key 用于统计。

**工作逻辑**：构造参数由 `KeySelector<RowData, RowData> keySelector` 替换为 `Schema schema` 和 `SortOrder sortOrder`；operator 持有 `RowDataWrapper rowDataWrapper` 与 `SortKey sortKey` 字段，在构造时根据 schema 与 sortOrder 初始化。`processElement` 中改为 `StructLike struct = rowDataWrapper.wrap(record); sortKey.wrap(struct); localStatistics.add(sortKey);`——即每条数据复用同一个 SortKey 对象（`wrap` 只是改引用），统计时由 `MapDataStatistics` 负责 copy。同时去掉了 `throws Exception` 声明。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsUtil.java`

**修改目的**：清理与新泛型签名无关但被泛型收紧后多余的 `@SuppressWarnings("unchecked")`。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapDataStatistics.java`

**修改目的**：把内部统计 map 的 key 类型从 `RowData` 换为 `SortKey`，并处理 SortKey 对象复用导致的拷贝问题。

**工作逻辑**：`Map<RowData, Long>` → `Map<SortKey, Long>`。`add(SortKey sortKey)` 实现关键变化：因为 operator 端复用同一个 SortKey 对象，所以新增键时必须 `sortKey.copy()` 后再 put；已有键则只做 `merge` 计数。这避免了"所有 key 都指向同一个对象"的 bug，是切换到可复用 SortKey 的核心正确性保证。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapDataStatisticsSerializer.java`

**修改目的**：把序列化器的 key 类型从 `RowData` 换为 `SortKey`，并重命名工厂方法以反映新语义。

**工作逻辑**：所有 `Map<RowData, Long>` 改为 `Map<SortKey, Long>`，`MapSerializer<RowData, Long>` 改为 `MapSerializer<SortKey, Long>`。工厂方法 `fromKeySerializer(TypeSerializer<RowData>)` 重命名为 `fromSortKeySerializer(TypeSerializer<SortKey>)`。`copy` 方法中用 `TypeSerializer<SortKey>` 复制每个 key。其余 `serialize`/`deserialize`/`snapshotConfiguration`/内部 `MapDataStatisticsSerializerSnapshot` 的泛型参数全部同步更新。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java`（新增）

**修改目的**：为 `SortKey` 提供专用的 Flink `TypeSerializer` 实现，支持 sort key 在 operator event 与 state 中的二进制序列化、checkpoint 恢复与 schema 兼容性检查。

**工作逻辑**：构造时根据 `Schema` 与 `SortOrder` 预计算 `transformedFields`（每个 sort 字段经 transform 后的结果 `Types.NestedField`，携带 source field id、optional、name、result type、doc）。`serialize`/`deserialize` 按 `transformedFields[i].type().typeId()` 分支处理 BOOLEAN/INTEGER/DATE/LONG/TIME/TIMESTAMP/FLOAT/DOUBLE/STRING/UUID/FIXED/BINARY/DECIMAL；对 STRUCT/MAP/LIST 抛 `UnsupportedOperationException`（因为 sort key 经 transform 后是扁平 struct，不会出现嵌套容器）。UUID 用高低位两个 long；FIXED/BINARY 先写长度再写 bytes；DECIMAL 写 unscaled BigInteger 字节数组+scale。还实现 `duplicate`、`copy`、`equals`/`hashCode`（基于 schema.asStruct 与 sortOrder），以及内部静态类 `SortKeySerializerSnapshot`：它实现 `TypeSerializerSnapshot<SortKey>`，`writeSnapshot` 把 schema 和 sortOrder 的 JSON 写出，`readV1` 用 `SchemaParser`/`SortOrderParser` 反序列化并 `bind(schema)`，`resolveSchemaCompatibility` 用 `CheckCompatibility.writeCompatibilityErrors` 判断写 schema 与读 schema 的兼容性（兼容则 `compatibleAsIs`，否则 `incompatible`）。`restoreSerializer` 返回基于快照中 schema/sortOrder 重建的 `SortKeySerializer`。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestAggregatedStatistics.java`

**修改目的**：把测试从 `RowData`/`RowDataSerializer` 迁移到 `SortKey`/`SortKeySerializer`，验证合并逻辑仍正确。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestAggregatedStatisticsTracker.java`

**修改目的**：迁移 tracker 测试到 SortKey，覆盖 newer/older/completed 三类 event 场景下聚合统计的正确性。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsCoordinator.java`

**修改目的**：迁移 coordinator 测试到 SortKey，并改用可复用的 `SortKey key` 配合 `key.set(0, ...)` 来构造多组测试数据，简化样板代码。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsCoordinatorProvider.java`

**修改目的**：迁移 provider 测试到 SortKey，并把 coordinator 用 try-with-resources 包起来保证资源释放；同时修正测试中原本 `keyD`/`keyE` 字面量赋值"c"的笔误（仍保持原测试意图，仅做类型迁移）。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsOperator.java`

**修改目的**：迁移 operator 测试到 SortKey，schema 扩展为两列（id+number）以更贴近真实场景，并改用 `Schema`+`SortOrder` 构造 operator（不再用 `KeySelector`）。`testRestoreState` 中专门处理了反序列化后 RowData 形态变化（BinaryRowData vs GenericRowData）带来的等值比较问题。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestMapDataStatistics.java`（新增）

**修改目的**：新增对 `MapDataStatistics` 的直接单元测试，重点验证 SortKey 对象复用场景下的计数正确性（重复 wrap 同一个 reusedRow 但 setField 不同值，应得到正确的分桶计数）。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSortKeySerializerBase.java`（新增）

**修改目的**：提供 `SortKeySerializer` 的测试基类，继承 Flink `SerializerTestBase<SortKey>`，覆盖序列化器常规契约（增量/全量、嵌套等）。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSortKeySerializerNestedStruct.java`（新增）

**修改目的**：针对嵌套 struct schema + 多种 transform（bucket、truncate）的 sort order 测试 `SortKeySerializer`。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSortKeySerializerPrimitives.java`（新增）

**修改目的**：针对基本类型 schema + 多种 transform（bucket、truncate、hour、day）的 sort order 测试 `SortKeySerializer`，覆盖 UUID/时间戳等类型的序列化路径。

## 小结

本次 backport 把 Flink 1.16 sink shuffle 的数据统计 key 从 `RowData` 切换为 Iceberg `SortKey`，并新增专用 `SortKeySerializer`，使分布统计能与带 transform 的 SortOrder 语义对齐，是 Iceberg Flink distribution write 走向正确支持复杂排序键的关键基础。
