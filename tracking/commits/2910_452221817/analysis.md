# 提交 2910：Align PlanTableScanRequest filter expression with OpenAPI spec (#14657)

## 提交信息

- **序号**：2910 / 4088
- **哈希**：452221817882335fc1afd98ac6f302a269cb08a3
- **短哈希**：452221817
- **日期**：2025-11-22 12:20:08 +0530
- **作者**：Drew Gallardo
- **提交说明**：Align PlanTableScanRequest filter expression with OpenAPI spec
- **PR/Issue**：#14657

## 总体目的

在 Iceberg REST 协议中，`PlanTableScanRequest` 的 `filter` 字段用于传递扫描过滤表达式。然而此前的实现中，filter 表达式被序列化为字符串形式（如 `"filter":"false"`），即先把表达式序列化为 JSON 字符串，再将该字符串作为字段值写入。这导致 filter 字段在 JSON 中是一个字符串类型，而不是一个嵌套的 JSON 对象/值。

这与 Iceberg REST OpenAPI 规范不一致，OpenAPI 规范要求 filter 字段直接是一个表达式对象（如 `"filter":{"type":"eq","term":"id","value":1}` 或 `"filter":false`），而非被字符串包裹。这种不一致会导致严格遵循 OpenAPI 规范的客户端或服务端无法正确解析 filter 字段，造成互操作性问题。

本提交修复了 `PlanTableScanRequestParser` 的序列化和反序列化逻辑，使 filter 字段以原生 JSON 对象/值形式写入和读取，与 OpenAPI 规范对齐。

## 如何达成设计目的

核心改动是在序列化时不再使用 `writeStringField` 将表达式转为字符串写入，而是使用 `writeFieldName` 写入字段名后直接将 `JsonGenerator` 传给 `ExpressionParser.toJson(expr, gen)`，让表达式以原生 JSON 结构写入。反序列化时不再调用 `textValue()` 取字符串再解析，而是直接将 `JsonNode` 传给 `ExpressionParser.fromJson(JsonNode)` 解析。同时更新测试用例中的期望 JSON，并新增了表达式对象和 null filter 的边界测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/requests/PlanTableScanRequestParser.java` (+3/-2 lines)

**修改目的**：修复 filter 字段的序列化/反序列化方式，使其输出原生 JSON 对象而非字符串。

**工作逻辑**：
序列化侧，将 `gen.writeStringField(FILTER, ExpressionParser.toJson(request.filter()))` 改为 `gen.writeFieldName(FILTER); ExpressionParser.toJson(request.filter(), gen);`，这样 filter 直接作为 JSON 对象写入而非字符串。反序列化侧，将 `json.get(FILTER).textValue()` 改为 `json.get(FILTER)`，直接传 JsonNode 给 ExpressionParser.fromJson。

### `core/src/test/java/org/apache/iceberg/rest/requests/TestPlanTableScanRequestParser.java` (+31/-2 lines)

**修改目的**：更新已有测试的期望 JSON 并新增覆盖用例。

**工作逻辑**：
将两处测试期望从 `"filter":"false"` / `"filter":"true"` 改为 `"filter":false` / `"filter":true`，反映布尔表达式现在以原生 JSON 布尔值输出。新增 `roundTripSerdeWithFilterExpression` 测试，验证 `Expressions.equal("id", 1)` 能正确序列化为 `{"type":"eq","term":"id","value":1}` 并完成往返。新增 `testFilterFieldWithExplicitNullThrowsError` 测试，验证 filter 为显式 null 时抛出 IllegalArgumentException。

## 总结

本提交修复了 REST 协议中 filter 表达式序列化与 OpenAPI 规范不一致的问题，将 filter 从字符串包裹形式改为原生 JSON 对象形式。这是一个影响互操作性的重要修复，确保 Java 实现与遵循 OpenAPI 规范的其他实现正确对接。改动简洁精准，并补充了表达式对象和 null 边界的测试覆盖。
