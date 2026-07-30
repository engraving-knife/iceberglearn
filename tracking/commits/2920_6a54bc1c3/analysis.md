# 提交 2920：Core: Add idempotency-key-lifetime to ConfigResponse (#14649)

## 提交信息

- **序号**：2920 / 4088
- **哈希**：6a54bc1c379d5af78a2dc746fa66c5f0706d0838
- **短哈希**：6a54bc1c3
- **日期**：2025-11-25 15:00:13 -0800
- **作者**：Huaxin Gao
- **提交说明**：Core: Add idempotency-key-lifetime to ConfigResponse
- **PR/Issue**：#14649

## 总体目的

在 REST 协议中，幂等性键（Idempotency-Key）是一种机制，允许客户端在重试请求时避免重复执行副作用操作。服务端通过记住一段时间内的 Idempotency-Key 来识别重复请求并返回缓存结果，而非重新执行。`ConfigResponse` 是 REST catalog 客户端在初始化时获取的服务端配置响应，此前它包含 defaults、overrides 和 endpoints 等配置项。

本提交为 `ConfigResponse` 增加了 `idempotency-key-lifetime` 字段，这是一个可选的 ISO-8601 持续时间字符串（如 `PT30M` 表示 30 分钟）。服务端通过此字段告知客户端其支持 Idempotency-Key 语义以及键的有效期。这使客户端能够知道何时可以安全地重试 mutation 请求（如 commit、create table 等），以及服务端会缓存多长时间的幂等结果，从而提升 REST catalog 在网络不稳定场景下的可靠性。

## 如何达成设计目的

设计上在 `ConfigResponse` 数据模型中新增可选的 `idempotencyKeyLifetime` 字段（使用 `@Nullable` 注解），在 Builder 中添加 `withIdempotencyKeyLifetime` 方法。在 `ConfigResponseParser` 中实现序列化（非 null 时写入 `idempotency-key-lifetime` 字段）和反序列化（存在非 null 值时解析）。同时在 `JsonUtil` 中新增 `getDurationStringOrNull` 工具方法，使用 `java.time.Duration.parse` 验证持续时间字符串格式，确保传入的值是合法的 ISO-8601 持续时间。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/responses/ConfigResponse.java` (+29/-2 lines)

**修改目的**：在 ConfigResponse 模型中添加 idempotencyKeyLifetime 字段。

**工作逻辑**：
新增 `private String idempotencyKeyLifetime` 字段并加 `@Nullable` 注解，构造函数增加对应参数。新增 `idempotencyKeyLifetime()` 访问器方法，Javadoc 说明返回 ISO-8601 持续时间字符串或 null。Builder 中新增 `withIdempotencyKeyLifetime(String)` 方法，`build()` 时传入构造函数。toString 中也添加该字段。

### `core/src/main/java/org/apache/iceberg/rest/responses/ConfigResponseParser.java` (+10/-0 lines)

**修改目的**：实现 idempotency-key-lifetime 字段的序列化和反序列化。

**工作逻辑**：
定义常量 `IDEMPOTENCY_KEY_LIFETIME = "idempotency-key-lifetime"`。序列化时，若 `response.idempotencyKeyLifetime()` 非 null 则写入字符串字段。反序列化时，若 `json.hasNonNull(IDEMPOTENCY_KEY_LIFETIME)` 则调用新增的 `JsonUtil.getDurationStringOrNull` 方法解析并通过 Builder 设置。

### `core/src/main/java/org/apache/iceberg/util/JsonUtil.java` (+18/-0 lines)

**修改目的**：新增工具方法验证并获取 ISO-8601 持续时间字符串。

**工作逻辑**：
新增 `getDurationStringOrNull(String property, JsonNode node)` 方法。先通过 `getStringOrNull` 获取字符串值，若为 null 返回 null。然后尝试 `java.time.Duration.parse(value)` 验证格式，若解析失败抛出 `IllegalArgumentException`，错误消息格式为 `Cannot parse to a duration string value: %s: %s`。验证通过则返回原始字符串（而非 Duration 对象，因为 ConfigResponse 存储的是字符串）。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestConfigResponseParser.java` (+43/-0 lines)

**修改目的**：测试 idempotency-key-lifetime 的序列化往返和格式校验。

**工作逻辑**：
`idempotencyLifetimeOnly` 测试验证仅含 idempotency-key-lifetime 的 ConfigResponse 序列化为 `{"defaults":{},"overrides":{},"idempotency-key-lifetime":"PT30M"}` 并完成往返。`invalidIdempotencyLifetime` 测试验证非法格式（`"not-a-duration"` 和空字符串 `""`）抛出 IllegalArgumentException 并检查错误消息。

### `core/src/test/java/org/apache/iceberg/util/TestJsonUtil.java` (+23/-0 lines)

**修改目的**：测试 getDurationStringOrNull 工具方法。

**工作逻辑**：
测试 null 字段返回 null、null 值返回 null、合法值 `"PT30M"` 返回原字符串、非法值 `"30M"` 抛异常、空字符串抛异常，覆盖各种边界场景。

## 总结

本提交为 REST catalog 配置响应增加了 `idempotency-key-lifetime` 字段，使服务端能够声明对 Idempotency-Key 的支持及有效期。这是提升 REST catalog 可靠性的基础设施改动，配合后续的客户端幂等重试机制，可以在网络不稳定时安全重试 mutation 请求。实现中通过 JsonUtil 新增的 Duration 格式校验确保了配置值的正确性，测试覆盖全面。
