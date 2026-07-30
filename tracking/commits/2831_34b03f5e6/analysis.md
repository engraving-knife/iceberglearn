# 提交 2831：Spark: Implement ArrayData.getVariant for row-based Parquet readers (#14349)

## 提交信息

- **序号**：2831 / 4088
- **哈希**：34b03f5e6fc1d3cd3b6b4817e27a6c6b334dfae4
- **短哈希**：34b03f5e6
- **日期**：2025-11-04 17:45:12 -0800
- **作者**：Huaxin Gao
- **提交说明**：Spark: Implement ArrayData.getVariant for row-based Parquet readers (#14349)
- **PR/Issue**：#14349

## 总体目的

Spark 4.0 引入了 VARIANT 类型，Iceberg 也开始支持 format-version=3 的 Variant 列。在 row-based（非向量化）的 Parquet 读取路径中，`SparkParquetReaders` 内部有一个内部类（继承自 `ArrayData` 或类似行式容器）实现了 `getVariant(int ordinal)`，但原本只是直接 `throw new UnsupportedOperationException("Unsupported method: getVariant")`。

这导致当 VARIANT 类型嵌套在 STRUCT、ARRAY、MAP 等复合类型中并被读取时，Spark 调用到该 `getVariant` 方法就会抛异常，无法读出嵌套的 Variant 数据。该提交将这个未实现的方法改为正确返回 `values[ordinal]` 强转为 `VariantVal` 的结果，使 row-based Parquet reader 完整支持嵌套 Variant 读取。

## 如何达成设计目的

1. 修改 `SparkParquetReaders.java` 中内部行式容器的 `getVariant` 方法，把抛异常改为返回 `values[ordinal]` 转换后的 `VariantVal`。
2. 在 `TestSparkVariantRead` 中新增三个参数化测试 `testNestedStructVariant`、`testNestedArrayVariant`、`testNestedMapVariant`，分别覆盖 VARIANT 嵌套在 STRUCT、ARRAY、MAP 三种复合类型中的读取场景。
3. 引入 `setVectorization` 辅助方法重构原有重复的 `ALTER TABLE ... SET TBLPROPERTIES ('read.parquet.vectorization.enabled'=...)` 调用，减少重复代码，并支持传入表名参数。
4. 测试中通过 `parse_json` 写入 JSON 字符串构造 Variant，再读取出来并通过 `Variant` 类的 `getFieldByKey(...).getLong()` 校验具体值，确保嵌套 Variant 的值与元数据均被正确读出。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetReaders.java` (+1/-1 lines)

**修改目的**：让 row-based Parquet reader 内部行容器的 `getVariant` 真正返回值，而不是抛异常。

**工作逻辑**：
```java
@Override
public VariantVal getVariant(int ordinal) {
    return (VariantVal) values[ordinal];
}
```
直接从存储字段值的 `values` 数组中取出指定 ordinal 的元素并强转为 `VariantVal`。`values` 数组在 row-based reader 中已被 Parquet 解码逻辑填充为 `VariantVal` 实例（包含 value 与 metadata 两部分），因此直接返回即可。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkVariantRead.java` (+155/-12 lines)

**修改目的**：覆盖嵌套 Variant 读取场景，并重构向量化开关设置。

**工作逻辑**：
- 新增 `setVectorization(boolean on)` 与 `setVectorization(String table, boolean on)` 两个辅助方法，统一通过 `ALTER TABLE ... SET TBLPROPERTIES` 控制向量化开关，替代原测试中重复的内联 SQL。
- `testNestedStructVariant`：建表 `STRUCT<v: VARIANT>`，插入两行，读取 `s.v` 并断言 `VariantVal` 可被还原为 `Variant`，且 `getFieldByKey("a").getLong()` 等于预期值。
- `testNestedArrayVariant`：建表 `ARRAY<VARIANT>`，插入两个含两个 Variant 元素的数组，读取 `arr[0]`、`arr[1]` 并断言对应字段值。
- `testNestedMapVariant`：建表 `MAP<STRING, VARIANT>`，插入含 k1/k2 两个键的 map，读取 `element_at(m, 'k1')`、`element_at(m, 'k2')` 并断言字段值。
- 测试均参数化 `vectorized=true/false`，但当前用 `assumeThat(vectorized).isFalse()` 跳过向量化路径（因 Variant 向量化读取尚未实现），仅验证 row-based 路径。

## 总结

该提交补全了 row-based Parquet reader 对嵌套 VARIANT 类型的读取支持，使 VARIANT 在 STRUCT/ARRAY/MAP 中能被正确读出。同时通过新增三类嵌套场景的参数化测试验证了正确性，并重构了测试中向量化开关的设置代码。这是 Iceberg 在 Spark 4.0 Variant 支持方向上的重要一环，与后续 2847（`StructInternalRow.getVariant`）一起构成完整的 Variant 行级读取能力。
