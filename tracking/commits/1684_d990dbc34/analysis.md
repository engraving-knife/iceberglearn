# 提交 1684 d990dbc34 分析

## 提交信息
- 哈希：d990dbc34ed882dc6b46c549997d55ee8669093e
- 日期：2025-02-05 09:36:53 +0100
- 作者：Maximilian Michels
- 消息：Flink: Add null check to writers to prevent resurrecting null values (#12049)

## 总体目的

本提交修复一个 Flink 集成中的数据正确性 bug：当 Flink 的 `BinaryRowData` 中某个 required（非空）字段实际为 null 时，Iceberg 的 Flink 写入器不会报错，而是把底层未初始化的随机字节当作真实值写入，导致 null 值被"复活"成垃圾数据。这会污染数据文件且难以察觉。

根因在于 Flink 自带的 `RowData.createFieldGetter(LogicalType, int)` 方法对非空（non-nullable）类型不做 null 检查。`BinaryRowData` 用 null 标志位记录字段是否为 null，但 Flink 的 field getter 在类型声明为非空时会直接跳过 `isNullAt` 检查、按偏移读取字节。如果上游由于 bug 或边界情况给一个 required 字段塞了 null，field getter 就会把 null 标志位后面的未定义字节解析成具体值（如把随机字节读成一个 long），最终被 Iceberg 写入器当作合法值写入 Parquet/ORC/Avro 文件。

提交作者引用了 Flink JIRA FLINK-37245。修复方式是引入一个包装方法 `FlinkRowData.createFieldGetter()`，在委托给 Flink 原 field getter 之前先做显式 null 检查：当字段类型非空且 `rowData.isNullAt(fieldPos)` 为真时直接返回 null，让 null 正确传播到写入器，写入器随后会对 required 字段抛 NPE（快速失败），而不是写入垃圾数据。

## 如何达成设计目的

设计思路是"统一入口 + 包装拦截"：

1. 新建工具类 `FlinkRowData`，提供静态方法 `createFieldGetter(LogicalType, int)`，内部先用 `RowData.createFieldGetter` 创建 Flink 原生 getter，再返回一个 lambda：当 `!fieldType.isNullable() && rowData.isNullAt(fieldPos)` 时返回 null，否则调用原生 getter。
2. 把 Iceberg Flink 集成中所有调用 `RowData.createFieldGetter(...)` 的位置统一替换为 `FlinkRowData.createFieldGetter(...)`，覆盖 Parquet/ORC/Avro 三种写入器、`RowDataWrapper`、`RowDataProjection`、`RowDataRecordFactory`。
3. 同一份改动同时应用到 Flink v1.18、v1.19、v1.20 三个版本目录（Iceberg 为每个支持的 Flink 大版本维护一份代码副本）。
4. 在 `DataTest` 基类新增测试 `testWriteNullValueForRequiredType`，构造一个 required 字段为 null 的记录，断言写入应抛异常（除非实现显式允许）。各子类（avro/parquet/flink parquet/flink orc 等）实现新的 `writeAndValidate(Schema, List<Record>)` 重载，并在 `TestIcebergSink` 增加端到端 sink 测试。

### 修改详情

#### flink/v{1.18,1.19,1.20}/flink/src/main/java/org/apache/iceberg/flink/FlinkRowData.java（新增，3 份相同副本）
新增工具类。核心方法：
```
public static RowData.FieldGetter createFieldGetter(LogicalType fieldType, int fieldPos) {
  RowData.FieldGetter flinkFieldGetter = RowData.createFieldGetter(fieldType, fieldPos);
  return rowData -> {
    if (!fieldType.isNullable() && rowData.isNullAt(fieldPos)) {
      return null;
    }
    return flinkFieldGetter.getFieldOrNull(rowData);
  };
}
```
注释明确说明 Flink 的 `RowData.createFieldGetter` 不对可选/可空类型做 null 检查，没有这个显式检查，`BinaryRowData` 的 null 标志位会被忽略，随机字节会被解析成实际值，产生错误写入而非 NPE。

#### flink/v{1.18,1.19,1.20}/.../data/FlinkParquetWriters.java
`FlinkParquetWriters` 内部 `StructureWriter`（或类似 struct 写入器）构造时，把 `RowData.createFieldGetter(types.get(i), i)` 替换为 `FlinkRowData.createFieldGetter(types.get(i), i)`，新增对应 import。

#### flink/v{1.18,1.19,1.20}/.../data/FlinkOrcWriters.java
同理，struct writer 的 `fieldGetters` 列表构造改用 `FlinkRowData.createFieldGetter`。

#### flink/v{1.18,1.19,1.20}/.../data/FlinkValueWriters.java
Avro ValueWriter 的 struct writer `getters` 数组构造改用 `FlinkRowData.createFieldGetter`。

#### flink/v{1.18,1.19,1.20}/.../flink/RowDataWrapper.java
`RowDataWrapper.get(int pos, Class<?> javaClass)` 中当 getters 未预生成时，临时创建 getter 改用 `FlinkRowData.createFieldGetter`。

#### flink/v{1.18,1.19,1.20}/.../data/RowDataProjection.java
`createFieldGetter` 方法在 STRUCT、LIST、default 三个分支中都把 `RowData.createFieldGetter` 替换为 `FlinkRowData.createFieldGetter`。

#### flink/v{1.18,1.19,1.20}/.../source/reader/RowDataRecordFactory.java
`createFieldGetters(RowType)` 静态方法中循环替换为 `FlinkRowData.createFieldGetter`。

#### data/src/test/java/org/apache/iceberg/data/DataTest.java
- 新增抽象方法 `writeAndValidate(Schema schema, List<Record> data)`，允许子类用指定数据（而非随机数据）测试写入。
- 新增 `allowsWritingNullValuesForRequiredFields()` 方法，默认返回 false，允许 ORC 等不区分 required 的实现返回 true。
- 新增测试 `testWriteNullValueForRequiredType`：构造 schema 有两个 required 字段（id: long, string: string），记录中 string 设为 null。若 `allowsWritingNullValuesForRequiredFields()` 为真则期望写入成功，否则期望抛异常（NPE 或 IllegalArgumentException）。

#### data/src/test/java/org/apache/iceberg/data/avro/TestGenericData.java
实现新的 `writeAndValidate(Schema, List<Record>)` 重载，把原有 `writeAndValidate(Schema, Schema)` 重构为委托给私有三参版本，复用同一套写入逻辑。

#### data/src/test/java/org/apache/iceberg/data/parquet/TestGenericData.java
同 avro，重构为支持传入指定数据。

#### data/src/test/java/org/apache/iceberg/data/orc/TestGenericData.java
重写 `allowsWritingNullValuesForRequiredFields()` 返回 true（因 ORC 写入器没有 required 字段的概念），并实现新的 `writeAndValidate` 重载。

#### flink/v1.19, v1.20/.../sink/TestIcebergSink.java
新增端到端测试 `testErrorOnNullForRequiredField`：用 ORC 之外格式建表（schema 含两个 required 字段），写入 `Row.of(42, null)`，执行 Flink job 断言抛出 `NullPointerException`（`hasRootCauseInstanceOf`）。ORC 格式用 `Assume.assumeFalse` 跳过。

#### flink/v{1.18,1.19,1.20}/.../data/TestFlinkParquetWriter.java
重写 `writeAndValidate(Schema, List<Record>)`：用 `RowDataSerializer.toBinaryRow` 把记录转成 `BinaryRowData`，专门触发 `BinaryRowData` 的 null 标志位路径，确保测试覆盖到 bug 场景。

#### flink/v{1.18,1.19,1.20}/.../data/TestFlinkParquetReader.java
补充 `writeAndValidate(Schema, List<Record>)` 重载委托。

#### flink/v{1.18,1.19,1.20}/.../data/TestFlinkOrcReaderWriter.java
补充相应重载。

#### flink/v{1.18,1.19,1.20}/.../data/AbstractTestFlinkAvroReaderWriter.java
补充相应重载。

#### flink/v{1.18,1.19,1.20}/.../flink/TestHelpers.java
简化/调整断言辅助方法（减少重复）。

#### .../parquet/TestParquetEncryptionWithWriteSupport.java
小幅适配新签名。

## 小结

成效：修复了一个严重的数据正确性 bug——required 字段的 null 值不再被"复活"成垃圾数据，而是快速失败抛 NPE。修复覆盖 Parquet、ORC、Avro 三种格式和 Flink v1.18/1.19/1.20 三个版本，并补充了从单元测试到端到端 sink 测试的多层测试。影响范围主要在 Flink 集成模块和 data 测试基类，core 写入逻辑不变。

回迁到 1.4.x 的注意事项：这是一个独立的 bug 修复，依赖较少，原则上可干净 cherry-pick。需注意：1) 1.4.x 支持的 Flink 版本目录（如 v1.17/1.18/1.19/1.20）与 main 可能不同，cherry-pick 时需按 1.4.x 实际存在的版本目录调整，漏掉某个版本目录会留下未修复的版本。2) `DataTest` 基类新增的抽象方法会强制所有子类实现，需确保 1.4.x 中所有 `DataTest` 子类都补上 `writeAndValidate(Schema, List<Record>)` 重载，否则编译失败。3) `BinaryRowData`/`RowDataSerializer` 相关测试依赖 Flink 版本是否提供这些 API，需确认 1.4.x 对应 Flink 版本兼容。4) 该修复有数据正确性影响，建议优先回迁。
