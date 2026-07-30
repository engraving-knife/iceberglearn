# 提交 1821：Avro: Support timestamp(9) and unknown types (#12455)

## 提交信息

- **序号**：1821 / 4088
- **哈希**：3c8366a4333e6d38c28d04c5959cdecb8cd81d6f
- **短哈希**：3c8366a43
- **日期**：2025-03-04 17:44:25 -0800
- **作者**：Ryan Blue
- **提交说明**：Avro: Support timestamp(9) and unknown types (#12455)
- **PR/Issue**：#12455

## 总体目的

该提交为 Iceberg 的 Avro 模块增加了对纳秒精度时间戳（timestamp(9)，即 TimestampNano）和 Unknown 类型的支持。这是 Iceberg 引入纳秒时间戳类型系列工作的一部分，使 Avro 文件格式能够正确读写 TimestampNano 类型数据，并能在 Avro schema 与 Iceberg 类型之间正确转换 Unknown 类型。

在此之前，Avro 模块仅支持毫秒和微秒精度的时间戳逻辑类型（TimestampMillis、TimestampMicros），无法处理 Avro 1.11+ 引入的 TimestampNanos 逻辑类型。同时，Avro 的 NULL 原始类型在转换为 Iceberg 类型时返回 null，未映射到 `Types.UnknownType`，导致包含未知类型的 union 处理不正确。

本提交通过在 SchemaToType、TypeToSchema、各类 Reader/Writer 中增加 TimestampNanos 分支，并将 NULL 原始类型映射为 UnknownType，使 Avro 模块完整支持这两种类型。

## 如何达成设计目的

整体思路是在 Avro schema 与 Iceberg 类型双向转换的所有节点增加 TimestampNanos 处理。将 SchemaToType 中原本内联的逻辑类型分发逻辑抽取为独立的 `logicalType()` 方法，便于新增 TimestampNanos 分支。在 TypeToSchema 中增加 TimestampNanoType 到 Avro schema 的转换。在各 Reader/Writer 中增加 `"timestamp-nanos"` case 处理（均按 long 读写）。对于 Unknown 类型，将 NULL 原始类型映射为 `Types.UnknownType.get()` 而非 null，并修复 union 中 NULL 与 Unknown 的字段索引计算。

## 修改详情

### api/src/main/java/org/apache/iceberg/types/TypeUtil.java (修改, 4 lines)

新增 `find(Type type, Predicate<Type> predicate)` 重载方法，支持在单个 Type 上查找匹配类型（原有只支持 Schema）。

### api/src/main/java/org/apache/iceberg/util/DateTimeUtil.java (修改, 4 lines)

新增 `timestamptzFromNanos(long nanosFromEpoch)` 方法，将纳秒时间戳转为 OffsetDateTime。

### api/src/test/java/org/apache/iceberg/util/RandomUtil.java (修改, 9 lines)

为随机数据生成增加 UNKNOWN（返回 null）和 TIMESTAMP_NANO（随机纳秒值）分支，支持测试。

### core/src/main/java/org/apache/iceberg/avro/AvroSchemaUtil.java (修改, 22 lines)

- `isTimestamptz` 增加 `TimestampNanos` 逻辑类型判断。
- 新增 `isOptional(Schema)` 方法：同时识别 option schema 和纯 NULL schema。
- `toOption` 改为 switch 结构，新增 NULL case 直接返回。

### core/src/main/java/org/apache/iceberg/avro/SchemaToType.java (修改, 70 lines)

- 将 `primitive()` 中的逻辑类型分发抽取为独立 `logicalType(Schema, LogicalType)` 方法，新增 `TimestampNanos` 分支返回 `TimestampNanoType.withZone()/withoutZone()`。
- `primitive()` 的 NULL case 返回 `Types.UnknownType.get()` 而非 null。
- 字段/map 的 optional 判断改用 `isOptional`。
- `union()` 中 NULL 判断改用 schema 类型检查而非 `options.get(0) == null`。

### core/src/main/java/org/apache/iceberg/avro/TypeToSchema.java (修改, 17 lines)

新增 `TimestampNanoType` 到 Avro schema 的转换，生成带 `LogicalTypes.timestampNanos()` 逻辑类型的 LONG schema。增加 NULL_SCHEMA 常量。

### core/src/main/java/org/apache/iceberg/avro/AvroWithPartnerByStructureVisitor.java (修改, 10 lines)

修复 union 中 NULL 与 Unknown 类型的字段索引计算：当遇到 NULL 分支且对应字段类型不是 Unknown 时才设置 `encounteredNullWithoutUnknown` 标志，避免 Unknown 字段打乱后续字段映射。

### core/src/main/java/org/apache/iceberg/avro/ 各 Reader/Writer (修改, 多个文件)

`BaseWriteBuilder`、`GenericAvroReader`、`InternalReader`、`DataReader`、`DataWriter`、`GenericReaders`、`GenericWriters`、`PlannedDataReader` 等增加 `"timestamp-nanos"` case，读写按 long 处理。

### 测试文件 (修改, 多个)

更新 `TestInternalAvro`、`AvroDataTest`（移除旧测试）、`DataTest`、`RandomInternalData`、`TestSchemaParser`、`TestGenericAvro`、`TestInternalParquet` 等以覆盖 timestamp(9) 和 unknown 类型。

## 小结

该提交为 Avro 模块补全了 TimestampNano 和 Unknown 类型支持，影响范围涵盖 api 和 core 模块的类型系统与 Avro 读写链路。回迁到 1.4.x 分支时需注意：1.4.x 分支需已包含 `Types.TimestampNanoType` 和 `Types.UnknownType` 类型定义；Avro 依赖版本需支持 TimestampNanos 逻辑类型（Avro 1.11+）。该提交改动面广但逻辑清晰，建议与 timestamp(9) 相关提交协同回迁。注意完整哈希对应的短哈希为 `3c8366a43`（取前9位）。
