# 提交 2082：AWS, GCP: Deprecate passing FileIO properties through constructor

## 提交信息

- **序号**：2082 / 4088
- **哈希**：84741867b9220f47b8e9a83edfb1be14d40cbefc
- **短哈希**：84741867b
- **日期**：2025-05-05 16:29:39 -0600
- **作者**：Eduard Tudenhoefner
- **提交说明**：AWS, GCP: Deprecate passing FileIO properties through constructor (#12972)
- **PR/Issue**：#12972

## 总体目的

Iceberg 的 `DelegateFileIO` 接口设计了一个标准的初始化流程：先通过无参构造器创建 FileIO 实例，再通过 `initialize(Map<String, String> properties)` 方法传入配置属性。这是 Iceberg 加载 FileIO 的标准方式（通过反射加载类、无参实例化、调用 initialize）。然而 `S3FileIO` 和 `GCSFileIO` 此前还提供了接受 `S3FileIOProperties` / `GCPProperties` 对象的便捷构造器，允许调用方在构造时直接传入预构建的属性对象。

这种"通过构造器传递属性"的方式存在几个问题：
1. 与 Iceberg 标准的 `initialize(Map)` 模式不一致，导致两套配置路径并存。
2. `S3FileIOProperties` / `GCPProperties` 是可变对象，通过构造器传入后存在状态管理风险。
3. 不利于后续统一配置管理和序列化。

本提交将 `S3FileIO` 和 `GCSFileIO` 中接受属性对象的构造器标记为 `@Deprecated`（自 1.10.0 起，将在 1.11.0 移除），引导用户改用无参/仅 supplier 的构造器配合 `initialize(Map)` 方法。同时为 `GCSFileIO` 新增仅接受 storage supplier 的构造器，并修复其 `initialize` 方法以正确处理已注入 supplier 的情况。测试代码也相应更新，将构造器传属性改为 `initialize(Map)` 调用。

## 如何达成设计目的

设计思路包含三部分：

1. **标记弃用**：为 `S3FileIO` 的两个带属性的构造器添加 `@Deprecated` 注解和 Javadoc 弃用说明；为 `GCSFileIO` 的带属性构造器同样添加弃用标记。
2. **新增替代构造器**：为 `GCSFileIO` 新增 `GCSFileIO(SerializableSupplier<Storage>)` 构造器（仅 supplier，不传属性，配合 initialize 使用）。
3. **修复 initialize 逻辑**：`GCSFileIO.initialize()` 中构建 storage supplier 的逻辑被包裹在 `if (null == storageSupplier)` 判断中，确保当 supplier 已通过构造器注入时不会覆盖它。
4. **更新测试**：将集成测试和单元测试中通过构造器传属性的方式改为 `new FileIO(supplier)` + `initialize(Map)` 模式。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java` (修改, +7/-0 lines)

**修改目的**：弃用 `S3FileIO` 中接受 `S3FileIOProperties` 的两个构造器。

**工作逻辑**：
- 为 `S3FileIO(SerializableSupplier<S3Client> s3, S3FileIOProperties s3FileIOProperties)` 构造器添加 `@Deprecated` 注解和 Javadoc：`@deprecated since 1.10.0, will be removed in 1.11.0; use S3FileIO#S3FileIO(SerializableSupplier) with S3FileIO#initialize(Map) instead`。
- 为 `S3FileIO(SerializableSupplier<S3Client> s3, SerializableSupplier<S3AsyncClient> s3Async, S3FileIOProperties s3FileIOProperties)` 构造器添加同样的弃用标记，指向 `S3FileIO#S3FileIO(SerializableSupplier, SerializableSupplier)` 配合 `initialize(Map)`。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSFileIO.java` (修改, +52/-31 lines)

**修改目的**：弃用带属性的构造器，新增仅 supplier 的构造器，并修复 initialize 逻辑。

**工作逻辑**：
- 新增 `GCSFileIO(SerializableSupplier<Storage> storageSupplier)` 构造器：设置 supplier 并初始化一个空的 `GCPProperties`，后续通过 `initialize(Map)` 填充属性。
- 为原有的 `GCSFileIO(SerializableSupplier<Storage> storageSupplier, GCPProperties gcpProperties)` 构造器添加 `@Deprecated` 注解和弃用说明，指向新的单参数构造器配合 `initialize(Map)`。
- 修改 `initialize(Map<String, String> properties)` 方法：将构建 storage supplier 的逻辑包裹在 `if (null == storageSupplier)` 判断中。这样当 supplier 已通过新构造器注入时，initialize 只更新属性而不覆盖 supplier；当使用无参构造器时，initialize 会构建 supplier。这是关键修复，使新构造器能与 initialize 正确协作。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIOIntegration.java` (修改, +58/-43 lines)

**修改目的**：将集成测试中通过构造器传 `S3FileIOProperties` 的方式改为 `initialize(Map)` 模式。

**工作逻辑**：
- 将 import 从 `software.amazon.awssdk.utils.ImmutableMap` 改为 `org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap`（统一使用 Iceberg 重定位的 Guava）。
- 多处将 `new S3FileIO(clientFactory::s3)` 后追加 `s3FileIO.initialize(ImmutableMap.of())` 调用，确保 FileIO 正确初始化。
- 加密相关测试（SSE-S3、SSE-KMS、DSSE-KMS、SSE-Custom）将原来的 `new S3FileIOProperties()` + setter + 构造器传属性，改为 `new S3FileIO(clientFactory::s3)` + `initialize(ImmutableMap.of(S3FileIOProperties.SSE_TYPE, ..., S3FileIOProperties.SSE_KEY, ...))`，通过 Map 传递配置属性。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3MultipartUpload.java` (修改, +6/-3 lines)

**修改目的**：将 multipart upload 测试改为 `initialize(Map)` 模式。

**工作逻辑**：
将 `io = new S3FileIO(() -> s3, properties)` 改为 `io = new S3FileIO(() -> s3)` + `io.initialize(ImmutableMap.of(S3FileIOProperties.MULTIPART_SIZE, ..., S3FileIOProperties.CHECKSUM_ENABLED, "true"))`。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/GCSFileIOTest.java` (修改, +2/-1 lines)

**修改目的**：将 GCS 单元测试改为使用新的单参数构造器。

**工作逻辑**：
将 `io = new GCSFileIO(() -> storage, new GCPProperties())` 改为 `io = new GCSFileIO(() -> storage)`，移除不再需要的 `GCPProperties` import。

## 总结

本提交弃用 `S3FileIO` 和 `GCSFileIO` 中通过构造器传递属性对象的便捷构造器（标记 `@Deprecated`，自 1.10.0 起，1.11.0 移除），引导用户改用无参/仅 supplier 构造器配合标准的 `initialize(Map)` 方法。为 `GCSFileIO` 新增仅接受 supplier 的构造器，并修复 initialize 方法以正确处理已注入的 supplier。同时更新所有相关集成测试和单元测试，将构造器传属性改为 `initialize(Map)` 调用模式，统一了 FileIO 的配置路径。
