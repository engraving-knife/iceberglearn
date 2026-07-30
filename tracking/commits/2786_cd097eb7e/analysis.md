# 提交 2786：REST: Remove deprecated allowEmptyValue from REST spec (#14364)

## 提交信息

- **序号**：2786 / 4088
- **哈希**：cd097eb7e94686854f8b0cb42c77d3d1362a9ab8
- **短哈希**：cd097eb7e
- **日期**：2025-10-22 08:05:24 -0700
- **作者**：Yuya Ebihara
- **提交说明**：REST: Remove deprecated allowEmptyValue from REST spec (#14364)
- **PR/Issue**：#14364

## 总体目的

本提交从 Iceberg REST Catalog OpenAPI 规范中移除已废弃的 `allowEmptyValue` 属性。

`allowEmptyValue` 是 OpenAPI 规范中的一个参数属性，用于指示查询参数允许传入空值（如 `?parent=`）。在 Iceberg REST 规范中，该属性被用于两处：

1. `ListNamespaces` 端点的 `parent` 查询参数：用于列出指定命名空间下的子命名空间。
2. `PageToken` 参数组件：用于分页。

然而 `allowEmptyValue` 在 OpenAPI 3.0 规范中已被标记为不推荐使用（deprecated），且其语义在不同工具链中实现不一致。更重要的是，Swagger UI 等工具对 `allowEmptyValue` 的处理可能导致混淆——它会将空值参数视为有意义的输入而非缺失。

本提交移除这两处的 `allowEmptyValue: true`，并更新 `parent` 参数的描述说明：空字符串暂时被视为等同于未提供（向后兼容），但未来可能会改变。这使规范更清晰，避免工具链的歧义。

## 如何达成设计目的

1. **`parent` 参数**：移除 `allowEmptyValue: true`，更新描述从 "If not provided or empty, all top-level namespaces should be listed." 改为 "If not provided, all top-level namespaces should be listed. For backward compatibility, empty string is treated as absent for now."——明确空字符串暂时被视为缺失，但这是向后兼容的临时行为。

2. **`PageToken` 参数**：移除 `allowEmptyValue: true`。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+2/-3 lines)

**修改目的**：移除两处 `allowEmptyValue: true`，更新 `parent` 参数描述。

**工作逻辑**：
- `ListNamespaces` 的 `parent` 参数：删除 `allowEmptyValue: true` 行，描述改为 "If not provided, all top-level namespaces should be listed. For backward compatibility, empty string is treated as absent for now."
- `PageToken` 组件参数：删除 `allowEmptyValue: true` 行。

## 总结

本提交从 REST Catalog OpenAPI 规范中移除了已废弃的 `allowEmptyValue` 属性，使规范更符合 OpenAPI 最佳实践，避免工具链歧义。同时明确记录了空字符串参数暂时被视为缺失的向后兼容行为，为未来可能的变更留下余地。
