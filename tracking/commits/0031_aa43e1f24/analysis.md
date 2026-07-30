# 提交 0031：OpenAPI: Add description for AssignUUID (#8753)

## 提交信息

- **序号**：0031 / 4088
- **哈希**：aa43e1f24f462b8c941ec7ab9afab2c9108bc0e1
- **短哈希**：aa43e1f24
- **日期**：2023-10-11 08:26:13 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：OpenAPI: Add description for AssignUUID (#8753)
- **PR/Issue**：#8753

## 总体目的

这个提交为 Iceberg REST Catalog OpenAPI 规范中的 `AssignUUIDUpdate` 操作补充一段说明文字，明确"为表/视图分配 UUID"这一更新动作的语义约束：UUID 只应在创建表/视图时分配，如果表/视图已经有 UUID 则不应重新分配，否则不安全。

`AssignUUIDUpdate` 是 REST Catalog 提交表更新时可用的一种 `Update` 动作，属于 [BaseUpdate](file:///Users/fengxiaohang/trae/iceberglearn/open-api/rest-catalog-open-api.yaml) 的子类型之一，在 commit 的快照中位于 `UpdateRequirement`/`TableUpdate` 的 oneOf 联合里。它的作用是把一个 UUID 绑定到表或视图的元数据上，常用于跨 catalog 迁移或导入已有表时保持标识一致。然而原规范中该 schema 只声明了 `uuid` 字段，没有任何 prose 说明该操作的语义边界，导致规范读者无法判断"是否可以对已有 UUID 的表再次 assign"。本提交就是补上这条规范级注释，使规范对客户端/服务端实现者给出明确的预期行为。

这一改动属于纯文档增强：不改变任何运行时行为，也不修改字段结构，只是在 OpenAPI 的 `description` 字段和对应 Python 模型的 docstring 中各加一段文字。这种对 OpenAPI 规范的持续打磨是 Iceberg REST Catalog 规范演进中的常见提交，旨在提升规范的清晰度与可消费性，减少客户端实现者因歧义而误用 API 的风险（例如误以为可以随时重写 UUID，从而破坏跨 catalog 标识稳定性或导致 `assert-table-uuid` 期望校验失败）。

## 如何达成设计目的

通过在 OpenAPI 规范的两个等价表达处同时补充同一段说明文字达成：(1) 在机器可消费的 YAML 规范文件的 `AssignUUIDUpdate` schema 下增加 `description` 字段；(2) 在用于校验/生成 Python 模型的 [rest-catalog-open-api.py](file:///Users/fengxiaohang/trae/iceberglearn/open-api/rest-catalog-open-api.py) 中给 `AssignUUIDUpdate` 类加 docstring。两处文字内容一致，保证 YAML 与 Python 模型描述同步。

## 修改详情

### [open-api/rest-catalog-open-api.py](file:///Users/fengxiaohang/trae/iceberglearn/open-api/rest-catalog-open-api.py)

**修改目的**：为 Python 端的 `AssignUUIDUpdate` 模型类补充 docstring，说明该操作的安全语义。

**工作逻辑**：在 `class AssignUUIDUpdate(BaseUpdate):` 下方、`uuid: str` 字段声明之前，插入一段三引号 docstring：

> Assigning a UUID to a table/view should only be done when creating the table/view. It is not safe to re-assign the UUID if a table/view already has a UUID assigned

这段 docstring 与 YAML 中的 `description` 内容完全一致，保证由该 Python 脚本重新生成 YAML 时描述不会丢失或漂移。

### [open-api/rest-catalog-open-api.yaml](file:///Users/fengxiaohang/trae/iceberglearn/open-api/rest-catalog-open-api.yaml)

**修改目的**：在 OpenAPI 规范的 `AssignUUIDUpdate` schema 下增加 `description` 字段，使规范消费者（如客户端代码生成器、文档渲染器、API 网关）能展示该操作的安全约束。

**工作逻辑**：在 `AssignUUIDUpdate:` 节点下、`allOf` 之前新增一行 `description:`，内容与上述 Python docstring 一致。`allOf` 引用 `BaseUpdate` 并定义 `uuid` 字段（`type: string`）的结构未改动。

## 小结

本提交通过为 `AssignUUIDUpdate` 在 OpenAPI YAML 与 Python 模型两处同步补充 description，明确了"UUID 仅在创建表/视图时分配、不可重新分配"的安全语义，是 Iceberg REST Catalog 规范清晰度的一次小幅但有意义提升。
