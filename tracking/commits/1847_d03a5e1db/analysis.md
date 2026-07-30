# 提交 1847：Revert "OpenAPI: Handle NamespaceNotEmptyException when dropping a namespace" (#12517)

## 提交信息

- **序号**：1847 / 4088
- **哈希**：d03a5e1dbcf622ab4883dabeb813f5d8f7728fb5
- **短哈希**：d03a5e1db
- **日期**：2025-03-13 16:08:38 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Revert "OpenAPI: Handle NamespaceNotEmptyException when dropping a namespace" (#12517)
- **PR/Issue**：#12517

## 总体目的

本提交回退了前一个提交（1846, fe258463f5）对 `rest-catalog-open-api.yaml` 的修改。前一个提交在删除命名空间的 400 响应中增加了 `NamespaceNotEmptyException` 的示例和错误处理文档，但该修改在提交后不到 10 分钟即被回退。

回退的原因未在提交消息中明确说明（仅标注 "This reverts commit fe258463f5..."），但通常回退 OpenAPI 规范修改的原因可能包括：(1) 修改与 REST Catalog 规范的其他部分不一致；(2) `NamespaceNotEmptyException` 的 HTTP 状态码或错误格式尚未在规范中达成共识；(3) 修改引入了 OpenAPI 验证问题；(4) 需要更全面的方案来处理命名空间非空场景（可能需要使用 409 Conflict 而非 400 Bad Request）。

回退后，`rest-catalog-open-api.yaml` 恢复到提交 1846 之前的状态，即 drop namespace 的 400 响应仍只引用通用的 `BadRequestErrorResponse`。

## 如何达成设计目的

通过 `git revert` 自动生成的回退提交，将 `rest-catalog-open-api.yaml` 中所有由提交 1846 引入的变更全部撤销：(1) 400 响应从完整定义恢复为 `$ref` 引用；(2) 删除新增的 `BadRequestError` 和 `NamespaceNotEmptyError` 示例。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (修改)

**修改目的**：撤销提交 1846 的所有变更。

**工作逻辑**：

1. DELETE `/v1/{prefix}/namespaces/{namespace}` 端点的 `400` 响应：从带 description/content/schema/examples 的完整定义恢复为 `$ref: '#/components/responses/BadRequestErrorResponse'`。

2. 从 `components/examples` 中删除 `BadRequestError` 和 `NamespaceNotEmptyError` 两个示例定义。

这些变更精确地撤销了提交 1846 引入的所有内容，使文件恢复到原始状态。

## 小结

本提交是对提交 1846 的回退，两者在同一作者的同一天内相继提交（间隔约 7 分钟），净效果为零。`rest-catalog-open-api.yaml` 在这两个提交前后内容完全一致。回迁到 1.4.x 时无需回迁这两个提交中的任何一个。
