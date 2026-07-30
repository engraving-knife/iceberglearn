# 提交 1536 4eb9f7ffa 分析

## 提交信息
- 哈希：4eb9f7ffa9b0f4b0adee7169588d81f46149af9f
- 日期：2024-12-26（Thu Dec 26 04:06:18 2024 +0900）
- 作者：Yuya Ebihara <ebyhry@gmail.com>
- 消息：Core: Replace deprecated Schema.toString with SchemaFormatter (#11867)

## 总体目的

Apache Avro 的 `org.apache.avro.Schema` 类提供了 `toString(boolean pretty)` 方法，用于将 schema 序列化为 JSON 字符串。在较新的 Avro 版本中，该方法被标记为 `@Deprecated`，官方推荐改用 `org.apache.avro.SchemaFormatter` API 来格式化 schema（例如 `SchemaFormatter.getInstance("json/pretty").format(schema)`），后者提供了更清晰、可扩展的格式化机制。

Iceberg 的 `core` 模块测试 `TestSchemaConversions` 中 `testComplexSchema()` 方法在构建一个复杂的 Iceberg schema 并通过 `AvroSchemaUtil.convert(...)` 转为 Avro Schema 后，调用了 `.toString(true)` 来"行使"该 schema（结果被丢弃，本质是一处冒烟测试，验证转换 + 序列化不抛异常）。由于该方法已弃用，构建时会触发 deprecation 告警。

本提交将该处调用替换为未弃用的 `SchemaFormatter.getInstance("json/pretty").format(...)`，消除告警并与新版 Avro 推荐用法对齐。这是与提交 1534（替换 Guava Files 弃用方法）同一波"清理弃用 API"工作的一部分。

## 如何达成设计目的

仅修改一个测试文件中的一处调用。新增 `org.apache.avro.SchemaFormatter` 的 import，将 `AvroSchemaUtil.convert(schema, "newTableName").toString(true)` 替换为 `SchemaFormatter.getInstance("json/pretty").format(AvroSchemaUtil.convert(schema, "newTableName"))`。

### 修改详情

#### `core/src/test/java/org/apache/iceberg/avro/TestSchemaConversions.java`

**修改目的**：消除 `testComplexSchema()` 中 Avro `Schema.toString(true)` 弃用调用。

**工作逻辑**：

该测试构建一个含嵌套 struct、map、list 的复杂 Iceberg schema（字段含 `preferences`/`locations`/`points`/`doubles`/`properties` 等），转为 Avro Schema 后调用序列化方法验证整体流程不报错。修改前：

```java
AvroSchemaUtil.convert(schema, "newTableName").toString(true);
```

修改后：

```java
SchemaFormatter.getInstance("json/pretty")
    .format(AvroSchemaUtil.convert(schema, "newTableName"));
```

并新增 import：
```java
import org.apache.avro.SchemaFormatter;
```

`Schema.toString(true)` 中的 `true` 表示 pretty print（缩进格式化）。`SchemaFormatter.getInstance("json/pretty")` 获取一个以 JSON 美化格式输出的 formatter，其 `format(schema)` 返回等价的 JSON 字符串。语义完全一致，但走的是 Avro 推荐的非弃用 API。返回值同样被丢弃（测试目的仅是触发序列化路径）。

## 小结

- **成效**：消除了 `TestSchemaConversions` 中 Avro `Schema.toString(true)` 的弃用调用，改用 `SchemaFormatter`，避免构建告警并保持与新版 Avro 兼容。
- **影响范围**：仅 1 个测试文件、1 处调用、1 行 import，不进入发布产物，对运行时无影响。
- **回迁到 1.4.x 的注意事项**：属于代码整洁/兼容性改进，不修复任何 bug，**无需回迁**。若 1.4.x 依赖的 Avro 版本尚未弃用 `toString(boolean)`，回迁反而可能因 `SchemaFormatter` API 不存在而编译失败——需确认 1.4.x 的 Avro 版本再决定。
