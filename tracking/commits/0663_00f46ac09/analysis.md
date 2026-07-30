# 提交 0663：Core: Introduce ConfigResponseParser

## 提交信息
- **序号**：0663 / 4088
- **哈希**：00f46ac0960237324e69401e98caa79c70407da5
- **短哈希**：00f46ac09
- **日期**：2024-04-05
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Introduce ConfigResponseParser (#9952)
- **PR/Issue**：PR #9952

## 总体目的

本提交为 Iceberg REST Catalog 的 `ConfigResponse`（服务端配置响应）引入专用的 JSON 解析器 `ConfigResponseParser`，并将其注册到 REST 序列化框架中。

Iceberg REST Catalog 协议中，客户端在初始化时会调用 `/v1/config` 端点获取服务端配置，返回的 `ConfigResponse` 包含两部分：`defaults`（默认配置，客户端应合并到自身配置）和 `overrides`（覆盖配置，客户端必须采用这些值覆盖自身配置）。这两个字段都是 `Map<String, String>`，且其中 value 允许为 `null`（表示"显式清空某个配置项"）。

在此提交之前，`ConfigResponse` 的序列化/反序列化依赖 Jackson 的默认机制（基于注解或 ObjectMapper 直接映射）。这种方式存在两个问题：第一，默认的 Jackson 反序列化在遇到类型不匹配（例如 `defaults` 字段不是对象而是数组/字符串）时，抛出的是 `JsonProcessingException`，错误信息晦涩（如 "Cannot deserialize value of type `java.util.LinkedHashMap`"），不利于客户端诊断问题；第二，无法优雅处理 map 中 value 为 null 的场景——Jackson 默认的 `Map<String, String>` 反序列化对 null value 的处理行为与 Iceberg 期望的"保留 null 值"语义不完全一致。

本提交的核心目标是：为 `ConfigResponse` 提供与 Iceberg 其他 REST 响应（如 `LoadTableResponse`、`ErrorResponse` 等）一致的、自定义的、可控的解析器，统一错误处理语义（抛出带清晰消息的 `IllegalArgumentException`），并正确支持 map 中可空的 value。

## 如何达成设计目的

提交遵循 Iceberg 既有的"为每个 REST 响应类型提供独立 Parser"的设计模式（与 `LoadViewResponseParser`、`UpdateTableRequestParser`、`ErrorResponseParser` 等保持一致），整体策略分为四步：

**第一步：新建 `ConfigResponseParser` 类。** 该类提供静态方法 `toJson`/`fromJson`，内部使用 `JsonUtil` 的工具方法完成序列化。序列化时通过 `JsonUtil.writeStringMap` 写出 `defaults` 和 `overrides` 两个 map；反序列化时通过新增的 `getStringMapNullableValues` 读取，并使用 `ConfigResponse.builder()` 构建对象。关键设计是反序列化时对每个字段先检查 `json.hasNonNull(...)`，只在字段存在且非 null 时才填充，从而容忍缺失字段和未知字段（前向兼容）。

**第二步：在 `JsonUtil` 中新增 `getStringMapNullableValues` 方法。** 这是本提交的关键支撑。原有的 `getStringMap` 方法在遇到 null value 时会抛出异常（因为它调用 `getString` 要求非 null），无法满足 ConfigResponse 中 value 可为 null 的语义。新方法使用 `getStringOrNull` 读取每个字段值，允许 value 为 null，从而正确保留"显式清空配置"的语义。同时该方法对非对象类型、缺失字段等做了清晰的参数校验，抛出 `IllegalArgumentException` 而非 Jackson 的晦涩异常。

**第三步：在 `RESTSerializers` 中注册自定义的序列化器/反序列化器。** 新增 `ConfigResponseSerializer` 和 `ConfigResponseDeserializer` 内部类，分别委托给 `ConfigResponseParser.toJson` 和 `ConfigResponseParser.fromJson`，并注册到 Jackson 的 `SimpleModule`。这样所有通过 `ObjectMapper` 处理 `ConfigResponse` 的代码路径都会自动走自定义解析逻辑，与 Iceberg 其他响应类型保持一致的处理范式。

**第四步：更新测试以反映新的错误语义。** 原 `TestConfigResponse` 中针对类型不匹配的测试断言期望 `JsonProcessingException`，现在改为期望 `IllegalArgumentException` 且消息为 "Cannot parse string map from non-object value: ..."。同时新增 `TestConfigResponseParser` 和 `TestJsonUtil` 的测试，覆盖 null/empty/unknown fields、defaults-only、overrides-only、round-trip serde 等场景，特别验证了 null value 的正确往返。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/responses/ConfigResponseParser.java`（新增）
**修改目的**：提供 `ConfigResponse` 的 JSON 序列化/反序列化能力。
**工作逻辑**：
- 常量 `DEFAULTS = "defaults"`、`OVERRIDES = "overrides"` 对应 JSON 字段名。
- `toJson(response, gen)`：先校验 response 非 null，写入起始对象，分别用 `JsonUtil.writeStringMap` 写出 defaults 和 overrides 两个 map，再写入结束对象。`toJson(response)` 和 `toJson(response, pretty)` 是便捷重载，后者通过 `JsonUtil.generate` 支持美化输出。
- `fromJson(json)`：校验 json 非 null，构造 `ConfigResponse.builder()`。对 `defaults` 和 `overrides` 分别用 `json.hasNonNull(...)` 判断是否存在且非 null，存在则用 `getStringMapNullableValues` 读取 map 并填入 builder。最后 `build()` 返回不可变对象。`fromJson(String)` 重载通过 `JsonUtil.parse` 委托。
- 私有构造函数防止实例化（工具类惯例）。

### `core/src/main/java/org/apache/iceberg/util/JsonUtil.java`
**修改目的**：新增支持 null value 的 map 解析方法。
**工作逻辑**：新增 `getStringMapNullableValues(String property, JsonNode node)` 方法。先校验 node 含该 property（否则抛 "Cannot parse missing map"），再校验 pNode 非 null、非 isNull、是对象（否则抛 "Cannot parse string map from non-object value"）。然后用 `Maps.newHashMap()`（可变 map，允许 null value）创建 map，遍历字段名，对每个字段调用 `getStringOrNull(field, pNode)` 读取（null 安全）。返回该 map。与原 `getStringMap`（用 `ImmutableMap.builder()`，不允许 null value）形成互补。同时新增 `Maps` 的 import。

### `core/src/main/java/org/apache/iceberg/rest/RESTSerializers.java`
**修改目的**：将 `ConfigResponse` 的自定义序列化器/反序列化器注册到 Jackson。
**工作逻辑**：导入 `ConfigResponse` 和 `ConfigResponseParser`。在 `registerSerializers` 方法的 module 构建链末尾追加 `addSerializer(ConfigResponse.class, new ConfigResponseSerializer<>())` 和 `addDeserializer(ConfigResponse.class, new ConfigResponseDeserializer<>())`。新增两个静态内部类：`ConfigResponseSerializer<T extends ConfigResponse>` 的 `serialize` 委托给 `ConfigResponseParser.toJson(request, gen)`；`ConfigResponseDeserializer<T extends ConfigResponse>` 的 `deserialize` 读取 `JsonNode` 后委托给 `ConfigResponseParser.fromJson(jsonNode)`。这种"Parser + Serializer/Deserializer 委托"模式与同文件中 `LoadViewResponseSerializer`/`LoadViewResponseDeserializer` 完全一致。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestConfigResponse.java`
**修改目的**：更新既有测试以匹配新的异常语义。
**工作逻辑**：两处针对类型不匹配（defaults 为数组、overrides 为字符串）的断言，由期望 `JsonProcessingException`（消息含 "Cannot deserialize value of type..."）改为期望 `IllegalArgumentException`（消息含 "Cannot parse string map from non-object value: ..."）。反映自定义解析器取代 Jackson 默认行为后的错误信息变化。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestConfigResponseParser.java`（新增）
**修改目的**：全面测试新解析器。
**工作逻辑**：包含 5 个测试：
- `nullAndEmptyCheck`：验证 `toJson(null)` 和 `fromJson((JsonNode) null)` 抛 `IllegalArgumentException`；`fromJson("{}")` 返回空的 ConfigResponse。
- `unknownFields`：验证未知字段被忽略，返回空 response（前向兼容）。
- `defaultsOnly`：验证只含 defaults（含 null value）的 round-trip 序列化/反序列化，比对精确 JSON 字符串。
- `overridesOnly`：对称验证 overrides。
- `roundTripSerde`：验证 defaults + overrides 同时存在且含 null value 的完整 round-trip。所有测试均比对美化后的 JSON 字符串，确保 null value 正确保留。

### `core/src/test/java/org/apache/iceberg/util/TestJsonUtil.java`
**修改目的**：测试新增的 `getStringMapNullableValues` 方法。
**工作逻辑**：新增 `getStringMapNullableValues` 测试，覆盖：缺失字段抛 "Cannot parse missing map"；null 值抛 "Cannot parse string map from non-object value: items: null"；非字符串值（数字 45）抛 "Cannot parse to a string value: b: 45"；正常含 null value 的 map 正确解析；以及通过 `JsonUtil.generate` + `writeStringMap` 序列化后再反序列化的 round-trip。

## 小结
- **成效**：成功为 `ConfigResponse` 引入自定义解析器，统一了错误处理语义（清晰的 `IllegalArgumentException`），正确支持了 map 中 null value 的往返，并与 Iceberg 其他 REST 响应类型的解析模式保持一致。
- **影响范围**：影响 `iceberg-core` 的 REST 模块（`RESTSerializers`、`rest.responses` 包）和工具层（`JsonUtil`）。属于序列化层增强，对运行时行为有轻微改善（错误信息更友好、null value 处理更正确），不改变正常路径的输出格式。
- **回迁到 1.4.x 的注意事项**：回迁需确认 1.4.x 分支中 `ConfigResponse` 类已存在且具有 `builder()`、`defaults()`、`overrides()` 方法，以及 `JsonUtil` 中 `getStringOrNull`、`writeStringMap`、`generate`、`parse` 等方法可用。若 1.4.x 的 `ConfigResponse` 接口与 main 有差异，需同步调整。整体改动内聚于 REST 序列化层，外部依赖少，回迁风险较低。注意 `TestConfigResponse` 中错误信息断言的修改要与新解析器的实际错误消息严格一致。
