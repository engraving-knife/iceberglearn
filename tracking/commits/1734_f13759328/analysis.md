# 提交 1734：OpenAPI: Add RemoveSchemas REST update type (#12022)

## 提交信息

- **序号**：1734 / 4088
- **哈希**：f13759328fd22293f8ef97c7ebe48cf74caa2f9c
- **短哈希**：f13759328
- **日期**：2025-02-14 17:11:01 +0530
- **作者**：gaborkaszab
- **提交说明**：OpenAPI: Add RemoveSchemas REST update type (#12022)
- **PR/Issue**：#12022

## 总体目的

Iceberg REST Catalog API 的 OpenAPI 规范中定义了多种表更新操作类型（TableUpdate），如添加 schema、移除分区规范（RemovePartitionSpecs）、更新统计信息等。此前规范中缺少 `RemoveSchemas` 更新类型，即通过 REST API 移除表中不再需要的 schema 的能力。

Iceberg 表可以拥有多个 schema（多 schema 演进），随着表的演进可能会积累大量历史 schema。提供 `remove-schemas` 更新类型允许客户端通过 REST API 清理不再使用的 schema，保持表元数据的整洁。本提交的目标是在 OpenAPI 规范（YAML 和 Python 模型）中新增 `RemoveSchemasUpdate` 类型定义。

## 如何达成设计目的

提交同时修改了 OpenAPI 规范的两个表示形式：

1. **YAML 规范文件**（`rest-catalog-open-api.yaml`）：定义了 `RemoveSchemasUpdate` schema，包含 `action` 常量字段（`remove-schemas`）和 `schema-ids` 数组字段，并将其添加到 `TableUpdate` 的 oneOf 联合类型和 action 映射中。

2. **Python 模型文件**（`rest-catalog-open-api.py`）：定义了对应的 `RemoveSchemasUpdate` Pydantic 模型类，并将其添加到 `TableUpdate` 的联合类型列表中。

两个文件保持同步，确保规范的一致性。

## 修改详情

### `open-api/rest-catalog-open-api.py`（修改, +6 lines）

**修改目的**：在 Python 模型中新增 `RemoveSchemasUpdate` 类。

**工作逻辑**：
- 新增 `RemoveSchemasUpdate(BaseUpdate)` 类，继承自 `BaseUpdate`。包含两个字段：
  - `action`: 字符串类型，固定为 `'remove-schemas'`（`const=True`）。
  - `schema_ids`: 整数列表，使用别名 `'schema-ids'`（与 YAML 中的 kebab-case 命名一致）。
- 在 `TableUpdate` 类的联合类型列表中添加 `RemoveSchemasUpdate`，位置在 `RemovePartitionSpecsUpdate` 之后、`EnableRowLineageUpdate` 之前。

### `open-api/rest-catalog-open-api.yaml`（修改, +16 lines）

**修改目的**：在 YAML 规范中定义 `RemoveSchemasUpdate` schema 并注册到 TableUpdate。

**工作逻辑**：
- 在 `components/schemas` 中新增 `RemoveSchemasUpdate` 定义，使用 `allOf` 引用 `BaseUpdate`，包含：
  - `required: [schema-ids]`
  - `action`: 字符串常量 `"remove-schemas"`
  - `schema-ids`: 整数数组
- 在 `TableUpdate` 的 `oneOf` 列表中添加对 `RemoveSchemasUpdate` 的 `$ref` 引用。
- 在 `TableUpdate` 的 `action` 属性的映射表中添加 `remove-schemas: '#/components/schemas/RemoveSchemasUpdate'` 条目。

## 小结

- **成效**：在 Iceberg REST Catalog API 的 OpenAPI 规范中新增了 `RemoveSchemasUpdate` 更新类型，使客户端能够通过 REST API 发送 `remove-schemas` 操作来移除表中不再需要的 schema。规范在 YAML 和 Python 两个表示形式中保持同步。
- **影响范围**：仅涉及 OpenAPI 规范文件（YAML 和 Python），不影响任何 Java 实现。REST Catalog 服务端的实际 `remove-schemas` 实现需要在后续提交中完成。
- **回迁到 1.4.x 的注意事项**：此提交仅修改 OpenAPI 规范文档，无代码依赖，回迁风险低。但需注意 1.4.x 分支是否已有对应的服务端实现支持 `remove-schemas` 操作。如果仅回迁规范而服务端不支持该操作，可能导致客户端发送请求后收到错误响应。建议结合服务端实现情况决定是否回迁。
