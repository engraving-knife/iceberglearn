# 提交 0135：Test: Add a test utility method to programmatically create expected partition specs (#8467)

## 提交信息

- **序号**：0135 / 4088
- **哈希**：e8bb8b502c981f6e5dea27cc6f833655bf79f4c6
- **短哈希**：e8bb8b502
- **日期**：2023-11-07 14:55:41 -0600
- **作者**：roryqi
- **提交说明**：Test: Add a test utility method to programmatically create expected partition specs (#8467)
- **PR/Issue**：#8467

## 总体目的

这个提交为 Iceberg 的测试套件引入一个可编程构造期望分区规格（expected partition spec）的工具方法，并用它替换掉散落在多个 Spark 测试中的 JSON 字符串硬编码。

此前，当测试需要构造一个期望的 `PartitionSpec` 来与实际表的 spec 比较时（例如 `TestAlterTablePartitionFields` 验证"日分区改为小时分区"后表 spec 是否符合预期），普遍采用 `PartitionSpecParser.fromJson(table.schema(), "{ \"spec-id\": 2, \"fields\": [ { \"name\": \"ts_hour\", \"transform\": \"hour\", \"source-id\": 3, \"field-id\": 1001 } ] }")` 的形式。这种写法有几个缺点：第一，JSON 字符串冗长、易错（多余/缺失逗号、引号转义、字段名拼写都难发现）；第二，可读性差，难以一眼看出 transform、source-id、field-id 的对应关系；第三，复制粘贴的痕迹明显，同一份 JSON 在 4 个 Spark 版本（3.2/3.3/3.4/3.5）× 4 个测试类中重复出现，维护成本高。

本次改动在 `api` 模块的 [`TestHelpers`](../../api/src/test/java/org/apache/iceberg/TestHelpers.java) 中新增 `ExpectedSpecBuilder` 构造器与 `newExpectedSpecBuilder()` 工厂方法，用流式 API（`withSchema` / `withSpecId` / `addField` / `build`）取代 JSON 解析，并批量改造 4 个 Spark 版本下的同名测试类，使期望 spec 的构造统一、可读、可复用。

## 如何达成设计目的

整体设计思路是"提供 builder + 复用现有 `UnboundPartitionSpec`"。`ExpectedSpecBuilder` 内部委托给 [`UnboundPartitionSpec.builder()`](../../api/src/main/java/org/apache/iceberg/UnboundPartitionSpec.java)，把 schema 单独保存（`UnboundPartitionSpec` 不持有 schema），在 `build()` 时调用 `unboundPartitionSpecBuilder.build().bind(schema)` 把未绑定的 spec 绑定到真实 schema 上得到最终的 `PartitionSpec`。这样既复用了 core/api 既有能力（`UnboundPartitionSpec` 已支持按 transform 字符串、source-id、field-id、name 添加字段），又提供了适合测试场景的简洁 API。改动结构是：1 个新增工具类 + 4 个 Spark 版本（v3.2/v3.3/v3.4/v3.5）下各 4 个测试文件（共 16 个测试文件）的等价替换。

## 修改详情

### [`api/src/test/java/org/apache/iceberg/TestHelpers.java`](../../api/src/test/java/org/apache/iceberg/TestHelpers.java)

**修改目的**：新增 `ExpectedSpecBuilder` 内部类与 `newExpectedSpecBuilder()` 静态工厂方法。

**工作逻辑**：

- 新增静态方法 `public static ExpectedSpecBuilder newExpectedSpecBuilder()`，返回 `new ExpectedSpecBuilder()`。
- 新增公共静态内部类 `ExpectedSpecBuilder`，字段包括：
  - `private final UnboundPartitionSpec.Builder unboundPartitionSpecBuilder;`（构造时初始化为 `UnboundPartitionSpec.builder()`）
  - `private Schema schema;`（待绑定 schema）
- 流式方法：
  - `withSchema(Schema newSchema)`：保存 schema，返回 `this`。
  - `withSpecId(int newSpecId)`：委托 `unboundPartitionSpecBuilder.withSpecId(newSpecId)`。
  - `addField(String transformAsString, int sourceId, int partitionId, String name)`：委托 `unboundPartitionSpecBuilder.addField(transformAsString, sourceId, partitionId, name)`——显式指定 field-id 的版本。
  - `addField(String transformAsString, int sourceId, String name)`：委托 `addField(transformAsString, sourceId, name)`——不指定 field-id 的版本。
  - `build()`：`Preconditions.checkNotNull(schema, "Field schema is missing")`，然后 `unboundPartitionSpecBuilder.build().bind(schema)` 返回绑定后的 `PartitionSpec`。
- 由于 `TestHelpers` 在 `api` 模块，而 `UnboundPartitionSpec`、`Schema`、`PartitionSpec`、`Preconditions` 都在 `api` 模块，依赖关系完全在模块内，符合该工具类供所有下游模块测试共享使用的定位。

### Spark 测试文件批量改造（v3.2 / v3.3 / v3.4 / v3.5 各 4 个文件）

涉及文件（每个 Spark 版本下结构相同）：

- `spark/v3.<x>/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAlterTablePartitionFields.java`
- `spark/v3.<x>/spark/src/test/java/org/apache/iceberg/spark/source/TestForwardCompatibility.java`
- `spark/v3.<x>/spark/src/test/java/org/apache/iceberg/spark/source/TestMetadataTablesWithPartitionEvolution.java`
- `spark/v3.<x>/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java`

**修改目的**：把所有 `PartitionSpecParser.fromJson(schema, "{...}")` 调用替换为 `TestHelpers.newExpectedSpecBuilder()...build()`。

**工作逻辑**（以 v3.5 的 `TestAlterTablePartitionFields` 为例）：

原写法（验证 ALTER PARTITION 把日分区改为小时分区后期望 spec）：

```java
expected = PartitionSpecParser.fromJson(
    table.schema(),
    "{\n  \"spec-id\" : 2,\n  \"fields\" : [ {\n    \"name\" : \"ts_hour\",\n    \"transform\" : \"hour\",\n    \"source-id\" : 3,\n    \"field-id\" : 1001\n  } ]\n}");
```

新写法：

```java
expected = TestHelpers.newExpectedSpecBuilder()
    .withSchema(table.schema())
    .withSpecId(2)
    .addField("hour", 3, 1001, "ts_hour")
    .build();
```

等价但更紧凑、字段含义清晰（transform=hour、source-id=3、field-id=1001、name=ts_hour）。改动同时移除 `import org.apache.iceberg.PartitionSpecParser;`，新增 `import org.apache.iceberg.TestHelpers;`。

在 `TestForwardCompatibility` 中，原来的静态字段 `UNKNOWN_SPEC` 和 `FAKE_SPEC` 也从 JSON 改为 builder 构造，例如：

```java
private static final PartitionSpec UNKNOWN_SPEC =
    org.apache.iceberg.TestHelpers.newExpectedSpecBuilder()
        .withSchema(SCHEMA)
        .withSpecId(0)
        .addField("zero", 1, "id_zero")
        .build();
```

这里使用了不带 field-id 的 `addField` 重载，对应 `UnboundPartitionSpec` 自动分配 field-id 的语义，与原 JSON 中未指定 `field-id` 的行为一致。注意部分 `TestForwardCompatibility` 改动中用了 `org.apache.iceberg.TestHelpers.newExpectedSpecBuilder()` 的全限定名形式（因静态字段初始化处不便加 import 或为减少改动），而 `TestAlterTablePartitionFields` 等则采用 `import + 简短名` 形式。

整个 16 个文件的改动是纯重构，期望 spec 的实际内容（spec-id、字段列表、transform、source-id、field-id、name）与原 JSON 完全等价，不改变测试断言的语义。

## 小结

通过在 `api` 模块的 `TestHelpers` 中新增 `ExpectedSpecBuilder` 并用它替换 16 个 Spark 测试文件中冗长易错的 JSON 字符串，使期望分区规格的构造统一为流式、类型安全的 API，显著提升测试可读性与可维护性。
