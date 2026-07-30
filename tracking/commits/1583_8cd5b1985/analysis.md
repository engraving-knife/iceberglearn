# 提交 1583：Open-API: Bump to OpenAPI 3.1 (#11955)

## 提交信息

- **序号**：1583
- **哈希**：8cd5b1985d3f9c55ab2ced174559a8416b6ca1b4
- **短哈希**：8cd5b1985
- **日期**：2025-01-14（Tue Jan 14 20:09:38 2025 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Open-API: Bump to OpenAPI 3.1 (#11955)
- **PR/Issue**：#11955

## 总体目的

本提交将 Iceberg REST Catalog 的 OpenAPI 规范从 **3.0.3 升级到 3.1.1**，并相应调整由规范生成的 Python（Pydantic）模型代码。核心动因是 OpenAPI 3.1 引入了 `const` 关键字，可以更精确地表达"单值枚举"（single-value enum）这一语义。

在 3.0.x 中，当一个字段只能取一个固定字符串值时（例如所有 `Update` 类的 `action` 字段、`StructType.type` 必为 `"struct"` 等），惯例写法是用 `enum: ["struct"]`，即只含一个元素的枚举。这种写法虽然可行，但语义上 `enum` 暗示"多选一"，而此处实际是"常量"。OpenAPI 3.1 借鉴 JSON Schema 2020-12，新增了 `const`，专门用于表达"该字段值恒等于某固定值"，更贴合语义。

升级还顺带修复了 PR #11806 中报告的编译问题——某些代码生成器在处理 `enum` 单值 + Pydantic `Literal` 的组合时存在兼容性问题，改用 `const` 后可消除。

此外，本提交顺带对 Python 模型中的 `TableRequirement` 类做了重构：原本它是一个带 `__root__` 判别联合（discriminated union）的包装模型，重构后改为基类，让各 `Assert*` 类直接继承 `TableRequirement`。这与 `const` 改造配合，简化了模型结构。

## 如何达成设计目的

整体改动是机械式但有语义的批量替换，分布在两个文件：
- `rest-catalog-open-api.yaml`：把 `openapi:` 版本号从 `3.0.3` 改为 `3.1.1`；把所有单值 `enum: ["xxx"]` 替换为 `const: "xxx"`；并相应从 `required` 列表中移除 `action`（因为 `const` 字段在 JSON Schema 2020-12 中默认即视为常量，且生成器层面 Pydantic 会带默认值，不再需要在 required 中显式声明）。
- `rest-catalog-open-api.py`：把 Pydantic 模型中所有 `Literal['xxx']` 类型注解替换为 `str = Field('xxx', const=True)`（有默认值）或 `str = Field(..., const=True)`（必填但值固定）。`const=True` 是 Pydantic v1 的字段参数，表示该字段值被锁定为给定常量。同时把 `TableRequirement` 从 `__root__` 联合模型改为基类。

下面分别说明。

### 修改详情

#### `open-api/rest-catalog-open-api.yaml`

**修改目的**：升级 OpenAPI 版本并采用 `const` 表达单值常量字段。

**主要变更**：
1. 顶层 `openapi: 3.0.3` → `openapi: 3.1.1`。
2. 类型字段单值枚举改 `const`，涉及：
   - `StructType.type`：`enum: ["struct"]` → `const: "struct"`
   - `ListType.type`、`MapType.type` 同理改为 `const`。
   - `TrueExpression.type` / `FalseExpression.type` / `NotExpression.type`：原本是 `$ref: ExpressionType` + `enum: ["true"/"false"/"not"]`，改为 `const`。
   - `TransformTerm.type`：`enum: ["transform"]` → `const: "transform"`。
3. 所有 `*Update` 的 `action` 字段（如 `assign-uuid`、`upgrade-format-version`、`add-schema`、`set-current-schema`、`add-spec`、`set-default-spec`、`add-sort-order`、`set-default-sort-order`、`add-snapshot`、`set-snapshot-ref`、`remove-snapshots`、`remove-snapshot-ref`、`set-location`、`set-properties`、`remove-properties`、`add-view-version`、`set-current-view-version`、`remove-statistics`、`remove-partition-statistics`、`remove-partition-specs`、`set-statistics`、`set-partition-statistics`）由 `enum: ["xxx"]` 改为 `const: "xxx"`，并从各 schema 的 `required` 列表中删除 `action`（约 22 处）。
4. `PositionDeleteFile.content` / `EqualityDeleteFile.content` / `DataFile.content`：单值 `enum` 改 `const`。
5. `FailedPlanningResult.status` / `AsyncPlanningResult.status` / `CompletedPlanningResult.status`：`enum: ["failed"/"submitted"/"completed"]` 改 `const`。
6. `TableRequirement` 的各 `Assert*` 子类的 `type` 字段单值枚举改 `const`。

#### `open-api/rest-catalog-open-api.py`

**修改目的**：同步 Pydantic 模型，使用 `Field(..., const=True)` 替代 `Literal[...]`，并重构 `TableRequirement`。

**主要变更模式**：
1. `TrueExpression` / `FalseExpression` / `NotExpression` 的 `type: ExpressionType` 改为：
   ```python
   type: ExpressionType = Field(
       default_factory=lambda: ExpressionType.parse_obj('true'), const=True
   )
   ```
   这里用 `default_factory` 是因为 `ExpressionType` 是枚举模型，需要解析；`const=True` 锁定值。
2. 所有 `*Update` 类的 `action: Literal['xxx']` 改为 `action: str = Field('xxx', const=True)`（带默认值，对应 YAML 中 action 不再 required）。
   - 注意 `RemovePartitionSpecsUpdate` 原本是 `action: Optional[Literal['remove-partition-specs']] = None`，改为 `action: str = Field('remove-partition-specs', const=True)`，去掉了 Optional，统一为常量。
3. `PositionDeleteFile.content` / `EqualityDeleteFile.content`：`content: Literal['position-deletes']` → `content: Literal['position-deletes'] = Field(..., const=True)`（这里保留了 `Literal` 但加了 `const=True`，仍是必填 `...`）。
   - `DataFile.content`：`Literal['data']` → `str = Field(..., const=True)`。
4. 类型字段：`StructType.type` / `ListType.type` / `MapType.type` / `TransformTerm.type` 改为 `str = Field('xxx', const=True)`。
5. 计划状态字段：`FailedPlanningResult.status` / `AsyncPlanningResult.status` / `CompletedPlanningResult.status` 改为带 `const=True` 的 `Literal` 或 `str`。
6. `TableRequirement` 重构：
   - 原本：
     ```python
     class TableRequirement(BaseModel):
         __root__: Union[AssertCreate, AssertTableUUID, ...] = Field(..., discriminator='type')
     ```
     这是一个判别联合包装模型。
   - 现在：
     ```python
     class TableRequirement(BaseModel):
         type: str

     class AssertCreate(TableRequirement):
         type: str = Field(..., const=True)
     # 其余 Assert* 类同理继承 TableRequirement
     ```
     `TableRequirement` 变成基类，各 `Assert*` 继承它并锁定 `type` 为各自常量。原来的 `__root__` 联合模型定义被删除。`ViewRequirement` 保持原样（仍是 `__root__: AssertViewUUID`）。
   - 这一步让类型层次更清晰：`TableRequirement` 是所有表需求的抽象基类，子类通过 `const` 表明自己的 `type` 值，便于类型检查与序列化。

## 小结

- **成效**：REST Catalog OpenAPI 规范升级到 3.1.1，采用语义更准确的 `const` 表达单值常量字段；Python 模型同步改造，并简化了 `TableRequirement` 的类层次。顺带修复了 PR #11806 的代码生成编译问题。
- **影响范围**：仅 `open-api/` 目录下的两个文件（`rest-catalog-open-api.yaml` 规范、`rest-catalog-open-api.py` 生成模型）。不涉及 Java/Spark 等运行时代码，对 Iceberg 核心 API 无影响。但这是规范层面的变更，下游使用该 OpenAPI 规范生成客户端/服务端代码的项目需要支持 OpenAPI 3.1。
- **回迁到 1.4.x 的注意事项**：这是规范升级，**回迁需谨慎**。1.4.x 作为维护分支，其 REST Catalog 规范若已发布为 3.0.x，升级到 3.1.1 属于规范版本变更，可能影响下游消费者的代码生成结果（旧版生成器可能不支持 3.1）。建议评估 1.4.x 的下游兼容性后再决定是否回迁。若仅为了修 #11806 的编译问题，可考虑只回迁 Python 模型部分而暂不动 YAML 版本号——但本提交是二者捆绑的，拆分回迁需手工处理。一般而言，**此类规范升级通常不回迁到维护分支**，除非 1.4.x 明确需要 3.1 特性。
