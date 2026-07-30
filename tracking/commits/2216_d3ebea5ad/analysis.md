# 提交 2216：AWS, GCS: Fix issue with Kryo and empty immutable collections for storage credential (#13216)

## 提交信息

- **序号**：2216 / 4088
- **哈希**：d3ebea5ada4e8ccf1308d22c44051e90ee1bf651
- **短哈希**：d3ebea5ad
- **日期**：2025-06-05 20:26:59 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：AWS, GCS: Fix issue with Kryo and empty immutable collections for storage credential (#13216)
- **PR/Issue**：#13216

## 总体目的

这个提交修复了 S3FileIO、GCSFileIO 和 ResolvingFileIO 在使用 Kryo 序列化时，当 storageCredentials 为空不可变集合（`ImmutableList.of()` 或 `List.of()`）时出现的序列化问题。Kryo 序列化框架在反序列化不可变集合时存在已知问题：它会尝试向反序列化后的不可变集合中添加元素，导致 UnsupportedOperationException。当 FileIO 实例没有配置存储凭证（storage credentials）时，字段初始化为空的不可变集合，在 Flink 等使用 Kryo 序列化的引擎中序列化/反序列化时会失败。本提交将 storageCredentials 字段的初始值从不可变空集合改为可变集合（`Lists.newArrayList()`），从而避免 Kryo 序列化问题。这是一个影响生产环境的 bug 修复，因为在 Flink 等分布式计算框架中，FileIO 实例需要在 task 之间序列化传输。

## 如何达成设计目的

- 将 S3FileIO、ResolvingFileIO、GCSFileIO 三个类的 `storageCredentials` 字段初始值从 `ImmutableList.of()` 或 `List.of()` 改为 `Lists.newArrayList()`（可变 ArrayList）。
- 添加注释说明 "use modifiable collection for Kryo serde"，解释为何使用可变集合。
- 为 S3FileIO 和 GCSFileIO 添加测试用例，验证在没有存储凭证时 Kryo 序列化和 Java 序列化的往返正确性。
- 为 ResolvingFileIO 添加测试用例，验证在没有存储凭证时序列化后 credentials() 返回空集合。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java` (修改, +2/-1 lines)

**修改目的**：修复 S3FileIO 的 storageCredentials 字段初始化值。

**工作逻辑**：将 `private List<StorageCredential> storageCredentials = ImmutableList.of();` 改为 `private List<StorageCredential> storageCredentials = Lists.newArrayList();`，并添加注释说明使用可变集合是为了 Kryo 序列化。

### `core/src/main/java/org/apache/iceberg/io/ResolvingFileIO.java` (修改, +2/-1 lines)

**修改目的**：修复 ResolvingFileIO 的 storageCredentials 字段初始化值。

**工作逻辑**：将 `private List<StorageCredential> storageCredentials = List.of();` 改为 `private List<StorageCredential> storageCredentials = Lists.newArrayList();`，同样添加注释。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSFileIO.java` (修改, +2/-1 lines)

**修改目的**：修复 GCSFileIO 的 storageCredentials 字段初始化值。

**工作逻辑**：将 `private List<StorageCredential> storageCredentials = ImmutableList.of();` 改为 `private List<StorageCredential> storageCredentials = Lists.newArrayList();`，同样添加注释。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIO.java` (修改, +106 lines)

**修改目的**：验证 S3FileIO 在无凭证场景下的序列化正确性。

**工作逻辑**：新增三个测试：
- `fileIOWithPrefixedS3ClientWithoutCredentialsKryoSerialization`：初始化 S3FileIO 仅设置 region（无凭证），验证 Kryo 序列化往返后 credentials 为空，且 client/asyncClient 正常工作。
- `fileIOWithPrefixedS3ClientWithoutCredentialsJavaSerialization`：同上但使用 Java 原生序列化。
- `resolvingFileIOLoadWithoutStorageCredentials`：测试 ResolvingFileIO 无凭证时 Kryo 和 Java 序列化往返，验证内部 S3FileIO 的 client 正常工作。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/GCSFileIOTest.java` (修改, +68 lines)

**修改目的**：验证 GCSFileIO 在无凭证场景下的序列化正确性。

**工作逻辑**：新增三个测试：
- `fileIOWithPrefixedStorageClientWithoutCredentialsKryoSerialization`：初始化 GCSFileIO 设置 OAuth token（无 storage credential），验证 Kryo 序列化往返后 credentials 为空且 client 正常。
- `fileIOWithPrefixedStorageClientWithoutCredentialsJavaSerialization`：同上但使用 Java 原生序列化。
- `resolvingFileIOLoadWithoutStorageCredentials`：测试 ResolvingFileIO 无凭证时 Kryo 和 Java 序列化往返。

## 总结

该提交修复了一个影响生产环境的序列化 bug：当 FileIO 实例没有配置存储凭证时，空的不可变集合在 Kryo 序列化时会导致反序列化失败。修复方案简单直接——将初始值改为可变 ArrayList，同时添加了全面的测试覆盖确保修复有效。该 bug 影响所有使用 Kryo 序列化的计算框架（如 Flink），修复后提升了 Iceberg 在分布式环境中的稳定性。
