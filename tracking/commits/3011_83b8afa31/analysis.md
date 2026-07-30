# 提交 3011：Build: Bump datamodel-code-generator from 0.41.0 to 0.43.1 (#14845)

## 提交信息

- **序号**：3011 / 4088
- **哈希**：83b8afa31a426af40317e4d685af99ad075ea1be
- **短哈希**：83b8afa31
- **日期**：2025-12-14 00:02:58 -0800
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump datamodel-code-generator from 0.41.0 to 0.43.1 (#14845)
- **提交说明（补充）**：Co-authored by dependabot[bot]
- **PR/Issue**：#14845

## 总体目的

`datamodel-code-generator` 是一个 Python 工具，能从 OpenAPI 规范文件自动生成 Pydantic 模型代码。Iceberg 在 `open-api/` 目录下维护 REST Catalog 的 OpenAPI 规范（`rest-catalog-open-api.yaml`），并用该工具生成对应的 Python 模型类 `open-api/rest-catalog-open-api.py`，作为 REST Catalog API 的 Python 客户端模型参考实现（pydantic 模型）。该生成文件被纳入仓库，便于 Python 生态对照规范使用。`open-api/requirements.txt` 钉住了生成器版本，保证生成的代码可复现。

dependabot 把生成器从 `0.41.0` 升到 `0.43.1`（minor 升级）。生成器跨两个 minor 版本后，其代码生成策略发生变化：对带有 `const`/固定值的字段，新版本倾向于生成更紧凑、更合理的形式（例如把 `type: ExpressionType = Field(default_factory=lambda: ExpressionType.parse_obj('true'), const=True)` 简化为 `type: str = Field('true', const=True)`），并会把共享字段的类改为继承公共基类以减少重复。因此本提交除了升版本号，还重新运行生成器，使 `rest-catalog-open-api.py` 同步更新为 0.43.1 的输出。改动动机是保持生成器最新、获取其改进的代码生成质量，并让仓库中的生成产物与规范及工具版本保持一致。

## 如何达成设计目的

两步：先在 `open-api/requirements.txt` 把 `datamodel-code-generator==0.41.0` 改为 `==0.43.1`；再用新版本生成器重新生成 `rest-catalog-open-api.py` 并提交其输出。生成结果的总体特征是"代码更精简、重复更少"：大量 update/requirement 类原本各自重复声明 `action: str = Field(..., const=True)`，新版改为继承 `BaseUpdate`/`TableRequirement` 基类并把固定值内联（如 `action: str = Field('assign-uuid', const=True)`）；`PositionDeleteFile`/`EqualityDeleteFile` 原本各自重复 `ContentFile` 的全部字段，新版改为继承 `ContentFile` 只声明自身差异字段；`TrueExpression`/`FalseExpression` 的 `type` 字段从用 `ExpressionType` 枚举 + `default_factory` 简化为直接用 `str` 字面量。此外还新增了 `AsyncPlanningResult`、`EmptyPlanningResult` 等模型（反映规范中已存在但旧生成器未产出的类型）。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本钉。

**工作逻辑**：
`datamodel-code-generator==0.41.0` 改为 `datamodel-code-generator==0.43.1`，与同文件中 `openapi-spec-validator==0.7.2`、`yamllint==1.37.1` 并列。该文件用于在 `open-api/` 下建立 Python 工具链环境，运行生成器校验/产出模型代码。dependabot 元数据标注 `update-type: version-update:semver-minor`。

### `open-api/rest-catalog-open-api.py` (+90/-158 lines)

**修改目的**：用 0.43.1 生成器重新生成 Pydantic 模型，使其与新工具版本及 OpenAPI 规范保持一致。

**工作逻辑**：
重新生成后的代码整体更精简，主要变化模式：

- **共享字段提取为基类继承**：各类 `*Update`（如 `AssignUUIDUpdate`、`UpgradeFormatVersionUpdate`、`SetCurrentSchemaUpdate`、`AddPartitionSpecUpdate`、`AddSnapshotUpdate`、`SetPropertiesUpdate`、`RemovePropertiesUpdate`、`AddViewVersionUpdate`、`AddEncryptionKeyUpdate` 等）原本都直接继承 `BaseModel` 并各自声明 `action: str = Field(..., const=True)`（占位无具体值）。新版改为继承 `BaseUpdate`（后者已声明 `action: str`），并在子类中把 `action` 内联为具体字面量，如 `action: str = Field('assign-uuid', const=True)`、`action: str = Field('upgrade-format-version', const=True)`。同理 `AssertCreate`、`AssertTableUUID`、`AssertRefSnapshotId`、`AssertCurrentSchemaId` 等要求类改为继承 `TableRequirement` 并把 `type` 内联为具体值。这消除了 `Field(..., const=True)`（无默认值的占位）这种语义上不完整的写法，改为既给默认值又标 const。
- **删除文件类去重**：`PositionDeleteFile` 与 `EqualityDeleteFile` 原本各自完整重复 `ContentFile` 的字段（`file_path`、`file_format`、`spec_id`、`partition`、`file_size_in_bytes`、`record_count`、`key_metadata`、`split_offsets` 等）。新版改为 `class PositionDeleteFile(ContentFile)` 与 `class EqualityDeleteFile(ContentFile)`，仅保留各自差异字段（如 `content`、`content_offset`、`content_size_in_bytes`、`equality_ids`），删除了大段重复字段声明，减少约 60+ 行重复代码。
- **枚举字段简化为字面量 str**：`TrueExpression`/`FalseExpression` 的 `type` 字段从 `ExpressionType = Field(default_factory=lambda: ExpressionType.parse_obj('true'), const=True)` 简化为 `type: str = Field('true', const=True)`，避免不必要的枚举解析工厂。
- **新增模型**：新增 `AsyncPlanningResult`（`status: Literal['submitted']`，含 `plan-id`）与 `EmptyPlanningResult`（`status: Literal['cancelled']`），对应服务端规划（server-side planning）的异步/空结果类型，反映 OpenAPI 规范中已有但旧版生成器未产出的定义。
- **杂项**：删除文件首行多余空行等格式微调。

总体上这些变化是生成器改进输出质量的体现，模型语义与 OpenAPI 规范一致，不改变 REST API 契约本身。

## 总结

该提交升级 OpenAPI Python 模型生成器 datamodel-code-generator（0.41.0 → 0.43.1，minor），并重新生成 `rest-catalog-open-api.py`。核心价值在于借助新版生成器的改进，让作为 REST Catalog API Python 模型参考的产物更精简、去重更彻底（update/requirement 类继承公共基类、删除文件类继承 ContentFile、枚举字段简化为字面量），并补出 `AsyncPlanningResult`/`EmptyPlanningResult` 等规范已有类型。属于依赖升级 + 生成产物同步刷新，不改变 API 契约，降低维护成本并保持模型与规范一致。
