# 提交 0247：Flink: backport PR #9212 to 1.18 for switching to SortKey for data statistics

## 提交信息

- **序号**：0247 / 4088
- **哈希**：2c31acc8aea7b807c91623c21c7fa2d979462021
- **短哈希**：2c31acc8a
- **日期**：2023-12-09 11:12:33 -0800
- **作者**：Steven Wu
- **提交说明**：Flink: backport PR #9212 to 1.18 for switching to SortKey for data statistics
- **PR/Issue**：#9212

## 总体目的

这个提交与紧邻的前一个提交（0246，回移植到 1.16）是同一 PR #9212 的姊妹 backport，目标完全相同：把 Flink sink shuffle 数据分布统计模块的 key 类型从 Flink 的 `RowData` 切换为 Iceberg 的 `SortKey`，使统计 key 能正确反映带 `Transform`（bucket/truncate/year 等）的 SortOrder 语义。本次是回移植到 Flink 1.18 模块（`flink/v1.18/flink/`），只影响 1.18 这一个 Flink 版本。

切换前，`DataStatistics` 用原始 `RowData` 作为统计 key 直接计数，无法表达 sort order 中变换函数产生的结果键，导致分布统计与实际写排序键不对齐，cluster/distribution 效果打折。切换到 `SortKey` 后，统计 key 与写文件 sort key 一致，为 range shuffle、数据 clustering 提供正确前提。同时本次 backport 同样新增独立的 `SortKeySerializer` 与配套快照，保证 SortKey 在 operator event 与 state 中能安全序列化与 checkpoint 恢复。

与 1.16 backport 的差异：1.18 版本未触及 `DataStatisticsCoordinator.java`（1.16 那次包含两处小幅 lint 修改，1.18 此处无需调整）。其余文件结构与改动逻辑与 0246 完全对应，只是落在 `flink/v1.18/` 路径下。

## 如何达成设计目的

整体设计与 0246 一致：以 `SortKey` 替换 `RowData` 作为统计与序列化的核心类型；`DataStatisticsOperator` 内部用 `RowDataWrapper` + `SortKey.wrap()` 直接从 RowData 生成 sort key，去掉了外部 `KeySelector`；新增 `SortKeySerializer` 按 sort order 各字段 transform 后的结果类型逐字段二进制读写，并实现 `TypeSerializerSnapshot` 用于 checkpoint 兼容性检查；`MapDataStatisticsSerializer` 替换为以 `SortKey` 为 map key 的版本；所有相关测试同步迁移到 `SortKey`，并新增针对 `MapDataStatistics` 与 `SortKeySerializer` 的单元测试。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatistics.java`

**修改目的**：把统计接口的 key 类型从 `RowData` 切换为 `SortKey`，并修正泛型自引用声明。

**工作逻辑**：`add` 方法签名由 `void add(RowData key)` 改为 `void add(SortKey sortKey)`；import 由 `org.apache.flink.table.data.RowData` 换为 `org.apache.iceberg.SortKey`。泛型 `DataStatistics<D extends DataStatistics, S>` 修正为 `DataStatistics<D extends DataStatistics<D, S>, S>`，使子类自引用类型正确。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOperator.java`

**修改目的**：把 operator 内部生成 sort key 的方式从外部 `KeySelector` 改为内置 `RowDataWrapper` + `SortKey`。

**工作逻辑**：构造参数由 `KeySelector<RowData, RowData> keySelector` 替换为 `Schema schema` 与 `SortOrder sortOrder`；operator 持有 `RowDataWrapper rowDataWrapper` 与 `SortKey sortKey` 字段。`processElement` 中改为 `StructLike struct = rowDataWrapper.wrap(record); sortKey.wrap(struct); localStatistics.add(sortKey);`——每条数据复用同一个 SortKey 对象，由 `MapDataStatistics` 负责 copy。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsUtil.java`

**修改目的**：清理与新泛型签名收紧后多余的 `@SuppressWarnings("unchecked")`。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapDataStatistics.java`

**修改目的**：把内部统计 map 的 key 类型从 `RowData` 换为 `SortKey`，并处理 SortKey 对象复用导致的拷贝问题。

