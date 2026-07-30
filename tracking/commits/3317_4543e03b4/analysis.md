# 提交 3317：Flink: Support Variant to Flink 2.1 (#15265)

## 提交信息

- **序号**：3317 / 4088
- **哈希**：4543e03b4a4fc2ee19c3848eb02e2f023331abf9
- **短哈希**：4543e03b4
- **日期**：2026-02-26
- **作者**：GuoYu
- **提交说明**：Flink: Support Variant to Flink 2.1 (#15265)
- **PR/Issue**：#15265（协作者 Talat UYARER）

## 总体目的

Variant 是一种半结构化数据类型（类似 JSON），允许在同一列中存储任意结构的数据。Iceberg 在 format version 3 中原生支持 `Types.VariantType`，并将 Variant 以"metadata + value"两段二进制（小端序）的形式存储在 Parquet 中。Spark 引擎此前已支持 Variant 的读写与转换。本提交的目标是为 Flink 2.1 引擎补齐 Variant 类型支持：使得 Flink 2.1 既能把 Iceberg 表中的 Variant 列读出来作为 Flink 的 `VariantType`/`BinaryVariant`，也能把 Flink 写入的 Variant 数据正确地序列化为 Iceberg 的 Variant 二进制格式落盘到 Parquet。

Flink 2.1 是首个内置 `org.apache.flink.table.types.logical.VariantType` 与 `org.apache.flink.types.variant.BinaryVariant` 类型的 Flink 版本，因此该支持专门落在 `flink/v2.1` 模块下（v1.20、v2.0 因 Flink 自身尚无 Variant 类型而不涉及）。完成该支持后，用户可在 Flink SQL 中使用 `PARSE_JSON(...)` 写入 Variant 列、并以 `VariantType` 读取，实现与 Spark/其他引擎间的 Variant 数据互操作。此外，本提交还顺带把原先散落在 Spark 测试中的 Variant 测试数据与用例抽取到 core 模块的共享 `VariantTestHelper`，供 Spark 与 Flink 测试复用，减少重复代码。

## 如何达成设计目的

整体思路是分层接入：首先在类型映射层完成 Iceberg `Types.VariantType` 与 Flink `VariantType` 的双向转换（`FlinkTypeToType`、`TypeToFlinkType`）；其次在 Parquet schema 适配层让 `ParquetWithFlinkSchemaVisitor` 能识别 Variant 的 Parquet 逻辑类型注解并分派到新的 `variant()` 钩子；然后在读写层分别实现 `FlinkParquetReaders` 与 `FlinkParquetWriters`，通过委托 Iceberg 已有的 `VariantReaderBuilder`/`VariantWriterBuilder` 完成底层 Parquet 列读写，并在外层做 Iceberg Variant 与 Flink `BinaryVariant` 之间的字节缓冲转换；最后补充 SQL 端到端测试、Parquet 往返测试，并把公共测试数据抽到 `VariantTestHelper`。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkTypeToType.java` (+6/-0 lines)

**修改目的**：让 Flink 逻辑类型到 Iceberg 类型的转换器识别 Flink 的 `VariantType`。

**工作逻辑**：
新增对 `org.apache.flink.table.types.logical.VariantType` 的 import，并覆写 `visit(VariantType variantType)` 方法，直接返回 `Types.VariantType.get()`。这样当 Flink schema 中出现 Variant 列时，会被转换为 Iceberg 的 Variant 类型，建立从 Flink 到 Iceberg 的类型映射。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/TypeToFlinkType.java` (+6/-0 lines)

**修改目的**：让 Iceberg 类型到 Flink 逻辑类型的转换器支持 Variant。

**工作逻辑**：
新增 `VariantType` import，并覆写 `variant(Types.VariantType variant)` 方法返回 `new VariantType()`，与 `FlinkTypeToType` 形成双向对称映射，保证 schema 转换的来回一致性。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/ParquetWithFlinkSchemaVisitor.java` (+19/-0 lines)

**修改目的**：在 Parquet schema 与 Flink schema 的对齐过程中识别 Variant 类型并分派到新的 visitor 钩子。

**工作逻辑**：
在处理 struct 字段时新增一个分支：当 Parquet 的逻辑类型注解为 `variantType(Variant.VARIANT_SPEC_VERSION)`，或 Flink 端类型 `sType` 本身为 `VariantType` 时进入 Variant 处理路径。这里采用"双重判定"是因为有的引擎（如 Spark）写出 Variant 时并不一定带上 Parquet variant 逻辑类型注解，因此同时依赖 Iceberg schema 信息做兜底。随后通过 `Preconditions.checkArgument` 校验 Flink 类型确为 `VariantType`，并调用 `visitor.variant(variant, group)`。同时在 visitor 基类中新增返回 `null` 的默认 `variant()` 方法，避免未实现该钩子的子类报错。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java` (+40/-0 lines)

**修改目的**：实现从 Parquet 中读出 Iceberg Variant 并转换为 Flink `BinaryVariant` 的读取器。

**工作逻辑**：
在内部 reader 构造类中覆写 `variantVisitor()` 返回基于 `VariantReaderBuilder` 的 visitor，用于构建 Variant 的底层 Parquet 值读取器；并覆写 `variant(...)` 用新建的 `VariantReader` 包装底层 reader。核心转换逻辑在 `VariantReader`（继承 `DelegatingValueReader`）中：先通过委托 reader 读出 `org.apache.iceberg.variants.Variant`，再分别申请 metadata 与 value 的字节数组，以 `ByteOrder.LITTLE_ENDIAN` 包装成 `ByteBuffer`，调用 `metadata.writeTo(...)` 与 `value.writeTo(...)` 把 Iceberg Variant 的两段二进制写出，最后用这两个字节数组构造 Flink 的 `BinaryVariant(valueBytes, metadataBytes)`。小端序是 Variant 规范与 Flink `BinaryVariant` 约定的字节序，必须保持一致。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetWriters.java` (+65/-0 lines)

**修改目的**：实现把 Flink `BinaryVariant` 写出为 Iceberg Variant 并落盘 Parquet 的写入器。

**工作逻辑**：
在 writer 构造类中覆写 `variant(VariantType, GroupType)`，通过 `ParquetVariantVisitor.visit(...)` 配合 `VariantWriterBuilder` 构建底层 Iceberg Variant 写入器，再用 `VariantWriter` 包装。`VariantWriter` 实现 `ParquetValueWriter<Variant>`：在 `write` 中校验入参确为 `BinaryVariant`，取出其 `getMetadata()` 与 `getValue()` 字节数组，分别以小端序 `ByteBuffer` 解析重建为 `VariantMetadata.from(...)` 与 `VariantValue.from(...)`，再组合成 `org.apache.iceberg.variants.Variant.of(metadata, value)` 委托给底层 writer 写出。同时实现 `columns()`、`setColumnStore()`、`metrics()` 等方法以正确代理底层 writer 的列与指标能力，保证 metrics 收集与列存储设置不丢失。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/RowDataConverter.java` (+15/-0 lines)

**修改目的**：在测试用的 RowData 转换工具中支持 Variant 类型的转换。

**工作逻辑**：
在 `convert` 的 switch 中新增 `case VARIANT`：把传入的 Iceberg `Variant` 对象的 metadata 与 value 各自序列化为小端序字节数组（与 reader 逻辑一致），构造并返回 Flink 的 `BinaryVariant`，使测试中可将 Iceberg 记录转换为 Flink `RowData`。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java` (+61/-0 lines)

**修改目的**：在测试断言工具中支持 Variant 值的相等比较。

**工作逻辑**：
在两个 `assertEquals` 重载的 switch 中各新增 `case VARIANT` 分支：校验实际值为 Flink `Variant`、期望值为 Iceberg `org.apache.iceberg.variants.Variant`，再调用新增的 `compareVariants(...)`。`compareVariants` 把 Iceberg variant 的 metadata 与 value 各自写出为小端序字节数组，分别与 `BinaryVariant` 的 `getMetadata()`/`getValue()` 做字节级 `isEqualTo` 断言，从而严格验证读写往返后的二进制一致性。

### `core/src/test/java/org/apache/iceberg/variants/VariantTestHelper.java` (新增, +142 lines)

**修改目的**：把原先在 Spark 测试中重复定义的 Variant 测试数据与用例模板抽取为 core 模块的共享工具类。

**工作逻辑**：
该类集中定义了 `PRIMITIVES`（覆盖 null/布尔/整型/浮点/日期时间/decimal4-8-16/二进制/字符串/UUID 等全部受支持的原语）与 `UNSUPPORTED_PRIMITIVES`（time、带纳秒的时间戳等 Flink 暂不支持的原语）两组测试数据，并提供 `testVariantPrimitiveRoundTrip`、`testVariantArrayRoundTrip`、`testVariantObjectRoundTrip`、`testVariantNestedStructures` 等模板方法，通过 `VariantTestFunction` 函数式接口回调引擎特定的往返逻辑，实现"一套数据、多引擎复用"。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkVariants.java` (+8/-79 lines)

**修改目的**：将 Spark 测试中重复的 Variant 数据定义替换为对共享 `VariantTestHelper` 的引用。

**工作逻辑**：
删除原先内联定义的 `PRIMITIVES`、`UNSUPPORTED_PRIMITIVES` 数组及各 round-trip 方法中的重复构造代码，改为 `@FieldSource("org.apache.iceberg.variants.VariantTestHelper#PRIMITIVES")` 引用共享数据，并调用 `VariantTestHelper.testVariantPrimitiveRoundTrip(...)` 等模板方法。这是一次纯粹的抽取重构，行为不变但消除重复、便于后续 Flink 等引擎复用。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/TestFlinkVariantType.java` (新增, +128 lines)

**修改目的**：在 Catalog/SQL 层面端到端验证 Flink 对 Variant 列的读写。

**工作逻辑**：
继承 `CatalogTestBase`，仅对 hadoop catalog（因 hive 元数据暂不支持 variant）参数化测试。`testInsertVariantFromFlink` 通过 `PARSE_JSON` 向 variant 列写入 JSON 并校验落盘后能读出含正确字段的 Iceberg `Variant`；`testReadVariantFromFlink` 反向地用 `GenericAppenderHelper` 写入 Iceberg Variant 记录，再通过 Flink source 的 `DataIterator` 读出并断言为 `BinaryVariant` 且字段值正确。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetWriter.java` (+5/-0 lines)

**修改目的**：声明 Flink Parquet writer 测试已支持 Variant。

**工作逻辑**：
覆写 `supportsVariant()` 返回 `true`，使基类 `DataTestBase` 中与 Variant 相关的用例对该 writer 生效。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkVariants.java` (新增, +183 lines)

**修改目的**：在 Parquet 读写层验证 Variant 的类型转换与各类值的往返一致性，并校验不支持类型的报错行为。

**工作逻辑**：
包含类型互转测试（Iceberg<->Flink VariantType）、基于 `VariantTestHelper` 的原语/数组/对象/嵌套结构往返测试，以及针对 `UNSUPPORTED_PRIMITIVES` 的负向测试——构造含不支持类型的 `BinaryVariant` 并断言调用 `toJson()` 时抛出 `VariantTypeException` 且消息含 `UNKNOWN_PRIMITIVE_TYPE_IN_VARIANT`，确保对暂不支持类型有明确报错而非静默错误。

## 总结

本提交为 Flink 2.1 引擎完整接入了 Iceberg Variant 类型支持，贯通了类型映射、Parquet schema 适配、读写器实现与端到端测试，并顺手把 Variant 测试数据重构为 core 模块共享工具以供多引擎复用。核心价值在于打通 Flink 与 Iceberg/Spark 之间的 Variant 数据互操作能力，使 Flink 用户能够以 SQL 原生方式读写半结构化 Variant 数据。
