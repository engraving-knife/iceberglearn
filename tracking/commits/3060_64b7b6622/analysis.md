# 提交 3060：Build: Bump datamodel-code-generator from 0.49.0 to 0.52.1 (#14962)

## 提交信息

- **序号**：3060 / 4088
- **哈希**：64b7b66222b1f59eaf0c4ce09350665d590271ae
- **短哈希**：64b7b6622
- **日期**：2026-01-03
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump datamodel-code-generator from 0.49.0 to 0.52.1 (#14962)
- **PR/Issue**：#14962

## 总体目的

本提交由 Dependabot 发起，将 `datamodel-code-generator` 从 `0.49.0` 升级到 `0.52.1`。该工具是 Iceberg REST Catalog OpenAPI 规范的代码生成器：Iceberg 在 `open-api/` 目录下维护 REST Catalog 的 OpenAPI 规范（YAML），并用 `datamodel-code-generator` 根据该规范自动生成对应的 Python Pydantic 模型文件 `rest-catalog-open-api.py`。这个生成文件被 pyiceberg 等 Python 客户端复用，作为 REST Catalog 请求/响应模型的类型定义来源。因此本次升级不仅是工具链版本提升，还必须把生成产物 `rest-catalog-open-api.py` 一并重新生成，以反映新版生成器对模型表达方式的调整。

从版本号跨度看，这是一次 semver-minor 升级（`0.49.0 → 0.52.1`，跨 0.50、0.51、0.52 三个 minor 版本）。Dependabot 元数据也标注 `update-type: version-update:semver-minor`、`dependency-type: direct:production`。升级后生成器对 Pydantic 模型的生成策略有明显变化，主要体现在两点：一是把原先 `type: str = Field('xxx', const=True)` 这种"用 `str` 类型 + `const=True` 约束固定值"的写法，改为更精确的 `type: Literal['xxx'] = Field('xxx', const=True)`，即用 `typing.Literal` 字面量类型来约束枚举值，使类型检查器能精确推断取值；二是对 `ResidualFilter` 相关的多个派生类（`ResidualFilter1`~`ResidualFilter8` 及 `ResidualFilter` 联合类型）做了大幅简化，改为直接复用 `Expression` 类型，删除了冗余的中间类定义。此外还新增了 `deprecated=True` 标记用于已废弃字段（如 `OAuthTokenRequest.__root__`、`SetStatisticsUpdate.snapshot_id`、`AddSchemaUpdate.last_column_id`），`SetSnapshotRefUpdate` 改为继承 `BaseUpdate` 与 `SnapshotReference`，这些变更使生成的模型更贴合规范语义。

这些变化对下游 pyiceberg 等消费方是有益的：`Literal` 类型让 IDE 和类型检查器能正确提示固定取值，废弃标记让使用者收到明确的弃用警告，模型简化降低了维护负担。预期影响是正向的，但因为重新生成了约 199 行变更，需要下游在同步该文件时注意类型签名变化的兼容性。

## 如何达成设计目的

两步：在 `open-api/requirements.txt` 中把生成器版本固定为 `0.52.1`；用新版生成器重新生成 `open-api/rest-catalog-open-api.py`，让产物与工具版本一致。改动集中在 `open-api/` 目录，不触及 Java 主代码。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 `datamodel-code-generator` 版本固定值。

**工作逻辑**：
将 `datamodel-code-generator==0.49.0` 改为 `datamodel-code-generator==0.52.1`。该文件锁定 OpenAPI 代码生成链路所用的 Python 依赖版本，与 `openapi-spec-validator`、`yamllint` 一同约束生成与校验环境，确保任何人重新生成 `rest-catalog-open-api.py` 时使用一致的生成器版本。

### `open-api/rest-catalog-open-api.py` (+74/-127 lines)

**修改目的**：用新版生成器重新生成 Pydantic 模型，反映生成策略变化。

**工作逻辑**：
重新生成的产物包含多类一致性改进。其一，大量 `type`/`action` 字段由 `str = Field(..., const=True)` 改为 `Literal['具体值'] = Field(..., const=True)`，覆盖 `TrueExpression`、`FalseExpression`、各 `*Update` 类（`AssignUUIDUpdate`、`UpgradeFormatVersionUpdate`、`SetCurrentSchemaUpdate`、`AddPartitionSpecUpdate` 等）、各 `TableRequirement` 子类（`AssertCreate`、`AssertTableUUID`、`AssertRefSnapshotId` 等）、`StructType`/`ListType`/`MapType`、`NotExpression`、`TransformTerm`、`DataFile.content` 等。这让这些字段的合法取值在类型层面被精确约束，而非宽松的任意字符串。

其二，删除了 `ResidualFilter1`~`ResidualFilter8` 这一系列中间基类及 `ResidualFilter` 联合类型（`__root__` 为多类型联合），将 `FileScanTask.residual_filter` 的类型直接改为 `Expression | None`，并移除了对应的 `ResidualFilter.update_forward_refs()` 调用。这大幅简化了模型层级，因为 `Expression` 本身已是这些过滤表达式的联合类型，无需再包一层 `ResidualFilter`。

其三，`SetSnapshotRefUpdate` 由原先独立定义 `type`/`ref_name`/`snapshot_id`/`max_ref_age_ms` 等字段，改为 `class SetSnapshotRefUpdate(BaseUpdate, SnapshotReference)`，复用 `SnapshotReference` 已有的 `type`/`snapshot_id`/`max_ref_age_ms`/`max_snapshot_age_ms`/`min_snapshots_to_keep` 字段，仅保留 `action: Literal['set-snapshot-ref']` 与 `ref_name`，减少重复定义。

其四，为已废弃字段补加 `deprecated=True`：`OAuthTokenRequest.__root__`、`SetStatisticsUpdate.snapshot_id`、`AddSchemaUpdate.last_column_id`，使生成的模型在序列化/反序列化时能体现废弃语义，提醒消费方迁移。

## 总结

本提交将 `datamodel-code-generator` 从 `0.49.0` 升级到 `0.52.1`（semver-minor），并用新版重新生成了 `rest-catalog-open-api.py`，带来 `Literal` 字面量类型约束、`ResidualFilter` 模型大幅简化、`SetSnapshotRefUpdate` 字段复用、废弃字段标记等改进，使 REST Catalog 的 Python 模型更精确、更易维护，对 pyiceberg 等下游消费方有正向价值。
