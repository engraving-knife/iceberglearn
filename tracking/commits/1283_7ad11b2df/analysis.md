# 提交 1283：Core: Snapshot `summary` map must have `operation` key (#11354)

## 提交信息

- **序号**：1283 / 4088
- **哈希**：7ad11b2df1a266d29f9e4f6bb5b499cb68c0afb7
- **短哈希**：7ad11b2df
- **日期**：2024-10-25（Fri Oct 25 17:23:44 2024 -0400）
- **作者**：Kevin Liu <kevinjqliu@users.noreply.github.com>
- **合著者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Snapshot `summary` map must have `operation` key (#11354)
- **PR/Issue**：#11354

## 总体目的

Iceberg 快照（`Snapshot`）的 `summary` 是一个 `Map<String, String>`，其中 `operation` 键描述本次快照的操作类型（`append`/`overwrite`/`replace`/`delete` 等，见 `DataOperations`）。Iceberg 在写出快照 JSON 时，总会把 `operation` 写进 `summary` 对象（参见 `Snapshot.toJson`），因此一个合法的快照 summary 必然包含 `operation`。

但旧的 `SnapshotParser.fromJson` 在解析 summary 时，采用的是"遍历字段、若遇到名为 `operation` 的字段才取出"的实现：

```java
while (fields.hasNext()) {
  String field = fields.next();
  if (field.equals(OPERATION)) {
    operation = JsonUtil.getString(OPERATION, sNode);  // 仅在遍历到时取
  } else {
    builder.put(field, ...);
  }
}
```

这导致**若 summary JSON 中缺失 `operation` 键，`operation` 变量保持 `null`，解析静默成功**，得到一个没有操作类型的快照。这与 Iceberg 自身的写出契约不符——缺失 `operation` 的 summary 本就是非法/损坏的数据，理应被拒绝，而非被静默接受。

本提交把 `operation` 的读取提前到遍历之前、改为**无条件**读取（`JsonUtil.getString(OPERATION, sNode)`，缺失即抛 `IllegalArgumentException("Cannot parse missing string: operation")`），从而强制要求 summary 必须含 `operation` 键，使解析行为与写出契约对齐，及早暴露损坏的快照元数据。

## 如何达成设计目的

核心改动是把"按字段名条件取值"改为"无条件取值"：

1. 在进入字段遍历循环**之前**，先调用 `JsonUtil.getString(OPERATION, sNode)` 读取 `operation`。该方法在键缺失时会直接抛出 `IllegalArgumentException`，从而把"缺 operation"从"静默 null"变为"显式失败"。
2. 遍历循环简化为：只把非 `operation` 的字段放入 summary builder（用 `if (!field.equals(OPERATION))` 取代原来的 if/else），`operation` 已在循环前取出，无需在循环内再处理。

这样既收紧了校验，又避免了"operation 既在循环前取、又在循环内取"的重复。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotParser.java`

**修改目的**：强制 summary 必须含 `operation` 键。

**工作逻辑**：在解析 `summary` 节点的逻辑中（`sNode` 为 summary 对象），把

```java
ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
Iterator<String> fields = sNode.fieldNames();
while (fields.hasNext()) {
  String field = fields.next();
  if (field.equals(OPERATION)) {
    operation = JsonUtil.getString(OPERATION, sNode);
  } else {
    builder.put(field, JsonUtil.getString(field, sNode));
  }
}
```

改为

```java
operation = JsonUtil.getString(OPERATION, sNode);   // 无条件取，缺失即抛
ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
Iterator<String> fields = sNode.fieldNames();
while (fields.hasNext()) {
  String field = fields.next();
  if (!field.equals(OPERATION)) {                   // operation 已取，跳过
    builder.put(field, JsonUtil.getString(field, sNode));
  }
}
```

行为变化：summary 缺 `operation` 键时，由"解析成功、operation 为 null"变为"抛 `IllegalArgumentException: Cannot parse missing string: operation`"。

### `core/src/test/java/org/apache/iceberg/TestSnapshotJson.java`

**修改目的**：覆盖新校验行为与既有序列化契约。

**工作逻辑**：

- 新增 import：`assertThatThrownBy`、`TypeReference`、`JsonNode`、`ObjectMapper`、`Map`。
- 新增 `testToJsonWithoutOperation()`：构造一个无 operation 的 `BaseSnapshot`（`operation=null`、`summary=null`），调用 `SnapshotParser.toJson`，断言生成的 JSON 中**不含** `summary` 字段（即无操作时不会写出 summary）。
- 新增 `testToJsonWithOperation()`：构造带 `operation=REPLACE` 与自定义 summary 的快照，序列化后把 JSON 的 `summary` 反序列化回 `Map<String,String>`，断言其等于"原 summary + `operation=replace`"——即序列化时 `operation` 被正确并入 summary。
- 新增 `testJsonConversionSummaryWithoutOperationFails()`：构造一个 summary **缺失** `operation` 键的快照 JSON，断言 `SnapshotParser.fromJson(json)` 抛 `IllegalArgumentException` 且消息为 `"Cannot parse missing string: operation"`，验证新校验生效。

## 小结

- **成效**：`SnapshotParser` 现强制要求快照 summary 含 `operation` 键，缺失时抛 `IllegalArgumentException`，与 Iceberg 自身写出 summary 总是带 `operation` 的契约对齐，能及早暴露损坏/不合规的快照元数据。
- **影响范围**：改动 2 个文件，新增 79 行、删除 3 行。仅涉及 `core` 模块的 `SnapshotParser`（解析路径）及其测试，无写出逻辑变更，不改变合法快照的解析结果。
- **回迁到 1.4.x 的注意事项**：
  - **行为收紧需评估兼容性**：这是一个**校验收紧**型改动——此前某些外部工具或历史数据若写出了"summary 不含 operation"的快照元数据，回迁后读取这类快照会从"静默成功"变为"抛异常"。回迁前应评估 1.4.x 目标用户是否存在这类历史/第三方生成的元数据；若有，可能需要在回迁时配套提供兼容模式或先清理数据。
  - **依赖前置**：本提交依赖 `SnapshotParser` 中 summary 解析的既有结构（`OPERATION` 常量、`JsonUtil.getString`），1.4.x 的 `SnapshotParser` 应已有相同结构，预期可直接 cherry-pick。
  - **风险**：属校验增强，建议回迁后跑 `TestSnapshotJson`（含新增的三个用例）验证。
