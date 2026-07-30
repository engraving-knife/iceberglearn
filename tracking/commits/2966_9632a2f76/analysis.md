# 提交 2966：AWS: Configure builder for reuse of http connection pool in SDKv2 (#14161)

## 提交信息

- **序号**：2966 / 4088
- **哈希**：9632a2f76061643a6cc51a29acb5d1f4c08cf2d3
- **短哈希**：9632a2f76
- **日期**：2025-12-05
- **作者**：Anurag Mantripragada
- **提交说明**：AWS: Configure builder for reuse of http connection pool in SDKv2 (#14161)
- **PR/Issue**：#14161

## 总体目的

在 AWS SDK v2 中，Iceberg 之前为每个 AWS 客户端构造过程注入的是一个 HTTP 客户端 **builder**（通过 `awsClientBuilder.httpClientBuilder(apacheHttpClientBuilder)`）。这意味着每次创建 S3、Glue、KMS 等 AWS 客户端时，都会由该 builder 现场构建一个独立的 `SdkHttpClient` 实例，并随之建立一套独立的 HTTP 连接池（连接、空闲回收线程、socket 等）。在一个进程中存在大量 catalog/客户端实例的场景下（例如多 catalog、Spark/Flink 引擎并发访问、短生命周期客户端频繁创建），这种"一客户端一连接池"的模式会导致连接数膨胀、文件描述符与内存占用过高，甚至触发连接耗尽或泄漏。

本提交要解决的核心问题就是 HTTP 客户端（及其底层连接池）无法跨 AWS 客户端复用造成的资源浪费与潜在泄漏。动机来自生产环境对 AWS 集成在重负载下的稳定性与资源效率诉求：希望具有相同 HTTP 配置（超时、最大连接数、代理等）的多个 AWS 客户端能共享同一个 `SdkHttpClient` 实例，并在所有引用者都释放后才真正关闭底层连接池。

## 如何达成设计目的

整体思路是引入一个进程内单例的 `HttpClientCache`，配合引用计数（reference counting）来管理 `SdkHttpClient` 的生命周期。新增抽象基类 `BaseHttpClientConfigurations` 承载与缓存交互的公共逻辑；`ApacheHttpClientConfigurations` 与 `UrlConnectionHttpClientConfigurations` 不再直接把 builder 塞给 AWS client builder，而是改为：基于自身全部配置参数生成一个确定性的缓存键，向缓存"获取或创建"一个被引用计数包装的 `ManagedHttpClient`，再通过 `awsClientBuilder.httpClient(managedHttpClient)` 注入已构建好的共享实例。当 AWS 客户端关闭时，`ManagedHttpClient.close()` 会触发引用计数减一，归零时才真正关闭底层 HTTP 客户端并从缓存移除。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/BaseHttpClientConfigurations.java` (+75/-0 lines)

**修改目的**：新增 HTTP 客户端配置抽象基类，封装与 `HttpClientCache` 的交互及共享客户端的注入逻辑。

**工作逻辑**：
该 abstract 类持有一个静态单例 `CACHE = HttpClientCache.instance()`，定义两个抽象方法：`generateHttpClientCacheKey()`（由子类根据全部影响 HTTP 行为的配置项生成缓存键）与 `buildHttpClient()`（仅在缓存未命中时真正构造 `SdkHttpClient`）。公共方法 `configureHttpClientBuilder(T awsClientBuilder)` 的关键改动是把原先"传 builder"改为"传实例"：先 `generateHttpClientCacheKey()` 得到键，再 `CACHE.getOrCreateClient(cacheKey, this::buildHttpClient)` 拿到一个被引用计数管理的 `SdkHttpClient`，最后调用 `awsClientBuilder.httpClient(managedHttpClient)`。这样多个 AWS 客户端构建器在配置相同时会拿到同一个共享实例，而引用计数保证生命周期安全。

### `aws/src/main/java/org/apache/iceberg/aws/HttpClientCache.java` (+203/-0 lines)

**修改目的**：新增进程内单例 HTTP 客户端缓存，基于引用计数管理共享 `SdkHttpClient` 的创建、复用与关闭。

**工作逻辑**：
- **单例**：`instance()` 采用双重检查锁（volatile + synchronized）实现懒加载单例。
- **缓存表**：`ConcurrentMap<String, ManagedHttpClient> clients`，`getOrCreateClient(clientKey, clientFactory)` 用 `computeIfAbsent` 保证每个键只创建一次底层客户端，随后调用 `managedClient.acquire()` 自增引用计数并返回同一包装实例。
- **释放**：`releaseClient(clientKey)` 取出 `ManagedHttpClient` 并调用 `release()`，若返回 true（引用归零、已关闭）则用 `clients.remove(clientKey, managedClient)` 从缓存移除，避免并发下误删新条目。
- **`ManagedHttpClient`**：实现 `SdkHttpClient`，包装真实客户端并做 `synchronized` 引用计数。`acquire()` 在已关闭时抛 `IllegalStateException`，否则 `refCount++`；`release()` 递减，归零时调用 `closeHttpClient()` 真正关闭底层客户端，对负数情况打告警并钳位到 0；对已关闭客户端再次 release 直接返回 false（幂等）。其 `close()` 不直接关底层客户端，而是委托 `HttpClientCache.instance().releaseClient(clientKey)`，使 AWS 客户端的关闭语义转化为"释放一次引用"。`prepareRequest`/`clientName` 直接转发给被包装客户端。还提供 `clear()` 用于测试时清空。

### `aws/src/main/java/org/apache/iceberg/aws/ApacheHttpClientConfigurations.java` (+34/-5 lines)

**修改目的**：将 Apache HTTP 客户端配置改造为可共享、可缓存的形式。

**工作逻辑**：
类改为 `extends BaseHttpClientConfigurations`。原 `configureHttpClientBuilder` 中 `awsClientBuilder.httpClientBuilder(apacheHttpClientBuilder)` 的逻辑下沉为 `@Override protected SdkHttpClient buildHttpClient()`：构建 `ApacheHttpClient.Builder`、配置后 `return apacheHttpClientBuilder.build()` 返回实例（而非 builder）。新增 `@Override protected String generateHttpClientCacheKey()`：用 `Maps.newTreeMap()`（TreeMap 保证键有序、生成字符串确定）收集 `type=apache` 及全部配置项（`connectionTimeoutMs`、`socketTimeoutMs`、`acquisitionTimeoutMs`、`connectionMaxIdleTimeMs`、`connectionTimeToLiveMs`、`expectContinueEnabled`、`maxConnections`、`tcpKeepAliveEnabled`、`useIdleConnectionReaperEnabled`、`proxyEndpoint`），再以 `key=value`（`null` 用 `Objects.toString` 兜底）拼接成 `apache[...]` 形式的键。只有全部配置完全一致才会命中同一缓存条目。

### `aws/src/main/java/org/apache/iceberg/aws/UrlConnectionHttpClientConfigurations.java` (+27/-5 lines)

**修改目的**：将 UrlConnection HTTP 客户端配置同样改造为可共享、可缓存形式。

**工作逻辑**：
与 Apache 版对称：类 `extends BaseHttpClientConfigurations`；`buildHttpClient()` 构建 `UrlConnectionHttpClient.Builder` 并 `return urlConnectionHttpClientBuilder.build()`；`generateHttpClientCacheKey()` 收集 `type=urlconnection` 及 `connectionTimeoutMs`、`socketTimeoutMs`、`proxyEndpoint`（UrlConnection 配置项较少），拼成 `urlconnection[...]`。注意两类客户端通过 `type` 前缀区分，避免 Apache 与 UrlConnection 误共享。

### `aws/src/test/java/org/apache/iceberg/aws/TestHttpClientCache.java` (+276/-0 lines)

**修改目的**：为新增的 `HttpClientCache`/`ManagedHttpClient` 提供覆盖缓存复用、引用计数、并发与边界情况的单元测试。

**工作逻辑**：
使用 Mockito mock `SdkHttpClient` 与工厂。覆盖：单例唯一性；同键二次获取复用同一客户端且工厂只调用一次、引用计数为 2；不同键创建不同客户端；`acquire` 两次后第一次 `release` 不关闭、第二次 `release` 关闭并调用底层 `close()`；已关闭客户端 `acquire` 抛 `IllegalStateException`；`releaseClient` 归零后从 map 移除；10 线程并发同键访问只创建一次且引用计数为 10；`clear()` 关闭并清空全部；重复 release 不会使引用计数为负且只关闭一次。这些用例直接验证了引用计数与并发安全的核心不变量。

### `aws/src/test/java/org/apache/iceberg/aws/TestHttpClientProperties.java` (+53/-14 lines)

**修改目的**：适配"注入实例而非 builder"的新行为，并新增共享资源验证测试。

**工作逻辑**：
原 urlconnection/apache 两个测试将 `ArgumentCaptor<SdkHttpClient.Builder>` 改为 `ArgumentCaptor<SdkHttpClient>`，由 `verify(mockS3ClientBuilder).httpClientBuilder(...)` 改为 `verify(...).httpClient(...)`，并断言捕获到的是 `ManagedHttpClient`、其 `httpClient()` 委托对象分别是 `UrlConnectionHttpClient`/`ApacheHttpClient`。新增 `testApacheHttpClientConfiguredAsSharedResource` 与 `testUrlConnectionHttpClientConfiguredAsSharedResource`，验证 `configureHttpClientBuilder` 确实以 `ManagedHttpClient`（共享资源）形式调用 `httpClient(...)`。

## 总结

本提交通过引入单例 `HttpClientCache` 与引用计数包装 `ManagedHttpClient`，把 AWS SDK v2 的 HTTP 客户端从"每客户端一套 builder/连接池"改为"按配置键共享、按引用计数回收"，显著降低了多客户端场景下的连接池数量与资源占用，并在客户端关闭时安全地延迟释放底层连接池。抽象基类 `BaseHttpClientConfigurations` 让 Apache 与 UrlConnection 两种实现复用同一套缓存与注入逻辑，配套的并发与引用计数测试保障了生命周期正确性。该改动对 Iceberg AWS 集成在高并发、多 catalog 场景下的稳定性与资源效率具有实际价值。
