# 提交 0778：Spark: Coerce shorts and bytes into ints in Parquet Writer (#10349)

## 提交信息

- **序号**：0778 / 4088
- **哈希**：8d6bee736884575da7368e0963268d1cbe362d90
- **短哈希**：8d6bee736
- **日期**：2024-05-19 18:17:36 -0700
- **作者**：Shardul Mahadik
- **提交说明**：Spark: Coerce shorts and bytes into ints in Parquet Writer (#10349)
- **PR/Issue**：#10349

## 总体目的

本提交修复了通过 Spark `DataFrameWriterV2`（`df.writeTo(...)`）将 `byte`/`short` 类型列写入 Iceberg 表时，因 Parquet 写入器类型不匹配导致的写入失败/数据错误问题。修复方式是在 `SparkParquetWriters` 中根据 Spark 的 `DataType` 将 `ByteType`/`ShortType` 分别路由到专用的 `tinyints()`/`shorts()` 写入器，而非一律使用面向 `Integer` 的通用 `ints()` 写入器。同时为 Spark 3.3、3.4、3.5 三个版本各新增一个参数化测试 `TestDataFrameWriterV2Coercion`，覆盖 AVRO/ORC/PARQUET 三种格式与 byte/short 两种类型的组合。

## Bug 成因（详细解释）

### 类型映射背景

Iceberg 原生只提供 `IntegerType`（对应 Parquet `INT32`）与 `LongType`（对应 Parquet `INT64`）两种整型，并不存在独立的 8 位/16 位整型。当用户用一个 schema 为 `id byte, data string`（或 `id short`）的 Spark DataFrame 写入 Iceberg 表时，Spark-Iceberg 集成会把 Spark 的 `ByteType`/`ShortType` 列"向上提升"（widen）为 Iceberg 的 `IntegerType`，最终落盘为 Parquet 的 `INT32` 物理类型。这是一次隐式的类型强转（coercion）。

### 关键事实：Parquet 没有 INT8/INT16 物理类型

Parquet 格式只有 `INT32`/`INT64` 等物理类型，8 位/16 位整型是用 `INT32` 物理类型配合 `INTEGER(8,false)` / `INTEGER(16,false)` 逻辑类型注解来表示的。因此无论 Iceberg 列是 int、还是从 Spark byte/short 提升而来的 int，落到 `SparkParquetWriters.primitive(DataType sType, PrimitiveType primitive)` 时，`primitive` 的物理类型都是 `INT32`，命中同一个 `case INT32:` 分支。

### 缺陷代码

修复前，`SparkParquetWriters` 中 `case INT32:` 分支无条件返回 `ParquetValueWriters.ints(desc)`：

```java
case INT32:
  return ParquetValueWriters.ints(desc);
```

`ParquetValueWriters.ints(desc)` 返回的是 `UnboxedWriter<Integer>`，即泛型参数为 `Integer` 的写入器，它期望从 Spark 侧接收 `Integer` 类型的值。

### 问题爆发点

但 `primitive(DataType sType, PrimitiveType primitive)` 的第一个参数 `sType` 携带了 Spark 侧的真实列类型。当源列是 `ByteType` 时，Spark 的 `InternalRow` 取出的值是 `Byte`；当源列是 `ShortType` 时，取出的值是 `Short`。把这些 `Byte`/`Short` 值交给期望 `Integer` 的 `UnboxedWriter<Integer>` 处理时，会发生类型不匹配——典型表现为在写入器尝试将值作为 `Integer` 处理时抛出 `ClassCastException`（`Byte`/`Short` 不能当作 `Integer`），导致 `df.writeTo(...).createOrReplace()` 直接失败；即便不抛异常，也会因为未做正确的 `byte→int`、`short→int` 提升而产生错误数据。

简言之：**写入器是按 Parquet 物理类型（INT32 → Integer 写入器）选择的，但实际流经的值是按 Spark 源类型（Byte/Short）编码的，二者类型不匹配，缺少了一层 byte/short → int 的强转。** 这正是提交标题 "Coerce shorts and bytes into ints" 所指。

值得注意的是：`ParquetValueWriters` 早已提供了对应的专用写入器——`tinyints(desc)` 返回 `ByteWriter`（接收 `Byte`，内部 `writeInteger(repetitionLevel, value.intValue())`），`shorts(desc)` 返回 `ShortWriter`（接收 `Short`，同样 `writeInteger(repetitionLevel, value.intValue())`）。这两个写入器正是在做 byte/short → int 的强转。问题在于 `SparkParquetWriters` 没有根据 Spark `DataType` 把 byte/short 列路由到它们，而是一律用了 `ints()`。

## 如何达成设计目的

整体设计思路是：在 `case INT32:` 分支不再无脑返回通用 `ints()`，而是新增一个私有静态方法 `ints(DataType type, ColumnDescriptor desc)`，依据传入的 Spark `DataType` 做三路分发：

- `type instanceof ByteType` → `ParquetValueWriters.tinyints(desc)`（`ByteWriter`，接收 `Byte`，写 `value.intValue()`）
- `type instanceof ShortType` → `ParquetValueWriters.shorts(desc)`（`ShortWriter`，接收 `Short`，写 `value.intValue()`）
- 其它（含 `IntegerType`）→ `ParquetValueWriters.ints(desc)`（`UnboxedWriter<Integer>`，接收 `Integer`）

这样就把"按物理类型选写入器"细化为"按 Spark 源类型选写入器"，让 byte/short 值交给能正确接收并提升它们的 `ByteWriter`/`ShortWriter`，从而完成 byte/short → int 的强转。该修复同时应用到 Spark 3.3、3.4、3.5 三个版本分支（三份 `SparkParquetWriters.java` 改动完全一致），并各配一个参数化测试。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java`（及 v3.4、v3.5 同名文件）

**修改目的**：在 Parquet INT32 写入路径上按 Spark `DataType` 分发到 byte/short/int 专用写入器。

**工作逻辑**：

1. 新增两个 import：`org.apache.spark.sql.types.ByteType` 与 `org.apache.spark.sql.types.ShortType`，用于在分发方法中做 `instanceof` 判定。

2. 将 `case INT32:` 分支由
   ```java
   return ParquetValueWriters.ints(desc);
   ```
   改为
   ```java
   return ints(sType, desc);
   ```
   即把 Spark 源类型 `sType` 一并传入新方法。注意三份文件的改动行号略有差异（v3.3/v3.4 在 269 行，v3.5 在 268 行），但内容一致。

3. 新增私有静态分发方法：
   ```java
   private static PrimitiveWriter<?> ints(DataType type, ColumnDescriptor desc) {
     if (type instanceof ByteType) {
       return ParquetValueWriters.tinyints(desc);
     } else if (type instanceof ShortType) {
       return ParquetValueWriters.shorts(desc);
     }
     return ParquetValueWriters.ints(desc);
   }
   ```
   该方法复用底层 `ParquetValueWriters` 已有的 `tinyints()`/`shorts()`/`ints()` 工厂，不引入新的写入器实现，仅做路由。`ByteWriter`/`ShortWriter` 内部均通过 `value.intValue()` 把 byte/short 提升为 int 再调用 `column.writeInteger(...)`，正确写入 Parquet INT32。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWriterV2Coercion.java`（新增）

**修改目的**：为 Spark 3.3 验证 byte/short 列经 `DataFrameWriterV2` 写入后再读回的正确性。

**工作逻辑**：

- 基于 JUnit 4 参数化（`@RunWith(Parameterized.class)`），继承 `SparkTestBaseWithCatalog`。
- 参数矩阵为 6 组：`{AVRO, ORC, PARQUET} × {byte, short}`。
- `testByteAndShortCoercion()` 用 `jsonToDF("id " + dataType + ", data string", ...)` 构造两行 `{1,"a"}`、`{2,"b"}` 的 DataFrame，以指定 `write-format` 写入表，再 `select * from %s order by id` 读回，断言等于 ` ImmutableList.of(row(1, "a"), row(2, "b"))`。这同时覆盖了"写入不抛异常"与"读回值正确（即强转后 1/2 不变）"两点。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWriterV2Coercion.java`（新增）

**修改目的**：同上，面向 Spark 3.4。

**工作逻辑**：与 v3.3 版本完全一致（同样 JUnit 4 + `SparkTestBaseWithCatalog`，同样 6 组参数，同样测试体），逐字节相同。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWriterV2Coercion.java`（新增）

**修改目的**：同上，面向 Spark 3.5。

**工作逻辑**：因 v3.5 测试基座演进为 JUnit 5，该版本测试结构有所调整：

- 改用 `@ExtendWith(ParameterizedTestExtension.class)`，继承 `TestBaseWithCatalog`（而非 `SparkTestBaseWithCatalog`）。
- 参数矩阵额外携带 catalog 三元组（`SparkCatalogConfig.HADOOP` 的 catalogName/implementation/properties），通过 `parameter(FileFormat, String)` 辅助方法组装，`@Parameters` 名称模板为 `catalogName = {0}, implementation = {1}, config = {2}, format = {3}, dataType = {4}`。
- 用 `@Parameter(index = 3)` / `@Parameter(index = 4)` 注入 `format` 与 `dataType`，测试方法标注为 `@TestTemplate`。
- 测试体与 v3.3/v3.4 一致：构造两行 byte/short DataFrame → `writeTo` 指定格式写入 → 读回断言。

三份测试共同保证：在三种文件格式、两种窄整型下，`DataFrameWriterV2` 路径都能正确完成 byte/short → int 的强转。

## 小结

- **成效**：修复了 `DataFrameWriterV2` 写入 byte/short 列时因写入器类型不匹配导致的失败/错误，使 Spark 窄整型列能正确强转为 Iceberg int（Parquet INT32）落盘；补齐了三个 Spark 版本的回归测试，防止同类回归。
- **影响范围**：仅 Spark 模块（v3.3/v3.4/v3.5）的 `SparkParquetWriters` 写入路径，且仅在源类型为 `ByteType`/`ShortType` 时行为改变（路由到专用写入器），对 `IntegerType` 及其它类型无任何影响。`tinyints()`/`shorts()` 写入器本就存在于 `parquet` 模块，本次仅是正确调用它们。
- **回迁注意事项**：回迁到 1.4.x 时需对三个 Spark 版本分支同步应用，缺一不可（1.4.x 通常同时维护 v3.3/v3.4/v3.5）。三份 `SparkParquetWriters.java` 改动一致，但行号可能因分支上其它改动而偏移，建议按"新增 ByteType/ShortType import + 替换 INT32 分支 + 新增 ints 分发方法"三步语义化套用，而非依赖行号。测试文件为新增，需注意 v3.5 使用 JUnit 5 基座（`ParameterizedTestExtension`/`TestBaseWithCatalog`/`SparkCatalogConfig`），若 1.4.x 上这些基类签名有差异需相应调整。另需确认 1.4.x 上 `ParquetValueWriters.tinyints/shorts` 工厂方法已存在（它们位于 `parquet` 模块，历史较久，一般无虞）。
