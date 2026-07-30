# 提交 2704：Parquet, Core: Allows Internal Parquet Readers to use Custom Types (#14040)

## 提交信息

- **序号**：2704 / 4088
- **哈希**：f9a41688e3ad67d30962b0d093698622b69cb0d9
- **短哈希**：f9a41688e
- **日期**：2025-09-29 23:04:01 -0500
- **作者**：Russell Spitzer
- **提交说明**：Parquet, Core: Allows Internal Parquet Readers to use Custom Types (#14040)
- **PR/Issue**：#14040

## 总体目的

本提交为 Iceberg 的内部 Parquet 读取器（Internal Parquet Readers）实现"自定义类型"（Custom Types）支持，使读取 Parquet 文件时可以将顶层记录或某个嵌套结构字段实例化为用户指定的 `StructLike` 实现类，而非固定的 `Record`/`GenericRecord`。

此前 `Parquet.ReadBuilder` 已经声明了 `setRootType(Class<? extends StructLike>)` 与 `setCustomType(int fieldId, Class<? extends StructLike>)` 两个 API，但内部实现直接抛 `UnsupportedOperationException("Custom types are not yet supported")`，即仅占位未实现。这限制了需要在读取阶段直接得到特定 `StructLike` 子类（如 Iceberg 内部的 `PartitionData`、或测试用的 `CustomRow`）的调用方，它们不得不在读取后再做一次拷贝/转换。

本次实现填补该能力：通过引入 `ReaderFunction` 抽象统一 reader 工厂入口，使 schema、root type、custom types（按 fieldId 映射的类）等信息能传递到 reader 构建过程；`BaseParquetReaders` 的 `createStructReader` 新增带 `fieldId` 的重载，使子类可根据 fieldId 选择实例化哪个 `StructLike` 类；新增 `StructLikeReader` 通过反射构造器（`DynConstructors`）实例化自定义类并按位 `set` 字段。`InternalReader` 利用该机制支持按 fieldId（含根 `ROOT_ID = -1`）选择类型。

## 如何达成设计目的

整体设计分几层：

1. **统一 reader 工厂为 `ReaderFunction` 接口**：原先 `Parquet.ReadBuilder` 持有两个互斥字段 `readerFunc`（单参 `Function`）与 `readerFuncWithSchema`（双参 `BiFunction`），现合并为单一 `ReaderFunction` 接口。该接口除 `apply()` 外，提供 `withSchema`、`withRootType`、`withCustomTypes` 三个默认方法，允许实现类按需接收这些配置。提供 `UnaryReaderFunction`（包装单参）与 `BinaryReaderFunction`（包装双参，需 schema）两个实现以兼容旧 API。新增 `createReaderFunc(ReaderFunction)` 重载以接受外部实现。

2. **`ReadBuilder` 真正存储 rootType/customTypes**：`setRootType` 与 `setCustomType` 不再抛异常，而是存入 `rootType` 字段与 `customTypes` map；构建 `ParquetReader` 时对 `readerFunction` 调用 `withSchema(schema).withRootType(rootType).withCustomTypes(customTypes).apply()`，将配置传递给 reader 工厂。

3. **`BaseParquetReaders` 传递 fieldId**：新增 `createStructReader(List, StructType, Integer fieldId)` 重载（旧二参重载标记 `@Deprecated`，新方法默认回退到旧方法）。在 `struct(...)` 与 `message(...)` 构建逻辑中，调用新三参重载并传入从 Parquet `GroupType.getId()` 解析的 fieldId；顶层 message 用 `ROOT_ID = -1` 标记（`message.withId(ROOT_ID)`），使根 struct 也能被识别。

4. **`InternalReader` 维护 typesById 映射**：`InternalReader` 新增 `typesById` map，`readerFunction()` 静态工厂返回的 `ReaderFunction` 实现通过 `withCustomTypes`/`withRootType` 把类型映射注入 reader 实例；`createStructReader(..., fieldId)` 用 `typesById.getOrDefault(fieldId, Record.class)` 选择目标类，调用新增的 `ParquetValueReaders.structLikeReader(...)` 构造 reader。

5. **`ParquetValueReaders.structLikeReader` 与 `StructLikeReader`**：新增 `structLikeReader` 工厂，若目标类是 `Record.class` 复用现有 `recordReader`，否则创建 `StructLikeReader`。`StructLikeReader<T>` 继承 `StructReader<T,T>`，用 `DynConstructors` 查找目标类的 `(StructType)` 构造器或无参构造器，`newStructData` 时反射实例化，`set`/`getField`/`buildStruct` 直接操作 `StructLike`。

6. **`InternalParquet` 改用 `readerFunction()`**：原先 `createReaderFunc(InternalReader::create)`（双参方法引用），改为 `createReaderFunc(InternalReader.readerFunction())`，使 InternalReader 走新的 `ReaderFunction` 通路，从而能接收 rootType/customTypes。

## 修改详情

### `api/src/test/java/org/apache/iceberg/TestHelpers.java` (+9/-0 lines)

**修改目的**：为测试夹具 `CustomRow` 增加可被反射构造的构造器。

**工作逻辑**：新增无参构造器 `CustomRow()`（调用 `this(new Object[0])`）与 `CustomRow(Types.StructType)` 构造器（按 struct 字段数初始化 `values` 数组）。这两个构造器供 `StructLikeReader` 的 `DynConstructors` 反射查找（`hiddenImpl(structLikeClass, Types.StructType.class)` 与 `hiddenImpl(structLikeClass)`），使 `CustomRow` 可作为自定义类型被 reader 实例化。

### `build.gradle` (+1/-0 lines)

**修改目的**：让 `iceberg-core` 测试运行时能访问 `iceberg-parquet`。

**工作逻辑**：在 `project(':iceberg-core')` 的依赖中新增 `testRuntimeOnly project(':iceberg-parquet')`，因为新增的 `TestInternalData`（位于 core 测试）需要 `InternalData`/`InternalParquet` 等 parquet 模块的实现来读写 Parquet 文件。

### `core/src/test/java/org/apache/iceberg/TestInternalData.java` (+158/-0 lines, 新文件)

**修改目的**：端到端验证自定义类型读取（root type 与嵌套字段自定义类型）。

**工作逻辑**：参数化测试（AVRO、PARQUET 两种格式）：
- `testCustomRootType`：用 `InternalData.write` 写入 1000 条 `Record`，再用 `InternalData.read(...).setRootType(PartitionData.class)` 读取，断言读出的对象是 `PartitionData` 且字段值与原始数据一致。
- `testCustomTypeForNestedField`：嵌套 schema 中字段 2 为 struct，用 `setCustomType(2, TestHelpers.CustomRow.class)` 读取，断言嵌套字段被实例化为 `CustomRow` 且内部字段正确；处理 null 嵌套值情况。

### `parquet/src/main/java/org/apache/iceberg/InternalParquet.java` (+1/-1 lines)

**修改目的**：让 InternalParquet 走新的 `ReaderFunction` 通路。

**工作逻辑**：`readInternal` 中 `createReaderFunc(InternalReader::create)` 改为 `createReaderFunc(InternalReader.readerFunction())`，使 `InternalReader` 的 `ReaderFunction` 实现能接收 schema/rootType/customTypes。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetReaders.java` (+35/-8 lines)

**修改目的**：在 struct reader 创建时传递 fieldId，使子类可按 fieldId 选择自定义类型。

**工作逻辑**：
- 新增 `ROOT_ID = -1` 常量标识顶层 struct。
- 旧 `createStructReader(List, StructType)` 标记 `@Deprecated`（1.12.0 移除），改为抛 `UnsupportedOperationException` 提示用三参重载。
- 新增 `createStructReader(List, StructType, Integer fieldId)`，默认回退到旧方法。
- `struct(...)`/`message(...)` 中调用三参 `createStructReader(newFields, expected, fieldId(struct))`，新增 `fieldId(GroupType)` 辅助方法返回 `struct.getId()`（可能为 null）。
- 顶层 message 用 `message.withId(ROOT_ID)` 标记根 struct，使根也带可识别的 fieldId。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/GenericParquetReaders.java` (+1/-1 lines)

**修改目的**：适配新的三参 `createStructReader` 签名。

**工作逻辑**：`createStructReader` 重写签名增加 `Integer fieldId` 参数，实现仍调用 `ParquetValueReaders.recordReader(fieldReaders, structType)`（忽略 fieldId，因为 Generic 始终用 `Record`）。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/InternalReader.java` (+43/-4 lines)

**修改目的**：InternalReader 维护 typesById 映射并提供 `ReaderFunction` 工厂。

**工作逻辑**：
- 新增实例字段 `typesById`（fieldId → StructLike 类）。
- 新增静态方法 `readerFunction()`：返回匿名 `ReaderFunction` 实现，`apply()` 返回 `messageType -> reader.createReader(schema, messageType)`；`withSchema` 设置 schema；`withCustomTypes` 把类型映射 `putAll` 到 `reader.typesById`；`withRootType` 把 rootType 以 `ROOT_ID` 为 key 存入 `typesById`。
- `createStructReader` 改为三参签名，用 `typesById.getOrDefault(fieldId, Record.class)` 选择目标类，调用 `ParquetValueReaders.structLikeReader(...)`。

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java` (+95/-22 lines)

**修改目的**：统一 reader 工厂为 `ReaderFunction` 接口，实现 rootType/customTypes 配置传递。

**工作逻辑**：
- 用 `ReaderFunction readerFunction` 字段替换原 `readerFunc` + `readerFuncWithSchema` 两字段；新增 `rootType` 字段与 `customTypes` map。
- 新增 `ReaderFunction` 接口（`apply()` + 默认 `withRootType`/`withCustomTypes`/`withSchema`）。
- 新增 `UnaryReaderFunction`（包装单参 `Function`）与 `BinaryReaderFunction`（包装双参 `BiFunction`，`apply` 前需 schema）两个实现。
- `createReaderFunc(Function)` 改为包装成 `UnaryReaderFunction`；`createReaderFunc(BiFunction)` 改为包装成 `BinaryReaderFunction`；新增 `createReaderFunc(ReaderFunction)` 重载。各 setter 的互斥校验统一改为检查 `readerFunction`/`batchedReaderFunc`。
- `setRootType`/`setCustomType` 不再抛异常，存入字段。
- 构建 `ParquetReader` 时：`readerFunction.withSchema(schema).withRootType(rootType).withCustomTypes(customTypes).apply()` 得到 `Function<MessageType, ParquetValueReader<?>>` 传入 `ParquetReader`。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReaders.java` (+52/-0 lines)

**修改目的**：提供通用 `StructLike` reader，通过反射实例化任意 `StructLike` 子类。

**工作逻辑**：
- 新增 `structLikeReader(readers, struct, structClass)` 工厂：若 `structClass` 是 `Record.class` 复用 `recordReader`，否则返回 `new StructLikeReader<>(...)`。
- 新增私有 `StructLikeReader<T extends StructLike>`：继承 `StructReader<T,T>`；构造时用 `DynConstructors.builder(StructLike.class).hiddenImpl(structLikeClass, Types.StructType.class).hiddenImpl(structLikeClass).build()` 查找构造器；`newStructData` 反射 `ctor.newInstance(struct)`（或无参）；`set`/`getField`/`buildStruct` 直接操作 `StructLike` 的 `set`/`get`。

## 总结

本提交实现了 Iceberg 内部 Parquet 读取器长期占位但未实现的"自定义类型"能力。通过引入 `ReaderFunction` 接口统一 reader 工厂入口、为 `createStructReader` 增加 fieldId 维度、新增基于反射的 `StructLikeReader`，使调用方可通过 `setRootType`/`setCustomType` 指定读取后得到的 `StructLike` 实现类（如 `PartitionData`、`CustomRow`），避免读取后的二次转换。配套测试覆盖根类型与嵌套字段自定义类型两种场景，跨 AVRO/PARQUET 格式。设计上保持了对旧单参/双参 reader 工厂 API 的向后兼容（通过包装类），并将旧 `createStructReader` 标记 `@Deprecated`。
