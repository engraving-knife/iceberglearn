# 提交 2975：OpenAPI: Use `PrimitiveTypeValue` rather than `object` (#14184)

## 提交信息

- **序号**：2975 / 4088
- **哈希**：0c194502b802630dde71120e5e48a074a0552ee8
- **短哈希**：0c194502b
- **日期**：2025-12-08
- **作者**：Fokko Driesprong
- **提交说明**：OpenAPI: Use `PrimitiveTypeValue` rather than `object` (#14184)
- **PR/Issue**：#14184

## 总体目的

Iceberg 的 REST Catalog OpenAPI 规范（`open-api/rest-catalog-open-api.yaml`）及对应的 Pydantic 模型（`open-api/rest-catalog-open-api.py`）定义了表达式（expression）相关的 schema，用于在 REST 请求中描述谓词/过滤条件。其中三类表达式涉及"值"字段：

- `UnaryExpression`：一元表达式（`is-null`、`not-null`、`is-nan`、`not-nan`），语义上只对 `term` 求值，**不需要**值操作数。
- `LiteralExpression`：字面量表达式，`value` 表示一个字面量。
- `SetExpression`：集合表达式，`values` 表示一组字面量。

此前这些"值"字段在 schema 中都被宽泛地声明为 `type: object`（Python 侧为 `Dict[str, Any]`），这带来两个问题：其一，`object` 是不透明的任意对象，没有表达出 Iceberg 实际使用的"带类型标签的值"结构（即 `PrimitiveTypeValue`，一个 `oneOf`，覆盖 Boolean/Integer/Long/Float/Double/Decimal/String/UUID/Date/Time/Timestamp/TimestampTz/各 Nanos 版本/Fixed/Binary 等带类型的值包装），丧失了类型信息，生成出的客户端代码也只能得到弱类型的 `object`/`Dict`；其二，`UnaryExpression` 本不该有 `value` 字段，却被声明在 `required` 与 `properties` 中，与一元表达式语义不符。

本提交要解决的即是这两点：把 `LiteralExpression.value` 与 `SetExpression.values` 的元素类型由模糊的 `object` 收紧为已有的 `PrimitiveTypeValue`（`$ref`），使 schema 真实反映 Iceberg 的带类型字面量结构；同时移除 `UnaryExpression` 中本不应存在的 `value` 字段，使 schema 与一元表达式语义一致。这既提升了规范的精确性与类型可读性，也改善了由规范生成的客户端/桩代码的类型质量。

## 如何达成设计目的

在 OpenAPI YAML 中把对应字段从 `type: object` 改为 `$ref: '#/components/schemas/PrimitiveTypeValue'`，并从 `UnaryExpression` 的 `required` 与 `properties` 中删除 `value`；在 Pydantic 模型中做对称改动（`Dict[str, Any]` → `PrimitiveTypeValue`，删除 `UnaryExpression.value`），并清理因此不再使用的 `Any` import。复用规范中已有的 `PrimitiveTypeValue` schema（一个覆盖全部基本类型值包装的 `oneOf`），无需新增类型定义。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+2/-5 lines)

**修改目的**：收紧表达式值字段类型并修正 `UnaryExpression` 的多余字段。

**工作逻辑**：
- **`UnaryExpression`**：从 `required` 列表删除 `- value`，并从 `properties` 删除 `value: type: object` 块。一元表达式（`is-null`/`not-null`/`is-nan`/`not-nan`）只作用于 `term`，移除 `value` 使其与语义一致。
- **`LiteralExpression`**：`value` 由 `type: object` 改为 `$ref: '#/components/schemas/PrimitiveTypeValue'`，明确字面量是带类型标签的值（`PrimitiveTypeValue` 是 `BooleanTypeValue`/`IntegerTypeValue`/.../`BinaryTypeValue` 等 `oneOf`）。
- **`SetExpression`**：`values` 数组的 `items` 由 `type: object` 改为 `$ref: '#/components/schemas/PrimitiveTypeValue'`，使集合元素同样为带类型值。

### `open-api/rest-catalog-open-api.py` (+3/-4 lines)

**修改目的**：使 Pydantic 模型与 YAML schema 对称改动。

**工作逻辑**：
- 从 `typing` 导入中移除 `Any`（不再使用）。
- `UnaryExpression` 删除 `value: Dict[str, Any]` 字段。
- `LiteralExpression.value` 由 `Dict[str, Any]` 改为 `PrimitiveTypeValue`。
- `SetExpression.values` 由 `List[Dict[str, Any]]` 改为 `List[PrimitiveTypeValue]`。
`PrimitiveTypeValue` 复用模型中已有的定义（对应 YAML 中的同名 `oneOf` schema），由此生成/校验代码获得强类型值对象而非不透明字典。

## 总结

本提交把 REST Catalog OpenAPI 规范中表达式相关"值"字段从宽泛的 `object` 收紧为已有的 `PrimitiveTypeValue`（带类型标签的值 `oneOf`），并移除 `UnaryExpression` 中语义上不应存在的 `value` 字段，YAML 与 Pydantic 模型对称改动。该改动提升了规范的类型精确性与一致性，改善了由规范生成的客户端代码的类型质量，是一处面向规范正确性与可用性的小而精的改进。
