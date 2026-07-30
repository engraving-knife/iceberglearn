# 提交 4012：Parquet: Cache adjacent identical metadata in variant reader (#16852)

## 提交信息

- **序号**：4012 / 4088
- **哈希**：e8959f23a749695478e9c9075364518d08e09c85
- **短哈希**：e8959f23a
- **日期**：2026-07-11 12:46:27 -0700
- **作者**：Neelesh Salian
- **提交说明**：Parquet: Cache adjacent identical metadata in variant reader (#16852)
- **PR/Issue**：#16852

## 总体目的

本提交对 Parquet variant reader 的 metadata 读取进行性能优化，通过缓存相邻行中相同的 variant metadata 字节，避免重复解析。

Variant 类型由 metadata（字段名/路径字典）和 value 两部分组成。在实际数据中，同一列的相邻行往往共享相同的 metadata（因为它们有相同的字段结构），但原实现对每一行都调用 `Variants.metadata(readBinary(column))` 重新解析 metadata 字节，造成不必要的 CPU 开销。本次在 `VariantMetadataReader` 中引入"上一行 metadata"缓存，若当前行的 metadata 字节与上一行完全相同则直接复用已解析的 `VariantMetadata` 对象。

## 如何达成设计目的

在 `VariantMetadataReader` 中新增两个字段：`lastMetadataBytes`（上次的字节数组）和 `cachedMetadata`（上次解析结果）。`read` 方法改为：
1. 从 `column.nextBinary()` 获取 `ByteBuffer`。
2. 若缓存非空且长度相同且字节逐位相等，直接返回 `cachedMetadata`。
3. 否则复制字节、调用 `Variants.metadata(...)` 解析，更新缓存并返回。

新增 `bufferEquals(ByteBuffer, byte[])` 私有方法做逐字节比较（避免完整复制后比较的开销）。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantReaders.java` (+31/-1 lines)

**修改目的**：在 `VariantMetadataReader` 中缓存相邻相同的 metadata。

**工作逻辑**：
```java
private static class VariantMetadataReader extends PrimitiveReader<VariantMetadata> {
  private byte[] lastMetadataBytes;
  private VariantMetadata cachedMetadata;

  @Override
  public VariantMetadata read(VariantMetadata reuse) {
    ByteBuffer data = column.nextBinary().toByteBuffer();
    int length = data.remaining();
    if (cachedMetadata != null && lastMetadataBytes != null
        && lastMetadataBytes.length == length && bufferEquals(data, lastMetadataBytes)) {
      return cachedMetadata;
    }
    byte[] bytes = new byte[length];
    data.get(bytes, 0, length);
    VariantMetadata parsed = Variants.metadata(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
    this.lastMetadataBytes = bytes;
    this.cachedMetadata = parsed;
    return parsed;
  }

  private static boolean bufferEquals(ByteBuffer buffer, byte[] expected) {
    int pos = buffer.position();
    for (int i = 0; i < expected.length; i++) {
      if (buffer.get(pos + i) != expected[i]) return false;
    }
    return true;
  }
}
```
缓存命中时直接返回已解析对象，避免重复解析；未命中时解析并更新缓存。注意 metadata 字节以小端序解析。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantReaders.java` (+109/-0 lines)

**修改目的**：新增测试验证缓存的命中和未命中行为。

**工作逻辑**：测试覆盖相邻行 metadata 相同（缓存命中）和不同（缓存未命中）的场景，验证读取结果正确且缓存逻辑生效（具体见文件）。

## 总结

本提交通过在 variant metadata reader 中缓存相邻相同行的已解析 metadata，减少了重复解析开销，对 variant 列密集的读取场景有性能提升。优化对用户透明，不影响读取结果。这是一个典型的"利用数据局部性"的读取端优化。
