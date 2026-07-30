# 提交 3234：Add DataLakeFileSystemClient constructor in ADLSFileIO (#14966)

## 提交信息

- **序号**：3234 / 4088
- **哈希**：42abc6b3e21814be0d02eabcfebc3528a0129491
- **短哈希**：42abc6b3e
- **日期**：2026-02-10
- **作者**：Sarthak Singh
- **提交说明**：Add DataLakeFileSystemClient constructor in ADLSFileIO (#14966)
- **PR/Issue**：#14966

## 总体目的

本提交为 Iceberg 的 Azure Data Lake Storage (ADLS) Gen2 文件 IO 实现新增一个接受自定义 `DataLakeFileSystemClient` 供应器（supplier）的构造器。`ADLSFileIO` 是 Iceberg 中用于读写 Azure ADLS Gen2 存储的 `FileIO` 实现。此前 `ADLSFileIO` 仅提供无参构造器，需要通过 `initialize(Map)` 方法配置属性后才能使用，且客户端构建逻辑硬编码在 `client()` 方法中，无法从外部注入自定义的 Azure SDK 客户端实例。

在实际应用场景中，用户可能需要使用自定义认证方式、自定义 HTTP 客户端配置、或测试环境中使用 mock 客户端的 `DataLakeFileSystemClient`。新构造器接受一个 `SerializableFunction<ADLSLocation, DataLakeFileSystemClient>` 函数式接口，使得用户可以在构造时直接传入客户端创建逻辑，无需依赖属性配置。构造器内部立即调用 `initialize()` 确保 `azureProperties` 等字段就绪，避免后续操作出现 NPE。

此外，本提交还引入了客户端缓存机制——按存储账户主机名和容器名组合作为缓存键，通过 `ConcurrentMap` 缓存已创建的客户端实例，避免对同一存储账户/容器重复创建客户端。缓存使用双重检查锁（double-checked locking）延迟初始化，并标记为 `transient` 以确保序列化时不携带缓存的客户端（Azure SDK 客户端通常不可序列化）。

## 如何达成设计目的

通过新增 `ADLSFileIO(SerializableFunction<ADLSLocation, DataLakeFileSystemClient> clientSupplier)` 构造器，存储供应器并立即调用 `initialize(Maps.newHashMap())`。将原有 `client()` 方法中的客户端构建逻辑提取到 `buildClient()` 私有方法，优先使用供应器创建客户端，回退到原有 `DataLakeFileSystemClientBuilder` 逻辑。新增 `clientCache` 字段（`transient volatile`）通过 `computeIfAbsent` 实现线程安全的客户端缓存。新增全面的测试文件验证构造器、序列化、缓存和线程安全性。

## 修改详情

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSFileIO.java` (+37/-0 lines)

**修改目的**：新增自定义客户端构造器和客户端缓存机制。

**工作逻辑**：
新增字段 `clientSupplier`（`SerializableFunction` 类型）和 `clientCache`（`transient volatile Map<String, DataLakeFileSystemClient>`）。新构造器接收供应器函数后立即调用 `initialize(Maps.newHashMap())` 初始化属性，使 `azureProperties` 等字段就绪。`client(ADLSLocation location)` 方法修改为：首先通过双重检查锁延迟初始化 `clientCache` 为 `ConcurrentMap`，然后以 `location.host() + "/" + location.container().orElse("")` 作为缓存键，通过 `computeIfAbsent` 获取或创建客户端。新建私有方法 `buildClient(ADLSLocation location)` 包含原有客户端构建逻辑——若 `clientSupplier` 非空则调用 `clientSupplier.apply(location)` 返回自定义客户端，否则使用 `DataLakeFileSystemClientBuilder` 按 `azureProperties` 配置构建。`transient` 修饰 `clientCache` 确保序列化时不携带 Azure SDK 客户端（不可序列化），反序列化后缓存为 null，下次调用 `client()` 时重新初始化。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/TestADLSFileIO.java` (+296/-0 lines)

**修改目的**：全面验证新构造器、客户端缓存和序列化行为。

**工作逻辑**：
新增测试类包含以下测试用例：`testConstructorWithClientSupplier` 验证构造后 properties 已初始化；`testConstructorWithClientSupplierAndInitialize` 验证构造后仍可调用 `initialize` 更新属性；`testClientSupplierIsUsed` 验证 `client()` 方法实际调用了供应器；`testClientSupplierWithoutInitialize` 验证构造后无需调用 `initialize` 即可使用（无 NPE）；`testNoArgConstructor` 验证无参构造器的原有行为。序列化测试 `testSerializationWithClientSupplier`/`testSerializationWithNoArgConstructor` 使用 `TestHelpers#serializers` 参数化测试，验证序列化往返后属性保留、供应器可被调用。缓存测试 `testClientSupplierIsCachedPerContainer` 和 `testClientCachedPerStorageAccountAndContainer` 验证相同 host+container 的客户端被缓存（供应器仅调用一次），不同 host 或 container 触发新创建。`testClientSupplierCachingIsThreadSafe` 使用 10 个线程并发调用 `client()`，验证供应器仅被调用一次且所有线程获得相同客户端实例。

## 总结

本提交为 `ADLSFileIO` 新增了自定义客户端供应器构造器和线程安全的客户端缓存机制，使测试和自定义认证场景下可以注入自定义 Azure 客户端，避免重复创建客户端的开销。设计上使用 `SerializableFunction` 确保供应器可序列化，`transient` 缓存确保序列化安全，双重检查锁保证线程安全。测试覆盖了功能正确性、序列化、缓存和并发场景。
