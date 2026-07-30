# 提交 0337：Parquet: Support reading INT96 column in row group filter (#8988)

## 提交信息

- **序号**：0337
- **哈希**：4a996783697faa1c0bd6b4fc6ceb97260f6aeb7a
- **短哈希**：4a9967836
- **日期**：2024-01-08 11:08:07 +0100
- **作者**：Manu Zhang
- **提交说明**：Parquet: Support reading INT96 column in row group filter (#8988)
- **PR/Issue**：#8988

## 总体目的

本提交修复 Iceberg 的 Parquet 字典行组过滤器（`ParquetDictionaryRowGroupFilter`）在面对 Parquet 旧版 INT96 时间戳列时无法正常下推过滤的缺陷。INT96 是 Parquet 早期（Impala、Spark 2.x 及更早 Hive 等引擎）用于存储时间戳的 12 字节原始类型，编码格式为：8 字节小端序存储"一天内的纳秒数" + 4 字节小端序存储"Julian Day"。虽然 Iceberg 自身写入时不会主动产出 INT96 列（默认使用 INT64 + TIMESTAMP_MICROS/TIMESTAMP_MILLIS 逻辑类型），但 Iceberg 支持通过 `SparkTableUtil.importSparkTable` 等机制把外部引擎写出的 Parquet 表导入为 Iceberg 表——这些外部表中的时间戳列很可能仍是 INT96 编码（如本提交新增/扩展的测试场景，显式设置 `SQLConf.PARQUET_OUTPUT_TIMESTAMP_TYPE() = INT96` 后写出的 Parquet 表）。

问题出在 `ParquetDictionaryRowGroupFilter` 的字典解码逻辑：`EvalVisitor.dict(...)` 方法在按 Parquet 原始类型分派时，只处理了 `INT32/INT64/FLOAT/DOUBLE/BINARY/FIXED_LEN_BYTE_ARRAY`，遇到 INT96 直接落入 `default` 分支抛出 `IllegalArgumentException("Cannot decode dictionary of type: INT96")`。这意味着：当用户对一张含 INT96 时间戳列的导入表执行带时间戳谓词的查询（如 `WHERE ts > '2000-01-31 08:30:00'`），Iceberg 在评估 row group 字典时直接抛异常，整个查询失败——即使该列确实使用了字典编码、本可受益于字典下推过滤。值得注意的是，Iceberg 的 `BaseParquetReaders.primitive(...)` 早已通过 `TimestampInt96Reader` 支持了 INT96 列的常规读取，但字典下推过滤路径却遗漏了对 INT96 的处理，形成读取路径与过滤路径的能力不对称。

本提交补齐这一缺口，让 INT96 列也能被字典行组过滤器正确解码与评估，使谓词下推（如 `tmp_col < to_timestamp('2000-01-31 08:30:00')`）对导入的 INT96 时间戳列正常工作，避免查询直接抛错。同时通过把 INT96 字节流交给 `ParquetUtil.extractTimestampInt96` 转换为统一的"距 Unix Epoch 微秒数"表示，让后续与 Iceberg `Literal<Long>` 的比较（TIMESTAMP 在 Iceberg 内部以 Long 微秒表示）能正确进行。

## 如何达成设计目的

修复分两处协同的改动：(1) **`ParquetConversions.converterFromParquet(PrimitiveType)`** 新增 `case INT96:` 分支，把 Parquet `Binary`（12 字节小端序 INT96）转换为 `Long`（距 Unix Epoch 微秒数）——具体通过 `ByteBuffer.wrap(((Binary) binary).getBytes()).order(ByteOrder.LITTLE_ENDIAN)` 拿到小端序 buffer，再交给 `ParquetUtil.extractTimestampInt96(buffer)` 完成 Julian Day + 纳秒 → 微秒的换算。这个转换函数会被 `ParquetDictionaryRowGroupFilter` 在初始化阶段通过 `conversions.put(id, ParquetConversions.converterFromParquet(colType, icebergType))` 缓存到字段 id 上。(2) **`ParquetDictionaryRowGroupFilter.EvalVisitor.dict(...)`** 的 switch 新增 `case INT96:` 分支，调用 `dict.decodeToBinary(i)` 把字典槽位解码为 `Binary`（与 `BINARY/FIXED_LEN_BYTE_ARRAY` 路径一致），再经 `conversion.apply(...)` 套用上面注册的 INT96→Long 转换函数，把结果加入 `dictSet`。这样后续 `eq/lt/gt/in` 等谓词在 `dictSet` 上的求值就拿到了与 Iceberg `TIMESTAMP` 类型（内部 Long 微秒）同构的 Long 值，可以直接与 `Literal<Long>` 比较。

测试侧，本提交把 `spark/v3.3`、`spark/v3.4`、`spark/v3.5` 三个版本的 `TestIcebergSourceTablesBase` 中"导入 INT96 时间戳 Parquet 表后能正确读取"的测试方法扩展为同时验证五种过滤谓词（`< / <= / == / > / >=`）。原测试只比对"无过滤条件下 Iceberg 读取结果与 Spark 原生 Parquet 读取结果一致"，仅覆盖了常规读取路径（`TimestampInt96Reader`），不能触发字典行组过滤；本提交新增 `testWithFilter(filterExpr, tableIdentifier)` 辅助方法，把过滤条件下推到 Iceberg 读取，从而强制 `ParquetDictionaryRowGroupFilter` 评估 INT96 列字典，验证修复后的下推过滤能正确裁剪 row group 并返回与 Spark 原生 Parquet（带同样过滤）一致的结果。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetConversions.java`

**修改目的**：在 Parquet 原始类型 → Java 对象的转换函数中新增 INT96 分支，把 INT96 字节流转换为距 Unix Epoch 的微秒数（Long），供字典过滤器后续与 Iceberg `TIMESTAMP` Literal（Long 微秒）比较。

**工作逻辑**：文件新增 `import java.nio.ByteOrder;`。在 `converterFromParquet(PrimitiveType type)` 方法的"按原始类型分派" switch 中（处理完 `FIXED_LEN_BYTE_ARRAY/BINARY` 后），新增：
```java
case INT96:
  return binary ->
      ParquetUtil.extractTimestampInt96(
          ByteBuffer.wrap(((Binary) binary).getBytes()).order(ByteOrder.LITTLE_ENDIAN));
```
关键点：(1) Parquet 的 `Binary` 持有 INT96 的 12 字节原始数据，但 `Binary.getBytes()` 返回的字节数组按"写入时的字节序"——INT96 历史上固定为小端序存储（与 `BaseParquetReaders.TimestampInt96Reader` 中 `column.nextBinary().toByteBuffer().order(ByteOrder.LITTLE_ENDIAN)` 处理方式一致），故必须显式 `order(ByteOrder.LITTLE_ENDIAN)` 才能正确解析；(2) `ParquetUtil.extractTimestampInt96(buffer)` 内部依次 `getLong()` 读 8 字节纳秒、`getInt()` 读 4 字节 Julian Day，再 `TimeUnit.DAYS.toMicros(julianDay - UNIX_EPOCH_JULIAN) + TimeUnit.NANOSECONDS.toMicros(timeOfDayNanos)` 换算为微秒（`UNIX_EPOCH_JULIAN = 2_440_588L` 是 Unix Epoch 1970-01-01 对应的 Julian Day 常量）。转换结果为 `Long`，与 Iceberg `Types.TimestampType` 在内部用 `Long` 表示微秒的契约对齐，让后续字典过滤器的比较逻辑无需再做类型适配。注意此处未引入 INT96 → Iceberg `TIMESTAMP` Logical 的额外封装，而是直接产出 `Long`——因为 `ParquetDictionaryRowGroupFilter.dict(...)` 把 `dictSet` 元素当 `T`（泛型）放入集合，最终由 `BoundReference.comparator()` 比较，而 Iceberg `TIMESTAMP` 类型的 `Literal` 内部值就是 `Long`，故 `Long` 即是正确的比较形态。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetDictionaryRowGroupFilter.java`

**修改目的**：在字典解码 switch 中新增 INT96 分支，避免遇到 INT96 列时落入 `default` 抛 `IllegalArgumentException`，让字典行组过滤器能正常评估 INT96 时间戳列上的谓词。

**工作逻辑**：在 `EvalVisitor.dict(int id, ColumnDescriptor col, Dictionary dict)` 方法的 switch（按 `col.getPrimitiveType().getPrimitiveTypeName()` 分派）中，于 `case DOUBLE:` 之后、`default:` 之前新增：
```java
case INT96:
  dictSet.add((T) conversion.apply(dict.decodeToBinary(i)));
  break;
```
关键点：(1) `dict.decodeToBinary(i)` 把字典槽位 i 解码为 Parquet `Binary` 对象——这是 Parquet `Dictionary` API 对 INT96 列的标准解码方式（与 `BINARY/FIXED_LEN_BYTE_ARRAY` 路径完全一致，因为 INT96 在 Parquet 内部就是定长 12 字节的字节数组）；(2) `conversion` 是在 `eval(...)` 初始化阶段通过 `conversions.put(id, ParquetConversions.converterFromParquet(colType, icebergType))` 注册的转换函数，对 INT96 列它会进一步委托到本提交在 `ParquetConversions` 新增的 `case INT96:` 分支（注意 `converterFromParquet(parquetType, icebergType)` 会先调用 `converterFromParquet(parquetType)` 拿到 INT96→Long 函数，再判断是否需要叠加 INT32→LONG/FLOAT→DOUBLE 调整——对 INT96 列无调整，直接返回）；(3) `(T)` 强转把 `Object` 转为 `dictSet` 的泛型元素类型 `T`，与 INT32/INT64/BINARY 等已有分支的处理方式一致。修复后，INT96 列字典中的每个值都被解码为 Long 微秒并加入 `dictSet`，后续 `eq(BoundReference, Literal)` / `lt(...)` / `gt(...)` / `in(...)` 等谓词即可在 Long 集合上正确求值，决定该 row group 是否可能包含匹配行（`ROWS_MIGHT_MATCH` 或 `ROWS_CANNOT_MATCH`）。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java`

**修改目的**：扩展原有"导入 INT96 时间戳 Parquet 表后能正确读取"的测试，新增五种过滤谓词（`< / <= / == / > / >=`）的下推过滤验证，确保 `ParquetDictionaryRowGroupFilter` 在 INT96 列上能正确评估字典谓词且结果与 Spark 原生 Parquet 读取一致。

**工作逻辑**：原测试方法末尾的"validate we get the expected results back"段（直接 `select("tmp_col").collectAsList()` 比对）被替换为五次 `testWithFilter(...)` 调用，分别传入 `tmp_col < to_timestamp('2000-01-31 08:30:00')`、`<=`、`==`、`>`、`>=` 五种过滤表达式。新增私有方法 `testWithFilter(String filterExpr, TableIdentifier tableIdentifier)`：对原 Spark Parquet 表 `spark.table("parquet_table").select("tmp_col").filter(filterExpr).collectAsList()` 取期望结果，对 Iceberg 导入表 `spark.read().format("iceberg").load(loadLocation(tableIdentifier)).select("tmp_col").filter(filterExpr).collectAsList()` 取实际结果，断言 `containsExactlyInAnyOrderElementsOf(expected)`。关键在于 `filter(filterExpr)` 会把过滤条件下推到 Iceberg 的 `ParquetDictionaryRowGroupFilter`（因测试已通过 `parquet.enable.dictionary=true/false` 双场景覆盖字典与非字典路径），从而触发本提交修复的 INT96 字典解码分支；时间戳 `'2000-01-31 08:30:00'` 落在测试数据区间 `[2000-01-31, 2100-01-01)` 内，确保五种谓词都能命中部分行、又能让部分 row group 被裁剪，有效验证过滤正确性。注意测试在 `useDict ∈ {true, false}` 与 `useVectorization ∈ {true, false}` 双重循环中跑，覆盖字典开启（触发字典下推）与字典关闭（走统计下推）两种代码路径。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java`

**修改目的**：与 v3.3 同步——Spark 3.4 版本的相同测试方法做相同扩展，验证 INT96 列在 Spark 3.4 集成下的字典行组过滤正确性。

**工作逻辑**：改动与 v3.3 完全一致：原"无过滤直接比对"替换为五次 `testWithFilter(...)` 调用，新增同名私有辅助方法 `testWithFilter(String filterExpr, TableIdentifier tableIdentifier)`，用 `Assertions.assertThat(actual).as("Rows must match").containsExactlyInAnyOrderElementsOf(expected)` 断言（注意 v3.3/v3.4 用 `Assertions.assertThat`，v3.5 用静态导入 `assertThat`）。这是 Iceberg 多 Spark 版本维护策略的体现——每个维护中的 Spark 版本（3.3/3.4/3.5）的集成测试需同步演进，确保修复在各版本都生效。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java`

**修改目的**：与 v3.3/v3.4 同步——Spark 3.5 版本的相同测试方法做相同扩展。

**工作逻辑**：改动与 v3.3/v3.4 几乎一致，唯一差异是断言写法用 `assertThat(actual).as("Rows must match").containsExactlyInAnyOrderElementsOf(expected)`（静态导入 `assertThat`，与 v3.5 测试基类 `TestBase` 的风格一致，v3.5 不再使用 `Assertions.assertThat` 前缀）。同样新增 `testWithFilter(String filterExpr, TableIdentifier tableIdentifier)` 私有方法，对原 Spark Parquet 表与 Iceberg 导入表分别加同样过滤后比对结果。三种谓词系列（`<`/`<=`/`==`/`>`/`>=`）覆盖了 `eq`/`lt`/`ltEq`/`gt`/`gtEq` 五个 `BoundExpressionVisitor` 谓词在 INT96 字典上的求值路径。

## 小结

本提交修复 Iceberg Parquet 字典行组过滤器对 INT96 时间戳列的解码遗漏，让导入的旧版 Parquet 表（含 INT96 时间戳）也能受益于字典下推过滤。核心改动是两处协同的 INT96 分支：`ParquetConversions.converterFromParquet` 把 INT96 `Binary` 经 `ParquetUtil.extractTimestampInt96` 转为距 Unix Epoch 微秒数（Long），与 Iceberg `TIMESTAMP` 类型的内部 Long 表示对齐；`ParquetDictionaryRowGroupFilter.EvalVisitor.dict` 调用 `dict.decodeToBinary(i)` 解码 INT96 字典槽位再经上述转换加入字典集合，供 `eq/lt/gt/in` 等谓词求值。修复前 INT96 列上的字典下推会抛 `IllegalArgumentException` 导致查询失败；修复后可正确裁剪 row group 并返回与 Spark 原生 Parquet 读取一致的结果。测试侧在 Spark 3.3/3.4/3.5 三个版本的 `TestIcebergSourceTablesBase` 中把"无过滤读取比对"扩展为五种过滤谓词（`< / <= / == / > / >=`）的下推过滤比对，覆盖 `eq/lt/ltEq/gt/gtEq` 五个谓词路径与字典开/关、向量化开/关四种组合。本提交让 Iceberg 对外部引擎产出的 INT96 Parquet 表的下推过滤能力与常规 INT64 时间戳列对齐，是 Iceberg 兼容旧版 Parquet 数据生态的重要一环。
