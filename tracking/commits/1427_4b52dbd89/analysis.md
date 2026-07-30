# 提交 1427：Core,Open-API: Don't expose the `last-column-id` (#11514)

## 提交信息

- **序号**：1427 / 4088
- **哈希**：4b52dbd89bf583f1bbdbf0b7a73b62404812ee11
- **短哈希**：4b52dbd89
- **日期**：2024-11-25（Mon Nov 25 10:25:56 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Core,Open-API: Don't expose the `last-column-id` (#11514)
- **PR/Issue**：#11514
- **Co-author**：Eduard Tudenhoefner <etudenhoefner@gmail.com>

## 总体目的

Iceberg 的 schema 演进依赖 `last-column-id`（表已分配过的最大列 ID），用于保证每次新增列都分配未使用的 ID。在 `MetadataUpdate.AddSchema` 这个元数据更新动作里，原本要求调用方传入 `lastColumnId`，并在 REST OpenAPI 规范里把 `last-column-id` 作为 `AddSchemaUpdate` 的字段暴露给客户端。

作者在评审 iceberg-rust 时意识到，这个设计是错误的：

1. `last-column-id` 本质上是表元数据的内部状态，应该由服务端统一维护，而不是让客户端计算并提交。客户端计算 `last-column-id` 容易出错，尤其在并发提交 / 冲突重试场景：当两个 schema 变更冲突时，客户端必须基于最新元数据重新计算 `last-column-id`，一旦算错就会破坏 ID 唯一性。
2. 服务端在应用 `AddSchema` 更新时，完全可以基于当前表元数据的 `lastColumnId` 和新 schema 的 `highestFieldId()` 自行推导出正确的新 `lastColumnId`，无需客户端介入。

本提交把 `last-column-id` 从公共 API 中"软下线"：
- Java 侧新增不传 `lastColumnId` 的重载构造/方法，旧签名标记 `@Deprecated`（1.8.0 起，1.9.0 或 2.0.0 移除）。
- 服务端 / 内部调用全部切换到新签名，自动用 `Math.max(currentLastColumnId, schema.highestFieldId())` 计算新值。
- REST OpenAPI 规范把 `last-column-id` 字段标记为 `deprecated: true`，描述改为"DEPRECATED for REMOVAL"。
- 同步更新所有相关测试。

## 如何达成设计目的

1. **`MetadataUpdate.AddSchema`**：新增 `AddSchema(Schema schema)` 构造，内部调用 `this(schema, schema.highestFieldId())`；旧构造 `AddSchema(Schema, int)` 标记 `@Deprecated`。
2. **`TableMetadata`**：
   - 新增 `updateSchema(Schema newSchema)`，内部用 `Math.max(this.lastColumnId, newSchema.highestFieldId())` 作为新 `lastColumnId`；旧 `updateSchema(Schema, int)` 标记 `@Deprecated`。
   - `Builder` 新增 `addSchema(Schema)` 重载，同样自动计算 `lastColumnId`；旧 `addSchema(Schema, int)` 标记 `@Deprecated` 并删除原 TODO 注释。
3. **`SchemaUpdate.commit()`**：从 `base.updateSchema(apply(), lastColumnId)` 改为 `base.updateSchema(apply())`。
4. **`RESTSessionCatalog`**：把 `new MetadataUpdate.AddSchema(schema, schema.highestFieldId())` 改为 `new MetadataUpdate.AddSchema(schema)`。
5. **`ViewMetadata`**：`addSchema` 内部不再手动计算 `highestFieldId`，直接用 `new MetadataUpdate.AddSchema(newSchema)`，并删除私有的 `highestFieldId()` 辅助方法。
6. **OpenAPI**：`rest-catalog-open-api.yaml` 给 `last-column-id` 加 `deprecated: true` 并改写描述；`rest-catalog-open-api.py`（Python 模型）同步描述。
7. **测试**：把所有 `new AddSchema(schema, x)` / `updateSchema(schema, x)` / `addSchema(schema, x)` 改成新签名，并相应调整断言（例如 `lastColumnId` 现在会等于 `max(旧, schema.highestFieldId)`，某些用例的预期值从 3 变为 2）。

