# 提交 2535：Spark 3.4: Support Parquet dictionary encoded UUIDs (#13877)

## 提交信息

- **序号**：2535 / 4088
- **哈希**：d5e3a56b3150b0e97adf52673526104d01e3aaa6
- **短哈希**：d5e3a56b3
- **日期**：2025-08-20 12:20:03 -0700
- **作者**：Fokko Driesprong
- **提交说明**：Spark 3.4: Support Parquet dictionary encoded UUIDs (#13877)
- **PR/Issue**：#13877（backport of #13324）

## 总体目的

Parquet 写入时对小基数列会启用字典编码（dictionary encoding）。当 UUID 列被字典编码后，向量化读取路径需要专门处理：通过 `IntVector` 拿到字典 id，再从 `Dictionary` 解码出原始字节，然后转成 UUID 字符串。Spark 3.4 的 `ArrowVectorAccessorFactory` 之前只为 UUID 实现了从 `FixedSizeBinaryVector` 直接读取的 accessor，没有实现 dictionary 路径的 `ofRow(IntVector offsetVector, Dictionary dictionary, int rowId)`，导致带字典编码的 UUID 列在向量化读取时无法正确解码（甚至可能抛错或返回错误结果）。

本提交从 #13324 backport 该能力到 Spark 3.4：补全 UUID 的 dictionary accessor，使其能从 `dictionary.decodeToBinary(offsetVector.get(rowId))` 取出字节，经 `UUIDUtil.convert` 转 UUID 后包装为 Spark `UTF8String`。

测试侧新增 `testUuidReads`：只写一行 UUID 数据，强制维持字典编码（单值场景下字典编码最稳），然后通过向量化路径读回并断言与原数据一致。

## 如何达成设计目的

- 在 `ArrowVectorAccessorFactory` 的 UUID accessor 部分追加 `ofRow(IntVector, Dictionary, int)` 重载：
  - 用 `offsetVector.get(rowId)` 取字典 id。
  - `dictionary.decodeToBinary(id).getBytes()` 取出 16 字节 UUID 数据。
  - `UUIDUtil.convert(bytes)` 转 `UUID`，再 `.toString()` 后用 `UTF8String.fromString` 包装，与现有非字典路径返回类型一致。
- 测试用 `numRows = 1` 强制字典编码，跑通 `generateData → 写 Parquet v2 → 向量化读 → assertRecordsMatch` 的完整链路。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ArrowVectorAccessorFactory.java` (+8)

**修改目的**：补全 UUID 字典编码读取路径。

**工作逻辑**：新增 `ofRow(IntVector offsetVector, Dictionary dictionary, int rowId)` 实现，按上面流程解码并返回 `UTF8String`。新增 `IntVector`、`Dictionary` 两个 import。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/parquet/vectorized/TestParquetVectorizedReads.java` (+14)

**修改目的**：覆盖 UUID 向量化读取。

**工作逻辑**：`testUuidReads` 写入 1 行 UUID 数据并通过向量化路径读回比对，验证字典编码下 UUID 能被正确解码。

## 总结

将 #13324 的 UUID 字典编码向量化读取支持 backport 到 Spark 3.4，补全 `ArrowVectorAccessorFactory` 中 UUID accessor 的 dictionary 重载，并通过单行数据测试验证字典编码下的正确解码，避免 UUID 列在字典编码时向量化读取失败。
