# 提交 2977：Build: Bump datamodel-code-generator from 0.36.0 to 0.41.0 (#14791)

## 提交信息

- **序号**：2977 / 4088
- **哈希**：01b29dd6403c8cc1fad084c3e11f2cb4fe88177e
- **短哈希**：01b29dd64
- **日期**：2025-12-08
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump datamodel-code-generator from 0.36.0 to 0.41.0 (#14791)
- **PR/Issue**：#14791

## 总体目的

这是一次纯依赖升级类提交，目标是将 `open-api/requirements.txt` 中用于从 OpenAPI 规范自动生成 Python Pydantic 模型的工具 `datamodel-code-generator` 从 0.36.0 升级到 0.41.0（中间跨越 0.37/0.38/0.39/0.40 共 5 个次版本，从提交说明的多个子提交可看出是逐版本递进升级后再合并）。`datamodel-code-generator` 是 Iceberg REST Catalog OpenAPI 模块的核心构建依赖：项目维护一份 YAML 形式的 OpenAPI 规范，再用该工具生成 `open-api/rest-catalog-open-api.py`（基于 Pydantic 的 Python 模型代码），供校验和参考实现使用。

升级的动机通常是：获取新版本对 OpenAPI 规范解析与代码生成的修正与改进、修复 bug、跟进 Pydantic v1/v2 兼容性、避免被旧版本锁死。由于 0.36→0.41 跨越多个次版本，新版本生成器在"继承 + 常量字段"以及"组合 schema（allOf/oneOf）"等场景下输出了结构不同的代码，因此本次升级同时重新生成了 `rest-catalog-open-api.py`，让生成物与新版本生成器保持一致，避免后续重新生成时出现大量噪声 diff。

## 如何达成设计目的

整体思路很简单：在 `open-api/requirements.txt` 中把版本号改为 `0.41.0`，然后使用新版本生成器重新生成 `rest-catalog-open-api.py`，提交两份产物。改动文件仅这两个，但生成代码差异较大（+227/-70 行），主要集中在两类变化：一是大量 `*Update`、`TableRequirement` 子类、`ContentFile` 子类从"继承父类并把 `action`/`type`/`content` 字段以默认常量赋值"改为"直接继承 `BaseModel` 并把常量字段标记为必填（`...`）"；二是新增了若干 `ResidualFilter1..8` 与 `ResidualFilter` 模型，并让 `FileScanTask.residual_filter` 与 `CommitTableRequest.requirements` 的类型表达更精确。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 `datamodel-code-generator` 版本号。

**工作逻辑**：
将 `datamodel-code-generator==0.36.0` 改为 `datamodel-code-generator==0.41.0`。该依赖是 OpenAPI 模块从 YAML 规范生成 Python Pydantic 模型代码的工具，升级版本语义类型为多个 minor 版本累积升级（0.36→0.41），预期影响是重新生成代码后输出格式发生如上所述的结构性变化，但对外暴露的 API 模型语义不变。其余两个依赖（`openapi-spec-validator==0.7.2`、`yamllint==1.37.1`）保持不变。

### `open-api/rest-catalog-open-api.py` (+227/-70 lines)

**修改目的**：用新版生成器重新生成 Python 模型代码，反映新版本对继承与组合 schema 的代码生成策略变化。

**工作逻辑**：
本次重新生成带来的几类典型变化：

1. **常量字段从"默认值"改为"必填 + const"**。所有 `*Update`（如 `AssignUUIDUpdate`、`UpgradeFormatVersionUpdate`、`SetCurrentSchemaUpdate`、`AddPartitionSpecUpdate`、`AddSnapshotUpdate`、`SetPropertiesUpdate`、`AddSchemaUpdate`、`SetStatisticsUpdate`、`SetPartitionStatisticsUpdate`、`AddEncryptionKeyUpdate` 等）以及 `TableRequirement` 子类（`AssertCreate`、`AssertTableUUID`、`AssertRefSnapshotId`、`AssertLastAssignedFieldId`、`AssertCurrentSchemaId`、`AssertDefaultSpecId`、`AssertDefaultSortOrderId` 等）原本形如 `action: str = Field('assign-uuid', const=True)`，现在统一改为 `action: str = Field(..., const=True)`。`...`（Ellipsis）表示该字段在构造时必填，但仍受 `const=True` 约束（值必须等于固定常量）。这是新版生成器对 OpenAPI 中"固定枚举值"字段的处理方式调整——不再隐式提供默认值，而是要求显式传入。同时这些类不再继承父类（`BaseUpdate`/`TableRequirement`），而是直接继承 `BaseModel`，即生成器把父类字段"内联"到了子类中。

2. **`SetSnapshotRefUpdate` 不再继承 `SnapshotReference`**。原代码 `class SetSnapshotRefUpdate(BaseUpdate, SnapshotReference)` 现改为 `class SetSnapshotRefUpdate(BaseModel)`，并把 `type`、`ref_name`、`snapshot_id`、`max_ref_age_ms`、`max_snapshot_age_ms`、`min_snapshots_to_keep` 等字段直接平铺到类中。这是新版本生成器对多重继承/`allOf` 组合的扁平化处理结果。

3. **`ContentFile` 子类字段被内联**。`PositionDeleteFile`、`EqualityDeleteFile`、`DataFile` 原本继承 `ContentFile`，现在改为直接继承 `BaseModel`，并把 `file_path`、`file_format`、`spec_id`、`partition`、`file_size_in_bytes`、`record_count`、`key_metadata`、`split_offsets`、`sort_order_id` 等共享字段逐一复制到每个子类中。这是扁平化策略的又一处体现，使得每个类自包含、不再依赖父类。

4. **新增 `ResidualFilter` 系列模型**。新版生成器为 `FileScanTask.residual-filter` 这个 oneOf/anyOf 组合表达式生成了独立的命名类：`ResidualFilter1`（基类，仅含文档字符串）、`ResidualFilter2`（继承 `TrueExpression` 与 `ResidualFilter1`）、`ResidualFilter3`（继承 `FalseExpression`）、`ResidualFilter4`（继承 `AndOrExpression`）、`ResidualFilter5`（继承 `NotExpression`）、`ResidualFilter6`（继承 `SetExpression`）、`ResidualFilter7`（继承 `LiteralExpression`）、`ResidualFilter8`（继承 `UnaryExpression`），以及一个根类型 `ResidualFilter`，其 `__root__` 是这些子类的 `Union`。`FileScanTask.residual_filter` 的类型由原来的 `Optional[Expression]` 改为 `Optional[ResidualFilter]`。语义上仍是"对文件扫描任务中行应用的残余过滤器"，但类型表达更精确、自包含。`ResidualFilter.update_forward_refs()` 也被加入到底部的前向引用更新调用列表中。

5. **`CommitTableRequest.requirements` 类型精确化**。原为 `List[TableRequirement]`，现在展开为 `List[Union[AssertCreate, AssertTableUUID, AssertRefSnapshotId, AssertLastAssignedFieldId, AssertCurrentSchemaId, AssertLastAssignedPartitionId, AssertDefaultSpecId, AssertDefaultSortOrderId]]`。这与上述把 `TableRequirement` 子类扁平化的策略一致：由于子类不再继承 `TableRequirement`，类型需要显式列出所有可能的断言类型，从而保持原有的多态语义。

6. 还有一处无关紧要的空白变化（文件顶部多一空行）。这些变化都不改变模型对外的语义和字段集合，仅是生成器输出风格的差异，符合依赖升级"重新生成产物"的预期。

## 总结

该提交将 OpenAPI 模块依赖的 `datamodel-code-generator` 从 0.36.0 升级到 0.41.0，并用新版本重新生成了 Python Pydantic 模型代码。核心价值在于跟进生成器演进、避免技术债，新版生成器对"继承 + 常量字段"和"组合 schema"采用了扁平化、显式枚举类型的输出策略，使生成的 `*Update`、`TableRequirement` 子类、`ContentFile` 子类自包含、不依赖父类继承，并通过新增 `ResidualFilter` 系列模型让 `FileScanTask.residual_filter` 与 `CommitTableRequest.requirements` 的类型表达更精确。模型对外语义不变，属于一次典型的、可控的依赖升级 + 产物重新生成。
