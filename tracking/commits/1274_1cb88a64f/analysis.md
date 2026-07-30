# 提交 1274：Core: Add LoadCredentialsResponse class/parser (#11339)

## 提交信息

- **序号**：1274 / 4088
- **哈希**：1cb88a64f8a065c87eb875cf08cfc70941a2fd05
- **短哈希**：1cb88a64f
- **日期**：2024-10-24 12:33:27 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Add LoadCredentialsResponse class/parser (#11339)
- **PR/Issue**：#11339

## 总体目的

Iceberg REST Catalog 协议中，`LoadTableResponse` 和 `LoadViewResponse` 都可能携带 `storage-credentials` 字段，用于向客户端下发存储访问凭证（vended credentials）。此前这两个响应解析器各自内联了一段相同的 credentials 解析逻辑：读取 `storage-credentials` 数组、校验是否为数组、逐个用 `CredentialParser.fromJson` 解析并加入 builder。这段逻辑重复出现，且 Iceberg REST 协议还规划了独立的 `/v1/credentials` 端点（专门加载凭证，对应 `LoadCredentialsResponse`），但项目缺少对应的响应类与解析器。

本提交新增 `LoadCredentialsResponse` 接口及其解析器 `LoadCredentialsResponseParser`，并在 `RESTSerializers` 中注册对应的 Jackson 序列化/反序列化器。同时把 `LoadTableResponseParser` 和 `LoadViewResponseParser` 中重复的 credentials 解析逻辑改为复用 `LoadCredentialsResponseParser.fromJson(json).credentials()`，消除重复并为后续实现独立凭证加载端点奠定基础。

## 如何达成设计目的

1. 新建 `LoadCredentialsResponse` 接口（Immutables 生成实现），持有一个 `List<Credential> credentials()`，实现 `RESTResponse`，`validate()` 为空（无强制校验）。
2. 新建 `LoadCredentialsResponseParser`，提供 `toJson` / `fromJson` 方法，复用 `CredentialParser` 处理单个凭证，JSON 字段名为 `storage-credentials`。
3. 在 `RESTSerializers` 中注册 `LoadCredentialsResponse`（及 `ImmutableLoadCredentialsResponse`）的 Jackson Serializer/Deserializer，使其可在 REST 通信中自动序列化。
4. 重构 `LoadTableResponseParser` 和 `LoadViewResponseParser`：把内联的 credentials 数组解析替换为 `builder.addAllCredentials(LoadCredentialsResponseParser.fromJson(json).credentials())`。
5. 新增 `TestLoadCredentialsResponseParser` 测试覆盖 null 校验、缺失字段、完整 round-trip 序列化。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadCredentialsResponse.java` (new file, +34 lines)

**修改目的**：定义加载凭证响应的接口类型。

**工作逻辑**：
```java
@Value.Immutable
public interface LoadCredentialsResponse extends RESTResponse {
  List<Credential> credentials();

  @Override
  default void validate() {
    // nothing to validate
  }
}
```
使用 Immutables 的 `@Value.Immutable` 注解，编译时生成 `ImmutableLoadCredentialsResponse`。接口继承 `RESTResponse` 但不强制校验（凭证列表可为空）。

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadCredentialsResponseParser.java` (new file, +77 lines)

**修改目的**：提供 `LoadCredentialsResponse` 的 JSON 序列化/反序列化能力。

**工作逻辑**：
- 常量 `STORAGE_CREDENTIALS = "storage-credentials"` 对应 JSON 字段名。
- `toJson(response, gen)`：写开始对象，写数组字段 `storage-credentials`，逐个调用 `CredentialParser.toJson` 写入凭证，结束数组、结束对象。
- `fromJson(JsonNode json)`：校验非 null，取出 `storage-credentials` 节点，校验为数组，遍历用 `CredentialParser.fromJson` 解析并加入 `ImmutableLoadCredentialsResponse.Builder`。
- `fromJson(String json)` 通过 `JsonUtil.parse` 委托给上述方法。
- 对入参做 Preconditions 校验（response 非 null、json 非 null）。

### `core/src/main/java/org/apache/iceberg/rest/RESTSerializers.java` (+25/-2 lines)

**修改目的**：在 Jackson 模块中注册 `LoadCredentialsResponse` 的序列化器/反序列化器。

**工作逻辑**：
- 新增 import：`ImmutableLoadCredentialsResponse`、`LoadCredentialsResponse`、`LoadCredentialsResponseParser`。
- 在 module 构建链上追加：
```java
.addSerializer(LoadCredentialsResponse.class, new LoadCredentialsResponseSerializer<>())
.addSerializer(ImmutableLoadCredentialsResponse.class, new LoadCredentialsResponseSerializer<>())
.addDeserializer(LoadCredentialsResponse.class, new LoadCredentialsResponseDeserializer<>())
.addDeserializer(ImmutableLoadCredentialsResponse.class, new LoadCredentialsResponseDeserializer<>())
```
- 新增内部静态类 `LoadCredentialsResponseSerializer<T>` 和 `LoadCredentialsResponseDeserializer<T>`，分别委托给 `LoadCredentialsResponseParser.toJson/fromJson`。同时注册 Immutable 实现类，是因为 Immutables 生成的实际类型是 `ImmutableLoadCredentialsResponse`，Jackson 默认按运行时类型匹配 serializer。

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadTableResponseParser.java` (+2/-6 lines)

**修改目的**：复用 `LoadCredentialsResponseParser` 消除重复的 credentials 解析逻辑。

**工作逻辑**：
```java
if (json.hasNonNull(STORAGE_CREDENTIALS)) {
- JsonNode credentials = JsonUtil.get(STORAGE_CREDENTIALS, json);
- Preconditions.checkArgument(credentials.isArray(), "Cannot parse credentials from non-array: %s", credentials);
- for (JsonNode credential : credentials) {
-   builder.addCredential(CredentialParser.fromJson(credential));
- }
+ builder.addAllCredentials(LoadCredentialsResponseParser.fromJson(json).credentials());
}
```
原 6 行内联逻辑压缩为 1 行复用。注意 `LoadCredentialsResponseParser.fromJson(json)` 会从同一 json 节点读取 `storage-credentials`，因此语义等价。

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadViewResponseParser.java` (+2/-6 lines)

**修改目的**：与 `LoadTableResponseParser` 相同的复用重构。

**工作逻辑**：
```java
if (json.hasNonNull(STORAGE_CREDENTIALS)) {
- // ... 同样的内联解析逻辑
+ builder.addAllCredentials(LoadCredentialsResponseParser.fromJson(json).credentials());
}
```
注意此处 builder 方法名是 `addAllCredentials`（LoadView 用复数 credentials，LoadTable 用单数 credential，但都是批量添加）。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestLoadCredentialsResponseParser.java` (new file, +112 lines)

**修改目的**：为 `LoadCredentialsResponseParser` 添加单元测试。

**工作逻辑**：
- `nullCheck()`：验证 `toJson(null)` 抛 IllegalArgumentException（"Invalid load credentials response: null"），`fromJson((JsonNode) null)` 抛 IllegalArgumentException（"Cannot parse load credentials response from null object"）。
- `missingFields()`：验证 `{}` 和 `{"x": "val"}` 都抛 IllegalArgumentException（"Cannot parse missing field: storage-credentials"）。
- `roundTripSerde()`：构造包含 3 个凭证（S3、两个 GCS）的响应，序列化为 JSON 后断言与预期字符串完全一致，再反序列化断言与原对象相等。预期 JSON 格式为 `storage-credentials` 数组，每个元素含 `prefix` 和 `config` 对象。

## 总结

这是一次为支持 REST Catalog 独立凭证加载端点做准备的基础设施提交。新增 `LoadCredentialsResponse` 及其解析器、Jackson 序列化器，并把 `LoadTableResponseParser` / `LoadViewResponseParser` 中重复的 credentials 解析逻辑统一委托给新解析器，消除代码重复。测试覆盖充分（null、缺失字段、完整 round-trip）。属于为后续功能（独立 `/v1/credentials` 端点）铺路的代码整理与扩展，同时改善了既有代码的可维护性。
