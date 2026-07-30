# 提交 2128：Flink: Add DynamicRecord / DynamicRecordInternal / DynamicRecordInternalSerializer

## 提交信息

- **序号**：2128 / 4088
- **哈希**：268661af252174ab265cb65b18f1e5fe98eb4f59
- **短哈希**：268661af2
- **日期**：2025-05-14 22:09:02 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Add DynamicRecord / DynamicRecordInternal / DynamicRecordInternalSerializer (#12996)
- **PR/Issue**：#12996

## 总体目的

本提交为 Flink Iceberg Sink 的动态表写入功能引入了核心数据结构。DynamicRecord 系列类是 Flink 动态 Sink 功能的基础设施，用于在 Flink 作业运行时支持动态地向不同表、不同 schema 的 Iceberg 表写入数据。传统 Flink Iceberg Sink 在作业启动时绑定固定的表 schema，而动态 Sink 允许在运行时处理不同表的记录，实现多表写入能力。本提交新增了 DynamicRecord（公共 API）、DynamicRecordInternal（内部表示）、DynamicRecordInternalSerializer（Flink 序列化器）、DynamicRecordInternalType（类型信息）和 TableSerializerCache（序列化器缓存）等组件，共新增 1102 行代码。

## 如何达成设计目的

1. 定义 `DynamicRecord` 作为公共 API 类，封装表标识符、分支、schema、分区规范、分发模式、写入并行度等元数据和 RowData
2. 定义 `DynamicRecordInternal` 作为内部表示类，使用 writerKey（而非完整表标识符）和 equalityFieldIds，适合 Flink 内部传输
3. 实现 `DynamicRecordInternalSerializer` 继承 Flink 的 `TypeSerializer`，支持 DynamicRecordInternal 的序列化和反序列化，用于 Flink 算子间的数据传输
4. 定义 `DynamicRecordInternalType` 实现 Flink 的 `AbstractDataType`，提供类型信息用于 Flink 类型系统注册
5. 实现 `TableSerializerCache` 使用 Caffeine 缓存按表 schema 缓存 RowDataSerializer，避免重复创建序列化器
6. 在 build.gradle 中添加 Caffeine 依赖用于缓存
7. 添加完整的测试覆盖序列化器的正确性和缓存行为

## 修改详情

### `flink/v2.0/build.gradle` (修改, +3/-0 lines)

**修改目的**：添加 Caffeine 缓存库依赖。

**工作逻辑**：添加 `implementation libs.caffeine` 依赖，用于 TableSerializerCache 中的序列化器缓存。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecord.java` (新增, +130 lines)

**修改目的**：定义动态记录的公共 API。

**工作逻辑**：DynamicRecord 封装了 TableIdentifier、branch、Schema、RowData、PartitionSpec、DistributionMode、writeParallelism、upsertMode 和可选的 equalityFields。提供完整的 getter/setter 方法。这是用户在动态 Sink 场景下使用的记录类型。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordInternal.java` (新增, +166 lines)

**修改目的**：定义动态记录的 Flink 内部表示。

**工作逻辑**：与 DynamicRecord 类似但面向 Flink 内部传输。使用 `writerKey`（int 类型）替代完整 TableIdentifier 以提高效率，使用 `equalityFieldIds`（List<Integer>）替代 equalityFields（List<String>）。实现了 hashCode/equals 方法，提供无参构造函数用于序列化反序列化时的实例化。标注 `@Internal` 表示为内部 API。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordInternalSerializer.java` (新增, +296 lines)

**修改目的**：实现 DynamicRecordInternal 的 Flink 序列化器。

**工作逻辑**：继承 Flink 的 `TypeSerializer<DynamicRecordInternal>`，实现序列化/反序列化逻辑。核心是使用 `TableSerializerCache` 获取对应 schema 的 RowDataSerializer 来序列化 RowData 部分，其余字段（tableName、branch、schema、spec、writerKey 等）使用基本类型序列化。实现了 createInstance、copy、serialize、deserialize 等方法，支持 Flink 的序列化栈。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordInternalType.java` (新增, +96 lines)

**修改目的**：提供 DynamicRecordInternal 的 Flink 类型信息。

**工作逻辑**：实现 `AbstractDataType`，用于在 Flink 类型系统中注册 DynamicRecordInternal 类型。提供类型描述和对应的 TypeInformation。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableSerializerCache.java` (新增, +135 lines)

**修改目的**：缓存 Flink RowDataSerializer 以避免重复创建。

**工作逻辑**：使用 Caffeine 缓存按 schema ID 缓存 RowDataSerializer 实例。当需要序列化某个 schema 的 RowData 时，先从缓存查找，不存在则创建新的 RowDataSerializer 并缓存。这避免了在动态多表写入场景下重复创建序列化器的开销。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordInternalSerializerTestBase.java` (新增, +96 lines)

**修改目的**：序列化器测试基类。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicRecordInternalSerializerWriteSchema.java` (新增, +28 lines)

**修改目的**：测试使用写入 schema 的序列化器。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicRecordInternalSerializerWriteSchemaId.java` (新增, +28 lines)

**修改目的**：测试使用 schema ID 的序列化器。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableSerializerCache.java` (新增, +124 lines)

**修改目的**：测试 TableSerializerCache 的缓存行为。

## 总结

本提交为 Flink Iceberg 动态 Sink 功能引入了核心数据结构和序列化基础设施。DynamicRecord 系列类支持在 Flink 作业运行时动态处理不同表和 schema 的记录，实现了多表动态写入的基础能力。通过 Caffeine 缓存优化序列化器创建，保证了动态场景下的性能。这是一个功能性的基础设施提交，为后续的动态 Sink 实现奠定了基础。
