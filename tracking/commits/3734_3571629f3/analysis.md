# 提交 3734：OpenAPI: Add CatalogObjectIdentifier schema (#16144)

## 提交信息

- **序号**：3734 / 4088
- **哈希**：3571629f3348f9ff2ecfcd2d9786867ce0ef715c
- **短哈希**：3571629f3
- **日期**：2026-05-18 11:01:27 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：OpenAPI: Add CatalogObjectIdentifier schema (#16144)
- **PR/Issue**：#16144

## 总体目的

本提交为 Iceberg REST Catalog 的 OpenAPI 规范新增 `CatalogObjectIdentifier` schema，用于以统一的、与对象类型无关的方式引用 catalog 中的对象（表、视图或命名空间）。

原先 REST Catalog 规范中针对不同对象类型（如 `TableIdentifier`）有各自的标识符 schema。随着 Iceberg 视图（View）特性的成熟以及命名空间管理的统一化，需要一个更通用的标识符来表示"catalog 中的某个对象"，其具体类型（table/view/namespace）由上下文（如端点或配套的类型判别字段）决定，而不是由标识符结构本身决定。

`CatalogObjectIdentifier` 定义为一个字符串数组的根模型（`list[str]`），表示对象的层级路径（如 `["accounting", "tax", "paid"]`）。这种设计避免了为每种对象类型重复定义几乎相同的标识符结构，为后续统一端点、批量操作等场景提供了基础。提交说明中还提到配套的 `CatalogObjectType` 枚举（table/view/namespace）作为判别器使用，但本次 diff 实际只引入了 `CatalogObjectIdentifier`（`CatalogObjectType` 可能已在之前的提交中引入或后续引入）。

## 如何达成设计目的

在 OpenAPI 规范文件 `rest-catalog-open-api.yaml` 的 `components.schemas` 下新增 `CatalogObjectIdentifier` 定义，类型为 `array`，元素为 `string`，并附上描述与示例。同时通过 `datamodel-code-generator` 重新生成对应的 Python 模型 `rest-catalog-open-api.py`，生成一个继承自 `RootModel[list[str]]` 的 Pydantic 模型类，保持 YAML 与 Python 代码同步。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+11/-0 lines)

**修改目的**：在 OpenAPI 规范中新增 `CatalogObjectIdentifier` schema 定义。

**工作逻辑**：
在 `components.schemas` 中新增：
```yaml
CatalogObjectIdentifier:
  description:
    Reference to a catalog object (for example, table, view, or namespace) as
    an ordered list of hierarchical levels.
    The object kind is determined by context (e.g. the endpoint or a
    companion type discriminator), not by the identifier structure alone.
  type: array
  items:
    type: string
  example: [ "accounting", "tax", "paid" ]
```
该 schema 是一个字符串数组，表示对象的层级路径。描述明确指出对象类型由上下文或配套的类型判别器决定，而非标识符结构本身。示例 `["accounting", "tax", "paid"]` 展示了一个三层级的对象引用。

### `open-api/rest-catalog-open-api.py` (+12/-0 lines)

**修改目的**：根据更新后的 YAML 重新生成 Python Pydantic 模型。

**工作逻辑**：
新增 `CatalogObjectIdentifier` 类，继承自 `RootModel[list[str]]`（Pydantic v2 的根模型，表示整个模型就是一个 list）：
```python
class CatalogObjectIdentifier(RootModel[list[str]]):
    """
    Reference to a catalog object (for example, table, view, or namespace) as an ordered list of hierarchical levels. The object kind is determined by context (e.g. the endpoint or a companion type discriminator), not by the identifier structure alone.
    """

    root: list[str] = Field(
        ...,
        description='Reference to a catalog object (for example, table, view, or namespace) as an ordered list of hierarchical levels. The object kind is determined by context (e.g. the endpoint or a companion type discriminator), not by the identifier structure alone.',
        examples=[['accounting', 'tax', 'paid']],
    )
```
该类由 `datamodel-code-generator` 自动生成，与 YAML 保持同步。

## 总结

本提交为 Iceberg REST Catalog 的 OpenAPI 规范新增了 `CatalogObjectIdentifier` schema，作为统一的、类型无关的 catalog 对象引用方式。该标识符为字符串数组形式，对象类型由上下文或配套判别器决定。这一设计为后续统一不同对象类型（表、视图、命名空间）的端点操作奠定基础，减少重复定义。YAML 规范与自动生成的 Python 模型同步更新，保持一致性。属于规范层面的前瞻性扩展，尚不影响现有 REST API 行为。
