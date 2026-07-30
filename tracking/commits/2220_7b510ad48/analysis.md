# 提交 2220：API, AWS, Azure, Core, GCP: Use parametrized tests for Kryo/Java serialization verification (#13244)

## 提交信息

- **序号**：2220 / 4088
- **哈希**：7b510ad48dc48639aa429c047b2b18d8c6555f80
- **短哈希**：7b510ad48
- **日期**：2025-06-06 14:48:47 +0200
- **作者**：Nándor Kollár
- **提交说明**：API, AWS, Azure, Core, GCP: Use parametrized tests for Kryo/Java serialization verification (#13244)
- **PR/Issue**：#13244

## 总体目的

这个提交是一个测试重构，将 Iceberg 多个模块（API、AWS、Azure、Core、GCP）中针对 Kryo 和 Java 序列化的验证测试从重复的独立测试方法改为参数化测试（Parameterized Test）。此前，对于每个需要验证序列化的场景，都需要分别编写 Kryo 序列化测试和 Java 序列化测试两个几乎相同的方法，导致大量代码重复。本提交在 `TestHelpers` 中引入 `RoundTripSerializer` 函数式接口和 `serializers()` 参数提供方法，将 Kryo 和 Java 两种序列化方式作为参数注入测试方法，使一个参数化测试方法即可覆盖两种序列化方式。这显著减少了测试代码量（净减少约 200 行），提高了测试可维护性，并确保两种序列化方式始终被同步测试。

## 如何达成设计目的

- 在 `TestHelpers` 中新增 `RoundTripSerializer<T>` 函数式接口，定义 `apply(T obj)` 方法执行序列化往返。
- 新增 `serializers()` 静态方法，返回包含 Kryo 序列化（`KryoHelpers::roundTripSerialize`）和 Java 序列化（`TestHelpers::roundTripSerialize`）两种实现的 Stream<Arguments>。
- 将各模块测试类中成对的 Kryo/Java 序列化测试方法合并为单个 `@ParameterizedTest` + `@MethodSource("org.apache.iceberg.TestHelpers#serializers")` 方法，接收 `RoundTripSerializer` 参数。
- 删除重复的测试方法，统一测试逻辑。

## 修改详情

### `api/src/test/java/org/apache/iceberg/TestHelpers.java` (修改, +18 lines)

**修改目的**：提供参数化序列化测试的基础设施。

**工作逻辑**：
- 新增 `RoundTripSerializer<T>` 函数式接口：`T apply(T obj) throws IOException, ClassNotFoundException`。
- 新增 `serializers()` 静态方法：返回 `Stream<Arguments>`，包含两个参数：
  - `Named.of("KryoSerialization", KryoHelpers::roundTripSerialize)` —— Kryo 序列化
  - `Named.of("JavaSerialization", TestHelpers::roundTripSerialize)` —— Java 原生序列化
- 使用 `Named` 包装使测试报告显示有意义的名称。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIO.java` (修改, +30/-149 lines)

**修改目的**：将 S3FileIO 的序列化测试改为参数化。

**工作逻辑**：
- 将以下成对测试合并为参数化测试：
  - `testS3FileIOKryoSerialization` + `testS3FileIOJavaSerialization` → `testS3FileIOSerialization`
  - `fileIOWithStorageCredentialsKryoSerialization` + `fileIOWithStorageCredentialsJavaSerialization` → `fileIOWithStorageCredentialsSerialization`
  - `fileIOWithPrefixedS3ClientWithoutCredentialsKryoSerialization` + `fileIOWithPrefixedS3ClientWithoutCredentialsJavaSerialization` → `fileIOWithPrefixedS3ClientWithoutCredentialsSerialization`
  - `fileIOWithPrefixedS3ClientKryoSerialization` + `fileIOWithPrefixedS3ClientJavaSerialization` → `fileIOWithPrefixedS3ClientSerialization`
  - `resolvingFileIOLoadWithoutStorageCredentials` 改为参数化
  - `resolvingFileIOLoadWithStorageCredentials` 改为参数化
- 每个参数化测试使用 `roundTripSerializer.apply(obj)` 替代直接调用 `KryoHelpers.roundTripSerialize` 或 `TestHelpers.roundTripSerialize`。

### `aws/src/test/java/org/apache/iceberg/aws/TestAwsClientFactories.java` (修改, +6/-26 lines)

**修改目的**：将 AwsClientFactories 的序列化测试改为参数化。

**工作逻辑**：合并 Kryo/Java 序列化测试对为参数化测试。

### `aws/src/test/java/org/apache/iceberg/aws/TestAwsProperties.java` (修改, +5/-6 lines)

**修改目的**：将 AwsProperties 的序列化测试改为参数化。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/ADLSFileIOTest.java` (修改, +10/-13 lines)

**修改目的**：将 ADLSFileIO 的序列化测试改为参数化。

### `azure/src/test/java/org/apache/iceberg/azure/AzurePropertiesTest.java` (修改, +5/-6 lines)

**修改目的**：将 AzureProperties 的序列化测试改为参数化。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/VendedAdlsCredentialProviderTest.java` (修改, +5/-6 lines)

**修改目的**：将 VendedAdlsCredentialProvider 的序列化测试改为参数化。

### `core/src/test/java/org/apache/iceberg/hadoop/HadoopFileIOTest.java` (修改, +12/-11 lines)

**修改目的**：将 HadoopFileIO 的序列化测试改为参数化。

### `core/src/test/java/org/apache/iceberg/io/TestResolvingIO.java` (修改, +20/-58 lines)

**修改目的**：将 ResolvingFileIO 的序列化测试改为参数化。

### `core/src/test/java/org/apache/iceberg/io/TestStorageCredential.java` (修改, +8/-10 lines)

**修改目的**：将 StorageCredential 的序列化测试改为参数化。

### `core/src/test/java/org/apache/iceberg/util/TestDataFileSet.java` (修改, +8/-10 lines)

**修改目的**：将 DataFileSet 的序列化测试改为参数化。

### `core/src/test/java/org/apache/iceberg/util/TestDeleteFileSet.java` (修改, +8/-11 lines)

**修改目的**：将 DeleteFileSet 的序列化测试改为参数化。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/GCSFileIOTest.java` (修改, +25/-136 lines)

**修改目的**：将 GCSFileIO 的序列化测试改为参数化。

**工作逻辑**：合并多对 Kryo/Java 序列化测试为参数化测试，包括带凭证和不带凭证的 prefixed storage client 测试。

## 总结

该提交是一个跨 5 个模块的测试重构，通过引入 `RoundTripSerializer` 函数式接口和参数化测试机制，将成对的 Kryo/Java 序列化测试合并为单个参数化测试方法。净减少约 200 行测试代码，消除了代码重复，提升了测试可维护性，同时确保两种序列化方式始终被同步覆盖。这是一个纯测试基础设施改进，不涉及生产代码变更。
