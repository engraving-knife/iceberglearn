# 提交 1114：Spec: Add RemovePartitionSpecsUpdate REST update type (#10846)

## 提交信息

- **序号**：1114 / 4088
- **哈希**：cd32ec76ecd2866c05185065e4ed7196121de49a
- **短哈希**：cd32ec76e
- **日期**：2024-08-28（Wed Aug 28 09:40:32 2024 -0600）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：Spec: Add RemovePartitionSpecsUpdate REST update type (#10846)
- **PR/Issue**：#10846

## 总体目的

Iceberg 表的元数据更新通过 REST Catalog OpenAPI 暴露为 `TableUpdate` 联合类型，每种更新动作（如 `assign-uuid`、`upgrade-format-version`、`add-snapshot` 等）对应一个 `Update` 子类型。在此之前，规范中已存在 `add-partition-spec`、`set-partition-spec` 等"添加/替换"分区规格的更新，但缺少一个独立的"删除（移除）分区规格"动作。虽然表元数据层（core）已支持通过 `RemovePartitionSpecsUpdate` 移除不再使用的分区规格，但 REST OpenAPI 规范尚未定义对应的 update 类型，导致 REST 客户端无法通过规范接口发起该操作。

本提交在 REST Catalog OpenAPI 规范（YAML 与等价的 Python 模型）中新增 `RemovePartitionSpecsUpdate` 类型：

- `action: "remove-partition-specs"`
- `spec-ids: List[int]`（要移除的分区规格 ID 列表）

并将其加入 `TableUpdate` 联合与 `UpdateAction` 的 discriminated mapping，使该动作成为规范一等公民，REST 客户端可序列化/反序列化该请求体，服务端实现可基于规范进行解析。

## 如何达成设计目的

通过同步修改 OpenAPI 规范的两个表达形式：

1. **`open-api/rest-catalog-open-api.yaml`**：标准的 OpenAPI 3.0 YAML 规范文件，新增 `RemovePartitionSpecsUpdate` schema 节点、在 `TableUpdate` 的 `anyOf` 中加入引用、在 `UpdateAction` 的 mapping 中添加 `remove-partition-specs` 映射。
2. **`open-api/rest-catalog-open-api.py`**：等价的 Python Pydantic 模型表达（Iceberg 用 Python 模型作为规范的另一种可执行表达形式，便于测试和生成客户端），新增 `RemovePartitionSpecsUpdate` Pydantic 类，并将其加入 `TableUpdate` 的 Union。

两份文件同步修改保证规范一致性。该改动仅扩展规范，不强制服务端实现（实现由各 REST Catalog 服务按需提供）。

## 修改详情

### `open-api/rest-catalog-open-api.py`

**修改目的**：在 Python 模型表达中新增 `RemovePartitionSpecsUpdate`。

**工作逻辑**：

1. 在 `RemovePartitionStatisticsUpdate` 之后、`AssertCreate` 之前新增类定义：

```python
class RemovePartitionSpecsUpdate(BaseUpdate):
    action: Optional[Literal['remove-partition-specs']] = None
    spec_ids: List[int] = Field(..., alias='spec-ids')
```

- 继承 `BaseUpdate`，与其他 update 类型一致；
- `action` 是可选字面量 `"remove-partition-specs"`（与 YAML 中的 enum 对应），可选是为了允许客户端省略 action 字段，由 discriminator 推断；
- `spec_ids` 是必填的整数列表，Pydantic alias `spec-ids` 对应 REST 规范中的连字符命名风格。

2. 在 `TableUpdate` 的 `Union[...]` 末尾（`RemoveStatisticsUpdate` 之后）追加 `RemovePartitionSpecsUpdate`：

```python
class TableUpdate(BaseModel):
    __root__: Union[
        AssignUUIDUpdate,
        ...
        RemoveStatisticsUpdate,
        RemovePartitionSpecsUpdate,
    ]
```

使 `TableUpdate` 联合类型可接受新动作。

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：在标准 OpenAPI YAML 规范中新增 schema 与映射。

**工作逻辑**：

1. 在 `UpdateAction` 的 discriminator `mapping` 中新增一行：

```yaml
remove-partition-specs: '#/components/schemas/RemovePartitionSpecsUpdate'
```

让 discriminator 能根据 `action` 字段值路由到对应 schema。

2. 在 `RemovePartitionStatisticsUpdate` 之后新增 `RemovePartitionSpecsUpdate` schema：

```yaml
RemovePartitionSpecsUpdate:
  allOf:
    - $ref: '#/components/schemas/BaseUpdate'
  required:
    - spec-ids
  properties:
    action:
      type: string
      enum: [ "remove-partition-specs" ]
    spec-ids:
      type: array
      items:
        type: integer
```

- 通过 `allOf` 复用 `BaseUpdate`（含 `action` 基本约束）；
- `spec-ids` 必填，整数数组；
- `action` 限定为枚举 `"remove-partition-specs"`。

3. 在 `TableUpdate` 的 `anyOf` 末尾追加引用：

```yaml
- $ref: '#/components/schemas/RemovePartitionSpecsUpdate'
```

使 `TableUpdate` 联合类型包含新动作。

## 小结

- **成效**：REST Catalog OpenAPI 规范现支持 `remove-partition-specs` 更新动作，REST 客户端可基于规范序列化该请求体（含 `action` 与 `spec-ids` 字段），服务端实现可解析并执行分区规格删除；与 core 层 `RemovePartitionSpecsUpdate` 元数据更新形成端到端规范覆盖。
- **影响范围**：2 个文件、22 增 0 删，纯规范扩展，无运行时代码改动；不破坏既有 API（仅追加新枚举值与新 schema）。
- **回迁到 1.4.x 的注意事项**：1.4.x 作为维护分支通常不扩展 REST 规范接口；本提交属于规范能力扩展（新动作类型），不是 bug 修复。若 1.4.x 也希望支持通过 REST 移除分区规格，则需同时回迁本提交与 core 层 `RemovePartitionSpecsUpdate` 实现（确认 1.4.x 的 core 是否已有该更新动作）。考虑到 1.4.x 的 REST Catalog 实现可能尚未支持此动作的服务端逻辑，**默认不建议回迁**；若确有需要，应在确认 1.4.x core 已支持该 update 后再回迁规范，避免规范暴露但实现未跟进。
