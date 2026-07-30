# 提交 0773：Spark: Fix issue when partitioning by UUID (#8250)

## 提交信息

- **序号**：0773 / 4088
- **哈希**：bd046f844a1cbad6c98919d8ea63176aeae78d33
- **短哈希**：bd046f844
- **日期**：2024-05-16 18:48:58 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Fix issue when partitioning by UUID (#8250)
- **PR/Issue**：#8250

## 总体目的

这个提交修复了在 Spark 中以 UUID 类型字段作为分区列时，写入数据会失败的 bug。当用户创建一张以 UUID 字段分区（例如 `bucket(uuid, 16)`）的 Iceberg 表，并尝试通过 Spark `INSERT INTO` 写入数据时，`InternalRowWrapper` 无法正确将 Spark 的 `UTF8String` 表示的 UUID 转换为 Iceberg `PartitionKey` 所期望的 `java.util.UUID` 对象，导致分区键计算阶段抛出类型不匹配异常（如 `ClassCastException`），写入失败。

本提交通过为 `InternalRowWrapper` 引入 Iceberg schema 类型信息，使其能在 Spark `StringType` 与 Iceberg `UUIDType` 之间正确转换，从而修复该问题。修复覆盖 Spark 3.3 与 3.4 两个版本模块。

## 如何达成设计目的

### Bug 成因

`InternalRowWrapper` 是 Spark 模块中将 Spark 的 `InternalRow`（Spark 内部行表示）适配为 Iceberg `StructLike` 接口的桥梁类，主要用于分区键（`PartitionKey`）计算和删除过滤（`SparkDeleteFilter`）等场景。它通过为每个字段预先构造一个 `getter`（`BiFunction<InternalRow, Integer, ?>`），在读取行中指定位置的值时调用对应类型的提取方法。

修复前，`InternalRowWrapper` 的构造函数只接收 Spark 的 `StructType`，并通过 `getter(DataType type)` 方法仅依据 Spark 的 `DataType` 来决定如何提取值：

```java
InternalRowWrapper(StructType rowType) {
  this.types = Stream.of(rowType.fields()).map(StructField::dataType).toArray(DataType[]::new);
  this.getters = Stream.of(types).map(InternalRowWrapper::getter).toArray(BiFunction[]::new);
}

private static BiFunction<InternalRow, Integer, ?> getter(DataType type) {
  if (type instanceof StringType) {
    return (row, pos) -> row.getUTF8String(pos).toString();
  }
  // ...
}
```

问题在于：Iceberg 的 `UUIDType` 在映射到 Spark schema 时被转换为 Spark 的 `StringType`（Spark 没有原生 UUID 类型，统一用字符串表示）。因此 `getter` 在遇到 UUID 字段时走了 `StringType` 分支，返回一个 `String`（如 `"550e8400-e29b-41d4-a716-446655440000"`）。

然而，Iceberg 的 `PartitionKey` 在计算分区值时，会根据 Iceberg schema 中字段的 `Type.TypeID` 来处理该值。对于 `UUIDType`，`PartitionKey` 期望收到的是 `java.util.UUID` 对象（用于计算 bucket 哈希等）。当它实际收到一个 `String` 时，就会发生类型不匹配，抛出异常，导致写入流程中断。

### 修复逻辑

修复的核心思路是：让 `InternalRowWrapper` 同时知道 Iceberg 侧的类型信息，从而在 Spark 类型不足以区分时（如 `StringType` 既可能是普通字符串也可能是 UUID），能根据 Iceberg 类型做出正确的转换。

具体做法：

1. **修改 `InternalRowWrapper` 构造函数签名**：新增 `Types.StructType icebergSchema` 参数，与原有 `StructType rowType`（Spark 侧）配对。构造时增加校验：Spark struct 的字段数必须与 Iceberg struct 的字段数相等，否则抛出 `IllegalArgumentException`，提前暴露 schema 不一致问题。

2. **逐字段配对构造 getter**：不再用 `Stream.map(InternalRowWrapper::getter)` 仅凭 Spark 类型批量构造，改为遍历每个字段，同时传入 Iceberg 类型和 Spark 类型：`getter(icebergType, sparkType)`。这样 getter 内部能感知到 Iceberg 的真实类型。

3. **在 `getter` 中增加 UUID 分支**：当 Spark 类型是 `StringType` 时，进一步检查 Iceberg 类型；若 Iceberg 类型是 `Type.TypeID.UUID`，则返回 `UUID.fromString(row.getUTF8String(pos).toString())`，把字符串正确转换为 `java.util.UUID`；否则维持原有行为返回 `String`。

4. **递归处理嵌套 struct**：对于 `StructType`（嵌套结构）字段，递归构造 `InternalRowWrapper` 时也传入对应的 Iceberg 子 struct 类型 `icebergType.asStructType()`，保证嵌套字段中的 UUID 也能正确处理。

5. **更新所有调用方**：`InternalRowWrapper` 构造函数签名变更后，所有创建该对象的调用点都必须补传 Iceberg schema 参数。涉及 `BaseReader.SparkDeleteFilter`、`SparkPartitionedFanoutWriter`、`SparkPartitionedWriter`、`SparkPositionDeltaWrite`（含 `initPartitionRowWrapper` 和 `RowDeltaChildWrite`）、`SparkWrite`（写入器基类），以及 `WritersBenchmark`。每个调用点传入的 Iceberg schema 来源略有不同（如 `schema.asStruct()`、`requiredSchema().asStruct()`、`dataSchema.asStruct()`、`partitionType` 等），但本质都是提供与该 Spark struct 对应的 Iceberg struct 类型。

6. **新增测试**：在 `TestCreateTable` 中新增 `testCreateTablePartitionedByUUID` 测试，创建一张以 `bucket("uuid", 16)` 分区的表，执行 `INSERT INTO` 写入一个随机 UUID 字符串，再 `SELECT` 验证数据正确返回。该测试在修复前会失败（写入即报错），修复后通过。同时在 `RecordWrapperTest` 的 schema 中新增 UUID 字段，覆盖 `InternalRowWrapper`（及更底层的 `RecordWrapper`）对 UUID 类型的转换测试。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/InternalRowWrapper.java`（及 v3.3 同名文件）

**修改目的**：使 `InternalRowWrapper` 能根据 Iceberg 类型正确转换 UUID 字段。

**工作逻辑**：

- 新增 import：`java.util.UUID`、`Preconditions`、`Type`、`Types`。
- 构造函数由 `InternalRowWrapper(StructType rowType)` 改为 `InternalRowWrapper(StructType rowType, Types.StructType icebergSchema)`。新增字段长度一致性校验。
- getter 构造方式由 Stream 批量映射改为 for 循环逐字段配对：`getters[i] = getter(icebergSchema.fields().get(i).type(), types[i]);`
- `getter` 方法签名由 `getter(DataType type)` 改为 `getter(Type icebergType, DataType type)`，在 `StringType` 分支内增加 UUID 判断：若 `icebergType.typeId() == UUID`，返回 `UUID.fromString(...)`。
- 嵌套 `StructType` 分支递归构造 `InternalRowWrapper` 时补传 `icebergType.asStructType()`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java`（及 v3.3 同名文件）

**修改目的**：`SparkDeleteFilter` 中构造 `InternalRowWrapper` 时补传 Iceberg schema。

**工作逻辑**：将 `new InternalRowWrapper(SparkSchemaUtil.convert(requiredSchema()))` 改为 `new InternalRowWrapper(SparkSchemaUtil.convert(requiredSchema()), requiredSchema().asStruct())`，确保删除过滤路径中行包装器也能正确处理 UUID。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPartitionedFanoutWriter.java`（及 v3.3 同名文件）

**修改目的**：fanout 分区写入器构造 `InternalRowWrapper` 时补传 Iceberg schema。

**工作逻辑**：将 `new InternalRowWrapper(sparkSchema)` 改为 `new InternalRowWrapper(sparkSchema, schema.asStruct())`，`schema` 为构造函数传入的 Iceberg 表 schema。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPartitionedWriter.java`（及 v3.3 同名文件）

**修改目的**：普通分区写入器构造 `InternalRowWrapper` 时补传 Iceberg schema。

**工作逻辑**：同上，`new InternalRowWrapper(sparkSchema)` 改为 `new InternalRowWrapper(sparkSchema, schema.asStruct())`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java`（及 v3.3 同名文件）

**修改目的**：位置增量写入（ROW-level DELETE/UPDATE/MERGE）的两处 `InternalRowWrapper` 构造补传 Iceberg schema。

**工作逻辑**：
- `initPartitionRowWrapper` 中，`new InternalRowWrapper(sparkPartitionType)` 改为 `new InternalRowWrapper(sparkPartitionType, partitionType)`。
- `RowDeltaChildWrite` 构造函数中，`new InternalRowWrapper(context.dataSparkType())` 改为 `new InternalRowWrapper(context.dataSparkType(), context.dataSchema().asStruct())`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java`（及 v3.3 同名文件）

**修改目的**：写入器基类构造 `InternalRowWrapper` 时补传 Iceberg schema。

**工作逻辑**：`new InternalRowWrapper(dataSparkType)` 改为 `new InternalRowWrapper(dataSparkType, dataSchema.asStruct())`。

### `spark/v3.4/spark/src/jmh/java/org/apache/iceberg/spark/source/WritersBenchmark.java`（及 v3.3 同名文件）

**修改目的**：JMH 基准测试中构造 `InternalRowWrapper` 时适配新签名。

**工作逻辑**：`new InternalRowWrapper(dataSparkType)` 改为 `new InternalRowWrapper(dataSparkType, table().schema().asStruct())`。

### `data/src/test/java/org/apache/iceberg/RecordWrapperTest.java`

**修改目的**：在底层 `RecordWrapper` 测试 schema 中新增 UUID 字段，覆盖 UUID 类型转换。

**工作逻辑**：在 `TIMESTAMP_WITHOUT_ZONE`（实际为通用测试 schema）的 `NestedField` 列表中追加 `optional(117, "uuid", Types.UUIDType.get())`，field-id 117。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestInternalRowWrapper.java`（及 v3.3 同名文件）

**修改目的**：适配 `InternalRowWrapper` 构造函数新签名。

**工作逻辑**：测试中构造 `InternalRowWrapper` 时补传对应的 Iceberg struct 类型。

### 其他测试文件适配

以下测试文件均因 `InternalRowWrapper` 构造签名变更而做对应适配（补传 Iceberg schema 参数），逻辑一致：`TestSparkAppenderFactory.java`、`TestSparkFileWriterFactory.java`、`TestSparkPartitioningWriters.java`、`TestSparkPositionDeltaWriters.java`、`TestSparkReaderDeletes.java`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestCreateTable.java`（及 v3.3 同名文件）

**修改目的**：新增端到端测试，验证以 UUID 分区的表可正常写入与读取。

**工作逻辑**：新增 `testCreateTablePartitionedByUUID` 测试方法。创建一个仅含 `uuid` 字段（`Types.UUIDType`）的 schema，分区规格为 `bucket("uuid", 16)`，通过 `validationCatalog` 建表后执行 `INSERT INTO %s VALUES('%s')` 写入一个随机 UUID 字符串，再 `SELECT uuid FROM %s` 验证返回单行且值匹配。

**diff 摘要**：43 files changed, 216 insertions(+), 59 deletions(-)（含 v3.3 与 v3.4 两个模块的对称改动）。

## 小结

### 成效

修复了 Spark 3.3 与 3.4 中以 UUID 字段分区时无法写入数据的 bug。修复后，用户可正常创建以 UUID 分区的 Iceberg 表（如 `bucket(uuid, n)`、`truncate(uuid, n)` 等分区变换）并通过 Spark 写入与读取数据。同时，`InternalRowWrapper` 现在具备 Iceberg 类型感知能力，为未来其他需要类型区分的转换（Spark 与 Iceberg 类型非一一对应时）奠定了更稳健的基础。

### 影响范围

改动涉及 Spark 3.3 与 3.4 两个模块的写入与读取路径。`InternalRowWrapper` 构造函数签名变更属于内部 API 变更（package-private 类），不影响最终用户 API。所有使用 `InternalRowWrapper` 的内部调用点均已同步更新。该修复对非 UUID 分区场景无行为影响（getter 在非 UUID 的 StringType 分支行为不变）。

### 回迁注意事项

- 该修复同时改动了 v3.3 与 v3.4 两个 Spark 模块，回迁时需确认 1.4.x 分支是否同时维护这两个 Spark 版本模块。若 1.4.x 只支持其中一个版本，可仅回迁对应模块的改动。
- `InternalRowWrapper` 构造函数签名变更会导致所有调用点必须同步修改，回迁时应整提交 cherry-pick，不可拆分，否则编译失败。
- 注意 `data` 模块的 `RecordWrapperTest` 也被修改（新增 UUID 字段），回迁时需包含，否则底层转换测试覆盖不全。
- 1.4.x 分支若有针对 `InternalRowWrapper` 的自定义扩展或子类，需检查构造函数签名是否兼容。
- 该 bug 对应 PR #8250，issue 编号较旧（#8250），说明该问题存在较长时间才被修复，回迁后建议在 1.4.x 上运行 `testCreateTablePartitionedByUUID` 测试确认修复有效。
