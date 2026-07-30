# 提交 0013：OpenAPI: Add AssignUUID update to metadata updates (#8716)

## 提交信息

- **序号**：0013 / 4088
- **哈希**：dd26f3630954e69c584faa06c9395f77d49337b1
- **短哈希**：dd26f3630
- **日期**：2023-10-05 11:23:10 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：OpenAPI: Add AssignUUID update to metadata updates (#8716)
- **PR/Issue**：#8716

## 总体目的

这个提交在 Iceberg REST Catalog 的 OpenAPI 规范中补充了 `assign-uuid` 这一元数据更新动作（metadata update action）。Iceberg 表的元数据（table metadata）通过一系列"更新操作（updates）"来演进，例如 `upgrade-format-version`、`add-schema`、`set-current-schema`、`add-snapshot` 等。这些更新动作既体现在 Iceberg 的格式规范（spec）中，也体现在 REST Catalog 的 OpenAPI 契约中——客户端通过提交一组 update actions 来变更表元数据。

`assign-uuid` 动作的用途是给表分配一个 UUID。表的 UUID 是表的全局唯一标识，写入表元数据的 `table-uuid` 字段，用于跨 catalog/跨系统的表身份识别与同步。在某些场景下（例如通过 `register_table` 把一个已存在元数据文件的表注册到 REST catalog，或需要为表补分配/重分配 UUID 时），需要单独通过一个 update action 来设置 UUID。该动作在 Iceberg 的 Java 实现与 spec 中已有定义，但 REST Catalog 的 OpenAPI 规范此前并未将其列入 `BaseUpdate` 的 action 枚举，也未定义对应的请求体结构，导致 REST 客户端无法通过标准契约发送 `assign-uuid` 更新。

本提交补齐了这一缺口：在 OpenAPI 的 YAML 规范与对应的 Python（pydantic）模型中都新增了 `assign-uuid` action 与 `AssignUUIDUpdate` 结构，使 REST Catalog 的契约与 Iceberg 核心规范保持一致，让客户端能合法地提交 UUID 分配更新。这是 REST Catalog 规范完整性维护的一部分。

## 如何达成设计目的

整体设计思路是在 OpenAPI 契约的两个等价表达——YAML 规范（`rest-catalog-open-api.yaml`，作为权威契约文档）与 Python 模型（`rest-catalog-open-api.py`，用于生成/校验客户端代码）——中同步新增 `assign-uuid`。具体做法是：在 `BaseUpdate` 的 `action` 枚举中加入 `assign-uuid` 值，新增 `AssignUUIDUpdate` 结构（继承 `BaseUpdate` 并要求一个 `uuid` 字段），并将 `AssignUUIDUpdate` 加入 `TableUpdate` 的联合类型（anyOf/Union）中，使其成为合法的表更新请求体。两处改动保持镜像一致。

## 修改详情

### `open-api/rest-catalog-open-api.py`

**修改目的**：在 Python（pydantic）模型中新增 `assign-uuid` action 与 `AssignUUIDUpdate`，并将其加入 `TableUpdate` 联合类型。

**工作逻辑**：
1. 在 `BaseUpdate` 类的 `action` 字段的 `Literal[...]` 枚举中，在列表最前面新增 `'assign-uuid'`（位于 `'upgrade-format-version'` 之前），使 `assign-uuid` 成为合法的 action 值。
2. 新增 `AssignUUIDUpdate(BaseUpdate)` 类，包含一个字段 `uuid: str`，表示要分配给表的 UUID。该类放在 `BaseUpdate` 定义之后、`UpgradeFormatVersionUpdate` 之前。
3. 在 `TableUpdate` 的 `__root__: Union[...]` 中，在联合类型最前面加入 `AssignUUIDUpdate`，使其成为合法的表更新类型之一。

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：在 YAML 规范中镜像新增 `assign-uuid` action、`AssignUUIDUpdate` schema，并将其加入 `TableUpdate` 的 anyOf。

**工作逻辑**：
1. 在 `BaseUpdate` schema 的 `action` 字段 `enum` 列表中，在最前面新增 `- assign-uuid`（位于 `- upgrade-format-version` 之前），与 Python 模型保持一致。
2. 新增 `AssignUUIDUpdate` schema，使用 `allOf` 组合：引用 `#$ref: '#/components/schemas/BaseUpdate'`，并追加一个 object 定义，其中 `required` 列出 `uuid`，`properties` 定义 `uuid` 为 `type: string`。这种 allOf 组合是 OpenAPI 表达"继承 + 扩展字段"的标准模式，与同文件中 `UpgradeFormatVersionUpdate`、`AddSchemaUpdate` 等结构写法一致。
3. 在 `TableUpdate` schema 的 `anyOf` 列表最前面新增 `- $ref: '#/components/schemas/AssignUUIDUpdate'`，使其成为合法的表更新请求体。

## 小结

本提交通过在 REST Catalog 的 OpenAPI YAML 规范与 Python 模型中同步新增 `assign-uuid` action 与 `AssignUUIDUpdate` 结构，补齐了表元数据 UUID 分配更新在 REST 契约中的缺失，使 REST Catalog 规范与 Iceberg 核心规范保持一致。
