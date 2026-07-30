# 提交 2092：REST Spec: Remove update to enable row lineage (#12986)

## 提交信息

- **序号**：2092 / 4088
- **哈希**：c4afd783d389f7d87b4b6354c814363a33604895
- **短哈希**：c4afd783d
- **日期**：2025-05-07 09:03:40 -0500
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：REST Spec: Remove update to enable row lineage (#12986)
- **PR/Issue**：#12986

## 总体目的

此前 REST Catalog OpenAPI 规范中定义了一个 `EnableRowLineageUpdate` 更新动作（action 为 `enable-row-lineage`），意图通过一个独立的"更新表"操作来开启行级血缘（row lineage）。但在后续规范演进中（参见紧随其后的 #12982，提交 2093），row lineage 的开启方式改为通过 `TableMetadata` 上的属性/配置项来控制，不再需要一个独立的 update action。

因此本次提交从 REST OpenAPI 规范（`rest-catalog-open-api.yaml` 与对应的 Python 模型 `rest-catalog-open-api.py`）中移除 `EnableRowLineageUpdate` 这一更新类型，与之配套的 discriminator 映射、`TableUpdate` 的 `anyOf` 引用也一并删除。这是为后续 #12982 引入新的 v3 摘要与 row lineage 表达方式扫清障碍的清理性改动。

## 如何达成设计目的

1. 在 YAML 中删除 `EnableRowLineageUpdate` schema 定义及其注释 `# Disabling Row Lineage is Forbidden`。
2. 在 `TableUpdate` 的 `anyOf` 列表中移除对 `EnableRowLineageUpdate` 的 `$ref` 引用。
3. 在 `TableUpdate` 的 `discriminator.mapping` 中移除 `enable-row-lineage: '#/components/schemas/EnableRowLineageUpdate'` 这一行。
4. 在 Python 模型中同步删除 `EnableRowLineageUpdate` 类定义，以及 `TableUpdate` 的 `Union` 中对该类的引用。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (修改, +0/-11 lines)

**修改目的**：从 REST Catalog OpenAPI 规范中移除 `enable-row-lineage` 更新动作。

**工作逻辑**：
- 删除 `EnableRowLineageUpdate` schema 节点（含 `allOf`、`action` 常量字段及注释）。
- 从 `TableUpdate.anyOf` 列表中删除 `- $ref: '#/components/schemas/EnableRowLineageUpdate'`。
- 从 `TableUpdate` 的 `discriminator.mapping` 中删除 `enable-row-lineage: '#/components/schemas/EnableRowLineageUpdate'` 行。

### `open-api/rest-catalog-open-api.py` (修改, +0/-5 lines)

**修改目的**：同步 Python 模型，移除 `EnableRowLineageUpdate` 类。

**工作逻辑**：
- 删除 `class EnableRowLineageUpdate(BaseUpdate): action: str = Field('enable-row-lineage', const=True)`。
- 从 `TableUpdate` 的 `Union[...]` 中删除 `EnableRowLineageUpdate` 引用。

## 总结

本次提交是 row lineage 规范演进过程中的清理步骤，从 REST Catalog OpenAPI 规范（YAML + Python 模型）中删除了 `EnableRowLineageUpdate` 这一独立的更新动作，为紧随其后的 #12982（提交 2093）以 `TableMetadata` 属性方式启用 row lineage 让路。改动纯属规范文本删除，不涉及运行时代码。
