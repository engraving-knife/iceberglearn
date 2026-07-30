# 提交 2036：Core: Fix Kryo ser/de with StorageCredential config

## 提交信息

- **序号**：2036 / 4088
- **哈希**：ee2ffb4e94f166f4ffb371a01050e4fb92474b22
- **短哈希**：ee2ffb4e9
- **日期**：2025-04-24 09:50:42 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Fix Kryo ser/de with StorageCredential config (#12882)
- **PR/Issue**：#12882

## 总体目的

`StorageCredential` 是 Iceberg 中用于存储访问凭证的类（包含 prefix 和 config 两个属性），它实现了 `Serializable` 接口以支持 Java 序列化和 Kryo 序列化。然而此前的实现中，`config` 字段的类型是普通的 `Map<String, String>`，当通过 `ImmutableStorageCredential.builder()` 构建时，Immutables 框架会使用 Guava 的 `ImmutableMap`（或其单例变体）作为实际实现。

问题在于：当 config map 包含两个或更多元素时，Guava 的 `ImmutableMap` 的内部结构（如 `RegularImmutableMap`）无法被 Kryo 正确序列化/反序列化，因为 Kryo 需要无参构造器或注册的序列化器，而 Guava 不可变集合的内部类不满足这些条件。这导致在 Flink 等使用 Kryo 序列化的引擎中，`StorageCredential` 对象的序列化会失败。

本提交通过将 `config` 字段的类型从 `Map<String, String>` 改为 `SerializableMap<String, String>` 来修复此问题，`SerializableMap` 是 Iceberg 提供的可序列化包装类，内部使用 `LinkedHashMap` 实现，可以被 Kryo 正确处理。

## 如何达成设计目的

核心修复在 `StorageCredential` 接口中：
1. 将 `config()` 方法的返回类型从 `Map<String, String>` 改为 `SerializableMap<String, String>`
2. 在 `create()` 工厂方法中，使用 `SerializableMap.copyOf(config)` 将传入的普通 Map 转换为 `SerializableMap`，确保无论外部传入什么 Map 实现，内部存储的都是可被 Kryo 序列化的类型

同时更新相关测试：
- 在 `TestStorageCredential` 的 Kryo/Java 序列化测试中，将测试数据从单元素 Map 改为双元素 Map，确保能复现并验证修复（单元素的 singleton map 恰好能被 Kryo 处理，不会暴露问题）
- 在 `TestS3FileIO` 和 `GCSFileIOTest` 中，将直接使用 `ImmutableStorageCredential.builder()` 构建的方式改为使用 `StorageCredential.create()` 工厂方法，统一构建入口并确保 SerializableMap 转换

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/StorageCredential.java` (修改, +5/-2 lines)

**修改目的**：将 config 字段类型改为 SerializableMap 以支持 Kryo 序列化。

**工作逻辑**：
- 导入 `org.apache.iceberg.util.SerializableMap`
- `config()` 返回类型从 `Map<String, String>` 改为 `SerializableMap<String, String>`
- `create()` 工厂方法中，将 `config(config)` 改为 `config(SerializableMap.copyOf(config))`，确保构建时将任意 Map 实现转换为 SerializableMap

### `core/src/test/java/org/apache/iceberg/io/TestStorageCredential.java` (修改, +7/-2 lines)

**修改目的**：增强序列化测试以覆盖多元素 Map 场景。

**工作逻辑**：
- Kryo 序列化测试 `kryoSerDe`：将测试数据从 `Map.of("token", "storageToken")`（单元素）改为 `ImmutableMap.of("token1", "storageToken1", "token2", "storageToken2")`（双元素），并添加注释说明单元素 map 创建的是 singleton map 能被 Kryo 处理，但双元素会失败
- Java 序列化测试 `javaSerDe`：同样改为双元素 Map

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIO.java` (修改, +20/-25 lines)

**修改目的**：统一使用 StorageCredential.create() 工厂方法替代直接构建。

**工作逻辑**：
在 `singleStorageCredentialConfigured` 和 `multipleStorageCredentialsConfigured` 两个测试方法中，将 `ImmutableStorageCredential.builder().prefix(...).config(...).build()` 的构建方式替换为 `StorageCredential.create(prefix, config)` 工厂方法调用，移除 `ImmutableStorageCredential` 的导入。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/GCSFileIOTest.java` (修改, +18/-21 lines)

**修改目的**：统一使用 StorageCredential.create() 工厂方法替代直接构建。

**工作逻辑**：
与 S3 测试相同的修改方式，在 `singleStorageCredentialConfigured` 和 `multipleStorageCredentialsConfigured` 方法中改用 `StorageCredential.create()` 工厂方法，移除 `ImmutableStorageCredential` 导入。

## 总结

本提交修复了 `StorageCredential` 在 Kryo 序列化时的失败问题。根本原因是 Immutables 生成的 Guava `ImmutableMap` 内部结构无法被 Kryo 正确序列化。修复方案是将 config 字段类型改为 `SerializableMap`，并在工厂方法中统一转换。同时增强了测试用例，使用双元素 Map 确保问题被覆盖。还统一了测试代码中凭证对象的构建方式，全部改用 `StorageCredential.create()` 工厂方法。
