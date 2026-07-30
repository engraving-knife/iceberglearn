# 提交 0259：Open-API: Refactor updates with discriminator (#9240)

## 提交信息

- **序号**：0259 / 4088
- **哈希**：492018902cd044adb8bdc312f01d70a4a63cdcc0
- **短哈希**：492018902
- **日期**：2023-12-11
- **作者**：Fokko Driesprong
- **提交说明**：Open-API: Refactor updates with discriminator (#9240)
- **PR/Issue**：#9240

## 总体目的

Iceberg 的 REST Catalog OpenAPI 规范中，表/视图的「更新操作」（`BaseUpdate` 及其 16 个子类：`AssignUUIDUpdate`、`UpgradeFormatVersionUpdate`、`AddSchemaUpdate`、`SetCurrentSchemaUpdate`、`AddPartitionSpecUpdate`、`SetDefaultSpecUpdate`、`AddSortOrderUpdate`、`SetDefaultSortOrderUpdate`、`AddSnapshotUpdate`、`SetSnapshotRefUpdate`、`RemoveSnapshotsUpdate`、`RemoveSnapshotRefUpdate`、`SetLocationUpdate`、`SetPropertiesUpdate`、`RemovePropertiesUpdate`、`AddViewVersionUpdate`、`SetCurrentViewVersionUpdate`）通过 `action` 字段的多值 enum 来区分子类型。重构前，`action` 的合法值枚举被集中定义在基类 `BaseUpdate` 上，而各子类只是用 `allOf` 引用 `BaseUpdate` 并补充自己的字段——子类本身并不重复声明 `action`，也没有约束 `action` 必须取某一个特定值。

这种写法存在两个问题：其一，OpenAPI 的 `allOf` 组合方式对很多代码生成器并不友好，生成的客户端模型往往退化为「基类带一个宽松的 action 字符串 + 子类一堆字段」，难以体现「这个子类的 action 只能是某个固定值」的约束；其二，缺乏 `discriminator`，反序列化端无法仅凭 `action` 字段就定位到正确的子类型，需要额外的运行时判断。

本次重构在 `BaseUpdate` 上引入标准的 OpenAPI `discriminator`，并把 `action` 枚举从基类下放到每个具体子类——每个子类各自声明 `action: enum: ["<对应单一值>"]`。这样规范本身更符合 OpenAPI 的多态建模惯例，配合 `datamodel-code-generator`（见 0257 升级）能生成「更干净」的类型层级：子类型成为带有判别字段的强类型模型，反序列化时可直接按 `action` 路由到对应类。提交说明原文：「This generates nicer code」。

## 如何达成设计目的

设计思路是「基类声明判别器 + 子类各自约束单一 action 值」。具体地：

1. 在 `BaseUpdate` 的 YAML 中新增 `discriminator`，`propertyName: action`，并通过 `mapping` 把每个 action 字符串显式映射到对应的 schema 引用。
2. 把 `BaseUpdate.action` 由「集中多值 enum」改为「普通 string」（约束下放到子类）。
3. 对每个具体 Update 子类，把原本 `allOf` 内联的「第二个对象片段（含 required/properties）」提升为与 `allOf` 同级的顶层 `required` / `properties`，并显式声明 `action: enum: ["<单一值>"]`。
4. 同步重新生成 `rest-catalog-open-api.py`：基类 `BaseUpdate.action` 退化为 `str`，每个子类新增 `action: Literal['<值>']` 字段。

这样的结构使生成器在生成 Pydantic 模型时，每个子类成为具有固定 `action` 字面量的独立类型，便于按 `action` 做判别式反序列化。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：在 `BaseUpdate` 上引入 OpenAPI discriminator，并把 `action` 单值枚举约束下放到每个具体 Update 子类，使其成为判别式多态模型。

**工作逻辑**：

- `BaseUpdate`：移除 `properties.action` 下的多值 `enum` 列表，`action` 改为 `type: string`；新增 `discriminator`：
  ```yaml
  discriminator:
    propertyName: action
    mapping:
      assign-uuid: '#/components/schemas/AssignUUIDUpdate'
      upgrade-format-version: '#/components/schemas/UpgradeFormatVersionUpdate'
      add-schema: '#/components/schemas/AddSchemaUpdate'
      set-current-schema: '#/components/schemas/SetCurrentSchemaUpdate'
      add-spec: '#/components/schemas/AddPartitionSpecUpdate'
      set-default-spec: '#/components/schemas/SetDefaultSpecUpdate'
      add-sort-order: '#/components/schemas/AddSortOrderUpdate'
      set-default-sort-order: '#/components/schemas/SetDefaultSortOrderUpdate'
      add-snapshot: '#/components/schemas/AddSnapshotUpdate'
      set-snapshot-ref: '#/components/schemas/SetSnapshotRefUpdate'
      remove-snapshots: '#/components/schemas/RemoveSnapshotsUpdate'
      remove-snapshot-ref: '#/components/schemas/RemoveSnapshotRefUpdate'
      set-location: '#/components/schemas/SetLocationUpdate'
      set-properties: '#/components/schemas/SetPropertiesUpdate'
      remove-properties: '#/components/schemas/RemovePropertiesUpdate'
      add-view-version: '#/components/schemas/AddViewVersionUpdate'
      set-current-view-version: '#/components/schemas/SetCurrentViewVersionUpdate'
  ```
  覆盖全部 16 个 `*Update` 子类型。

- 各具体子类（如 `AssignUUIDUpdate`、`UpgradeFormatVersionUpdate`、`AddSchemaUpdate`、`SetCurrentSchemaUpdate`、`AddPartitionSpecUpdate`、`SetDefaultSpecUpdate`、`AddSortOrderUpdate`、`SetDefaultSortOrderUpdate`、`AddSnapshotUpdate`、`SetSnapshotRefUpdate`、`RemoveSnapshotsUpdate`、`RemoveSnapshotRefUpdate`、`SetLocationUpdate`、`SetPropertiesUpdate`、`RemovePropertiesUpdate`、`AddViewVersionUpdate`、`SetCurrentViewVersionUpdate`）：保留对 `BaseUpdate` 的 `$ref`（`SetSnapshotRefUpdate` 还保留对 `SnapshotReference` 的 `$ref`），但把原本写在 `allOf` 第二个数组元素里的 `required` 与 `properties` 提升到与 `allOf` 同级的顶层，并新增 `action: type: string enum: ["<对应单一 action 值>"]`。例如 `AssignUUIDUpdate` 变为：
  ```yaml
  AssignUUIDUpdate:
    allOf:
      - $ref: '#/components/schemas/BaseUpdate'
    required:
      - action
      - uuid
    properties:
      action:
        type: string
        enum: ["assign-uuid"]
      uuid:
        type: string
  ```
  其余子类同理，各自把 `action` 约束为单一字面量。

### `open-api/rest-catalog-open-api.py`

**修改目的**：依据重构后的 YAML 重新生成 Pydantic 模型，使基类 `action` 退化为普通 `str`，各子类显式声明 `action: Literal['<值>']`。

**工作逻辑**：

- `BaseUpdate.action` 由原来的 `Literal['assign-uuid', 'upgrade-format-version', ...]`（16 个值）改为 `action: str`，即基类不再约束具体取值。
- 每个 `*Update` 子类新增形如 `action: Literal['add-schema']` 的字段，把单一 action 值的约束固化到子类。例如：
  ```python
  class AssignUUIDUpdate(BaseUpdate):
      action: Literal['assign-uuid']
      uuid: str

  class UpgradeFormatVersionUpdate(BaseUpdate):
      action: Literal['upgrade-format-version']
      format_version: int = Field(..., alias='format-version')

  class AddSchemaUpdate(BaseUpdate):
      action: Literal['add-schema']
      schema_: Schema = Field(..., alias='schema')
      last_column_id: Optional[int] = Field(None, alias='last-column-id')
  ```
  覆盖全部 16 个子类（`AddSchemaUpdate` 的 `action` 字段被补在文件靠后位置，对应提交说明里的「Add missing」补丁）。

## 小结

通过在 `BaseUpdate` 上引入 OpenAPI `discriminator` 并将 `action` 单值枚举约束下放到各具体 Update 子类，本提交让 REST Catalog 的更新操作模型成为标准判别式多态结构，从而生成更干净、更利于按 action 路由反序列化的客户端代码。
