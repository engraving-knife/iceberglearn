# 提交 1830：Parquet: Support unknown and timestamp(9) in internal model and generics (#12463)

## 提交信息

- **序号**：1830 / 4088
- **哈希**：afe0787b900978be0700b161a2fa308accf48704
- **短哈希**：afe0787b9
- **日期**：2025-03-06 11:35:49 -0800
- **作者**：Ryan Blue
- **提交说明**：Parquet: Support unknown and timestamp(9) in internal model and generics (#12463)
- **PR/Issue**：#12463

## 总体目的

本提交为 Iceberg 的 Parquet 读写模块增加了对 unknown 类型（`UnknownType`）和纳秒精度时间戳（`timestamp(9)` / `TimestampNanoType`）的支持。这是 Iceberg 类型系统扩展的重要一环：unknown 类型用于表示 schema 演化中被删除或尚未确定的字段，而 timestamp(9) 则是 Iceberg 新增的高精度时间戳类型。在此之前，Parquet 内部模型和 generic 读写器只支持 micros/millis 精度的时间戳，遇到 nanos 精度会抛出 `UnsupportedOperationException`，且无法跳过 unknown 类型的字段。

对于 unknown 类型，核心改动在 `TypeToMessageType`：当字段类型为 `TypeID.UNKNOWN` 时，`field()` 方法返回 null，上层 `struct()`/`schema()` 方法在添加字段时跳过 null 类型，从而实现"unknown 类型不写入数据文件"的语义。对于 list 和 map 的元素/键值类型，则通过 `Preconditions.checkArgument` 确保不为 null（因为集合元素不能是 unknown）。

对于 timestamp(9)，改动覆盖了 Parquet 读写链路的全流程：`TypeToMessageType` 将 `TimestampNanoType` 映射为 Parquet 的 `TIMESTAMP_NANOS`/`TIMESTAMPTZ_NANOS` 逻辑类型注解；`BaseParquetWriter`/`BaseParquetReaders` 将具体 reader/writer 的创建逻辑从基类下放到子类（generic 实现），并新增 `fromParquet` 方法把 Parquet 的 `TimeUnit.NANOS` 映射为 Java 的 `ChronoUnit.NANOS`；`GenericParquetReaders`/`GenericParquetWriter` 中的 `TimestampReader`/`TimestamptzReader`/`TimestampWriter`/`TimestamptzWriter` 增加 `ChronoUnit unit` 字段，根据实际精度进行读写而非硬编码 MICROS。

## 如何达成设计目的

整体设计思路是"将 reader/writer 工厂方法从基类下移到子类 + 精度参数化"。原先 `BaseParquetReaders`/`BaseParquetWriter` 中 `fixedReader`/`dateReader`/`timeReader`/`timestampReader`（及对应的 writer）是具体方法，直接实例化 `GenericParquetReaders`/`GenericParquetWriter` 的内部类，这导致基类绑定了 generic 实现，其他子类无法自定义。本提交将这些方法改为 `abstract`，由 `GenericParquetReaders`/`GenericParquetWriter` 各自实现，从而使基类只负责调度、子类负责实例化。在 generic 子类的 `timestampReader`/`timestampWriter` 实现中，通过 switch 处理 NANOS/MICROS/MILLIS 三种精度，nanos 精度使用 `ChronoUnit.NANOS` 构造 reader/writer。同时，`timestampReader` 的 `isAdjustedToUTC` 参数来源从 `expected` 类型改为 `timestampLogicalType.isAdjustedToUTC()`，直接从 Parquet schema 获取，避免与 Iceberg schema 不一致。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetReaders.java` (修改)

**修改目的**：将 `fixedReader`/`dateReader`/`timeReader`/`timestampReader` 从具体方法改为抽象方法，并将 `isAdjustedToUTC` 来源改为从 Parquet 逻辑类型注解获取。

**工作逻辑**：删除了基类中四个方法的实现体（原本直接 new GenericParquetReaders 的内部类），改为 `protected abstract` 声明。在 `TimestampLogicalTypeAnnotation` 的 visit 方法中，原来通过 `((Types.TimestampType) expected).shouldAdjustToUTC()` 获取是否调整 UTC，改为 `timestampLogicalType.isAdjustedToUTC()`，直接从 Parquet schema 的逻辑类型注解获取，这样即使 expected 类型与实际 Parquet 类型不完全对应也能正确判断。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetWriter.java` (修改)

**修改目的**：将 `fixedWriter`/`dateWriter`/`timeWriter`/`timestampWriter` 从具体方法改为抽象方法，并放宽 timestamp 精度校验。

**工作逻辑**：四个 writer 工厂方法改为 `abstract`。在 `TimestampLogicalTypeAnnotation` 的 visit 方法中，校验从 "只允许 MICROS" 改为 "不允许 MILLIS"（即允许 MICROS 和 NANOS），错误消息相应更新为 "only MICROS and NANOS are supported"。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/GenericParquetReaders.java` (修改)

**修改目的**：实现基类的抽象 reader 工厂方法，并增加 nanos 精度支持。

**工作逻辑**：

1. 实现 `fixedReader`/`dateReader`/`timeReader`/`timestampReader` 四个方法，逻辑与原基类实现基本一致，但 `timestampReader` 新增 `NANOS` 分支：nanos 精度使用 `new TimestamptzReader(desc, ChronoUnit.NANOS)` 或 `new TimestampReader(desc, ChronoUnit.NANOS)`。

2. `TimestampReader`/`TimestamptzReader` 类增加 `ChronoUnit unit` 字段，构造函数接受 unit 参数，`read()` 方法用 `EPOCH.plus(column.nextLong(), unit)` 代替原来硬编码的 `ChronoUnit.MICROS`。

3. 多个内部 reader 类的访问修饰符从 `static`（包级可见）改为 `private static`，收紧可见性。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/GenericParquetWriter.java` (修改)

**修改目的**：实现基类的抽象 writer 工厂方法，并增加 nanos 精度支持。

**工作逻辑**：

1. 实现 `fixedWriter`/`dateWriter`/`timeWriter`/`timestampWriter` 四个方法。`timestampWriter` 通过 `fromParquet(unit)` 将 Parquet 的 `TimeUnit` 转为 `ChronoUnit`，再构造 `TimestampWriter`/`TimestamptzWriter`。

2. `TimestampWriter`/`TimestamptzWriter` 类增加 `ChronoUnit unit` 字段，`write()` 方法用 `unit.between(EPOCH, value)` 代替原来硬编码的 `ChronoUnit.MICROS.between(...)`。

3. 新增 `fromParquet(LogicalTypeAnnotation.TimeUnit unit)` 私有方法：MICROS→ChronoUnit.MICROS，NANOS→ChronoUnit.NANOS，其他抛 `UnsupportedOperationException`。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReaders.java` (修改)

**修改目的**：在 `UnboxedReader` 的工厂逻辑中为 NANOS 精度返回默认 reader。

**工作逻辑**：在 switch 语句的 `MICROS` case 下方新增 `case NANOS:`，与 MICROS 共用 `return new UnboxedReader<>(desc)`，使 nanos 时间戳在非 generic 路径下也能被读取为 long 值。

### `parquet/src/main/java/org/apache/iceberg/parquet/TypeToMessageType.java` (修改)

**修改目的**：支持 unknown 类型字段跳过和 timestamp(9) 到 Parquet 类型的映射。

**工作逻辑**：

1. 新增 `TIMESTAMP_NANOS` 和 `TIMESTAMPTZ_NANOS` 两个静态常量（`LogicalTypeAnnotation.timestampType(..., TimeUnit.NANOS)`）。

2. `field()` 方法：在判断 `field.type().isPrimitiveType()` 之前，新增 `if (field.type().typeId() == TypeID.UNKNOWN) return null;`，使 unknown 类型字段返回 null。

3. `schema()` 和 `struct()` 方法：遍历字段时先调用 `field(field)` 取得类型，若为 null 则跳过不添加（注释 "unknown type is not written to data files"）。

4. `list()`/`map()` 方法：对元素/键值类型调用 `field()` 后用 `Preconditions.checkArgument` 确保不为 null（集合元素不能是 unknown）。

5. `primitive()` 方法新增 `case TIMESTAMP_NANO:`，根据 `shouldAdjustToUTC()` 映射为 `TIMESTAMPTZ_NANOS` 或 `TIMESTAMP_NANOS` 的 INT64 原始类型。

### `parquet/src/test/java/org/apache/iceberg/data/parquet/TestGenericData.java` (修改)

**修改目的**：启用 generic data 测试对 unknown 类型和 timestamp nanos 的支持。

**工作逻辑**：新增 `supportsUnknown()` 和 `supportsTimestampNanos()` 两个 override 方法返回 true，使基类 `DataTest` 中对应的测试用例在本测试类中执行。同时将 `supportsDefaultValues()` 的 override 位置移到类顶部。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestInternalParquet.java` (修改)

**修改目的**：启用内部 Parquet 测试对 unknown 类型和 timestamp nanos 的支持。

**工作逻辑**：同上，新增 `supportsUnknown()` 和 `supportsTimestampNanos()` 返回 true。

## 小结

本提交为 Parquet 模块补全了 unknown 类型和 timestamp(9) 的读写支持，核心设计是将 reader/writer 工厂方法从基类下移到子类实现，并通过 `ChronoUnit` 参数化时间戳精度。改动涉及 parquet 模块的 7 个文件，影响面较广但设计清晰。回迁到 1.4.x 时需注意：1.4.x 中若 `BaseParquetReaders`/`BaseParquetWriter` 有其他子类（如 Spark 专用的 reader/writer），这些子类需要同步实现新增的抽象方法；`TypeToMessageType` 对 unknown 类型的跳过逻辑可能影响 schema 转换的兼容性，需验证现有读写流程不受影响。
