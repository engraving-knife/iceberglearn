# 提交 3076：Data: Handle TIMESTAMP_NANO in InternalRecordWrapper (#14974)

## 提交信息

- **序号**：3076 / 4088
- **哈希**：1dce77c788a761f74e7ff8a4f5ce2d0a75b06359
- **短哈希**：1dce77c78
- **日期**：2026-01-07
- **作者**：Ayush Saxena
- **提交说明**：Data: Handle TIMESTAMP_NANO in InternalRecordWrapper (#14974)
- **PR/Issue**：#14974

## 总体目的

Iceberg 在类型系统中引入了纳秒级时间戳类型 `TIMESTAMP_NANO`（`Types.TimestampNanoType`），用于支持比微秒更高精度的时间戳。然而 `InternalRecordWrapper`（位于 `data` 模块中，负责将上层引擎的记录对象包装为 Iceberg 内部 `StructLike` 接口的核心适配器）此前只处理了 `TIMESTAMP`（微秒级）类型，缺少对 `TIMESTAMP_NANO` 的 case 分支。

这意味着当表 schema 中包含纳秒时间戳字段时，`InternalRecordWrapper` 在构建字段访问器（accessor）的 switch 语句中会落入默认分支，无法正确地将 `LocalDateTime`/`OffsetDateTime` 转换为 Iceberg 内部存储的 long 纳秒值，导致读写纳秒时间戳数据时出现类型转换错误或数据损坏。本提交通过在 `InternalRecordWrapper` 中新增 `TIMESTAMP_NANO` 分支，补齐了这一能力缺口，使数据模块完整支持纳秒时间戳的记录包装。

此外，本提交还完善了测试覆盖：在 `RecordWrapperTestBase` 中新增了带时区和不带时区的纳秒时间戳测试 schema 与测试方法，并新建了 `TestInternalRecordWrapper` 具体测试类。对于 Spark（v3.4/v3.5/v4.0/v4.1）和 Flink（v1.20/v2.0/v2.1）各版本，由于这些引擎本身尚不支持纳秒时间戳类型，相关测试方法被标注为 `@Disabled` 并附说明，避免在不兼容的引擎上运行失败。

## 如何达成设计目的

整体思路是在 `InternalRecordWrapper` 的类型转换 switch 中新增 `TIMESTAMP_NANO` 分支，根据 `Types.TimestampNanoType.shouldAdjustToUTC()` 判断是否带时区，分别调用 `DateTimeUtil.nanosFromTimestamptz`（带时区，参数为 `OffsetDateTime`）和 `DateTimeUtil.nanosFromTimestamp`（不带时区，参数为 `LocalDateTime`）完成转换。测试侧通过在公共基类 `RecordWrapperTestBase` 增加测试用例，让各引擎子类继承后按需禁用不支持的场景，实现一次编写、多引擎复用。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/InternalRecordWrapper.java` (+6/-0 lines)

**修改目的**：为 `InternalRecordWrapper` 的字段访问器 switch 语句新增 `TIMESTAMP_NANO` 类型处理分支。

**工作逻辑**：
在 `accessorFor` 方法的 switch 中，紧随 `TIMESTAMP` 分支之后新增 `case TIMESTAMP_NANO`。通过 `((Types.TimestampNanoType) type).shouldAdjustToUTC()` 判断时间戳是否需要调整为 UTC（即是否带时区）：若带时区则返回 lambda `timestamp -> DateTimeUtil.nanosFromTimestamptz((OffsetDateTime) timestamp)`，将 `OffsetDateTime` 转为纳秒；若不带时区则返回 `timestamp -> DateTimeUtil.nanosFromTimestamp((LocalDateTime) timestamp)`，将 `LocalDateTime` 转为纳秒。这与 `TIMESTAMP` 分支处理微秒的逻辑完全对称，只是调用的转换方法不同（`nanos*` vs `micros*`）。

### `data/src/test/java/org/apache/iceberg/RecordWrapperTestBase.java` (+20/-0 lines)

**修改目的**：在测试基类中新增纳秒时间戳的 schema 定义和对应测试方法。

**工作逻辑**：
新增两个 `Types.StructType` 常量 `TIMESTAMP_NS_WITHOUT_ZONE` 和 `TIMESTAMP_NS_WITH_ZONE`，分别由两个 `Types.TimestampNanoType.withoutZone()` 和 `withZone()` 字段组成（字段 id 复用 101、102，与微秒版本一致）。新增 `testTimestampNanoWithoutZone()` 和 `testTimestampNanoWithZone()` 两个 `@Test` 方法，各自调用 `generateAndValidate(new Schema(...))` 走子类实现的校验逻辑。这样所有继承 `RecordWrapperTestBase` 的引擎测试类默认都会尝试运行这两个测试，不支持的引擎可在子类中 override 并标注 `@Disabled`。

### `data/src/test/java/org/apache/iceberg/TestInternalRecordWrapper.java` (+40/-0 lines, 新文件)

**修改目的**：为 `InternalRecordWrapper` 创建具体的测试实现类。

**工作逻辑**：
新建 `TestInternalRecordWrapper` 继承 `RecordWrapperTestBase`，实现 `generateAndValidate` 方法：使用 `RandomGenericData.generate(schema, 1, 101L)` 生成随机记录，用 `new InternalRecordWrapper(schema.asStruct()).wrap(record)` 包装，然后遍历每个字段断言非 null 值的类型与 schema 中声明的类型 `typeId().javaClass()` 一致。此前该基类只有 Flink/Spark 的具体实现，缺少针对 data 模块自身 `InternalRecordWrapper` 的直接测试，本提交一并补齐。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/TestRowDataWrapper.java` (+13/-0 lines)

**修改目的**：在 Flink v1.20 的 `TestRowDataWrapper` 中禁用纳秒时间戳测试。

**工作逻辑**：
导入 `@Disabled` 注解，override `testTimestampNanoWithoutZone()` 和 `testTimestampNanoWithZone()` 并标注 `@Disabled`，注释说明 "Flink does not support nanosecond timestamp without/with zone."。因为 Flink 的类型系统尚不支持纳秒精度时间戳，运行这些测试会失败，故显式跳过。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/TestRowDataWrapper.java` (+13/-0 lines)

**修改目的**：同上，为 Flink v2.0 禁用纳秒时间戳测试。逻辑与 v1.20 完全一致。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/TestRowDataWrapper.java` (+13/-0 lines)

**修改目的**：同上，为 Flink v2.1 禁用纳秒时间戳测试。逻辑与 v1.20 完全一致。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestInternalRowWrapper.java` (+12/-0 lines)

**修改目的**：在 Spark v3.4 的 `TestInternalRowWrapper` 中禁用纳秒时间戳测试。

**工作逻辑**：
导入 `@Disabled`，override 两个纳秒时间戳测试方法并标注 `@Disabled`，注释说明 "Spark does not support nanosecond timestamp without/with zone."。Spark 的 `TimestampType` 仅支持微秒精度，因此跳过。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestInternalRowWrapper.java` (+12/-0 lines)

**修改目的**：同上，为 Spark v3.5 禁用纳秒时间戳测试。逻辑与 v3.4 一致。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestInternalRowWrapper.java` (+12/-0 lines)

**修改目的**：同上，为 Spark v4.0 禁用纳秒时间戳测试。逻辑与 v3.4 一致。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestInternalRowWrapper.java` (+12/-0 lines)

**修改目的**：同上，为 Spark v4.1 禁用纳秒时间戳测试。逻辑与 v3.4 一致。

## 总结

本提交为 Iceberg 数据模块的 `InternalRecordWrapper` 补齐了纳秒级时间戳（`TIMESTAMP_NANO`）类型的转换支持，使 Iceberg 在内部记录包装层完整支持纳秒精度。同时通过公共测试基类新增测试用例并新建 data 模块的直接测试类，对 Flink 和 Spark 各版本因引擎自身限制标注 `@Disabled`，体现了对多引擎兼容性的审慎处理。这是 Iceberg 全面落地 `TIMESTAMP_NANO` 类型支持的重要一环。
