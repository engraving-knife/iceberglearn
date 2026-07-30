# 提交 3323：Core: Add properties to InMemoryFileIO (#15469)

## 提交信息

- **序号**：3323 / 4088
- **哈希**：1a20b546c7bd266ea381e7600689bcead61a8d30
- **短哈希**：1a20b546c
- **日期**：2026-02-27
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Add properties to InMemoryFileIO (#15469)
- **PR/Issue**：#15469

## 总体目的

`FileIO` 是 Iceberg 中抽象底层存储读写的接口，定义了 `initialize(Map<String, String>)` 与 `properties()` 两个默认方法（默认分别为空操作与返回空 map），用于在 FileIO 实例化后传入配置并随后查询这些配置。真实的 FileIO 实现（如 `S3FileIO`、`HadoopFileIO` 等）通常会保存并暴露这些属性，供需要读取 IO 配置的代码使用。

`InMemoryFileIO` 是用于测试的内存版 FileIO 实现，它把所有"文件"保存在一个静态的并发 map 中。问题在于，它此前并未覆写 `initialize` 与 `properties()`，因此这两个方法始终是默认的空实现——即使被初始化，也无法保存或回传任何配置。而 `InMemoryCatalog`（使用 `InMemoryFileIO` 的内存 catalog）在 `initialize(...)` 中接收到了 catalog 的全部属性（包含 warehouse 等配置），却直接 `new InMemoryFileIO()` 构造 IO 实例，绕过了 `CatalogUtil.loadFileIO(...)` 这条会调用 `initialize(properties)` 的标准加载路径。结果是：通过 `InMemoryCatalog` 创建的 `FileIO` 实例的 `properties()` 永远为空，无法反映传入的 catalog 属性。这在测试场景下会导致依赖 `fileIO.properties()` 的代码路径（例如读取 IO 级配置）在内存 catalog 下行为与真实 catalog 不一致，影响测试的真实性。

本提交为 `InMemoryFileIO` 补上属性存储能力，并让 `InMemoryCatalog` 通过 `CatalogUtil.loadFileIO(...)` 标准化加载 IO，使内存实现与其它 FileIO 实现在属性处理上保持一致。

## 如何达成设计目的

在 `InMemoryFileIO` 中新增 `SerializableMap` 类型的 `properties` 字段（初值为空），并覆写 `initialize(Map)` 把传入属性以 `SerializableMap.copyOf(...)` 保存、覆写 `properties()` 返回其不可变视图。使用 `SerializableMap` 而非普通 `Map` 是因为 `FileIO` 实现需要可序列化（在 Spark/Flink 等分布式引擎中 IO 实例会被序列化分发到各节点），普通 map 字段可能不可序列化。在 `InMemoryCatalog.initialize(...)` 中把 `new InMemoryFileIO()` 替换为 `CatalogUtil.loadFileIO(InMemoryFileIO.class.getName(), properties, null)`，后者会实例化该类并调用其 `initialize(properties)`，从而把 catalog 属性透传给 FileIO。

## 修改详情

### `core/src/main/java/org/apache/iceberg/inmemory/InMemoryFileIO.java` (+13/-0 lines)

**修改目的**：为内存 FileIO 增加属性存储与查询能力。

**工作逻辑**：
新增 import `ImmutableMap` 与 `SerializableMap`。新增字段 `private SerializableMap<String, String> properties = SerializableMap.copyOf(ImmutableMap.of())`，以空 map 初始化。覆写 `initialize(Map<String, String> props)`，把入参用 `SerializableMap.copyOf(props)` 保存到该字段，保证可序列化且不可被外部修改。覆写 `properties()` 返回 `properties.immutableMap()`，对外提供只读视图。这两处覆写使 `InMemoryFileIO` 满足 `FileIO` 接口关于配置传递的契约，与真实实现一致。

### `core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java` (+1/-1 lines)

**修改目的**：通过标准加载路径创建 FileIO 以透传属性。

**工作逻辑**：
在 `initialize(String name, Map<String, String> properties)` 中，把 `this.io = new InMemoryFileIO()` 改为 `this.io = CatalogUtil.loadFileIO(InMemoryFileIO.class.getName(), properties, null)`。`CatalogUtil.loadFileIO` 会按实现类名反射实例化 `InMemoryFileIO` 并调用其 `initialize(properties)`，把 catalog 初始化时收到的全部属性（如 warehouse 等）传入 FileIO。这样 `InMemoryCatalog` 持有的 IO 实例即可通过 `properties()` 暴露这些属性，与其它 catalog 通过 `loadFileIO` 加载 IO 的行为保持一致。

### `core/src/test/java/org/apache/iceberg/inmemory/TestInMemoryFileIO.java` (+10/-0 lines)

**修改目的**：验证 InMemoryFileIO 的属性初始化与查询。

**工作逻辑**：
新增 `properties()` 测试：构造 `InMemoryFileIO`，用 `ImmutableMap.of("key1", "value1", "key2", "value2")` 调用 `io.initialize(...)`，随后断言 `io.properties()` 与传入的 map 相等，确认属性被正确保存并可读。

## 总结

本提交为测试用的 `InMemoryFileIO` 补齐了 `initialize`/`properties()` 的属性存储能力，并改用 `CatalogUtil.loadFileIO` 在 `InMemoryCatalog` 中标准化加载 IO，使内存 catalog 持有的 FileIO 能正确透传并暴露 catalog 属性。改动小但消除了内存实现与真实 FileIO 实现在属性处理上的不一致，提升了基于内存 catalog 的测试真实性与一致性。
