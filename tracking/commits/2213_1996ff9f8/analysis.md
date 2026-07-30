# 提交 2213：Flink: Backport add DynamicRecord / DynamicRecordInternal / DynamicRecordInternalSerializer to Flink 1.19 / 1.20 (#13246)

## 提交信息

- **序号**：2213 / 4088
- **哈希**：1996ff9f80e5c1df7cd114efc7864f4f6a38fa02
- **短哈希**：1996ff9f8
- **日期**：2025-06-05 14:46:39 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport add DynamicRecord / DynamicRecordInternal / DynamicRecordInternalSerializer to Flink 1.19 / 1.20 (#13246) backports #12996
- **PR/Issue**：#13246（backport #12996）

## 总体目的

这个提交是 PR #12996 的反向移植（backport），将 DynamicRecord、DynamicRecordInternal、DynamicRecordInternalSerializer 及相关类同时添加到 Flink 1.19 和 1.20 两个版本的模块中。这些类为 Iceberg Flink sink 的"动态路由"功能（dynamic sink，即一条流数据可以路由到不同的 Iceberg 表）提供基础数据结构和序列化机制。DynamicRecord 携带 RowData 及其目标 Iceberg 表的元数据（表名、schema、分区规格、分支等），DynamicRecordInternal 是其可序列化的内部表示，DynamicRecordInternalSerializer 负责在 Flink 算子间传输时的序列化/反序列化。由于 Iceberg 同时维护 Flink 1.19 和 1.20 两个版本的模块，此 backport 确保两个版本同步获得该基础能力。该提交为后续实现完整的动态 sink 功能奠定了序列化基础。

## 如何达成设计目的

- 引入 `DynamicRecord` 作为面向用户的 API 类，携带 RowData 及 Iceberg 表元数据（TableIdentifier、branch、Schema、PartitionSpec、DistributionMode、writeParallelism、upsertMode、equalityFields）。
- 引入 `DynamicRecordInternal` 作为 Flink 内部传输的可序列化表示，将 TableIdentifier 简化为 tableName 字符串，equalityFields 转为 ID 列表，并新增 writerKey 字段。
- 引入 `DynamicRecordInternalSerializer` 继承 Flink 的 `TypeSerializer`，实现序列化/反序列化逻辑，支持两种模式：完整写入 schema/spec 的 JSON，或仅写入 schema id/spec id（优化传输量）。
- 引入 `DynamicRecordInternalType` 作为 Flink 的 `TypeInformation`，用于创建 serializer 实例。
- 引入 `TableSerializerCache` 基于 Caffeine 缓存 RowDataSerializer，避免每条记录重复创建序列化器，支持通过 schema/spec 或其 id 查找，id 模式下从 catalog 加载。
- 在 `build.gradle` 中为 1.19 和 1.20 模块添加 Caffeine 缓存依赖。
- 提供测试基类和测试用例验证序列化器和缓存的行为。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecord.java` (新增, +130 lines)

**修改目的**：定义面向用户的动态记录类，封装 RowData 及目标表元数据。

**工作逻辑**：包含 TableIdentifier、branch、Schema、RowData、PartitionSpec、DistributionMode、writeParallelism、upsertMode、equalityFields 字段，提供构造函数和全部 getter/setter。这是动态 sink 路由的输入数据模型。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordInternal.java` (新增, +166 lines)

**修改目的**：定义 Flink 内部传输用的可序列化记录表示。

**工作逻辑**：
- 将 TableIdentifier 简化为 tableName 字符串，equalityFields 转为 equalityFieldIds（List<Integer>），新增 writerKey 字段。
- 提供无参构造函数供序列化框架实例化。
- 实现 `equals` 和 `hashCode`：equals 中对 RowData 比较，若类型相同直接比较，否则通过 RowDataSerializer 转为 BinaryRow 后比较，处理不同 RowData 实现类的等价性。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordInternalSerializer.java` (新增, +296 lines)

**修改目的**：实现 DynamicRecordInternal 的 Flink 序列化器。

**工作逻辑**：
- 支持两种序列化模式（由 `writeSchemaAndSpec` 标志控制）：
  - **完整模式**：写入 schema 和 spec 的 JSON 字符串，适合 schema 可能未在目标端缓存的情况。
  - **ID 模式**：仅写入 schemaId 和 specId，减少传输量，目标端通过 TableSerializerCache 从 catalog 解析。
- `serialize`：依次写入 tableName、branch、schema/spec（JSON 或 ID）、writerKey、RowData（通过缓存的 RowDataSerializer）、upsertMode、equalityFieldIds 列表。
- `deserialize`：逆序读取并重建 DynamicRecordInternal，通过 serializerCache 获取 RowDataSerializer 反序列化 RowData。
- 实现 `duplicate()`、`isImmutableType()`、`getLength()` 等 TypeSerializer 必需方法，以及 `snapshotConfiguration()` 返回快照。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordInternalType.java` (新增, +103 lines)

**修改目的**：提供 Flink TypeInformation 用于类型推断和 serializer 创建。

**工作逻辑**：继承 `TypeInformation<DynamicRecordInternal>`，持有 CatalogLoader、writeSchemaAndSpec、cacheSize 参数，在 `createSerializer` 时创建 `DynamicRecordInternalSerializer` 并传入新的 `TableSerializerCache`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableSerializerCache.java` (新增, +135 lines)

**修改目的**：缓存 RowDataSerializer 以避免重复创建，提升性能。

**工作逻辑**：
- 使用 Caffeine 缓存按 tableName 存储 `SerializerInfo`，SerializerInfo 内部维护 schemas/specs map 和 serializers map。
- `serializer(tableName, schema, spec)`：直接模式，用提供的 schema/spec 创建或获取 RowDataSerializer。
- `serializerWithSchemaAndSpec(tableName, schemaId, specId)`：ID 模式，若缓存未命中则通过 CatalogLoader 从 catalog 加载表的 schemas 和 specs。
- `SerializerInfo.update()`：从 catalog 重新加载表的 schemas 和 specs，支持 schema 演进场景。
- 懒加载缓存，transient 修饰避免序列化。

### `flink/v1.19/build.gradle` 和 `flink/v1.20/build.gradle` (修改, 各 +3 lines)

**修改目的**：添加 Caffeine 缓存库依赖。

**工作逻辑**：在两个版本的 build.gradle 中添加 `testImplementation` 或 `implementation` 依赖 `com.github.ben-manees.caffeine:caffeine`。

### 测试文件（新增, 共约 +276 lines per version）

**修改目的**：验证序列化器正确性和缓存行为。

**工作逻辑**：
- `DynamicRecordInternalSerializerTestBase.java`：测试基类，验证两种模式（writeSchemaAndSpec / writeSchemaId）下的序列化/反序列化往返正确性。
- `TestDynamicRecordInternalSerializerWriteSchema.java` 和 `TestDynamicRecordInternalSerializerWriteSchemaId.java`：分别测试两种序列化模式。
- `TestTableSerializerCache.java`：测试缓存的创建、命中、更新（catalog reload）行为。

### Flink 1.19 模块的相同文件

**修改目的**：将相同代码同步到 Flink 1.19 模块。

**工作逻辑**：与 1.20 模块完全相同的文件集合，确保两个 Flink 版本功能一致。

## 总结

该提交为 Iceberg Flink sink 的动态路由功能引入了核心数据结构和序列化基础设施。DynamicRecord/DynamicRecordInternal 提供了携带表元数据的记录模型，DynamicRecordInternalSerializer 支持完整和 ID 两种序列化模式以平衡兼容性和性能，TableSerializerCache 通过 Caffeine 缓存优化序列化器创建开销。作为 backport 提交，确保 Flink 1.19 和 1.20 两个版本同步获得该能力，为后续动态 sink 功能的完整实现奠定基础。
