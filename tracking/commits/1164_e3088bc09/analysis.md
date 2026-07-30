# 提交 1164：Core: Add explicit JSON parser for LoadTableResponse (#11148)

## 提交信息

- **序号**：1164 / 4088
- **哈希**：e3088bc098606ae0dfa727a27ff12a0ff3b6ccb9
- **短哈希**：e3088bc09
- **日期**：2024-09-19（Thu Sep 19 07:48:29 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Add explicit JSON parser for LoadTableResponse (#11148)
- **PR/Issue**：#11148

## 总体目的

Iceberg REST 协议中 `LoadTableResponse` 是服务端返回「加载表」结果的核心响应体，承载 `metadata-location`、`metadata`（`TableMetadata` JSON）和 `config` 三部分。重构前，`LoadTableResponse` 的 JSON 序列化/反序列化完全依赖 Jackson 的注解驱动（默认 ObjectMapper 自动绑定），存在以下问题：

1. **行为不够显式**：与 Iceberg 其他 REST 响应（`ConfigResponse`、`LoadViewResponse`、`ErrorResponse` 等）已经各自拥有显式 `Parser` 类的风格不一致，难以集中管控字段名、必填项与默认行为。
2. **缺少校验**：默认 Jackson 反序列化不会校验 `metadata` 字段必填，缺失时只会得到一个 `metadata=null` 的对象，下游再调用 `tableMetadata()` 才会 NPE，错误延迟暴露。
3. **重复构造开销**：`LoadTableResponse.tableMetadata()` 每次调用都会执行 `TableMetadata.buildFrom(metadata).withMetadataLocation(metadataLocation).build()`，把元数据 + metadata-location 合并成一个新 `TableMetadata`；若调用方多次取用，会反复构建，浪费 CPU 与对象分配。

本提交为 `LoadTableResponse` 引入显式 JSON 解析器 `LoadTableResponseParser`，并把它注册到 `RESTSerializers` 的 ObjectMapper 模块中；同时给 `LoadTableResponse.tableMetadata()` 加缓存字段 `metadataWithLocation`，避免重复构建。这样既统一了 REST 响应解析风格，又在校验时机与性能上得到改善。

## 如何达成设计目的

1. **新增 `LoadTableResponseParser`**：仿照 `ConfigResponseParser`、`LoadViewResponseParser` 的写法，提供静态 `toJson`/`fromJson` 工具方法，直接操作 `JsonGenerator`/`JsonNode`，明确字段名常量、必填校验、可选字段处理。反序列化时若 `metadata` 字段缺失会通过 `JsonUtil.get(METADATA, json)` 立即抛 `IllegalArgumentException("Cannot parse missing field: metadata")`，把错误前置到解析阶段。
2. **在 `RESTSerializers` 注册新的 Serializer/Deserializer**：将 `LoadTableResponse.class` 与 `LoadTableResponseSerializer`/`LoadTableResponseDeserializer` 绑定，让 ObjectMapper 在 REST 客户端/服务端使用时走显式解析器，而不是默认字段反射。
3. **给 `LoadTableResponse` 加缓存字段**：新增 `private TableMetadata metadataWithLocation;`，在 `tableMetadata()` 中懒加载：首次调用时构建「带 metadata-location 的 TableMetadata」并缓存，后续直接返回缓存对象。这样多次调用 `tableMetadata()` 不再重复构建。
4. **新增单元测试 `TestLoadTableResponseParser`**：覆盖 null/空对象/缺字段校验、纯元数据往返、带 config 往返三类场景，验证显式解析器行为符合预期。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadTableResponseParser.java`（新增）

**修改目的**：为 `LoadTableResponse` 提供显式 JSON 序列化/反序列化工具。

**工作逻辑**：
- 字段名常量：`METADATA_LOCATION = "metadata-location"`、`METADATA = "metadata"`、`CONFIG = "config"`，与 REST 协议规范一致。
- 私有构造函数 `LoadTableResponseParser()` 防止实例化。
- `toJson(LoadTableResponse)` / `toJson(LoadTableResponse, boolean pretty)` / `toJson(LoadTableResponse, JsonGenerator)` 三个重载：
  - 校验 `response != null`，否则抛 `IllegalArgumentException("Invalid load table response: null")`。
  - 写起始对象 `{`；若 `metadataLocation()` 非空则写 `metadata-location` 字段；通过 `TableMetadataParser.toJson(...)` 写 `metadata` 字段；若 `config()` 非空则通过 `JsonUtil.writeStringMap(CONFIG, ...)` 写 `config` 字段；写结束对象 `}`。
- `fromJson(String)` / `fromJson(JsonNode)` 两个重载：
  - 校验 `json != null`，否则抛 `IllegalArgumentException("Cannot parse load table response from null object")`。
  - 通过 `json.hasNonNull(METADATA_LOCATION)` 判断并读取 `metadataLocation`；通过 `JsonUtil.get(METADATA, json)` 强制要求 `metadata` 字段存在（缺失会抛 `Cannot parse missing field: metadata`）；调用 `TableMetadataParser.fromJson(...)` 解析为 `TableMetadata`。
  - 若 `metadataLocation` 非空，用 `TableMetadata.buildFrom(metadata).withMetadataLocation(metadataLocation).build()` 把 location 注入 metadata 对象。
  - 用 `LoadTableResponse.builder().withTableMetadata(metadata)` 构造响应；若 `CONFIG` 非空则 `addAllConfig(JsonUtil.getStringMap(CONFIG, json))`；最后 `build()`。

### `core/src/main/java/org/apache/iceberg/rest/RESTSerializers.java`

**修改目的**：把 `LoadTableResponse` 与新解析器绑定到 REST ObjectMapper 模块。

**工作逻辑**：
- 新增 import：`LoadTableResponse`、`LoadTableResponseParser`。
- 在 `module.addDeserializer(ConfigResponse.class, ...)` 之后追加：
  ```java
  .addSerializer(LoadTableResponse.class, new LoadTableResponseSerializer<>())
  .addDeserializer(LoadTableResponse.class, new LoadTableResponseDeserializer<>());
  ```
- 新增两个静态内部类：
  - `LoadTableResponseSerializer<T extends LoadTableResponse> extends JsonSerializer<T>`：`serialize` 方法直接调用 `LoadTableResponseParser.toJson(request, gen)`。
  - `LoadTableResponseDeserializer<T extends LoadTableResponse> extends JsonDeserializer<T>`：`deserialize` 方法读取 `JsonNode` 后调用 `LoadTableResponseParser.fromJson(jsonNode)` 并强转为 `T`。

这样所有走 `RESTSerializers.objectMapper()` 的 REST 客户端/服务端在遇到 `LoadTableResponse` 时都会走显式解析器。

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadTableResponse.java`

**修改目的**：给 `tableMetadata()` 加缓存，避免重复构建带 location 的 `TableMetadata`。

**工作逻辑**：
- 新增字段 `private TableMetadata metadataWithLocation;`。
- `tableMetadata()` 改为：
  ```java
  if (null == metadataWithLocation) {
    this.metadataWithLocation =
        TableMetadata.buildFrom(metadata).withMetadataLocation(metadataLocation).build();
  }
  return metadataWithLocation;
  ```
- 这样首次调用时构建并缓存，后续调用直接返回，避免重复 `buildFrom(...).withMetadataLocation(...).build()` 链路。
- 注意：`metadataWithLocation` 不参与 Jackson 默认序列化（无 getter），不影响 REST 传输；新解析器也只读写 `metadataLocation` 与 `metadata` 两个字段，缓存字段仅用于内存访问优化。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestLoadTableResponseParser.java`（新增）

**修改目的**：覆盖新解析器的核心行为。

**工作逻辑**：3 个测试用例：
- `nullAndEmptyCheck`：验证 `toJson(null)` 抛 `Invalid load table response: null`；`fromJson((JsonNode) null)` 抛 `Cannot parse load table response from null object`；`fromJson("{}")` 抛 `Cannot parse missing field: metadata`。
- `missingFields`：验证仅有 `metadata-location` 而无 `metadata` 时抛 `Cannot parse missing field: metadata`。
- `roundTripSerde`：构造一个 v2 表的 `TableMetadata`（含 UUID、location、schema、partition spec、sort order），用 `LoadTableResponseParser.toJson(response, true)` 序列化，比对精确的期望 JSON 字符串（含 `metadata-location`、`metadata` 完整结构、`format-version`/`table-uuid`/`location`/`schemas`/`partition-specs`/`sort-orders`/`properties`/`refs`/`snapshots`/`statistics`/`partition-statistics`/`snapshot-log`/`metadata-log` 等字段）；再反序列化回来重新序列化，比对一致性，确保 round-trip 不丢字段。
- `roundTripSerdeWithConfig`：在 `roundTripSerde` 基础上加 `addAllConfig(ImmutableMap.of("key1", "val1", "key2", "val2"))`，验证 `config` 字段也能正确往返。

## 小结

- **成效**：`LoadTableResponse` 的 JSON 处理与 Iceberg 其他 REST 响应风格统一，由显式 `Parser` 类管理字段名、必填校验与序列化顺序；`metadata` 必填字段缺失能在解析阶段立即报错，而不是延后到 `tableMetadata()` 调用时 NPE；`tableMetadata()` 加缓存后多次调用不再重复构建，性能改善；新增 203 行测试覆盖核心行为。
- **影响范围**：core 模块 4 个文件（3 改 1 新增）。属于 REST 协议层内部改进，对外 JSON 格式与字段语义保持不变，向后兼容。
- **回迁到 1.4.x 的注意事项**：
  - 这是 REST 客户端/服务端序列化层的健壮性与一致性改进，**对运行时行为兼容**，可以安全回迁到 1.4.x。
  - 回迁时需连同 `LoadTableResponseParser` 新文件、`RESTSerializers` 的注册、`LoadTableResponse` 的缓存字段、测试类一并 cherry-pick；缺一会编译失败或测试漏覆盖。
  - 注意 `LoadTableResponse` 的 `metadataWithLocation` 字段是可变状态，回迁后需确认 1.4.x 现有调用方不会在 `tableMetadata()` 返回后修改该对象（事实上原代码每次新建，调用方持有的引用不互相影响；改为缓存后多个调用方共享同一对象，若有调用方在拿到 `TableMetadata` 后做 mutating 操作可能引入新问题——不过 `TableMetadata` 本身是不可变的，所以风险很低）。
  - 若 1.4.x 已有用户依赖默认 Jackson 反序列化的某些容错行为（如缺失 `metadata` 字段返回 null 对象），回迁后会变成抛 `IllegalArgumentException`，需评估调用方是否能正确处理。
