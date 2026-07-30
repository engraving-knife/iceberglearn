# 提交 2900：REST: Make plan status consistent with the SPEC

## 提交信息

- **序号**：2900 / 4088
- **哈希**：f045d5a38ed12cc8b2ffd39709a458d9d9a64f0c
- **短哈希**：f045d5a38
- **日期**：2025-11-20 07:09:23 -0800
- **作者**：Prashant Singh
- **提交说明**：REST: Make plan status consistent with the SPEC
- **PR/Issue**：#14642

## 总体目的

本提交旨在修复 Java REST 实现中扫描规划状态字段名称与 OpenAPI 规范（SPEC）不一致的问题。

在 Iceberg REST Catalog 的 OpenAPI 规范中，`CompletedPlanningResult`、`CompletedPlanningWithIDResult` 等响应模型使用 `status` 作为 JSON 字段名来标识规划状态（如 `"status": "completed"`）。然而，Java 端的两个响应序列化/反序列化解析器（`FetchPlanningResultResponseParser` 和 `PlanTableScanResponseParser`）在实现中使用了 `"plan-status"` 作为 JSON 字段名，这导致 Java 客户端/服务器与规范定义不兼容——其他语言的客户端或直接遵循 SPEC 的实现将无法正确解析这些响应。

这是一个规范性（spec-compliance）缺陷。由于异步规划（async planning）功能是新引入的，字段名不一致会影响跨语言互操作性，因此需要尽早修复，使 Java 实现与 SPEC 对齐。

## 如何达成设计目的

该提交的核心修改非常直接：将两个解析器类中的常量 `PLAN_STATUS = "plan-status"` 改为 `STATUS = "status"`，从而在 JSON 序列化与反序列化时使用正确的字段名。

修改涉及两个主代码文件和两个测试文件：
- 主代码：`FetchPlanningResultResponseParser` 和 `PlanTableScanResponseParser` 中所有引用 `PLAN_STATUS` 常量的位置（序列化 `writeStringField` 和反序列化 `JsonUtil.getString`）都改为使用 `STATUS`。
- 测试：对应的测试类中所有硬编码的 JSON 字符串中 `"plan-status"` 键名也都同步改为 `"status"`，以验证新的行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/responses/FetchPlanningResultResponseParser.java` (+3/-3 lines)

**修改目的**：将 FetchPlanningResult 响应中 plan-status 字段名改为 status。

**工作逻辑**：
将常量定义从 `private static final String PLAN_STATUS = "plan-status";` 改为 `private static final String STATUS = "status";`。在序列化方法中，`gen.writeStringField(PLAN_STATUS, ...)` 改为 `gen.writeStringField(STATUS, ...)`；在反序列化方法中，`JsonUtil.getString(PLAN_STATUS, json)` 改为 `JsonUtil.getString(STATUS, json)`。

### `core/src/main/java/org/apache/iceberg/rest/responses/PlanTableScanResponseParser.java` (+3/-3 lines)

**修改目的**：将 PlanTableScan 响应中 plan-status 字段名改为 status。

**工作逻辑**：
与上述文件相同的修改模式。常量 `PLAN_STATUS = "plan-status"` 改为 `STATUS = "status"`，序列化的 `writeStringField` 和反序列化的 `getString` 调用均同步更新。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestFetchPlanningResultResponseParser.java` (+5/-6 lines)

**修改目的**：更新测试中期望的 JSON 字符串，将 `"plan-status"` 改为 `"status"`。

**工作逻辑**：
测试中所有硬编码的 JSON 断言字符串，例如 `"{\"plan-status\":\"submitted\"}"` 改为 `"{\"status\":\"submitted\"}"`，以及 `"{\"plan-status\": \"someStatus\"}"` 改为 `"{\"status\": \"someStatus\"}"` 等，确保测试与修改后的字段名一致。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestPlanTableScanResponseParser.java` (+12/-12 lines)

**修改目的**：更新测试中期望的 JSON 字符串，将 `"plan-status"` 改为 `"status"`。

**工作逻辑**：
与上一个测试文件类似的修改，覆盖了多种场景（completed、submitted、cancelled、failed 等状态），将所有 JSON 断言字符串中的 `"plan-status"` 键改为 `"status"`。包括格式化 JSON（带空格的 `"plan-status" : "completed"`）和紧凑 JSON（`"plan-status":"completed"`）两种形式。

## 总结

本提交修复了一个规范合规性问题：Java REST 实现中扫描规划响应的 JSON 字段名 `plan-status` 与 OpenAPI SPEC 中定义的 `status` 不一致。修改涉及两个解析器类（序列化/反序列化逻辑）及对应的两个测试类，将字段名统一改为 `status`。由于异步规划是新功能，此修复确保了 Java 实现与其他遵循 SPEC 的客户端/服务器的互操作性。