## 修改详情

### `aws/src/test/java/org/apache/iceberg/aws/glue/TestIcebergToGlueConverter.java`

**修改目的**：测试适配新签名。

**工作逻辑**：`tableMetadata.updateSchema(newSchema, 3)` → `tableMetadata.updateSchema(newSchema)`。该 schema 最高字段 ID 为 1，新签名内部取 `max(旧 lastColumnId, 1)`，逻辑等价。

### `core/src/main/java/org/apache/iceberg/MetadataUpdate.java`

**修改目的**：`AddSchema` 提供无 `lastColumnId` 重载，旧签名废弃。

**工作逻辑**：
```java
public AddSchema(Schema schema) {
  this(schema, schema.highestFieldId());
}

@Deprecated
public AddSchema(Schema schema, int lastColumnId) { ... }
```
注意：新构造直接用 `schema.highestFieldId()` 作为 `lastColumnId` 传给旧构造。这在"客户端只提交单次 schema 新增、服务端再 max 一次"的语义下是安全的——服务端 `TableMetadata.updateSchema(Schema)` 会再做一次 `max`。

### `core/src/main/java/org/apache/iceberg/SchemaUpdate.java`

**修改目的**：`commit()` 改用新签名。

**工作逻辑**：`base.updateSchema(apply(), lastColumnId)` → `base.updateSchema(apply())`。`SchemaUpdate` 内部维护的 `lastColumnId` 字段不再被使用（但仍保留，后续可清理）。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：服务端自动计算 `lastColumnId`，旧签名废弃。

**工作逻辑**：
- 新增 `updateSchema(Schema newSchema)`：
  ```java
  return new Builder(this)
      .setCurrentSchema(newSchema, Math.max(this.lastColumnId, newSchema.highestFieldId()))
      .build();
  ```
- 旧 `updateSchema(Schema, int)` 标记 `@Deprecated`。
- `Builder.addSchema(Schema schema)` 新增，内部 `addSchemaInternal(schema, Math.max(lastColumnId, schema.highestFieldId()))`。
- 旧 `Builder.addSchema(Schema, int)` 标记 `@Deprecated`，删除原 `// TODO: remove requirement for newLastColumnId` 注释（TODO 已完成）。

关键点：`Math.max(this.lastColumnId, newSchema.highestFieldId())` 保证 `lastColumnId` 只增不减，即使新 schema 的字段 ID 较小（例如回滚到旧 schema），`lastColumnId` 也不会回退，从而保证 ID 永不复用。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：REST 客户端组装 `AddSchema` 更新时不再传 `lastColumnId`。

**工作逻辑**：`new MetadataUpdate.AddSchema(schema, schema.highestFieldId())` → `new MetadataUpdate.AddSchema(schema)`。

### `core/src/main/java/org/apache/iceberg/view/ViewMetadata.java`

**修改目的**：View 元数据也复用同一逻辑，删除自有的 `highestFieldId()` 辅助方法。

**工作逻辑**：
- `addSchema` 内删除 `int highestFieldId = Math.max(highestFieldId(), newSchema.highestFieldId());`，改为 `changes.add(new MetadataUpdate.AddSchema(newSchema));`。
- 删除私有方法 `private int highestFieldId()`（原实现是遍历所有 schema 取最大）。

### `core/src/test/java/org/apache/iceberg/TestMetadataUpdateParser.java`

**修改目的**：JSON 解析测试适配新语义。

