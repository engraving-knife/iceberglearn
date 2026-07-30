# 提交 3130：Core: Add RegisterViewRequest, parser, and serializers (#15068)

## 提交信息

- **序号**：3130 / 4088
- **哈希**：7acc150ac57f48fabdd9e9ceeb72b1feaf4c1660
- **短哈希**：7acc150ac
- **日期**：2026-01-19
- **作者**：Ajantha Bhat
- **提交说明**：Core: Add RegisterViewRequest, parser, and serializers (#15068)
- **PR/Issue**：#15068

## 总体目的

本提交为 Iceberg REST Catalog 协议新增"注册视图"（Register View）请求的数据模型、JSON 解析器与 Jackson 序列化器，为后续实现 REST Catalog 的 `registerView` 端点（`POST /v1/{prefix}/namespaces/{namespace}/register_view`）补齐请求侧的序列化基础设施。

Iceberg 的 REST Catalog 规范中，表早已支持 `RegisterTableRequest`：当元数据文件已存在于外部存储时，可通过提供 `name` 与 `metadata-location` 把一个已存在的表"注册"到 catalog，而无需重新创建。视图（View）作为 Iceberg 1.x 起一等公民的对象，同样需要这种注册能力——例如把已存在元数据 JSON 的视图接入某个 catalog，或在迁移/导入场景下复用视图定义。此前 REST 协议层缺少与表对称的 `RegisterViewRequest` 模型与编解码支持，本提交正是补上这一缺口。

本提交只引入请求对象本身、其 JSON parser、在 `RESTSerializers` 中注册的 Jackson 序列化/反序列化器，以及对应的单元测试，属于纯增量、无破坏性的协议层补全。它不改动 catalog 客户端或服务端的实际 register 业务逻辑（那部分由后续提交完成），但为该逻辑提供了序列化前置条件。

## 如何达成设计目的

设计上完全沿用现有 `RegisterTableRequest` / `RegisterTableRequestParser` 的成熟模式：用 Immutables 定义不可变接口，编写独立的 `*Parser` 类负责手写 JSON 读写（保证字段顺序与字段名精确），再在 `RESTSerializers` 中把 parser 包装成 Jackson `JsonSerializer`/`JsonDeserializer` 注册到 `SimpleModule`，使 REST 客户端的对象映射器能自动处理该类型。共改动 4 个文件、新增 209 行，无删除。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/requests/RegisterViewRequest.java` (+35 lines)

**修改目的**：定义注册视图请求的不可变数据模型。

**工作逻辑**：
新接口 `RegisterViewRequest extends RESTRequest`，用 `@Value.Immutable` 标注以生成 `ImmutableRegisterViewRequest`。包含两个字段：`String name()`（视图名）与 `String metadataLocation()`（视图元数据文件位置）。`validate()` 默认实现为空，注释说明因无法构造非法实例故无需校验——字段均为非空 String，由 Immutables 与 parser 保证。这与 `RegisterTableRequest` 结构一致，体现了表/视图协议的对称性。

### `core/src/main/java/org/apache/iceberg/rest/requests/RegisterViewRequestParser.java` (+69 lines)

**修改目的**：提供注册视图请求的 JSON 序列化与反序列化逻辑。

**工作逻辑**：
定义常量 `NAME = "name"` 与 `METADATA_LOCATION = "metadata-location"`（与 REST 规范字段名一致）。`toJson` 系列方法支持普通字符串输出、`pretty` 格式化输出，以及直接写 `JsonGenerator`（供 Jackson 序列化器复用）；写对象时先 `writeStartObject`，依次写 `name` 与 `metadata-location` 两个字符串字段，再 `writeEndObject`。`fromJson` 接受 JSON 字符串或 `JsonNode`，用 `JsonUtil.getString` 读取两个字段（缺失时抛出明确的 `IllegalArgumentException`），并用 `ImmutableRegisterViewRequest.builder()` 构造实例。`toJson` 对入参 null 做 `Preconditions.checkArgument` 校验。

### `core/src/main/java/org/apache/iceberg/rest/RESTSerializers.java` (+26 lines)

**修改目的**：在 REST 的 Jackson 模块中注册 `RegisterViewRequest` 的序列化器与反序列化器。

**工作逻辑**：
新增内部类 `RegisterViewRequestSerializer<T extends RegisterViewRequest>`（`serialize` 委托 `RegisterViewRequestParser.toJson`）与 `RegisterViewRequestDeserializer<T extends RegisterViewRequest>`（`deserialize` 读取 `JsonNode` 后委托 `RegisterViewRequestParser.fromJson`）。在 `RESTSerializers` 的 `addSerializers` 方法中，对 `RegisterViewRequest` 与 `ImmutableRegisterViewRequest` 两个类型分别注册序列化器与反序列化器（共 4 条注册），与已有的 `LoadViewResponse`、`ConfigResponse`、`LoadTableResponse` 等注册方式保持一致，确保无论使用接口类型还是具体 Immutable 类型都能正确编解码。

### `core/src/test/java/org/apache/iceberg/rest/requests/TestRegisterViewRequestParser.java` (+79 lines)

**修改目的**：验证 parser 的边界条件与往返序列化正确性。

**工作逻辑**：
三个测试用例：`nullCheck` 验证 `toJson(null)` 与 `fromJson((JsonNode) null)` 抛出带正确消息的 `IllegalArgumentException`；`missingFields` 验证空对象、缺 `metadata-location`、缺 `name` 时分别抛出对应的缺失字段错误消息；`roundTripSerde` 构造一个含 `view_1` 与示例 metadata 路径的请求，断言 `toJson(request, true)` 等于预期的多行 JSON 字符串，并验证 `fromJson(toJson(...))` 再序列化后仍相等，确保往返不丢信息。

## 总结

本提交为 Iceberg REST Catalog 补齐了注册视图请求的协议层基础设施，新增 `RegisterViewRequest` 不可变模型、`RegisterViewRequestParser` JSON 编解码器，并在 `RESTSerializers` 中注册对应的 Jackson 序列化器，配套单元测试覆盖 null、缺字段与往返场景，为后续实现 REST Catalog 的 `registerView` 端点提供了序列化前置条件，结构与既有的表注册请求完全对称。
