# 提交 1217：Core: Add internal Avro reader (#11108)

## 提交信息

- **序号**：1217 / 4088
- **哈希**：f0e4fd2f557529eaa87c78d8d6585105e40b1f10
- **短哈希**：f0e4fd2f5
- **日期**：2024-10-07（Mon Oct 7 16:12:39 2024 -0700）
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：Core: Add internal Avro reader (#11108)
- **PR/Issue**：#11108

## 总体目的

Iceberg 在读取 manifest 文件（Avro 格式）时，原本通过 `GenericAvroReader` + `rename(...)` 机制把 Avro 的 `GenericData.Record` 反序列化到 Iceberg 内部的 `StructLike` 实现类（`GenericManifestEntry`、`PartitionData`、`GenericDataFile`、`GenericDeleteFile`）。该机制依赖 Avro 的反射式 `GenericData` 加上字段名重命名，路径较长，且 `GenericAvroReader` 同时承担了"读取到 GenericRecord"和"读取到内部 StructLike"两种职责，逻辑耦合。

本提交引入新的 `InternalReader`，专门用于把 Avro 数据直接反序列化为 Iceberg 内部对象模型（即 `Type.TypeID#javaClass()` 定义的类型，如 `Integer`、`Long`、`CharSequence`、`UUID`、`ByteBuffer`、`BigDecimal` 等，以及 `StructLike` 实现类）。`ManifestReader` 改用 `InternalReader`，通过 `setRootType`/`setCustomType` 显式声明根记录类型与特定字段的实现类，避免 `rename` 机制。

同时把 `GenericAvroReader` 中通用的 read plan 构造逻辑（`buildReadPlan`、`idToPos`、`skipStruct`）下沉到 `ValueReaders`，供 `GenericAvroReader` 与 `InternalReader` 共享，消除重复代码。

这是为后续 spec v3（默认值、`unknown` 类型、类型提升等）以及性能优化铺路的基础设施重构。

## 如何达成设计目的

1. **新增 `InternalReader<T>`**：实现 `DatumReader<T>` 与 `SupportsRowPosition`。其 `ResolvingReadBuilder` 内部类继承 `AvroWithPartnerVisitor`，partner 类型为 `Pair<Integer, Type>`（携带字段 ID 与 Iceberg 类型），用于在遍历 Avro schema 时同时跟踪 Iceberg 类型与字段 ID，从而支持 `setCustomType` 按字段 ID 替换实现类。
2. **新增 `InternalReaders`**：提供两个 `struct` 工厂方法，一个返回基于 `GenericRecord` 的 reader（无自定义类时使用），一个返回基于指定 `StructLike` 实现类的 reader（通过 `DynConstructors` 反射构造实例）。后者支持 `ManifestReader` 直接读到 `GenericManifestEntry`/`PartitionData`/`GenericDataFile`。
3. **`ValueReaders` 扩展**：把 `GenericAvroReader` 原有的 read plan 构造逻辑（`buildReadPlan`、`idToPos`）与 `skipStruct` reader 下沉到 `ValueReaders`，作为静态方法/内部类，供两个 reader 复用。`buildReadPlan` 包含了默认值回填逻辑（依赖提交 1213 的 `field.initialDefault()`）。
4. **`ManifestReader` 改造**：`FileType` 枚举从持有 `String fileClass` 改为持有 `Class<? extends StructLike> fileClass`；读取时不再调用多次 `rename(...)` 与 `classLoader(...)`，而是通过 `createResolvingReader(this::newReader)` 注入一个返回 `InternalReader` 的工厂方法，`newReader` 中调用 `InternalReader.create(schema).setRootType(GenericManifestEntry.class).setCustomType(DATA_FILE_ID, content.fileClass()).setCustomType(PARTITION_ID, PartitionData.class)`。
5. **`GenericAvroReader` 精简**：移除被下沉到 `ValueReaders` 的 `buildReadPlan`、`idToPos` 等代码，`record(...)` 方法改为调用 `ValueReaders.buildReadPlan(...)` 与 `ValueReaders.skipStruct(...)`。同时清理不再需要的 import。
6. **测试适配**：`TestManifestReader` 在 `AssertJ` 的 `usingRecursiveComparison` 忽略字段列表中新增 `partitionData.partitionType.fieldsById`，因为 `InternalReader` 直接构造 `PartitionData` 时其内部 `partitionType.fieldsById` 可能与原 `GenericData` 路径下的实例不同（不同实例的 lazy 初始化 map），需忽略以保证测试通过。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestReader.java`

**修改目的**：改用 `InternalReader` 读取 manifest。

**工作逻辑**：

- `FileType` 枚举字段从 `String fileClass` 改为 `Class<? extends StructLike> fileClass`，构造时直接传入 `GenericDataFile.class` / `GenericDeleteFile.class`（而非 `.getName()`），`fileClass()` 返回类型也改为 `Class<? extends StructLike>`。
- `entries(...)` 方法中构建 `AvroIterable` 时，删除 5 行 `.rename(...)` 与 `.classLoader(...)` 调用，替换为 `.createResolvingReader(this::newReader)`。
- 新增私有方法 `newReader(Schema schema)`：
  ```java
  private DatumReader<?> newReader(Schema schema) {
    return InternalReader.create(schema)
        .setRootType(GenericManifestEntry.class)
        .setCustomType(ManifestEntry.DATA_FILE_ID, content.fileClass())
        .setCustomType(DataFile.PARTITION_ID, PartitionData.class);
  }
  ```
  这告诉 `InternalReader`：根记录用 `GenericManifestEntry`，`DATA_FILE_ID` 字段用 `GenericDataFile`/`GenericDeleteFile`，`PARTITION_ID` 字段用 `PartitionData`。

### `core/src/main/java/org/apache/iceberg/avro/GenericAvroReader.java`

**修改目的**：精简，下沉通用逻辑到 `ValueReaders`。

**工作逻辑**：

- 移除对 `MetadataColumns`、`Lists`、`Maps` 的 import（不再直接使用）。
- `idToConstant` 字段类型从 `Map<Integer, ?>` 改为 `Map<Integer, Object>`（与 `ValueReaders.buildReadPlan` 签名一致）。
- `record(...)` 方法大幅精简：原内联的 read plan 构造逻辑（约 35 行）替换为：
  ```java
  if (partner == null) {
    return ValueReaders.skipStruct(fieldResults);
  }
  Types.StructType expected = partner.asStructType();
  List<Pair<Integer, ValueReader<?>>> readPlan =
      ValueReaders.buildReadPlan(expected, record, fieldResults, idToConstant);
  return recordReader(readPlan, avroSchemas.get(partner), record.getFullName());
  ```
- 删除私有方法 `idToPos(Types.StructType)`（已下沉到 `ValueReaders`）。

### `core/src/main/java/org/apache/iceberg/avro/InternalReader.java`（新文件，252 行）

**修改目的**：新增专门读取 Iceberg 内部对象模型的 Avro reader。

**工作逻辑**：

- 实现 `DatumReader<T>` 与 `SupportsRowPosition`。
- `expectedType` 为 `Types.StructType`；`typeMap` 为 `Map<Integer, Class<? extends StructLike>>`，记录字段 ID 到自定义实现类的映射（`ROOT_ID = -1` 表示根记录）。
- `create(org.apache.iceberg.Schema)` 静态工厂；`setRootType(Class)` 与 `setCustomType(int fieldId, Class)` 链式配置。
- `setSchema(Schema)` 触发 `initReader()`，通过 `AvroWithPartnerVisitor.visit(Pair.of(ROOT_ID, expectedType), fileSchema, new ResolvingReadBuilder(), AccessByID.instance())` 构造 `ValueReader<T>`。
- `ResolvingReadBuilder` 内部类：
  - `record(...)`：partner 为 `Pair<Integer, Type>`。若 partner 为 null 调用 `ValueReaders.skipStruct`；否则用 `ValueReaders.buildReadPlan` 构造 read plan，再调用 `structReader(readPlan, fieldId, struct)`，后者根据 `typeMap` 是否有该字段 ID 的自定义类，选择 `InternalReaders.struct(struct, structClass, readPlan)` 或 `InternalReaders.struct(struct, readPlan)`。
  - `union`、`arrayMap`、`array`、`map`：委托 `ValueReaders` 对应工厂。
  - `primitive(...)`：处理 Avro logical type（date、time-micros、timestamp-millis 调整为微秒、timestamp-micros、decimal、uuid）与原始类型（null、boolean、int、long、float、double、string、fixed、bytes、enum）。其中 `int` 在 partner 为 `LONG` 时调用 `ValueReaders.intsAsLongs()`（类型提升），`float` 在 partner 为 `DOUBLE` 时调用 `ValueReaders.floatsAsDoubles()`。
- `AccessByID` 内部类实现 `PartnerAccessors<Pair<Integer, Type>>`，提供 `fieldPartner`、`mapKeyPartner`、`mapValuePartner`、`listElementPartner`，返回 `Pair.of(fieldId, type)`，从而在遍历时携带字段 ID 信息。
- `setRowPositionSupplier` 委托给内部 reader（若实现 `SupportsRowPosition`）。
- `read(T reuse, Decoder)` 委托给 `reader.read(decoder, reuse)`。

### `core/src/main/java/org/apache/iceberg/avro/InternalReaders.java`（新文件，110 行）

**修改目的**：提供基于 `GenericRecord` 与基于自定义 `StructLike` 类的 struct reader 工厂。

**工作逻辑**：

- `struct(Types.StructType, List<Pair<Integer, ValueReader<?>>> readPlan)`：返回 `RecordReader`（内部类，继承 `ValueReaders.PlannedStructReader<GenericRecord>`），`reuseOrCreate` 复用或 `GenericRecord.create(structType)`，`get`/`set` 委托 `GenericRecord`。
- `struct(Types.StructType, Class<S>, readPlan)`：返回 `PlannedStructLikeReader<S>`（内部类，继承 `ValueReaders.PlannedStructReader<S>`），通过 `DynConstructors.builder(StructLike.class).hiddenImpl(structClass, Types.StructType.class).hiddenImpl(structClass).build()` 反射构造实例，`reuseOrCreate` 复用或反射新建，`get`/`set` 委托 `StructLike` 接口。

### `core/src/main/java/org/apache/iceberg/avro/ValueReaders.java`

**修改目的**：承接从 `GenericAvroReader` 下沉的通用逻辑。

**工作逻辑**：

- 新增 `skipStruct(List<ValueReader<?>>)` 工厂，返回 `SkipStructReader` 内部类实例。
- 新增 `buildReadPlan(Types.StructType expected, Schema record, List<ValueReader<?>> fieldReaders, Map<Integer, Object> idToConstant)` 静态方法：构造 read plan，逻辑与原 `GenericAvroReader` 内联版本一致——遍历文件字段填充 `(pos, reader)`，对期望但缺失的字段按"常量 → initialDefault → IS_DELETED → ROW_POSITION → 可空填 null → 必填抛异常"优先级处理。
- 新增私有 `idToPos(Types.StructType)` 静态方法（从 `GenericAvroReader` 迁移）。
- 新增 `SkipStructReader` 内部类：实现 `ValueReader<Void>`，`read` 调用 `skip` 后返回 `null`，`skip` 依次调用每个子 reader 的 `skip`。

### `core/src/test/java/org/apache/iceberg/TestManifestReader.java`

**修改目的**：适配 `InternalReader` 路径下 `PartitionData` 内部状态差异。

**工作逻辑**：在 `usingRecursiveComparison().ignoringFields(...)` 列表末尾新增 `"partitionData.partitionType.fieldsById"`。原因：`InternalReader` 直接构造 `PartitionData`，其 `partitionType.fieldsById` 是一个 lazily 初始化的 map，不同实例间该 map 的初始化状态可能不同，递归比较时会误报差异，故忽略。

## 小结

- **成效**：引入了专用的 `InternalReader`，把 Avro 反序列化直接对接到 Iceberg 内部对象模型（`StructLike`），消除 `ManifestReader` 对 `rename` 反射机制的依赖；同时把 read plan 构造逻辑下沉到 `ValueReaders`，消除 `GenericAvroReader` 与新 reader 的代码重复。为后续 spec v3（默认值、`unknown` 类型、类型提升）与性能优化铺路。
- **影响范围**：Core 模块。新增 2 个文件（`InternalReader` 252 行、`InternalReaders` 110 行），修改 `ManifestReader`、`GenericAvroReader`、`ValueReaders` 与 1 个测试。无公共 API 签名变更（`InternalReader`/`InternalReaders` 是 core 内部包可见或公开但供内部使用的类）。
- **回迁到 1.4.x 的注意事项**：这是一个较大的内部重构，**回迁需谨慎**。
  - 它依赖提交 1213 的 `field.initialDefault()`（`buildReadPlan` 中引用了 `field.initialDefault()`），若 1.4.x 未回迁 1213，则 `ValueReaders.buildReadPlan` 无法编译，需同步处理（要么同时回迁 1213，要么在回迁版本中删除 `initialDefault` 分支）。
  - `ManifestReader` 的读取路径从 `GenericData` 反射改为 `InternalReader` 直接构造，行为差异（如 `PartitionData.partitionType.fieldsById` 的初始化）需在 1.4.x 测试中验证。
  - 1.4.x 作为维护分支，若无明确性能或正确性需求，**一般不建议回迁**此类大型内部重构；若 1.4.x 需要支持 spec v3 表读取，则需连同 1213、1219 等提交一起评估回迁。
