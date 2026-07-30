# 提交 2249：Core, REST: Add context aware response parsing (#13191)

## 提交信息

- **序号**：2249 / 4088
- **哈希**：5b50afe8f2b4cf16af6c015625385021b44aca14
- **短哈希**：5b50afe8f
- **日期**：2025-06-17 16:25:58 -0500
- **作者**：Prashant Singh
- **提交说明**：Core, REST: Add context aware response parsing
- **PR/Issue**：#13191

## 总体目的

本提交为 Iceberg REST 客户端引入了上下文感知的响应解析能力。在原有的 REST 客户端实现中，HTTP 响应的 JSON 反序列化仅依赖于 Jackson 的 `ObjectMapper.readValue()` 方法，使用固定的 ObjectMapper 实例直接解析响应体为目标类型。这种方式无法在解析过程中注入额外的上下文信息（例如当前请求的元数据、配置参数等），限制了 REST 客户端在更复杂场景下的灵活性。

随着 Iceberg REST Catalog 的功能不断扩展，某些响应类型的反序列化可能需要额外的上下文信息才能正确完成。例如，某些字段的反序列化可能依赖于请求路径、查询参数或会话状态等信息。本提交通过引入 `ParserContext` 概念，使得调用方可以在发起 REST 请求时传入解析上下文，该上下文会被转换为 Jackson 的 `InjectableValues`，从而在反序列化过程中可以被注入到目标对象中。

## 如何达成设计目的

- 新增 `ParserContext` 类作为解析上下文的载体，内部维护一个不可变的键值对 Map，可通过 Builder 模式构建，并支持转换为 Jackson 的 `InjectableValues.Std`。
- 在 `RESTClient` 接口中新增带 `ParserContext` 参数的 `get()` 和 `post()` 方法重载，默认实现中如果 parserContext 不为 null 则抛出 `UnsupportedOperationException`，保持向后兼容。
- 在 `BaseHTTPClient` 抽象类中实现这些新方法，将其委托给新的带 ParserContext 的抽象 `execute()` 方法。
- 在 `HTTPClient`（具体实现类）中实现新的 `execute()` 方法，使用 `ObjectReader` 缓存机制提升性能，并在 parserContext 非空时通过 `with()` 方法注入 InjectableValues。
- 在 `RESTCatalogAdapter` 中适配新的方法签名。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ParserContext.java` (新增, +64/0 lines)

**修改目的**：创建解析上下文类，作为传递解析时附加信息的载体。

**工作逻辑**：`ParserContext` 使用内部 Builder 模式构建不可变的 `Map<String, Object>` 数据。核心方法 `toInjectableValues()` 将内部数据转换为 Jackson 的 `InjectableValues.Std`，后者可在反序列化时通过 `@JacksonInject` 注解将值注入到目标对象的字段中。`isEmpty()` 方法用于判断上下文是否包含有效数据，以决定是否需要应用注入逻辑。

### `core/src/main/java/org/apache/iceberg/rest/RESTClient.java` (修改, +27/0 lines)

**修改目的**：在 REST 客户端接口中声明支持 ParserContext 的方法。

**工作逻辑**：新增 `get()` 和 `post()` 的 default 方法重载，接收 `ParserContext` 参数。默认实现检查 parserContext 是否为 null，若非 null 则抛出 `UnsupportedOperationException("Parser context is not supported")`，否则委托给原有的不带 parserContext 的方法。这种设计确保了向后兼容——现有实现无需修改即可继续工作，只有需要支持上下文解析的实现才需要覆盖这些方法。

### `core/src/main/java/org/apache/iceberg/rest/BaseHTTPClient.java` (修改, +32/0 lines)

**修改目的**：在抽象基类中实现 ParserContext 相关方法，并声明新的抽象 execute 方法。

**工作逻辑**：新增 `get()` 和 `post()` 的具体实现，构建 HTTPRequest 后调用新的带 ParserContext 的 `execute()` 抽象方法。同时声明了一个新的抽象方法 `execute(HTTPRequest, Class<T>, Consumer<ErrorResponse>, Consumer<Map<String, String>>, ParserContext)`，强制子类实现带上下文的执行逻辑。

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (修改, +20/-1 lines)

**修改目的**：在具体 HTTP 客户端中实现上下文感知的响应解析。

**工作逻辑**：新增 `ConcurrentMap<Class<?>, ObjectReader> objectReaderCache` 缓存，按响应类型缓存 ObjectReader 实例以提升性能。原有的 `execute()` 方法委托给新的带 ParserContext 的 `execute()` 方法，传入空的 ParserContext。新方法中，通过 `objectReaderCache.computeIfAbsent()` 获取或创建 ObjectReader，若 parserContext 非空且非空数据，则通过 `reader.with(parserContext.toInjectableValues())` 创建带注入值的 reader，最后调用 `reader.readValue()` 解析响应体。相比直接使用 `mapper.readValue()`，ObjectReader 是线程安全的且支持配置注入值。

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (修改, +11/0 lines)

**修改目的**：适配新的接口方法，确保 RESTCatalogAdapter 的实现与接口变更一致。

### `.palantir/revapi.yml` (修改, +6/0 lines)

**修改目的**：记录 API 兼容性变更，声明新增的抽象方法为可接受的破坏性变更。

**工作逻辑**：在 revapi 配置中添加一条 `java.method.abstractMethodAdded` 规则，标记 `BaseHTTPClient` 中新增的带 ParserContext 的 execute 抽象方法为已知且可接受的 API 变更，理由是"Add context aware parsing"。

## 总结

本提交为 Iceberg REST 客户端引入了上下文感知解析的基础设施，通过 `ParserContext` 和 Jackson 的 `InjectableValues` 机制，使得 REST 响应的反序列化可以依赖额外的上下文信息。这是一个基础性改动，为后续更复杂的 REST Catalog 功能（如需要请求上下文的响应类型）铺平了道路。设计上保持了向后兼容，现有实现不受影响，同时通过 ObjectReader 缓存优化了解析性能。
