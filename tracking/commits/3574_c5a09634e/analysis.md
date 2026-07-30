# 提交 3574：REST Spec: Clarify identifier uniqueness across tables and views (#15691)

## 提交信息

- **序号**：3574 / 4088
- **哈希**：c5a09634ef6236b35653c2233e0738a5de920e4f
- **短哈希**：c5a09634e
- **日期**：2026-04-22 21:56:42 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：REST Spec: Clarify identifier uniqueness across tables and views (#15691)
- **PR/Issue**：#15691

## 总体目的

该提交澄清了 Iceberg REST Catalog 规范中关于标识符（identifier）在表和视图之间唯一性的描述。在 Iceberg 中，表和视图共享相同的命名空间作用域，即同一命名空间下不允许存在同名的表和视图。然而之前 REST 规范中，createTable 和 registerTable 的 409 冲突描述为"The table already exists"，createView 的 409 描述为"The view already exists"，这些描述仅提及了同类型冲突，未明确说明跨类型冲突（即表名和视图名冲突）。

该提交统一了所有写操作（createTable、registerTable、createView）的 409 冲突描述为"The identifier already exists as a table or view"，明确表示标识符在所有目录对象类型中必须唯一。同时更新了错误示例中的消息文本，使其更加准确。

## 如何达成设计目的

修改 OpenAPI 规范文件 `rest-catalog-open-api.yaml` 中三处 409 响应的描述文本，统一为跨类型冲突描述。同时更新错误响应示例中的 message 字段。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+5/-5 lines)

**修改目的**：统一 409 冲突描述，澄清跨类型唯一性。

**工作逻辑**：
- createTable 端点的 409 描述从 "Conflict - The table already exists" 改为 "Conflict - The identifier already exists as a table or view"。
- registerTable 端点的 409 描述同样修改。
- createView 端点的 409 描述从 "Conflict - The view already exists" 改为 "Conflict - The identifier already exists as a table or view"。
- TableAlreadyExistsError 示例的 message 从 "The given table already exists" 改为 "The requested table identifier already exists"。
- ViewAlreadyExistsError 示例的 message 从 "The given view already exists" 改为 "The requested view identifier already exists"。

## 总结

该提交是 REST Catalog 规范的文档澄清，统一了表和视图的 409 冲突描述，明确标识符在所有目录对象类型中的唯一性要求。这有助于 REST 客户端正确理解和处理冲突场景，特别是当表名与视图名冲突时。虽然仅涉及文档修改，但对规范的准确性和一致性有重要意义。
