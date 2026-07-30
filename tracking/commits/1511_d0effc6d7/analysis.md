# 提交序号 1511 短哈希 d0effc6d7 分析

## 提交信息
- 哈希：d0effc6d73c3187983105b5df14d889ff0283f09
- 日期：2024-12-18
- 作者：Ryan Blue <blue@apache.org>
- 消息：Data: Fix Parquet and Avro defaults date/time representation (#11811)

## 总体目的

本提交修复了在读取旧数据文件时填充字段默认值时，日期/时间类型默认值表示不正确的问题。这是对前一个提交（1510，为 Spark Parquet 读取器添加默认值支持）的后续修复与扩展：将默认值支持推广到 Avro 读取器和通用数据模型（generic data model），并修复日期/时间类型默认值在从 Iceberg 内部表示转换到目标数据模型时缺失的转换步骤。

问题根因在于：Iceberg 在 schema 中存储的默认值使用的是 Iceberg 内部表示——日期（DATE）存储为自 epoch 起的天数（Integer），时间（TIME）和时间戳（TIMESTAMP）存储为微秒（Long），FIXED 存储为 ByteBuffer。但各读取器输出的目标数据模型对日期/时间有不同的表示：通用数据模型使用 `LocalDate`/`LocalTime`/`LocalDateTime`/`OffsetDateTime`，Spark 使用自己的内部类型。前一个提交（1510）在填充默认值时直接使用了内部表示值（如天数 Integer），未经转换就传给目标模型，导致日期/时间字段读出的默认值是错误的（例如读到天数整数而非日期对象）。

本提交通过引入一个转换层来解决此问题：在 Avro 的 `ValueReaders.buildReadPlan` 中新增一个 `convert` 函数参数，在 Parquet 的 `BaseParquetReaders` 中新增一个可覆写的 `convertConstant` 方法，使得默认值在填充前先从内部表示转换为目标数据模型表示。同时新增 `GenericDataUtil.internalToGeneric` 工具方法提供通用数据模型的转换，并将 Spark 的 `convertConstant` 重命名为更清晰的 `internalToSpark` 并补充 UUID 转换。

此外，本提交还将默认值相关测试从 Spark 专有测试类上提到共享的 `DataTest`/`AvroDataTest` 基类，使通用数据模型和 Spark 数据模型都能复用同一套测试用例。

## 如何达成设计目的

本提交通过三类改动达成目的：（1）在 Avro 和 Parquet 读取器中引入默认值转换钩子；（2）提供通用数据模型和 Spark 的具体转换实现；（3）将默认值测试上提到共享基类。整体设计采用"模板方法 + 函数式注入"模式，让基类读取器决定何时转换，子类/调用方决定如何转换。

### 修改详情

#### core/src/main/java/org/apache/iceberg/avro/ValueReaders.java

Avro 读取器的读取计划构建逻辑。修改要点：

1. 新增 `buildReadPlan` 重载，接受一个 `BiFunction<Type, Object, Object> convert` 参数。原有的四参数 `buildReadPlan` 方法委托给新方法，传入恒等函数 `(type, value) -> value` 作为 convert，保持向后兼容。

2. 在读取计划中，当字段不在文件中但定义了 `initialDefault()` 时，原先直接用 `ValueReaders.constant(field.initialDefault())`，现改为 `ValueReaders.constant(convert.apply(field.type(), field.initialDefault()))`，即在填充前通过 convert 函数将默认值从内部表示转换为目标模型表示。

这一改动使得 Avro 读取器能在填充默认值时进行必要的类型转换，而具体转换逻辑由调用方注入。

#### core/src/main/java/org/apache/iceberg/data/GenericDataUtil.java（新增）

新增通用数据模型工具类，核心方法 `internalToGeneric(Type type, Object value)` 将 Iceberg 内部表示转为通用数据模型表示：
- DATE：`Integer`（天数）→ `LocalDate`（via `DateTimeUtil.dateFromDays`）
- TIME：`Long`（微秒）→ `LocalTime`（via `DateTimeUtil.timeFromMicros`）
- TIMESTAMP：`Long`（微秒）→ `LocalDateTime` 或 `OffsetDateTime`（根据是否带时区，via `DateTimeUtil.timestampFromMicros`/`timestamptzFromMicros`）
- FIXED：`ByteBuffer` → `byte[]`（via `ByteBuffers.toByteArray`）
- 其它类型：原值返回

这些转换覆盖了通用数据模型与 Iceberg 内部表示存在差异的所有类型。

#### core/src/main/java/org/apache/iceberg/data/avro/PlannedDataReader.java

通用数据模型的 Avro 读取器。修改为调用新的五参数 `buildReadPlan`，传入 `GenericDataUtil::internalToGeneric` 作为 convert 函数，使 Avro 读取的默认值能正确转为通用数据模型的日期/时间表示。

#### parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetReaders.java

Parquet 读取器的抽象基类。修改要点：

1. 新增可覆写方法 `convertConstant(Type type, Object value)`，默认实现为恒等（直接返回 value）。这是模板方法模式——基类定义转换点，子类可覆写以提供具体转换。

2. 在构建读取计划时，填充默认值处由原先的 `field.initialDefault()` 改为 `convertConstant(field.type(), field.initialDefault())`，使子类能介入转换。

#### parquet/src/main/java/org/apache/iceberg/data/parquet/GenericParquetReaders.java

通用数据模型的 Parquet 读取器，继承自 `BaseParquetReaders`。覆写 `convertConstant` 方法，委托给 `GenericDataUtil.internalToGeneric`，使 Parquet 读取的默认值同样能正确转为通用数据模型表示。

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkUtil.java

Spark 工具类。修改要点：

1. 将方法 `convertConstant` 重命名为 `internalToSpark`，名称更清晰地表达"从 Iceberg 内部表示转为 Spark 表示"的语义。递归调用处同步更名。

2. 在 STRING 类型的处理中新增 `case UUID:`，使 UUID 类型也走 UTF8String 转换路径（与 STRING 合并处理），补全了前一个提交中遗漏的 UUID 默认值转换。

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetReaders.java

Spark Parquet 读取器。将默认值填充处的 `SparkUtil.convertConstant` 调用改为 `SparkUtil.internalToSpark`，跟随方法重命名。

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkPlannedAvroReader.java

Spark Avro 读取器。修改为调用新的五参数 `buildReadPlan`，传入 `SparkUtil::internalToSpark` 作为 convert 函数，使 Spark 的 Avro 读取也能正确转换默认值。新增 `SparkUtil` 的 import。

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java

Spark 读取器基类。将 `constantsMap` 中对 `SparkUtil::convertConstant` 的引用改为 `SparkUtil::internalToSpark`，跟随方法重命名。

#### 测试重构：data/src/test/java/org/apache/iceberg/data/DataTest.java

通用数据模型的测试基类。新增默认值相关测试方法（`testMissingRequiredWithoutDefault`、`testDefaultValues`、`testNullDefaultValue`、`testNestedDefaultValue`、`testMapNestedDefaultValue` 等），这些方法原本只在 Spark 的 `TestSparkParquetReader` 中存在，现上提为共享测试。新增：
- `writeAndValidate(Schema writeSchema, Schema expectedSchema)` 重载方法（默认抛 `UnsupportedEncodingException`，子类按需实现），支持"用旧 schema 写、用新 schema 读"的测试。
- `supportsDefaultValues()` 标志方法（默认 false），子类覆写为 true 以启用默认值测试。测试方法用 `Assumptions.assumeThat(supportsDefaultValues()).isTrue()` 进行条件门控。

#### 测试重构：spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/AvroDataTest.java

Spark Avro 数据测试基类，类似 `DataTest`，新增同样的默认值测试方法和支持方法，使 Spark 的 Avro 读取器也运行默认值测试。

#### 测试重构：其余测试文件

- `data/src/test/java/org/apache/iceberg/data/avro/TestGenericData.java` 和 `data/src/test/java/org/apache/iceberg/data/parquet/TestGenericData.java`：各减少约 222 行，移除了上提到 `DataTest` 的重复测试方法，改为继承共享测试并覆写 `supportsDefaultValues()` 返回 true、实现 `writeAndValidate(Schema, Schema)`。
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkParquetReader.java`：减少约 204 行，同样移除上提的测试方法。
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkAvroReader.java`：调整以继承 `AvroDataTest` 的默认值测试。
- `core/src/test/java/org/apache/iceberg/data/DataTestHelpers.java` 和 `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java`：小幅调整断言辅助方法，支持写入 schema 与读取 schema 不一致场景下的期望值计算（对于读取时新增的默认值列，用 `field.initialDefault()` 作为期望值）。

## 小结

本提交修复了默认值填充中日期/时间类型表示不正确的问题，并将默认值支持从 Spark Parquet 读取器扩展到 Avro 读取器和通用数据模型。核心设计是在 Avro 的 `buildReadPlan` 中引入 convert 函数参数、在 Parquet 的 `BaseParquetReaders` 中引入可覆写的 `convertConstant` 方法，形成转换钩子；然后通过新增的 `GenericDataUtil.internalToGeneric` 和重命名的 `SparkUtil.internalToSpark` 提供具体转换实现，覆盖 DATE/TIME/TIMESTAMP/FIXED 等需要转换的类型。同时将默认值测试上提到 `DataTest`/`AvroDataTest` 共享基类，消除了 Spark 专有测试中的重复，并通过 `supportsDefaultValues()` 标志实现条件门控。这一提交与前一个提交（1510）共同构成了 Iceberg 对 schema 演进中字段默认值的完整读取支持。
