# 提交 4020：Arrow: Fix dict-encoded VARCHAR/VARBINARY read for direct ByteBuffers (#17055)

## 提交信息

- **序号**：4020 / 4088
- **哈希**：486fd93b402b9f8052d854d951cd4af1e3026a37
- **短哈希**：486fd93b4
- **日期**：2026-07-13 13:43:51 +0200
- **作者**：Eunbin Son
- **提交说明**：Arrow: Fix dict-encoded VARCHAR/VARBINARY read for direct ByteBuffers (#17055)
- **PR/Issue**：#17055

## 总体目的

本提交修复 Arrow 向量化 Parquet 读取器在处理字典编码的 VARCHAR/VARBINARY 列时，遇到 direct ByteBuffer（非堆内、无 backing array）会失败的 bug。

原实现中，`VarWidthBinaryDictEncodedReader` 调用 `BaseVariableWidthVector.setSafe(idx, buffer.array(), buffer.position() + buffer.arrayOffset(), buffer.limit() - buffer.position())`，直接访问 `buffer.array()`。但 direct ByteBuffer（堆外内存）没有 backing array，调用 `buffer.array()` 会抛 `UnsupportedOperationException`。当 Parquet 字典解码返回 direct buffer（例如使用堆外内存的 Parquet reader 配置）时，读取 VARCHAR/VARBINARY 列会崩溃。

本提交改用 Arrow `setSafe` 的 `ByteBuffer` 重载，避免访问 backing array，从而支持堆内和堆外两种 buffer。

## 如何达成设计目的

将 `setSafe` 调用从基于 `byte[]` 的重载改为基于 `ByteBuffer` 的重载：
```java
// 修改前
((BaseVariableWidthVector) vector).setSafe(
    idx, buffer.array(), buffer.position() + buffer.arrayOffset(), buffer.limit() - buffer.position());
// 修改后
((BaseVariableWidthVector) vector).setSafe(idx, buffer, buffer.position(), buffer.remaining());
```
`setSafe(int index, ByteBuffer value, int start, int length)` 是 Arrow 提供的接受 ByteBuffer 的重载，内部不依赖 `buffer.array()`，能正确处理 direct buffer。`buffer.remaining()` 等价于原来的 `buffer.limit() - buffer.position()`。

并新增测试验证 direct buffer 场景。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedDictionaryEncodedParquetValuesReader.java` (+1/-5 lines)

**修改目的**：修复对 direct ByteBuffer 的支持。

**工作逻辑**：
```java
ByteBuffer buffer = dict.decodeToBinary(currentVal).toByteBuffer();
((BaseVariableWidthVector) vector).setSafe(idx, buffer, buffer.position(), buffer.remaining());
```
改用 ByteBuffer 重载的 `setSafe`，不再调用 `buffer.array()`，兼容 direct（堆外）和 heap（堆内）buffer。

### `arrow/src/test/java/org/apache/iceberg/arrow/vectorized/parquet/TestVectorizedDictionaryEncodedParquetValuesReader.java` (+81/-0 lines, 新文件)

**修改目的**：新增针对 direct ByteBuffer 的回归测试。

**工作逻辑**：
`varWidthBinaryDictEncodedReaderHandlesNonArrayBackedByteBuffer` 测试：
1. 构造 payload "hello"，放入一个 direct ByteBuffer 的非零偏移位置（前后填充 -1 字节），`flip()` 后断言 `hasArray()` 为 false。
2. 用 `Binary.fromConstantByteBuffer(directBuffer, 3, payload.length)` 包装，模拟字典解码结果。
3. 构造 mock `Dictionary`，`decodeToBinary` 返回该 binary。
4. 创建 `VarCharVector`，通过 `VectorizedDictionaryEncodedParquetValuesReader` 读取一批。
5. 断言 `vector.get(0)` 解码为 "hello"。

测试刻意将 payload 放在非零偏移，验证 position/offset 处理正确，而非仅仅是空 buffer 边界情况。

## 总结

本提交修复了 Arrow 向量化 Parquet 读取器在字典编码 VARCHAR/VARBINARY 列上对 direct ByteBuffer 的兼容性 bug。原代码直接访问 `buffer.array()` 在堆外 buffer 上会抛异常，改用 Arrow 的 ByteBuffer 重载 `setSafe` 后同时支持堆内和堆外 buffer。修复对使用堆外内存的 Parquet 读取配置（高性能场景常见）至关重要，并配套新增了针对 direct buffer 非零偏移的回归测试。
