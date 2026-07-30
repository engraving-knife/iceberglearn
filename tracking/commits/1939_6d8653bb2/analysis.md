# 提交 1939：Spark, API: Enhance hashing efficiency by operating on raw UTF-8 bytes (#12657)

## 提交信息

- **序号**：1939 / 4088
- **哈希**：6d8653bb2bbcecdbe38e98da60ef5578f083e933
- **短哈希**：6d8653bb2
- **日期**：2025-03-31 08:02:49 -0600
- **作者**：Xiaoxuan
- **提交说明**：Spark, API: Enhance hashing efficiency by operating on raw UTF-8 bytes (#12657)
- **PR/Issue**：#12657

## 总体目的

此提交优化了 Spark 中 Iceberg `bucket` 函数对字符串类型输入的哈希计算效率。Iceberg 的分桶（bucket）分区变换使用 Murmur3 哈希对值进行分桶。对于字符串输入，Spark 的 `BucketFunction.BucketString` 此前的实现是：先调用 `value.toString()` 将 Spark 的 `UTF8String` 转为 Java `String`，再由 `BucketUtil.hash(String)` 内部用 `MURMUR3.hashString(value, StandardCharsets.UTF_8)` 将该 String 重新编码为 UTF-8 字节后哈希。

这里存在一个冗余的"字节→String→字节"往返：Spark 的 `UTF8String` 本身内部就是以 UTF-8 字节数组存储的，`toString()` 会把字节解码成 Java String（涉及 UTF-8 解码，分配新对象），然后 `hashString` 又把 String 重新编码回 UTF-8 字节再哈希。这两次编解码完全是浪费，且会产生额外的对象分配与 GC 压力。代码中甚至有 `// TODO - We can probably hash the bytes directly given they're already UTF-8 input.` 注释标记此优化机会。

本提交直接通过 `UTF8String.getBytes()` 获取底层 UTF-8 字节数组，调用新增的 `BucketUtil.hash(byte[])` 用 `MURMUR3.hashBytes(value)` 直接对原始字节哈希，跳过 String 解码/编码往返。结果在数值上与原实现完全一致（因为 UTF-8 编解码是幂等的），但避免了不必要的对象分配与编解码开销，提升了 bucket 分区列在高数据量场景下的性能。

## 如何达成设计目的

设计思路是：在 `BucketUtil` 中新增一个接收 `byte[]` 的 `hash` 重载（直接调用 `MURMUR3.hashBytes`），并在 Spark 的 `BucketFunction.BucketString` 中改为获取 `UTF8String.getBytes()` 后调用此重载。由于哈希输入的字节序列不变，结果与原 `hash(String)` 完全一致，因此是纯性能优化、无行为变更。改动同时应用到 Spark v3.4 与 v3.5 两个模块（代码一致）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/util/BucketUtil.java` (修改, +4 lines)

**修改目的**：新增 `byte[]` 类型的 hash 重载。

**工作逻辑**：新增方法 `public static int hash(byte[] value) { return MURMUR3.hashBytes(value).asInt(); }`。直接对原始字节数组做 Murmur3 哈希，避免字符串编解码。与已有 `hash(String)`（内部 `hashString(value, UTF_8)`）对相同 UTF-8 内容产生相同结果。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/functions/BucketFunction.java` (修改, +8/-2 lines)

**修改目的**：字符串 bucket 改用原始字节哈希。

**工作逻辑**：
- `BucketString` 的 `apply` 方法中：将 `return apply(numBuckets, hash(value.toString()));` 改为 `return apply(numBuckets, hash(value.getBytes()));`，直接取 `UTF8String` 底层 UTF-8 字节数组。
- 删除原 `// TODO - We can probably hash the bytes directly given they're already UTF-8 input.` 注释（TODO 已完成）。
- 新增 `public static int hash(byte[] value) { return BucketUtil.hash(value); }`（标注 `// Visible for testing`），便于测试直接调用。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkBucketFunction.java` (修改, +5 lines)

**修改目的**：验证字符串与原始字节哈希结果一致。

**工作逻辑**：新增断言验证 `BucketString().hash("iceberg".getBytes(StandardCharsets.UTF_8))` 等于 `BucketString().hash("iceberg")`，确认优化后行为不变。

### `spark/v3.5/spark/...` 下的 `BucketFunction.java` 与 `TestSparkBucketFunction.java` (同上)

**修改目的**：对 Spark v3.5 应用完全相同的优化与测试。

**工作逻辑**：v3.5 与 v3.4 改动一致（v3.5 测试用 AssertJ 风格 `assertThat`，v3.4 用 JUnit `Assert.assertEquals`，适配各自测试基类风格）。

## 总结

本次提交优化了 Spark `bucket` 函数对字符串输入的哈希效率：通过 `UTF8String.getBytes()` 直接获取底层 UTF-8 字节并调用新增的 `BucketUtil.hash(byte[])`（基于 `Murmur3.hashBytes`），跳过了原先"字节→String→字节"的冗余编解码往返与对象分配。结果与原实现完全一致（纯性能优化），同时清理了对应 TODO 注释。改动同步应用到 Spark v3.4 与 v3.5，并补充了一致性测试。
