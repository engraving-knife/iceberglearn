# 提交 0109：Open-API: Refactor TableRequirements (#7710)

## 提交信息

- **序号**：0109 / 4088
- **哈希**：a66166326d0ab3ee54065ac3accd6a3a1c3b89d9
- **短哈希**：a66166326
- **日期**：2023-10-30 16:58:59 +0100
- **作者**：Fokko Driesprong
- **提交说明**：Open-API: Refactor TableRequirements (#7710)
- **PR/Issue**：#7710

## 总体目的

这个提交对 Iceberg REST Catalog OpenAPI 规范中的 `TableRequirement` 模型进行了重构，把原先一个“大而全”的合并模型拆分为以 `type` 为判别字段（discriminator）的多态联合类型，使每种 `assert-*` 断言都成为带有自己精确字段约束的独立子模型。

在 REST Catalog 的提交协议里，客户端在 `UpdateTableRequest` 中会携带一组 `TableRequirement`（断言）和 `Update`（变更操作）。服务端必须先校验所有 requirement 成立，才会应用 update。requirement 共有 8 种类型：`assert-create`、`assert-table-uuid`、`assert-ref-snapshot-id`、`assert-last-assigned-field-id`、`assert-current-schema-id`、`assert-last-assigned-partition-id`、`assert-default-spec-id`、`assert-default-sort-order-id`。

重构前的问题在于：原规范把这 8 种断言塞进一个 `TableRequirement` 对象，所有字段（`ref`、`uuid`、`snapshot-id`、`last-assigned-field-id` 等）统统声明为可选（`Optional`/无 `required`），`type` 只是一个枚举。这种建模方式无法表达“某种断言类型必须携带哪些字段”这一约束。正如 PR 描述里展示的，从原规范生成的 Python 代码会得到一个所有字段都可为 `None` 的扁平类，调用方无法从类型上知道 `assert-table-uuid` 必须带 `uuid`、`assert-ref-snapshot-id` 必须带 `ref` 和 `snapshot-id`，只能靠运行时文档和人工记忆。

这带来的后果是：自动生成的客户端代码类型安全性差、容易漏传字段；服务端实现者也只能靠文档理解每种断言的必填字段，规范本身没有约束力；规范的可读性也差——一段冗长的描述把 8 种断言塞在一个 description 里。

重构后，规范使用 OpenAPI 的 `discriminator` 机制（`propertyName: type` + `mapping`）把 `TableRequirement` 定义为一个判别基类，并为每种断言单独定义 `AssertCreate`、`AssertTableUUID`、`AssertRefSnapshotId` 等子 schema，每个子 schema 通过 `allOf` 引用基类，并在自己的 `required` 里精确声明该断言必须携带的字段。这样自动生成的代码（无论 Python、Java、Go）就能得到一个真正的判别联合类型，每种断言的字段约束在类型层面被强制表达，编译器可以检查，调用方不容易出错。

这个重构对 Iceberg REST 协议的演进有重要意义：它让规范的类型表达力对齐了协议本身的语义，提升了多语言客户端生成质量与服务端实现的一致性，是 REST Catalog 规范走向成熟、可被多方独立实现的关键一步。

## 如何达成设计目的

整体设计思路是“用 OpenAPI 的 discriminator + allOf 把扁平合并模型改造成多态联合类型”。改动同步体现在两个文件：声明式规范 `rest-catalog-open-api.yaml`（权威来源）和生成式参考代码 `rest-catalog-open-api.py`（Pydantic 模型）。

在 `yaml` 中：原 `TableRequirement` 改为只含 `type: string` 的基类 schema，并新增 `discriminator`（`propertyName: type`）和 `mapping`，把 8 种 `type` 值映射到 8 个子 schema。然后新增 8 个 `AssertXxx` schema，每个都用 `allOf` 引用基类 `TableRequirement`，并在自己的 `properties`/`required` 里精确声明该断言的字段与必填性，`type` 字段用 `enum` 单值锁定该断言类型。原 `TableRequirement` 里那 8 种字段和长描述被拆解、迁移到各子 schema 的 `description` 中。

在 `py` 中：把原来一个扁平的 `TableRequirement`（所有字段 `Optional`）改造成一个基类 `TableRequirement`（仅 `type: str`）加 8 个继承它的子类（`AssertCreate`、`AssertTableUUID` 等），每个子类用 `Literal['assert-xxx']` 锁定 `type`，并用 `Field(..., alias='...')`（注意是 `...` 而非 `None`）把必填字段标为必填，把原来 `Optional` 的弱约束改为强约束。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：把扁平的 `TableRequirement` 改造为带 discriminator 的多态基类，并为每种断言新增独立子 schema，使各断言的必填字段在规范层面被精确表达。

**工作逻辑**：

- **`TableRequirement` 基类重写**：原 schema 包含冗长 description（列举 8 种断言）和全部可选字段。重写后变为：

  ```yaml
  TableRequirement:
    discriminator:
      propertyName: type
      mapping:
        assert-create: '#/components/schemas/AssertCreate'
        assert-table-uuid: '#/components/schemas/AssertTableUUID'
        assert-ref-snapshot-id: '#/components/schemas/AssertRefSnapshotId'
        assert-last-assigned-field-id: '#/components/schemas/AssertLastAssignedFieldId'
        assert-current-schema-id: '#/components/schemas/AssertCurrentSchemaId'
        assert-last-assigned-partition-id: '#/components/schemas/AssertLastAssignedPartitionId'
        assert-default-spec-id: '#/components/schemas/AssertDefaultSpecId'
        assert-default-sort-order-id: '#/components/schemas/AssertDefaultSortOrderId'
    type: object
    required:
      - type
    properties:
      type:
        type: "string"
  ```

  `discriminator.propertyName: type` 告诉生成器：依据 `type` 字段选择具体子类型；`mapping` 把每个枚举值显式映射到对应子 schema 的引用，避免依赖命名约定。基类只强制要求 `type`，其余字段由子类声明。

- **8 个 `AssertXxx` 子 schema**：每个结构一致，以 `AssertTableUUID` 为例：

  ```yaml
  AssertTableUUID:
    allOf:
      - $ref: "#/components/schemas/TableRequirement"
    description: The table UUID must match the requirement's `uuid`
    required:
      - type
      - uuid
    properties:
      type:
        type: string
        enum: ["assert-table-uuid"]
      uuid:
        type: string
  ```

  关键点：
  - `allOf` 引用基类，使子类继承 `type` 字段并参与 discriminator 解析；
  - `required` 列出该断言必须携带的字段（如 `assert-table-uuid` 必须有 `uuid`，`assert-ref-snapshot-id` 必须有 `ref` 和 `snapshot-id`）；
  - 子类自己的 `type` 用 `enum: ["assert-xxx"]` 单值枚举锁定，保证该子类只匹配这一种断言；
  - 原 `TableRequirement` 里那段合并 description 被拆分到各子 schema 的 `description`，可读性更好。

  其余 7 个子 schema（`AssertCreate`、`AssertRefSnapshotId`、`AssertLastAssignedFieldId`、`AssertCurrentSchemaId`、`AssertLastAssignedPartitionId`、`AssertDefaultSpecId`、`AssertDefaultSortOrderId`）遵循同样模式，各自声明对应必填字段（如 `snapshot-id`、`last-assigned-field-id`、`current-schema-id`、`last-assigned-partition-id`、`default-spec-id`、`default-sort-order-id`）。注意 `AssertCreate` 比较特殊——它只需要 `type`，没有额外字段，因为 `assert-create` 仅断言“表必须不存在”。

### `open-api/rest-catalog-open-api.py`

**修改目的**：同步更新 Pydantic 参考实现，使生成代码体现重构后的多态结构，验证规范的可生成性。

**工作逻辑**：

- **`TableRequirement` 基类简化**：原类把 8 种断言的字段全声明为 `Optional`（`Field(None, alias=...)`）。重构后简化为：

  ```python
  class TableRequirement(BaseModel):
      type: str
  ```

  仅保留 `type` 作为基类字段。

- **8 个 `AssertXxx` 子类**：每个继承 `TableRequirement`，用 `Literal['assert-xxx']` 锁定 `type`，并把该断言的必填字段声明为必填（用 `Field(..., alias='...')`，注意 `...` 是 Pydantic 表示“必填”的 ellipsis 而非 `None`）。以 `AssertRefSnapshotId` 为例：

  ```python
  class AssertRefSnapshotId(TableRequirement):
      """
      The table branch or tag identified by the requirement's `ref` must reference the requirement's `snapshot-id`; if `snapshot-id` is `null` or missing, the ref must not already exist
      """
      type: Literal['assert-ref-snapshot-id']
      ref: str
      snapshot_id: int = Field(..., alias='snapshot-id')
  ```

  对比原写法 `snapshot_id: Optional[int] = Field(None, alias='snapshot-id')`，重构后 `snapshot_id` 必填且非空，`ref` 也由 `Optional[str] = None` 变为 `str`。这正是 PR 描述里强调的“约束定义不对”的核心修复——把弱约束升级为强约束。

  8 个子类对应 8 种断言类型，`AssertCreate` 仅含 `type: Literal['assert-create']` 无额外字段；其余各自声明对应字段。每个子类的 docstring 即原 `TableRequirement` 那段长描述里对应断言的语义说明，保持了文档信息不丢失。

  注意 PR 描述里展示的目标代码用 `TableRequirement.__root__: Union[...]` 的判别联合写法，但实际提交的 `py` 文件采用的是“基类 + 子类继承”的面向对象写法（子类 `class AssertXxx(TableRequirement)`），这是与 yaml 里 `allOf` 引用基类相对应的实现方式，语义等价。

## 小结

这个提交把 REST Catalog OpenAPI 规范里扁平、弱约束的 `TableRequirement` 重构为基于 discriminator 的多态联合类型，为每种 `assert-*` 断言定义独立子 schema 并精确声明必填字段，使规范能正确表达协议语义，显著提升了多语言客户端生成的类型安全性与服务端实现的一致性。
