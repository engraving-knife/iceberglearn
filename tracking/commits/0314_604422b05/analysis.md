# 提交 0314：Core: Refactor internal Avro reader to resolve schemas directly (#9366)

## 提交信息

- **序号**：0314
- **哈希**：604422b056efee145f69944c39b4d99787dfd9f1
- **短哈希**：604422b05
- **日期**：2024-01-01 11:03:41 -0800
- **作者**：Ryan Blue
- **提交说明**：Core: Refactor internal Avro reader to resolve schemas directly (#9366)
- **PR/Issue**：#9366

## 总体目的

本提交对 Iceberg core 模块的内部 Avro 读取器（internal Avro reader）进行了一次结构性重构，改变其解析文件 schema 的方式——由原来依赖 Avro 的 `Schema.applyAliases(fileSchema, readSchema)` 进行 schema 解析、再通过 `DecoderResolver.resolveAndRead` 在解码时按 read schema 投影，改为基于"partner 类型"（Iceberg 的 `Type`/`Types.StructType`）直接遍历文件 schema 并构建一个"读取计划"（read plan），由计划中的 `ValueReader` 直接读取或跳过文件字段。重构后读取器不再需要 Avro 的 schema 解析机制，而是利用新建的 `AvroWithPartnerVisitor` 在遍历 Avro schema 时同时访问对应的 Iceberg partner 类型，按字段 ID 匹配决定是读取、跳过、还是用常量替换字段。

重构的核心动机是消除旧实现对 `DecoderResolver`（位于 `iceberg-data` 模块的 `org.apache.iceberg.data.avro.DecoderResolver`）的依赖——`DecoderResolver.resolveAndRead` 是一个较为迂回的解码路径，它会按 read schema 顺序解码并处理字段映射；新实现将这一职责下放到 `ValueReaders.PlannedStructReader`，由预构建的 `readPlan`（`List<Pair<Integer, ValueReader<?>>>`，其中 `Integer` 为目标结构中的位置、`ValueReader` 为字段读取器或 null 表示跳过）驱动单趟解码。这种"先规划后执行"的模式让读取逻辑更直观、更易扩展（如本提交顺便支持了 int→long、float→double 的类型提升读取，以及 metadata 列 `_pos`/`_deleted` 的常量注入）。

重构还重新组织了 name mapping 的应用时机：原 `Avro.ReadBuilder` 在 `build()` 时把 `nameMapping` 传给 `ProjectionDatumReader`，由后者在 `setSchema` 时调用 `AvroSchemaUtil.applyNameMapping`；新实现引入独立的 `NameMappingDatumReader` 包装器，在 `setSchema` 时根据文件 schema 是否已含 ID 决定是否应用 name mapping，使 name mapping 逻辑与投影读取逻辑解耦。同时 `Avro.ReadBuilder` 现在会在未显式提供 nameMapping 时自动用 `MappingUtil.create(schema)` 创建一个（基于 read schema 的 name mapping），保证读取器总是有 name mapping 可用。

## 如何达成设计目的

重构以新建 `AvroWithPartnerVisitor<P, R>` 为骨架——它是一个带"partner 类型"参数的 Avro schema 访问者，提供 `PartnerAccessors<P>` 接口（`fieldPartner`/`mapKeyPartner`/`mapValuePartner`/`listElementPartner`）让调用方告知如何从 partner 导出子元素的 partner，并提供静态 `visit(partner, schema, visitor, accessors)` 方法按 RECORD/UNION/ARRAY/MAP/primitive 分派。`GenericAvroReader` 内部的 `ResolvingReadBuilder` 实现该访问者，以 Iceberg `Type`（来自 read schema 的 `asStruct()`）为 partner，遍历文件 Avro schema 时按字段 ID 匹配 partner 中的预期字段，构建 `readPlan`。`Avro.ReadBuilder.build()` 的组装逻辑改为：根据三种 reader 创建方式（`createReaderBiFunc`/`createReaderFunc`/`createResolvingReaderFunc`/默认）选择性地包成 `ProjectionDatumReader`（前两种）或直接使用（后两种），再用 `NameMappingDatumReader` 包装，注入 classLoader 与 renames（通过新的 `SupportsCustomRecords` 接口）。`ValueReaders` 新增 `PlannedStructReader` 抽象类及其两个具体实现 `PlannedRecordReader`（生成 `GenericData.Record`）与 `PlannedIndexedReader`（生成指定 `IndexedRecord` 子类），并统一为所有 primitive reader（`NullReader`/`BooleanReader`/`IntegerReader`/...）和容器 reader（`ArrayReader`/`MapReader`/`UnionReader` 等）补充 `skip(Decoder)` 方法以支持跳过未投影字段。

## 修改详情

### `core/src/main/java/org/apache/iceberg/avro/AvroWithPartnerVisitor.java`（新增）

**修改目的**：提供带 partner 类型的 Avro schema 访问者框架，是本次重构的骨架。

**工作逻辑**：新文件 211 行。定义泛型类 `AvroWithPartnerVisitor<P, R>`（P 为 partner 类型，R 为访问结果类型）：
- 内部接口 `PartnerAccessors<P>`：声明 `fieldPartner(P partnerStruct, Integer fieldId, String name)`、`mapKeyPartner(P partnerMap)`、`mapValuePartner(P partnerMap)`、`listElementPartner(P partnerList)` 四个方法，让调用方告知如何从 partner 取出子元素的 partner。
- 内部类 `FieldIDAccessors implements PartnerAccessors<Type>`：以 Iceberg `Type` 为 partner 的具体实现，`fieldPartner` 通过 `partner.asStructType().field(fieldId).type()` 取出（按字段 ID 而非名称），`mapKeyPartner`/`mapValuePartner`/`listElementPartner` 分别调用 `asMapType().keyType()`/`valueType()`/`asListType().elementType()`。这是 `GenericAvroReader` 实际使用的访问器。
- `Deque<String> recordLevels` 字段 + `visitRecord` 中的 push/pop，用于检测递归类型并抛出 `Cannot process recursive Avro record` 异常。
- 公开的 `record`/`union`/`array`/`arrayMap`/`map`/`primitive` 方法默认返回 null（子类覆盖）。
- 静态 `visit(P partner, Schema schema, visitor, accessors)` 按 `schema.getType()` 分派：RECORD → `visitRecord`（遍历字段，按 `AvroSchemaUtil.fieldId(field)` 取 ID，通过 `accessors.fieldPartner(partner, fieldId, field.name())` 取子 partner 递归 visit）；UNION → `visitUnion`（校验为 option schema，遍历分支）；ARRAY → `visitArray`（区分 LogicalMap 与普通 array，LogicalMap 走 `arrayMap` 拆分 key/value）；MAP → 直接 `visitor.map(partner, schema, visit(mapValuePartner, getValueType))`；default → `visitor.primitive`。

### `core/src/main/java/org/apache/iceberg/avro/GenericAvroReader.java`

**修改目的**：用 `AvroWithPartnerVisitor` 重写读取器构建逻辑，从"按 read schema 构建 reader"改为"按 partner 类型解析文件 schema 构建 readPlan"。

**工作逻辑**：
- 类签名新增 `implements SupportsCustomRecords`，提供 `setClassLoader`/`setRenames` 的接口实现。
- 字段从 `final Schema readSchema` 改为 `final Types.StructType expectedType`（read schema 的 struct 视图）。
- 新增工厂方法 `public static <D> GenericAvroReader<D> create(org.apache.iceberg.Schema schema)`，接受 Iceberg schema（而非 Avro schema）。原 `create(Schema schema)` 保留为包级，内部用 `AvroSchemaUtil.convert(readSchema).asStructType()` 转 Iceberg 类型。
- `setSchema(Schema schema)` 不再调用 `Schema.applyAliases(schema, readSchema)`，直接 `this.fileSchema = schema; initReader()`——schema 解析职责下沉到 reader 内部。
- `initReader()` 从 `AvroSchemaVisitor.visit(readSchema, new ReadBuilder(loader))` 改为 `AvroWithPartnerVisitor.visit(expectedType, fileSchema, new ResolvingReadBuilder(expectedType, fileSchema.getFullName()), FieldIDAccessors.get())`——partner 为 `expectedType`，schema 为文件 schema。
- `read(T reuse, Decoder decoder)` 从 `DecoderResolver.resolveAndRead(decoder, readSchema, fileSchema, reader, reuse)` 改为 `reader.read(decoder, reuse)`——直接调用 reader，因为 reader 已内置 readPlan。
- 内部类 `ReadBuilder extends AvroSchemaVisitor<ValueReader<?>>` 改为 `ResolvingReadBuilder extends AvroWithPartnerVisitor<Type, ValueReader<?>>`：
  - 持有 `Map<Type, Schema> avroSchemas`（通过 `AvroSchemaUtil.convertTypes(expectedType, rootName)` 预生成 Iceberg 类型 → Avro schema 的映射，供 recordReader 复用 Avro schema）。
  - `record(Type partner, Schema record, List<ValueReader<?>> fieldResults)`：构建 `readPlan`——遍历文件字段，按 `AvroSchemaUtil.fieldId(field)` 取 ID，从 `idToPos(expected)` 中查出预期位置 `projectionPos` 并移除；若该 ID 在 `idToConstant` 中有常量则用 `ValueReaders.replaceWithConstant(fieldReader, constant)`（读但仍替换值）；处理完文件字段后，剩余 `idToPos` 中未匹配的预期字段按 metadata 列规则补全：`IS_DELETED` → `constant(false)`、`ROW_POSITION` → `positions()`、optional → `constant(null)`、required 且缺失 → 抛 `Missing required field` 异常。最终 `recordReader(readPlan, avroSchemas.get(partner), record.getFullName())`。
  - `recordReader(readPlan, avroSchema, recordName)`：按 `renames` map 重命名类名，尝试 `DynClasses.builder().loader(loader).impl(className).buildChecked()` 加载 `IndexedRecord` 子类，加载成功则 `ValueReaders.record(avroSchema, recordClass, readPlan)`（走 `PlannedIndexedReader`），否则 `ValueReaders.record(avroSchema, readPlan)`（走 `PlannedRecordReader`）。
  - `arrayMap`/`array`/`map`/`union` 方法签名新增 `Type partner` 参数，逻辑基本平移。
  - `primitive(Type partner, Schema primitive)` 新增类型提升逻辑：当 partner 为 `LONG` 但文件为 `INT` 时返回 `ValueReaders.intsAsLongs()`（读 int 转 long）；当 partner 为 `DOUBLE` 但文件为 `FLOAT` 时返回 `ValueReaders.floatsAsDoubles()`。
  - 私有 `idToPos(Types.StructType struct)` 构建 fieldId → position 映射。

### `core/src/main/java/org/apache/iceberg/avro/Avro.java`

**修改目的**：重组 `ReadBuilder.build()` 的读取器组装逻辑，引入 `createResolvingReader` 入口与 `NameMappingDatumReader` 包装。

**工作逻辑**：
- `defaultCreateReaderFunc` 类型从 `Function<Schema, DatumReader<?>>` 改为 `Function<org.apache.iceberg.Schema, DatumReader<?>>`，并改用 `GenericAvroReader.create(readSchema)`（新工厂接受 Iceberg schema）。
- 新增字段 `createResolvingReaderFunc` 与方法 `createResolvingReader(Function<org.apache.iceberg.Schema, DatumReader<?>>)`，用于直接提供已构造好的 reader（不再走 ProjectionDatumReader 包装）。
- `createReaderFunc` 两个重载的校验逻辑加入 `createResolvingReaderFunc == null` 检查，防止多种 reader 创建方式并存。
- `build()` 逻辑重写：
  - 若未提供 `nameMapping`，自动 `this.nameMapping = MappingUtil.create(schema)`（基于 read schema 生成默认 name mapping）。
  - 按优先级选择 reader：`createReaderBiFunc` → `ProjectionDatumReader<>(avroSchema -> createReaderBiFunc.apply(schema, avroSchema), schema, renames, null)`；`createReaderFunc` → `ProjectionDatumReader<>(createReaderFunc, schema, renames, null)`；`createResolvingReaderFunc` → 直接 `createResolvingReaderFunc.apply(schema)`；默认 → `defaultCreateReaderFunc.apply(schema)`。
  - 若 reader 实现 `SupportsCustomRecords`，调用 `setClassLoader(loader)` 与 `setRenames(renames)` 注入配置。
  - 用 `NameMappingDatumReader<>(nameMapping, reader)` 包装后传入 `AvroIterable`。

### `core/src/main/java/org/apache/iceberg/avro/NameMappingDatumReader.java`（新增）

**修改目的**：将 name mapping 应用逻辑从 `ProjectionDatumReader` 解耦为独立的 `DatumReader` 包装器。

**工作逻辑**：新文件 66 行。实现 `DatumReader<D>` 与 `SupportsRowPosition`。`setSchema(Schema newFileSchema)` 中判断 `AvroSchemaUtil.hasIds(newFileSchema)`：若文件 schema 已含字段 ID（即文件本身是 Iceberg 写的），直接用；否则 `AvroSchemaUtil.applyNameMapping(newFileSchema, nameMapping)` 应用 name mapping（为无 ID 的旧文件补 ID）。之后调用 `wrapped.setSchema(fileSchema)`。`read` 直接委托 `wrapped.read`。`setRowPositionSupplier` 转发给实现 `SupportsRowPosition` 的 wrapped。

### `core/src/main/java/org/apache/iceberg/avro/SupportsCustomRecords.java`（新增）

**修改目的**：定义一个让 `Avro.ReadBuilder` 向 `DatumReader` 注入 classLoader 与 renames 的接口。

**工作逻辑**：新文件 28 行。包级接口 `SupportsCustomRecords`，声明 `setClassLoader(ClassLoader loader)` 与 `setRenames(Map<String, String> renames)`。`GenericAvroReader` 实现该接口，`Avro.ReadBuilder.build()` 在创建 reader 后检查 `instanceof SupportsCustomRecords` 并注入。

### `core/src/main/java/org/apache/iceberg/avro/ValueReader.java`

**修改目的**：为 `ValueReader` 接口增加默认 `skip` 方法。

**工作逻辑**：新增 `default void skip(Decoder decoder) throws IOException { read(decoder, null); }`——默认实现通过 `read` 读取并丢弃值，但各具体 reader 会覆盖为更高效的直接 skip 调用（如 `decoder.readNull()`、`decoder.skipFixed(4)` 等）。这是支持 readPlan 中跳过未投影字段的基础。

### `core/src/main/java/org/apache/iceberg/avro/ValueReaders.java`

**修改目的**：为所有 reader 实现 `skip` 方法，并新增 `PlannedStructReader` 体系与若干常量/类型提升 reader。

**工作逻辑**：
- 新增 `constant(T value)` → `ConstantReader`（返回常量，skip 空操作）；`replaceWithConstant(reader, value)` → `ReplaceWithConstantReader`（继承 ConstantReader，read 时先调用 replaced.read 消费文件值再返回常量，skip 时调用 replaced.skip）。
- 新增 `intsAsLongs()` → `IntegerAsLongReader`（读 int 转 long）；`floatsAsDoubles()` → `FloatAsDoubleReader`（读 float 转 double）；`positions()` → `PositionReader`（生成行号）。
- 为所有现有 reader（`NullReader`/`BooleanReader`/`IntegerReader`/`LongReader`/`FloatReader`/`DoubleReader`/`StringReader`/`Utf8Reader`/`UUIDReader`/`FixedReader`/`GenericFixedReader`/`BytesReader`/`ByteBufferReader`/`DecimalReader`/`UnionReader`/`EnumReader`/`ArrayReader`/`ArrayMapReader`/`MapReader`）补充 `skip(Decoder)` 方法——多数调用对应的 `decoder.skipXxx()`，`DecimalReader.skip` 委托 `bytesReader.skip`，容器类的 skip 循环跳过所有元素。
- 新增 `PlannedStructReader<S>` 抽象类（implements `ValueReader<S>`, `SupportsRowPosition`）：从 `readPlan` 拆出 `readers[]` 与 `positions[]` 数组；`read` 中按 positions[i] 是否为 null 决定 `read` 并 `set` 或 `skip`；`setRowPositionSupplier` 转发给所有 `SupportsRowPosition` 子 reader。两个具体实现：`PlannedRecordReader`（生成 `GenericData.Record`，`reuseOrCreate` 复用或新建 Record）与 `PlannedIndexedReader<R extends IndexedRecord>`（用 `DynConstructors` 反射构造指定 record 类，`hiddenImpl(recordClass, Schema.class)` 或 `hiddenImpl(recordClass)`）。
- `record(Schema recordSchema, List<Pair<Integer, ValueReader<?>>> readPlan)` 与 `record(Schema recordSchema, Class<R> recordClass, List<Pair<Integer, ValueReader<?>>> readPlan)` 两个新工厂方法对应上述两类。
- `StructReader` 中 `setRowPositionSupplier` 简化：不再在内部缓存 `startingPos`，直接 `this.readers[posField] = new PositionReader()` 并统一转发 `posSupplier`。
- `PositionReader` 重构：从构造器接收 `rowPosition` 改为 `implements SupportsRowPosition`，`setRowPositionSupplier` 中 `this.currentPosition = posSupplier.get() - 1`；新增 `skip` 空操作。
- `StructReader` 中两处 `AvroSchemaUtil.getFieldId(field)` → `AvroSchemaUtil.fieldId(field)` 并用 `Objects.equals` 比较（fieldId 可能为 null）。

### `core/src/main/java/org/apache/iceberg/avro/AvroIterable.java`

**修改目的**：将 `SupportsRowPosition` 的 `posSupplier` 用 `Suppliers.memoize` 包装，避免重复扫描定位起始行。

**工作逻辑**：`setRowPositionSupplier(() -> AvroIO.findStartingRowPos(file::newStream, start))` 改为 `Suppliers.memoize(() -> AvroIO.findStartingRowPos(file::newStream, start))`——`findStartingRowPos` 涉及打开文件流扫描，多次调用代价高，memoize 保证只计算一次。这与 `PositionReader` 改为 `setRowPositionSupplier` 模式配合：每次 `read` 不再各自缓存位置，而是统一从 memoized supplier 取初始位置。

### `core/src/test/java/org/apache/iceberg/avro/TestAvroNameMapping.java`

**修改目的**：更新 name mapping 测试的断言，反映重构后字段不再被重命名。

**工作逻辑**：旧实现在 name mapping 缺失某个字段 ID 时，会通过 `Schema.applyAliases` 把文件字段重命名为 `原名_r<fieldId>` 形式（如 `location_r5`、`long_r2`、`point_r22`、`y_r18`）。新实现不再走 applyAliases 路径，文件字段保持原名（如 `location`、`long`、`point`、`y`），但值为 null（因未投影）。测试断言相应从 `assertThat(projected.getSchema().getField("location_r5")).isNotNull()` + `assertThat(projected.get("location_r5")).isNull()` 简化为 `assertThat(projected.get("location")).isNull()`，多处处类似简化。这是重构的预期行为变化——重命名是旧实现的副作用，新实现通过字段 ID 匹配直接处理投影，无需重命名。

## 小结

本次提交是对 Iceberg core Avro 读取器的结构性重构，净增 747 行（835 增 / 88 删），涉及 9 个文件（4 个新增：`AvroWithPartnerVisitor`、`NameMappingDatumReader`、`SupportsCustomRecords` 及测试调整）。重构以"partner 类型驱动的 schema 解析"取代旧的 `applyAliases` + `DecoderResolver` 路径，通过 `AvroWithPartnerVisitor` 框架在遍历文件 schema 时同时访问 Iceberg 预期类型，按字段 ID 匹配构建 readPlan，由 `PlannedStructReader` 单趟执行读取/跳过/常量替换。重构带来三点能力提升：支持 int→long/float→double 类型提升、统一支持 metadata 列（`_pos`/`_deleted`）注入、name mapping 应用与投影读取逻辑解耦。同时消除了对 `iceberg-data` 模块 `DecoderResolver` 的依赖，使 core 模块的 Avro 读取路径更自洽。测试断言更新反映了字段不再被重命名的预期行为变化。
