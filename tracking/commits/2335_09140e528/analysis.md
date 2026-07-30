# 提交 2335：Spark: Support Parquet dictionary encoded UUIDs (#13324)

## 提交信息

- **序号**：2335 / 4088
- **哈希**：09140e52836048b112c42c9cfe721295bd57048b
- **短哈希**：09140e528
- **日期**：2025-07-10 00:29:16 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Spark: Support Parquet dictionary encoded UUIDs (#13324)
- **PR/Issue**：#13324

## 总体目的

本提交修复了 Spark 向量化读取 Parquet 文件时，无法正确处理字典编码（dictionary encoded）的 UUID 类型数据的问题。

Parquet 支持字典编码，这是一种高效的列式存储编码方式，特别适用于取值有限的列。UUID 在 Iceberg 中以 16 字节的 FIXED_LEN_BYTE_ARRAY 存储。当 Parquet 文件中的 UUID 列使用字典编码时，底层数据存储的是字典 ID（整数索引），需要通过字典解码获取实际值。

此前，Iceberg 的 Arrow 向量化读取器在处理字典编码的 BINARY/FIXED_LEN_BYTE_ARRAY 类型时，使用的是 `DictionaryBinaryAccessor`。这个 accessor 只实现了 `getBinary()` 方法，没有实现 `getUTF8String()` 方法。当 Spark 读取 UUID 列并尝试以字符串形式获取值时，会回退到父类的默认实现，而父类默认实现不支持从字典中解码 UUID，导致读取失败或结果错误。

作者在修复 PyIceberg 的 UUID 支持时发现了此问题。由于 PyIceberg 生成的数据量较小，UUID 列更容易触发字典编码，从而暴露了这个 bug。

## 如何达成设计目的

整体设计思路是为 `DictionaryBinaryAccessor` 增加从字典解码 UTF8 字符串（包括 UUID）的能力，并在 Spark 的 `ArrowVectorAccessorFactory` 中实现 UUID 特定的字典解码逻辑。

关键设计点：
1. 在 `GenericArrowVectorAccessorFactory` 的 `StringFactory` 接口中新增 `ofRow(IntVector, Dictionary, int)` 默认方法，支持从字典创建字符串。
2. 修改 `DictionaryBinaryAccessor` 使其持有 `StringFactory` 引用，并实现 `getUTF8String()` 方法，当有 `StringFactory` 时通过它从字典解码。
3. 在 Spark 的 `ArrowVectorAccessorFactory.UUIDStringFactory` 中实现 `ofRow()`，将字典解码的字节转换为 UUID 字符串。
4. 新增测试验证字典编码 UUID 的向量化读取。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/GenericArrowVectorAccessorFactory.java` (+22/-2 lines)

**修改目的**：为字典编码的 BINARY 类型添加 UTF8 字符串解码支持。

**工作逻辑**：

1. **`DictionaryBinaryAccessor` 构造函数变更**：新增 `StringFactory` 参数，存储为字段。在 `case FIXED_LEN_BYTE_ARRAY` / `case BINARY` 分支中，创建 accessor 时传入 `stringFactorySupplier.get()`。

2. **`getUTF8String()` 方法实现**：新增重写方法。当 `stringFactory` 不为 null 时，调用 `stringFactory.ofRow(offsetVector, dictionary, rowId)` 从字典解码；否则回退到父类默认实现。

3. **`StringFactory` 接口新增方法**：添加 `ofRow(IntVector offsetVector, Dictionary dictionary, int rowId)` 默认方法，默认抛出 `UnsupportedOperationException`，由具体实现类覆盖。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ArrowVectorAccessorFactory.java` (+8/-0 lines)

**修改目的**：为 Spark 实现 UUID 的字典解码逻辑。

**工作逻辑**：在 `UUIDStringFactory` 内部类中重写 `ofRow(IntVector, Dictionary, int)` 方法。通过 `dictionary.decodeToBinary(offsetVector.get(rowId)).getBytes()` 从字典解码二进制数据，然后使用 `UUIDUtil.convert(bytes)` 将字节转换为 UUID，再 `toString()` 并包装为 Spark 的 `UTF8String`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetVectorizedReads.java` (+15/-0 lines)

**修改目的**：新增测试验证字典编码 UUID 的向量化读取。

**工作逻辑**：新增 `testUuidReads()` 测试方法。只写入一行数据以确保触发字典编码（数据量少时 Parquet 更倾向使用字典编码），schema 为单个 UUID 列。写入后使用向量化读取验证数据正确匹配。

## 总结

本提交修复了 Spark 向量化读取 Parquet 字典编码 UUID 的缺陷。通过在通用的 `DictionaryBinaryAccessor` 中增加 `StringFactory` 支持并在 Spark 端实现 UUID 的字典解码，使 Iceberg 能够正确读取 PyIceberg 等工具生成的包含字典编码 UUID 的 Parquet 文件。这对跨语言互操作性具有重要意义。
