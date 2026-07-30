# 提交 0819：Open-API: Use union instead of inheritance for TableRequirements (#10434)

## 提交信息
- **序号**：0819 / 4088
- **哈希**：e0dc57e3366347c773ff1c333ee158f9a49eb5c6
- **短哈希**：e0dc57e33
- **日期**：2024-06-06 16:12:05 -0700
- **作者**：Anurag Mantripragada <amantripragada@apple.com>
- **提交说明**：Open-API: Use union instead of inheritance for TableRequirements (#10434)
- **PR/Issue**：#10434

## 总体目的

本提交重构了 Iceberg REST Catalog OpenAPI 规范中 `TableRequirement`（及 `ViewRequirement`）的多态建模方式：从"继承（inheritance / allOf）"改为"联合（union / oneOf）"。

REST Catalog 的提交协议中，客户端在 `UpdateTableRequest` 里携带一组 requirement 断言（如 `assert-create`、`assert-table-uuid`、`assert-ref-snapshot-id` 等 8 种表断言和 `assert-view-uuid` 视图断言），服务端校验通过后才应用变更。这些断言共享 `type` 判别字段（discriminator），但各自有不同字段。

此前（PR #7710 引入）的建模采用 OpenAPI 的 `allOf` 继承模式：`TableRequirement` 作为基类定义 `type` 字段和 discriminator，各 `AssertXxx` 通过 `allOf: - $ref: TableRequirement` 继承基类。然而这种 `allOf` + discriminator 的继承模式在许多 OpenAPI 代码生成器中存在兼容性问题——部分生成器无法正确处理基类带 discriminator 的 allOf 继承，可能生成含有冗余 `type` 字段的基类、空子类，或在反序列化时无法正确按 discriminator 派发。

本提交改用 `oneOf` 联合类型：`TableRequirement` 本身定义为带 discriminator 的 `oneOf`，直接枚举所有 `AssertXxx`；各 `AssertXxx` 成为自包含的独立 schema（不再 allOf 继承）。这是 OpenAPI 3.0 中表达"判别联合"更地道的写法，能被更广泛的代码生成器正确处理，提升规范的可移植性。Python 端（`rest-catalog-open-api.py`，基于 Pydantic）同步从类继承（`AssertXxx(TableRequirement)`）改为 `Union[...]` + `__root__` 的判别联合。

## 如何达成设计目的

### YAML 规范侧

**1. `TableRequirement` 从基类改为联合**：
- 保留 `discriminator`（propertyName: type，mapping 保持不变）。
- 移除原来的 `required: [type]` 和 `properties: type`（基类的 type 字段定义）。
- 新增 `oneOf`，列出全部 8 个 `AssertXxx` 的 `$ref`。
- 仍保留 `type: object`。

**2. 各 `AssertXxx` 移除继承**：
- 删除每个 `AssertXxx` 上的 `allOf: - $ref: "#/components/schemas/TableRequirement"`。
- 各 schema 保留自己的 `type` 字段（enum 固定为对应的断言类型）和自身特有字段，成为完全自包含的定义。

**3. `ViewRequirement` 同样改造**：
- 从基类（带 type 字段 + discriminator）改为 `oneOf: [$ref: AssertViewUUID]` + discriminator。
- `AssertViewUUID` 移除 `allOf` 继承。

### Python 侧（Pydantic 模型）

**1. 各 `AssertXxx` 类不再继承 `TableRequirement`**：
- `class AssertCreate(TableRequirement)` → `class AssertCreate(BaseModel)`，其余 7 个表断言和 `AssertViewUUID` 同理。
- 每个类自身已定义 `type: Literal['...']`，不依赖父类。

**2. `TableRequirement` / `ViewRequirement` 改为判别联合**：
- 删除原来作为基类的 `TableRequirement(BaseModel)`（含 `type: str`）。
- 在文件靠后位置（所有 AssertXxx 定义之后）新增：
  ```python
  class TableRequirement(BaseModel):
      __root__: Union[AssertCreate, AssertTableUUID, AssertRefSnapshotId, ...] = Field(..., discriminator='type')
  
  class ViewRequirement(BaseModel):
      __root__: AssertViewUUID = Field(..., discriminator='type')
  ```
  使用 Pydantic 的 `__root__` + `Union` + `discriminator='type'` 实现判别联合，与 YAML 的 oneOf + discriminator 语义对应。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`
**修改目的**：将 TableRequirement/ViewRequirement 从 allOf 继承改为 oneOf 联合。
**工作逻辑**：TableRequirement 移除基类 type 字段定义，改为 oneOf 枚举 8 个 AssertXxx；8 个 AssertXxx 各自移除 allOf 继承，保留自身 type enum 和特有字段；ViewRequirement 同理改为 oneOf: AssertViewUUID，AssertViewUUID 移除 allOf。discriminator（type + mapping）全部保留，确保按 type 派发语义不变。

### `open-api/rest-catalog-open-api.py`
**修改目的**：Pydantic 模型同步从类继承改为判别联合。
**工作逻辑**：8 个 AssertXxx 和 AssertViewUUID 的基类从 `TableRequirement`/`ViewRequirement` 改为 `BaseModel`；原作为基类的 `TableRequirement(BaseModel)`（含 `type: str`/`type: Literal[...]`）和 `ViewRequirement(BaseModel)` 删除，在文件后部重新定义为带 `__root__: Union[...]` + `Field(..., discriminator='type')` 的判别联合模型。各 Assert 类自身的 `type: Literal['...']` 保留不变。

## 小结
- **成效**：用更地道的 OpenAPI `oneOf` 判别联合替代 `allOf` 继承，提升了规范对各类代码生成器的兼容性；YAML 与 Python 模型保持一致；语义上 discriminator + type 派发行为不变，对 REST 协议本身无影响。
- **影响范围**：仅改动 OpenAPI 规范文件（yaml + py），不涉及 Java 实现代码（服务端 `UpdateRequirement`/`CatalogHandlers` 和客户端 `RESTSessionCatalog` 不受影响，它们用 Java 自有的多态机制而非 OpenAPI 生成）。影响面主要是从该规范生成客户端/服务端代码的第三方消费者——生成代码的结构会从继承体系变为联合类型。
- **回迁注意事项**：纯规范文件改动，回迁风险低。回迁时需注意：(1) 若 1.4.x 分支的规范仍处于更早的"单一大模型"状态（PR #7710 之前），则需先回迁 #7710 再回迁本提交；(2) 本提交改变了生成代码的形态（从继承到联合），若有下游已基于继承形态生成代码并依赖，需重新生成适配；(3) discriminator 和 type enum 值未变，线上的 REST 请求/响应 JSON 格式完全不变，是向后兼容的规范层重构。
