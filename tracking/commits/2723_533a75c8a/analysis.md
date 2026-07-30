# 提交 2723：OpenAPI: Fix inconsistent error example names

## 提交信息

- **序号**：2723 / 4088
- **哈希**：533a75c8a50a4c9ade7f22e03b61f2828e91e742
- **短哈希**：533a75c8a
- **日期**：2025-10-08 08:41:55 -0700
- **作者**：Huaxin Gao
- **提交说明**：OpenAPI: Fix inconsistent error example names
- **PR/Issue**：#14248

## 总体目的

在 Iceberg REST Catalog 的 OpenAPI 规范文件中，错误响应的示例（example）名称存在不一致的问题。具体来说，有些 API 端点的 409 Conflict 错误响应中，example 的键名与实际引用的错误类型不匹配。

例如，在创建表（Create Table）的端点中，当返回 409 错误（表已存在）时，错误响应引用的是 `TableAlreadyExistsError` 示例，但 example 的键名却错误地写成了 `NamespaceAlreadyExists`。这种名称不匹配虽然不影响功能（因为 example 通过 `$ref` 引用），但在 API 文档的展示和自动生成的客户端代码中会造成混淆。

此提交修正了三处 example 名称与实际错误类型不匹配的问题。

## 如何达成设计目的

通过在 OpenAPI 规范 YAML 文件中，将三处错误示例的键名从错误的 `NamespaceAlreadyExists` 更正为与 `$ref` 引用相匹配的正确名称。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+3/-3 lines)

**修改目的**：修正三处错误示例名称与引用类型不匹配的问题。

**工作逻辑**：三处修改如下：
1. **创建表端点**（Create Table，约第 593 行）：example 键名从 `NamespaceAlreadyExists` 改为 `TableAlreadyExists`，与 `$ref: '#/components/examples/TableAlreadyExistsError'` 一致。
2. **创建表端点**（Create Table，约第 901 行）：同样将 `NamespaceAlreadyExists` 改为 `TableAlreadyExists`，与 `TableAlreadyExistsError` 引用一致。
3. **创建视图端点**（Create View，约第 1517 行）：example 键名从 `NamespaceAlreadyExists` 改为 `ViewAlreadyExists`，与 `$ref: '#/components/examples/ViewAlreadyExistsError'` 一致。

这些修改只改了 example 的键名，不影响 `$ref` 引用路径，因此不影响实际 API 行为，但使 OpenAPI 文档更加一致和准确。

## 总结

此提交修复了 OpenAPI 规范中三处错误示例名称不一致的问题。虽然这些名称不匹配不影响 API 功能，但修复后提高了 API 文档的准确性和可读性，特别是在自动生成的文档页面和客户端代码中，用户不再会看到令人困惑的错误类型名称。
