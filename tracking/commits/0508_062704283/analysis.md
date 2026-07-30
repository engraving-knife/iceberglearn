# 提交 0508：Core: Make InMemoryFileIO map shared across instances (#9722)

## 提交信息

| 字段 | 内容 |
|------|------|
| 序号 | 0508 |
| 完整哈希 | 062704283918ae843d0e40b3f279f1482f0835e5 |
| 短哈希 | 062704283 |
| 日期 | 2024-02-16 |
| 作者 | Drew Gallardo <dru@amazon.com> |
| 提交说明 | Core: Make InMemoryFileIO map shared across instances (#9722) |
| PR | #9722 |

## 总体目的

本提交修复了 `InMemoryFileIO` 在测试场景下的一个架构缺陷。`InMemoryFileIO` 是 Iceberg 提供的一个内存文件 IO 实现，主要用于单元测试和 REST Catalog 测试。在修改之前，每个 `InMemoryFileIO` 实例都持有一个独立的实例字段 `inMemoryFiles`（一个 `ConcurrentMap<String, byte[]>`），这意味着不同实例之间无法共享文件。当测试代码创建一个 `InMemoryFileIO` 实例写入文件后，另一个 `InMemoryFileIO` 实例无法读取到这些文件。

这一缺陷在 REST Catalog 测试中尤为突出：REST Catalog 架构中，客户端和服务器端各自创建自己的 `FileIO` 实例。服务器端使用 `FileIO` 读写元数据文件，客户端也通过 `FileIO` 操作文件。如果两个实例各自持有独立的文件映射，那么服务器端写入的元数据文件对客户端不可见，反之亦然，导致 REST Catalog 的端到端测试无法正常工作。

本提交将文件映射从实例字段改为静态字段（`static final`），使所有 `InMemoryFileIO` 实例共享同一个全局文件存储。这确保了在同一 JVM 内（即测试环境中），无论创建多少个 `InMemoryFileIO` 实例，它们都能访问同一组文件。为避免不同测试之间的文件路径冲突，测试代码同时改为使用随机生成的 UUID 路径。

## 如何达成设计目的

实现非常直接：将 `InMemoryFileIO` 中的 `private final Map<String, byte[]> inMemoryFiles` 改为 `private static final Map<String, byte[]> IN_MEMORY_FILES`，并更新所有引用处。同时更新测试：为每个测试方法生成随机文件路径（基于 UUID）以避免共享状态导致的测试间干扰，新增一个专门验证跨实例共享的测试，并在 REST Catalog 测试中配置使用 `InMemoryFileIO` 作为文件 IO 实现类，使服务器端和客户端共享同一文件存储。

## 修改详情

### core/src/main/java/org/apache/iceberg/inmemory/InMemoryFileIO.java

**修改目的**：将文件存储映射改为静态共享。

**工作逻辑**：将实例字段改为静态字段：
```java
// 修改前：
private final Map<String, byte[]> inMemoryFiles = Maps.newConcurrentMap();

// 修改后：
private static final Map<String, byte[]> IN_MEMORY_FILES = Maps.newConcurrentMap();
```
随后在 `addFile`、`fileExists`、`newInputFile`、`deleteFile` 四个方法中，将所有 `inMemoryFiles` 引用改为 `IN_MEMORY_FILES`。`closed` 布尔字段仍保持为实例字段（每个实例独立跟踪是否已关闭），但文件数据本身全局共享。使用 `Maps.newConcurrentMap()` 保证线程安全。

### core/src/test/java/org/apache/iceberg/inmemory/TestInMemoryFileIO.java

**修改目的**：适配共享存储带来的测试隔离需求，并验证跨实例共享行为。

**工作逻辑**：

1. 移除了类级别的固定路径字段 `String location = "s3://foo/bar.txt"`。

2. 新增 `randomLocation()` 私有方法，返回 `"s3://foo/" + UUID.randomUUID()`，确保每个测试使用唯一路径，避免共享静态 Map 导致的测试间干扰。

3. 在 `testBasicEndToEnd`、`testCreateNoOverwrite`、`testOverwriteBeforeAndAfterClose` 三个既有测试方法中，改用 `randomLocation()` 生成路径。

4. 新增 `testFilesAreSharedAcrossMultipleInstances` 测试：创建第一个 `InMemoryFileIO` 实例写入文件，然后创建第二个实例，验证第二个实例能通过 `fileExists` 检测到该文件存在，明确断言"Files should be shared across all InMemoryFileIO instances"。

### core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java

**修改目的**：使 REST Catalog 测试利用共享的 InMemoryFileIO，让服务器端和客户端能互相访问文件。

**工作逻辑**：在 `restCatalog` 初始化的配置 Map 中，新增两项配置：
```java
CatalogProperties.FILE_IO_IMPL,
"org.apache.iceberg.inmemory.InMemoryFileIO",
```
这样 REST Catalog 服务器端和客户端都会使用 `InMemoryFileIO`，由于文件映射现在是静态共享的，服务器端写入的元数据文件可以被客户端读取，反之亦然。这使得 REST Catalog 的测试流程（客户端提交元数据 -> 服务器端持久化 -> 客户端加载）能在一个共享的内存文件系统中正常运转。

## 小结

本提交通过一个简洁的改动（实例字段改静态字段）解决了 `InMemoryFileIO` 跨实例无法共享文件的问题，使 REST Catalog 测试能正确运行。改动虽小但影响关键——它打通了测试环境中服务器端与客户端的文件 IO 通道。测试侧通过引入随机 UUID 路径来保证测试隔离性，并通过新增专门测试来明确验证共享语义。
