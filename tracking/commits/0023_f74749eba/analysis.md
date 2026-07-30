# 提交 0023：OpenAPI: uniqueItems is not valid on type object (#8751)

## 提交信息

- **序号**：0023 / 4088
- **哈希**：f74749eba1bdd8e1d045eb268c4eb9cb27a01f2e
- **短哈希**：f74749eba
- **日期**：2023-10-09 16:09:58 +0200
- **作者**：Johan Henriksson
- **提交说明**：OpenAPI: uniqueItems is not valid on type object (#8751)
- **PR/Issue**：#8751

## 总体目的

这个提交修复了 Iceberg REST Catalog OpenAPI 规范文件中的一处不符合 OpenAPI / JSON Schema 规范的定义：在 `UpdateNamespacePropertiesRequest` 的 `updates` 字段上错误地使用了 `uniqueItems: true`，而该字段类型为 `object`，`uniqueItems` 仅对 `array` 类型有效。

根据 OpenAPI 3.x（以及它所继承的 JSON Schema Draft）规范，`uniqueItems` 是一个用于约束数组元素唯一性的关键字，只能出现在 `type: array` 的 schema 上。把它放在 `type: object` 上属于无效约束，会被严格校验的 OpenAPI 工具链（如代码生成器、规范校验器、Swagger UI 等）报告为告警或错误，可能在生成客户端 SDK 时产生异常行为，也会让规范在合规性检查中失败。`updates` 字段本身是一个 `additionalProperties` 定义字符串值的映射对象（示例 `{"owner": "Hank Bendickson"}`），其"键唯一"的语义已由 object 的天然属性保证，根本不需要 `uniqueItems`。

本提交删除这一行无效约束，使规范恢复合规，提升 Iceberg REST Catalog API 定义的规范性与下游工具链的兼容性。

## 如何达成设计目的

改动极小：仅删除 `open-api/rest-catalog-open-api.yaml` 中 `updates` 字段定义下的 `uniqueItems: true` 一行。该字段其余定义（`type: object`、`example`、`additionalProperties` 描述字符串值类型）保持不变。这是一次针对规范合规性的精确单点修正，不涉及任何 API 行为变化。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：移除 `UpdateNamespacePropertiesRequest.updates` 字段上非法的 `uniqueItems: true` 约束，使该处 schema 符合 OpenAPI/JSON Schema 规范。

**工作逻辑**：`updates` 字段定义在 `components.schemas.UpdateNamespacePropertiesRequest` 下，表示要设置/更新的命名空间属性键值对，类型为 `object`，值通过 `additionalProperties` 声明为 `string`。修改前该字段同时声明了 `uniqueItems: true`，但 `uniqueItems` 仅适用于 `type: array`，对 object 无意义且违规；object 的键天然唯一，已满足"唯一"语义。删除该行后，字段语义不变，规范通过校验。

## 小结

修正 REST Catalog OpenAPI 规范中 `updates`（object 类型）字段上非法的 `uniqueItems: true` 约束，恢复规范合规性，避免下游代码生成与校验工具链告警。
