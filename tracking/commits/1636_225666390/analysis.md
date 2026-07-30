# 提交 1636 225666390 分析

## 提交信息
- 哈希：2256663902c6bb6c429fcb21d78356ec32840572
- 日期：2025-01-24 11:50:02 -0600
- 作者：Russell Spitzer
- 消息：Spec, OpenAPI: Adds EnableRowLineage Metadata Update (#12050)

## 总体目的

本提交在 Iceberg REST Catalog 的 OpenAPI 规范中新增一种表元数据更新操作 `EnableRowLineageUpdate`（动作标识为 `enable-row-lineage`），用于通过 REST API 在表上启用行级血缘（row lineage）。

行级血缘是 Iceberg V3 表的重要能力，启用后表会维护每行的血缘标识，支持跨快照的行追踪。本提交将该元数据更新纳入公开的 REST Catalog OpenAPI 规范，使 REST Catalog 客户端可以构造“启用行级血缘”的提交请求（作为 `TableUpdate` 的一种），从而与服务端实现对接。

特别值得注意的是，注释明确写出 “Disabling Row Lineage is Forbidden”（禁止关闭行级血缘）。这表明 `enable-row-lineage` 是一个单向、不可逆的操作：一旦某表启用了行级血缘，就不能再关闭。这与 V3 格式版本升级类似，体现了“一旦承诺维护血缘，便不可回退”的语义。

## 如何达成设计目的

设计思路：沿用 REST Catalog OpenAPI 既有的“更新（Update）”建模模式——为每种元数据更新定义一个继承自 `BaseUpdate` 的 schema，其 `action` 字段为固定常量字符串，并把新 schema 注册到 `TableUpdate` 的 `anyOf` 联合以及更新类型的映射中。YAML 与 Python（pydantic）两份生成/手写同源规范同步修改，保持一致。

### 修改详情

#### open-api/rest-catalog-open-api.yaml

1. 在 `TableUpdate` 的 discriminator 映射（`action` 到 schema 的 `$ref` 字典）中新增一行：
   `enable-row-lineage: '#/components/schemas/EnableRowLineageUpdate'`
   该映射用于客户端根据 `action` 字段值定位对应的更新 schema。

2. 新增 `EnableRowLineageUpdate` schema 定义，紧跟在 `RemovePartitionSpecsUpdate` 之后：
   ```yaml
   # Disabling Row Lineage is Forbidden
   EnableRowLineageUpdate:
     allOf:
       - $ref: '#/components/schemas/BaseUpdate'
     properties:
       action:
         type: string
         const: "enable-row-lineage"
   ```
   它通过 `allOf` 引用 `BaseUpdate`（继承基础更新字段），并约束 `action` 为常量字符串 `"enable-row-lineage"`。schema 本身除了 action 外不带额外属性，因为“启用行级血缘”是一个无参动作。

3. 在 `TableUpdate` 的 `anyOf` 联合列表末尾追加 `- $ref: '#/components/schemas/EnableRowLineageUpdate'`，使其成为合法的表更新类型之一。

#### open-api/rest-catalog-open-api.py

与 YAML 对应，在 pydantic 模型中同步新增：

1. 新增类 `EnableRowLineageUpdate(BaseUpdate)`，其中 `action: str = Field('enable-row-lineage', const=True)`——用 pydantic 的 `const=True` 约束 action 固定为 `'enable-row-lineage'`，等价于 YAML 中的 `const`。

2. 在 `TableUpdate` 的联合类型列表（`Union[...]`）中加入 `EnableRowLineageUpdate`。

工作逻辑：REST Catalog 客户端若要启用某表的行级血缘，可在 `commit` 请求的 `updates` 数组中放入一个 `{"action": "enable-row-lineage"}` 的更新项；服务端识别该 action 后执行启用逻辑。由于规范未定义对应的 “disable-row-lineage” action，客户端无法表达关闭操作，从而在协议层面落实“不可逆”语义。

## 小结

- 成效：为 REST Catalog OpenAPI 规范补充了 `enable-row-lineage` 元数据更新，使行级血缘启用能力可通过标准 REST 接口提交；同时通过“只增 enable、不增 disable”在协议层固化了不可逆语义。改动仅 16 行，聚焦且自洽。
- 影响范围：仅影响 `open-api` 目录下的 REST Catalog 规范文档（YAML + Python 模型），不涉及 Java 实现、Spark 引擎或核心库代码。是规范层面的前置定义，为后续服务端/客户端实现提供契约。
- 回迁到 1.4.x 的注意事项：1.4.x 默认不支持 V3 与行级血缘。本提交属纯规范文档变更，回迁无技术风险，但回迁意义取决于 1.4.x 是否计划通过 REST Catalog 暴露 V3/行级血缘能力。若 1.4.x 的 REST Catalog 实现尚不能处理 `enable-row-lineage` action，则仅回迁规范会造成“规范允许但服务端未实现”的不一致，建议仅在 1.4.x 同时具备服务端处理能力时才回迁。此外需确认 1.4.x 的 `BaseUpdate` schema 与本提交引用的形状一致（含 `action` 字段）。