**工作逻辑**：`Map<RowData, Long>` → `Map<SortKey, Long>`。`add(SortKey sortKey)` 关键变化：因 operator 复用同一个 SortKey 对象，新增键时必须 `sortKey.copy()` 后再 put；已有键则只做 `merge` 计数，避免"所有 key 都指向同一个对象"的 bug。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapDataStatisticsSerializer.java`

**修改目的**：把序列化器的 key 类型从 `RowData` 换为 `SortKey`，并重命名工厂方法以反映新语义。

**工作逻辑**：所有 `Map<RowData, Long>` 改为 `Map<SortKey, Long>`，`MapSerializer<RowData, Long>` 改为 `MapSerializer<SortKey, Long>`。工厂方法 `fromKeySerializer(TypeSerializer<RowData>)` 重命名为 `fromSortKeySerializer(TypeSerializer<SortKey>)`。`copy` 用 `TypeSerializer<SortKey>` 复制每个 key，其余 serialize/deserialize/snapshotConfiguration/内部 `MapDataStatisticsSerializerSnapshot` 泛型参数同步更新。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java`（新增）

**修改目的**：为 `SortKey` 提供专用的 Flink `TypeSerializer`，支持 sort key 在 operator event 与 state 中的二进制序列化、checkpoint 恢复与 schema 兼容性检查。

**工作逻辑**：构造时根据 `Schema` 与 `SortOrder` 预计算 `transformedFields`（每个 sort 字段经 transform 后的结果 `Types.NestedField`）。`serialize`/`deserialize` 按 `typeId()` 分支处理 BOOLEAN/INTEGER/DATE/LONG/TIME/TIMESTAMP/FLOAT/DOUBLE/STRING/UUID/FIXED/BINARY/DECIMAL；对 STRUCT/MAP/LIST 抛 `UnsupportedOperationException`。UUID 用高低位两个 long；FIXED/BINARY 先写长度再写 bytes；DECIMAL 写 unscaled BigInteger 字节数组+scale。内部静态类 `SortKeySerializerSnapshot` 实现 `TypeSerializerSnapshot<SortKey>`：`writeSnapshot` 写 schema 与 sortOrder 的 JSON，`readV1` 用 `SchemaParser`/`SortOrderParser` 反序列化并 `bind(schema)`，`resolveSchemaCompatibility` 用 `CheckCompatibility.writeCompatibilityErrors` 判断兼容性。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestAggregatedStatistics.java`

**修改目的**：把测试从 `RowData`/`RowDataSerializer` 迁移到 `SortKey`/`SortKeySerializer`，验证合并逻辑仍正确。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestAggregatedStatisticsTracker.java`

**修改目的**：迁移 tracker 测试到 SortKey，覆盖 newer/older/completed 三类 event 场景下聚合统计的正确性。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsCoordinator.java`

**修改目的**：迁移 coordinator 测试到 SortKey，并改用可复用的 `SortKey key` 配合 `key.set(0, ...)` 构造多组测试数据。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsCoordinatorProvider.java`

**修改目的**：迁移 provider 测试到 SortKey，并用 try-with-resources 包裹 coordinator 保证资源释放。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsOperator.java`

**修改目的**：迁移 operator 测试到 SortKey，schema 扩展为两列（id+number），用 `Schema`+`SortOrder` 构造 operator。`testRestoreState` 中专门处理反序列化后 RowData 形态变化（BinaryRowData vs GenericRowData）的等值比较问题。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestMapDataStatistics.java`（新增）

**修改目的**：新增对 `MapDataStatistics` 的直接单元测试，重点验证 SortKey 对象复用场景下的计数正确性。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSortKeySerializerBase.java`（新增）

**修改目的**：提供 `SortKeySerializer` 的测试基类，继承 Flink `SerializerTestBase<SortKey>`，覆盖序列化器常规契约。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSortKeySerializerNestedStruct.java`（新增）

**修改目的**：针对嵌套 struct schema + 多种 transform（bucket、truncate）的 sort order 测试 `SortKeySerializer`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSortKeySerializerPrimitives.java`（新增）

**修改目的**：针对基本类型 schema + 多种 transform（bucket、truncate、hour、day）的 sort order 测试 `SortKeySerializer`，覆盖 UUID/时间戳等类型的序列化路径。

## 小结

本次 backport 把 PR #9212 的 SortKey 切换应用到 Flink 1.18 模块，使该版本的 sink shuffle 数据分布统计能与带 transform 的 SortOrder 语义对齐，补齐了 Iceberg 在多个 Flink 版本上的 distribution write 一致性。
