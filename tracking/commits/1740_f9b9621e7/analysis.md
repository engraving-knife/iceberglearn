# 提交 1740：OpenAPI: Add overwrite option when registering a table (#12239)

## 提交信息

- **序号**：1740 / 4088
- **哈希**：f9b9621e77ef44d4e5fd72bf6014e3037514ba98
- **短哈希**：f9b9621e7
- **日期**：2025-02-17 10:49:21 +0100
- **作者**：Hongyue/Steve Zhang
- **提交说明**：OpenAPI: Add overwrite option when registering a table (#12239)
- **PR/Issue**：#12239

## 总体目的

Iceberg REST Catalog API 提供了 `RegisterTableRequest` 接口，允许客户端通过指定表名和 metadata 位置来注册一个已存在的表。此前，当尝试注册一个与已有表同名的表时，操作会失败并返回错误，因为注册接口不支持覆盖已有表的 metadata。

在实际使用场景中，用户可能需要重新注册一个表（例如 metadata 文件已更新或位置已变更），此时必须先删除旧表再注册新表，操作不够便捷。本提交的目标是在 `RegisterTableRequest` 中新增一个可选的 `overwrite` 布尔字段，允许客户端在注册表时指定是否覆盖已存在的表 metadata，简化重新注册流程。

## 如何达成设计目的

提交同时修改了 OpenAPI 规范的 YAML 和 Python 两个表示形式，保持两者同步：

1. **YAML 规范**：在 `RegisterTableRequest` schema 中新增 `overwrite` 布尔属性，默认值为 `false`，并附带描述说明。
2. **Python 模型**：在 `RegisterTableRequest` Pydantic 类中新增 `overwrite` 可选布尔字段，默认值为 `False`，并附带字段描述。

`overwrite` 默认为 `false`，确保向后兼容——现有客户端不传该参数时行为不变。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`（修改, +4 lines）

**修改目的**：在 `RegisterTableRequest` schema 中新增 `overwrite` 属性。

**工作逻辑**：在 `RegisterTableRequest` 的 `properties` 中（`metadata-location` 之后）新增 `overwrite` 属性：
- `description`: `"Whether to overwrite table metadata if the table already exists"`
- `type`: `boolean`
- `default`: `false`

该属性为可选（未列入 `required` 列表），默认值为 `false`，确保向后兼容。

### `open-api/rest-catalog-open-api.py`（修改, +4 lines）

**修改目的**：在 Python 模型中新增对应的 `overwrite` 字段。

**工作逻辑**：在 `RegisterTableRequest` 类中新增 `overwrite` 字段：
- 类型为 `Optional[bool]`
- 默认值为 `False`
- 使用 `Field(False, description='Whether to overwrite table metadata if the table already exists')` 定义

## 小结

- **成效**：在 REST Catalog API 的 `RegisterTableRequest` 中新增了 `overwrite` 可选参数，允许客户端在注册表时覆盖已存在的表 metadata，简化了重新注册流程。默认值为 `false` 确保了向后兼容。
- **影响范围**：仅涉及 OpenAPI 规范文件（YAML 和 Python），不影响 Java 服务端实现。REST Catalog 服务端需要后续实现对该参数的支持。
- **回迁到 1.4.x 的注意事项**：此提交仅修改 OpenAPI 规范文档，无代码依赖，回迁风险低。但需注意 1.4.x 分支的 REST Catalog 服务端是否支持 `overwrite` 参数。如果服务端不支持，仅回迁规范不会带来实际功能。建议结合服务端实现情况决定是否回迁。
