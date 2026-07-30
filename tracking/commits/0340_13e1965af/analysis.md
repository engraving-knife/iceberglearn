# 提交 0340：Parquet: Move to ValueReader generation to a visitor (#9063)

## 提交信息

- **序号**：0340
- **哈希**：13e1965af44a652d634c54ffe3e2d3739a40eceb
- **短哈希**：13e1965af
- **日期**：2024-01-08 11:40:29 -0800
- **作者**：Fokko Driesprong
- **提交说明**：Parquet: Move to ValueReader generation to a visitor (#9063)
- **PR/Issue**：#9063

## 总体目的

本提交对 Iceberg 的 Parquet 读取器构建基类 `BaseParquetReaders` 做一次结构性的重构：把原本在 `ReadBuilder.primitive(...)` 方法中基于 `primitive.getOriginalType()` 大 switch 的"Parquet 逻辑类型 → ParquetValueReader"分派逻辑，迁移到基于 Parquet 官方 `LogicalTypeAnnotation.LogicalTypeAnnotationVisitor` 访问者模式的实现上。这是一次"行为保持、结构升级"的纯重构，目的是让 Iceberg 的 Parquet 读取路径与 Parquet 上游 API 的演进方向对齐——从已逐渐被弃用的 `OriginalType` 枚举迁移到更现代、更具表达力的 `LogicalTypeAnnotation` 类型体系上。

要理解这次重构的必要性，需要先厘清 Parquet schema 元数据中两套并存的"逻辑类型"表达：(1) **`OriginalType`** 是 Parquet 早期的扁平枚举（`UTF8`、`INT_8`、`INT_16`、`INT_32`、`INT_64`、`DATE`、`TIME_MICROS`、`TIME_MILLIS`、`TIMESTAMP_MICROS`、`TIMESTAMP_MILLIS`、`DECIMAL`、`BSON`、`JSON`、`ENUM` 等），通过 `primitive.getOriginalType()` 获取；它的局限在于枚举值是扁平的，无法承载附加属性（例如 INT 的位宽 8/16/32/64 被强行拆成四个独立枚举值，Time/Timestamp 的时间单位 MICROS/MILLIS 也被拆成独立枚举值，而 DECIMAL 的 precision/scale 则要另外从 `getLogicalTypeAnnotation()` 强转获取）。(2) **`LogicalTypeAnnotation`** 是 Parquet 1.10+ 引入的、面向对象的类型体系（`StringLogicalTypeAnnotation`、`IntLogicalTypeAnnotation`、`DecimalLogicalTypeAnnotation`、`TimeLogicalTypeAnnotation`、`TimestampLogicalTypeAnnotation` 等），每个子类用字段承载自身属性（`IntLogicalTypeAnnotation.getBitWidth()` 返回 8/16/32/64、`isSigned()` 返回是否有符号、`TimeLogicalTypeAnnotation.getUnit()` 返回 MICROS/MILLIS/NANOS、`DecimalLogicalTypeAnnotation.getScale()/getPrecision()` 返回精度信息）。Parquet 官方在 `LogicalTypeAnnotation` 上提供了 `LogicalTypeAnnotationVisitor<R>` 访问者接口与 `accept(visitor)` 双重分派方法，让消费方按"类型多态"而非"枚举 switch"处理逻辑类型。上游 Parquet 已在逐步弃用 `OriginalType`、推荐迁移到 `LogicalTypeAnnotation`，未来版本可能移除 `getOriginalType()`。

老代码的痛点很明确：`primitive(...)` 方法的 `if (primitive.getOriginalType() != null)` 分支是一个 60+ 行的嵌套 switch， cyclomatic complexity 很高（方法上挂着 `@SuppressWarnings("checkstyle:CyclomaticComplexity")`），新增逻辑类型需要在 switch 中追加 case 并容易遗漏边界；DECIMAL 还要二次强转 `(DecimalLogicalTypeAnnotation) primitive.getLogicalTypeAnnotation()` 拿 scale——这种"先用 `OriginalType` 分派、再回头取 `LogicalTypeAnnotation` 取属性"的混合写法本身就体现了 `OriginalType` 表达力不足。重构后，所有逻辑类型分派统一交给 `LogicalTypeAnnotation.accept(visitor)` 完成，每个逻辑类型对应 visitor 上的一个 `visit(XxxLogicalTypeAnnotation)` 方法，属性直接从参数对象上取，结构清晰、可扩展性强（新增逻辑类型只需在 visitor 上加 `visit` 方法，编译器会通过接口契约提醒）。

附带地，重构还在两个细节上做了行为对齐与改进：(1) INT 系列逻辑类型被统一到 `visit(IntLogicalTypeAnnotation)` 中，通过 `getBitWidth() == 64` 区分 INT64 与 INT8/16/32，并把"期望 Iceberg 类型是 LONG 时用 `IntAsLongReader`"的提升逻辑收敛到一处——老代码里 INT_8/INT_16/INT_32 共用一个 case 做 LONG 提升、INT_64 单独一个 case 不提升，新写法用 `getBitWidth()` 表达同一语义但更紧凑；(2) Time/Timestamp 在遇到未支持的单位（如 NANOS）时，老代码落入 `default` 抛 `UnsupportedOperationException`，新代码让 `visit` 返回 `Optional.empty()`（Time）或调用 `super.visit(...)` 返回 `Optional.empty()`（Timestamp），再由调用方 `.orElseThrow(...)` 统一抛错——错误路径更一致，也为将来扩展 NANOS 支持留好了挂钩点。

## 如何达成设计目的

重构采用经典的"访问者模式替换 switch"手法：在 `BaseParquetReaders` 中新增内部类 `LogicalTypeAnnotationParquetValueReaderVisitor` 实现 `LogicalTypeAnnotation.LogicalTypeAnnotationVisitor<ParquetValueReader<?>>`，为每种逻辑类型（String/Enum/Decimal/Date/Time/Timestamp/Int/Json/Bson）各写一个 `visit(XxxLogicalTypeAnnotation)` 方法，把原 switch 各 case 的 reader 构造逻辑原样搬过去（DECIMAL 还保留了对 `primitive.getPrimitiveTypeName()` 的二次 switch，因为需要按底层存储类型 BINARY/INT64/INT32 选不同的 Decimal reader 并传 scale）。然后 `ReadBuilder.primitive(...)` 方法中原本 60+ 行的 `switch (primitive.getOriginalType())` 被替换为 6 行：`primitive.getLogicalTypeAnnotation().accept(new LogicalTypeAnnotationParquetValueReaderVisitor(desc, expected, primitive)).orElseThrow(() -> new UnsupportedOperationException("Unsupported logical type: " + primitive.getLogicalTypeAnnotation()))`。

`accept` 方法是 Parquet `LogicalTypeAnnotation` 上的双重分派入口——它根据自身具体子类类型回调 visitor 上对应的 `visit` 方法（如 `StringLogicalTypeAnnotation.accept(v)` 调 `v.visit(this)`），返回 `Optional<ParquetValueReader<?>>`。`Optional.empty()` 表示该 visitor 不处理此逻辑类型，由调用方 `orElseThrow` 转为异常。`expected` 与 `primitive` 作为 visitor 构造参数传入，供 `visit` 方法在需要时查询 Iceberg 期望类型（如 INT→LONG 提升、Timestamp 是否带时区）与 Parquet 原始类型（如 DECIMAL 的底层存储类型）。注意第二个 switch（按 `primitive.getPrimitiveTypeName()` 处理无逻辑类型的旧文件，包括 BINARY/INT32/FLOAT/INT96 等）保持不变——这部分本来就不依赖逻辑类型，INT96 时间戳的处理（`TimestampInt96Reader`）也保留在原处。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetReaders.java`

**修改目的**：把 `ReadBuilder.primitive(...)` 中基于 `OriginalType` 枚举的大 switch 重构为基于 `LogicalTypeAnnotation` 访问者模式的分派，对齐 Parquet 上游 API 演进方向、降低圈复杂度、提升可扩展性。

**工作逻辑**：改动分两部分——新增 visitor 内部类、替换 `primitive(...)` 中的 switch。

**第一部分：新增 `LogicalTypeAnnotationParquetValueReaderVisitor` 内部类（约 113 行）**

声明为 `private class LogicalTypeAnnotationParquetValueReaderVisitor implements LogicalTypeAnnotation.LogicalTypeAnnotationVisitor<ParquetValueReader<?>>`，持有三个 final 字段：`ColumnDescriptor desc`（列描述符，传给各 reader 构造器）、`org.apache.iceberg.types.Type.PrimitiveType expected`（Iceberg 期望类型，用于 INT→LONG、TIMESTAMP 时区判断）、`PrimitiveType primitive`（Parquet 原始类型，用于 DECIMAL 二次分派）。为每种逻辑类型实现一个 `visit` 方法：

- `visit(StringLogicalTypeAnnotation)` → `Optional.of(new ParquetValueReaders.StringReader(desc))`：对应老 `case UTF8`。
- `visit(EnumLogicalTypeAnnotation)` → `Optional.of(new ParquetValueReaders.StringReader(desc))`：对应老 `case ENUM`。
- `visit(JsonLogicalTypeAnnotation)` → `Optional.of(new ParquetValueReaders.StringReader(desc))`：对应老 `case JSON`（老代码把 ENUM/JSON/UTF8 合并到一个 case；新代码结构上分开但行为一致，便于未来分化处理）。
- `visit(DecimalLogicalTypeAnnotation)` → 内部仍按 `primitive.getPrimitiveTypeName()` 二次 switch：`BINARY/FIXED_LEN_BYTE_ARRAY` → `BinaryAsDecimalReader(desc, decimalLogicalType.getScale())`、`INT64` → `LongAsDecimalReader(...)`、`INT32` → `IntegerAsDecimalReader(...)`、`default` 抛 `UnsupportedOperationException`。对应老 `case DECIMAL`（scale 直接从 `decimalLogicalType` 参数取，省去了老代码 `(DecimalLogicalTypeAnnotation) primitive.getLogicalTypeAnnotation()` 强转）。
- `visit(DateLogicalTypeAnnotation)` → `Optional.of(new DateReader(desc))`：对应老 `case DATE`。
- `visit(TimeLogicalTypeAnnotation)` → 按 `timeLogicalType.getUnit()` 分派：`MICROS` → `TimeReader`、`MILLIS` → `TimeMillisReader`、其他（含 NANOS）→ `Optional.empty()`（让调用方 `orElseThrow` 抛错）。对应老 `case TIME_MICROS/TIME_MILLIS`。
- `visit(TimestampLogicalTypeAnnotation)` → 按 `timestampLogicalType.getUnit()` 分派：`MICROS` → 按 `((Types.TimestampType) expected).shouldAdjustToUTC()` 选 `TimestamptzReader`（带时区）或 `TimestampReader`（不带时区）；`MILLIS` → 同理选 `TimestamptzMillisReader` 或 `TimestampMillisReader`；其他单位（含 NANOS）→ `return LogicalTypeAnnotation.LogicalTypeAnnotationVisitor.super.visit(timestampLogicalType)`，即调用接口默认方法返回 `Optional.empty()`。对应老 `case TIMESTAMP_MICROS/TIMESTAMP_MILLIS`。
- `visit(IntLogicalTypeAnnotation)` → 先判 `intLogicalType.getBitWidth() == 64`：是则返回 `UnboxedReader`（对应老 `case INT_64`）；否则按 `expected.typeId() == LONG` 选 `IntAsLongReader`（提升）或 `UnboxedReader`——对应老 `case INT_8/INT_16/INT_32` 的合并分支。新写法用 `getBitWidth()` 把四个枚举值统一为带属性的单一逻辑类型，语义更紧凑。
- `visit(BsonLogicalTypeAnnotation)` → `Optional.of(new ParquetValueReaders.BytesReader(desc))`：对应老 `case BSON`。

**第二部分：替换 `ReadBuilder.primitive(...)` 中的逻辑类型 switch**

原 60+ 行的 `switch (primitive.getOriginalType()) { case ENUM: ... case JSON: ... case UTF8: ... case INT_8: ... case INT_64: ... case DATE: ... case TIMESTAMP_MICROS: ... case TIMESTAMP_MILLIS: ... case TIME_MICROS: ... case TIME_MILLIS: ... case DECIMAL: ... case BSON: ... default: throw ... }` 整段被替换为：
```java
return primitive
    .getLogicalTypeAnnotation()
    .accept(new LogicalTypeAnnotationParquetValueReaderVisitor(desc, expected, primitive))
    .orElseThrow(
        () ->
            new UnsupportedOperationException(
                "Unsupported logical type: " + primitive.getLogicalTypeAnnotation()));
```
关键点：(1) `primitive.getLogicalTypeAnnotation()` 取 `LogicalTypeAnnotation` 对象——当 `primitive.getOriginalType() != null` 时该对象非空（Parquet 保证两者一致性）；(2) `.accept(visitor)` 触发双重分派，回调 visitor 上对应类型的 `visit` 方法返回 `Optional<ParquetValueReader<?>>`；(3) `.orElseThrow(...)` 把 `Optional.empty()`（visitor 不处理该逻辑类型）转为 `UnsupportedOperationException`，错误消息从原来的 `primitive.getOriginalType()`（枚举名）改为 `primitive.getLogicalTypeAnnotation()`（类型对象 toString，信息更丰富）。方法上 `@SuppressWarnings("checkstyle:CyclomaticComplexity")` 保留——因为 `primitive(...)` 后半段仍有针对 `primitive.getPrimitiveTypeName()` 的 switch（处理无逻辑类型的旧文件），整体圈复杂度仍超阈值；但相比重构前已显著降低。后半段的 `case INT96: return new TimestampInt96Reader(desc)` 等无逻辑类型路径完全不变，INT96 时间戳读取行为保持。

**Import 调整**：新增 `import java.util.Optional;`（visitor 返回类型用 `Optional`）与 `import org.apache.parquet.schema.LogicalTypeAnnotation;`（visitor 接口与各 `XxxLogicalTypeAnnotation` 类型的命名空间）。原 `import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;` 保留（visitor 的 `visit(DecimalLogicalTypeAnnotation)` 仍引用该嵌套类型）。

## 小结

本次提交是 Iceberg Parquet 读取路径的一次结构性重构：把 `BaseParquetReaders.ReadBuilder.primitive(...)` 中基于已逐渐被弃用的 `OriginalType` 枚举的大 switch，迁移到基于 Parquet 官方 `LogicalTypeAnnotation.LogicalTypeAnnotationVisitor` 访问者模式的分派上。新增 113 行的 `LogicalTypeAnnotationParquetValueReaderVisitor` 内部类，为 String/Enum/Json/Decimal/Date/Time/Timestamp/Int/Bson 九种逻辑类型各实现一个 `visit` 方法，把原 switch 各 case 的 reader 构造逻辑原样搬过去；原 60+ 行 switch 被压缩为 6 行 `accept(...).orElseThrow(...)`。重构是"行为保持、结构升级"：所有逻辑类型的 reader 选择与老代码等价（包括 DECIMAL 的底层存储类型二次分派、INT→LONG 提升、Timestamp 带时区/不带时区区分、INT96 旧版时间戳读取路径完全不变），仅在两个细节上做了对齐——INT 系列用 `getBitWidth() == 64` 统一表达、Time/Timestamp 未支持单位统一走 `Optional.empty()` + `orElseThrow` 抛错。重构让 Iceberg 与 Parquet 上游 API 演进方向对齐（弃用 `OriginalType`、迁移到 `LogicalTypeAnnotation`），降低 `primitive(...)` 圈复杂度，并为未来扩展 NANOS 时间戳等新逻辑类型留好挂钩点（只需在 visitor 上加 `visit` 方法，无需再改 switch）。该重构不动运行时行为，是典型的"为可维护性与未来兼容性投资"的代码现代化工作。