**工作逻辑**：
- 合并 `testAddSchemaFromJson` 和 `testAddSchemaFromJsonWithoutLastColumnId` 为一个 `testAddSchemaFromJson`：JSON 只含 `{"action":"add-schema","schema":...}`（不再带 `last-column-id`），构造 `new AddSchema(schema)` 对比。
- `testAddSchemaToJson` 仍保留带 `last-column-id` 的 JSON 期望（因为序列化时仍会写出该字段，向后兼容），但构造改为 `new AddSchema(schema)`。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`

**修改目的**：`updateSchema` 测试改用新签名，并修正预期 `lastColumnId`。

**工作逻辑**：
- 多处 `updateSchema(schema, n)` → `updateSchema(schema)`。
- 关键修正：原 `differentColumnIdTable = sameSchemaTable.updateSchema(sameSchema2, 3)` 期望 `lastColumnId == 3`；新签名下 `lastColumnId = max(2, sameSchema2.highestFieldId()=2) = 2`，断言改为 `isEqualTo(2)`。
- 后续 `revertSchemaTable` 的 `lastColumnId` 断言也从 3 改为 2。

### `core/src/test/java/org/apache/iceberg/TestUpdateRequirements.java`

**修改目的**：`AddSchema` 构造改用新签名。

**工作逻辑**：3 处批量构造 `new AddSchema(new Schema(), lastColumnId + k)` → `new AddSchema(new Schema())`，对应"冲突检测"和"forReplaceView"两类测试。

### `open-api/rest-catalog-open-api.py`

**修改目的**：Python 模型字段描述同步。

**工作逻辑**：`last_column_id` 的 description 改为"DEPRECATED for REMOVAL..."。

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：OpenAPI 规范把字段标记为废弃。

**工作逻辑**：
```yaml
last-column-id:
  type: integer
  deprecated: true
  description:
    This optional field is **DEPRECATED for REMOVAL** since it more safe to handle this internally,
    and shouldn't be exposed to the clients.
    ...
```

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java`

**修改目的**：Spark 3.5 测试适配新签名。

**工作逻辑**：`base.updateSchema(manyColumnsSchema, manyColumnsSchema.highestFieldId())` → `base.updateSchema(manyColumnsSchema)`，逻辑等价（新签名内部同样取 max）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReadProjection.java`

**修改目的**：Spark 3.5 测试适配新签名。

**工作逻辑**：`readMetadata(desc).updateSchema(expectedSchema, 100)` → `updateSchema(expectedSchema)`。注意：原代码硬编码 `100` 作为 `lastColumnId`，新签名下会变成 `max(旧, expectedSchema.highestFieldId())`，可能小于 100。这对后续测试逻辑（依赖 ID 映射）无影响，因为 `expectedSchema` 的字段 ID 已经在 `reassignIds` 时确定。

## 小结

- **成效**：`last-column-id` 不再要求客户端计算并提交，服务端自动用 `max(当前 lastColumnId, 新 schema highestFieldId)` 维护，避免并发冲突重试时客户端算错导致 ID 复用。OpenAPI 把字段标记为 `deprecated`，引导客户端停止提交。旧的带 `lastColumnId` 的 Java API 标记 `@Deprecated`（1.8.0 起），保留向后兼容。
- **影响范围**：核心 `core` 模块（`MetadataUpdate`、`TableMetadata`、`SchemaUpdate`、`ViewMetadata`、`RESTSessionCatalog`）、`aws` 与 `spark/v3.5` 测试、OpenAPI 规范。属于 API 行为调整，但通过重载 + `@Deprecated` 做到源码与二进制向后兼容。
- **回迁到 1.4.x 的注意事项**：**需谨慎评估是否回迁**。
  - 这是对公共 API（`MetadataUpdate.AddSchema`、`TableMetadata.updateSchema`、`Builder.addSchema`）和 REST OpenAPI 规范的语义调整。1.4.x 是维护分支，通常只接收 bug 修复，不引入 API 变更。
  - 若 1.4.x 的 REST 服务端仍接受客户端提交的 `last-column-id`，回迁本提交的"服务端自动 max"逻辑是安全的（向后兼容），但若同时回迁 OpenAPI 的 `deprecated` 标记，会影响 1.4.x 客户端的契约文档。
  - 建议：1.4.x **不回迁** OpenAPI 的 `deprecated` 标记和 Java API 的 `@Deprecated` 标记，避免在维护分支引入 API 弃用信号；如果 1.4.x 已有客户端算错 `last-column-id` 的 bug，可仅回迁"服务端自动 max"的核心逻辑（`TableMetadata.updateSchema(Schema)` 新方法 + `SchemaUpdate.commit()` 改用新方法），作为静默修复。
  - 测试断言的 `lastColumnId` 数值变化（3→2）需一并回迁，否则 CI 失败。
