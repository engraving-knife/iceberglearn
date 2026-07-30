# 提交 3964：Build: Bump datamodel-code-generator from 0.63.0 to 0.64.1 (#16993)

## 提交信息

- **序号**：3964 / 4088
- **哈希**：2dbe96bb41bd5a1087220e8a1136e7cfe708bf38
- **短哈希**：2dbe96bb4
- **日期**：2026-06-29 09:51:24 -0700
- **作者**：drexler-sky
- **提交说明**：Build: Bump datamodel-code-generator from 0.63.0 to 0.64.1 (#16993)
- **PR/Issue**：#16993

## 总体目的

本提交升级 `datamodel-code-generator` 工具从 0.63.0 到 0.64.1，并重新生成 REST catalog 的 OpenAPI Python 模型代码。从 0.64.0 版本起，该工具改变了可选 primitive const 字段的代码生成行为：不再将 const 值作为注入的默认值，因此生成的判别器字段（如 `action`/`type`）从 `Literal[...] = 'xxx'` 变为 `Literal[...] | None = None`。

为了保持生成的判别器字段为必填（非空），本提交同时在 REST spec 的 YAML 中将相关判别器字段标记为 `required`，使重新生成后判别器字段保持为 `Literal[...]`（不带 `| None = None`）。

## 如何达成设计目的

分两步：
1. 升级 `open-api/requirements.txt` 中的版本号，并重新运行代码生成器更新 `rest-catalog-open-api.py`。
2. 修改 `rest-catalog-open-api.yaml`，在相关 schema 定义中将判别器字段（如 `action`、`type`）加入 `required` 列表。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本。

**工作逻辑**：
```
datamodel-code-generator==0.64.1  # 原为 0.63.0
```

### `open-api/rest-catalog-open-api.py` (+30/-34 lines)

**修改目的**：使用新版本生成器重新生成 Python 模型。

**工作逻辑**：判别器字段的生成形式从带默认值变为不带默认值（因为字段被标记为 required）：
```python
# 旧：action: Literal['assign-uuid'] = 'assign-uuid'
# 新：action: Literal['assign-uuid']
```
涉及多个 Update 类（AssignUUIDUpdate、UpgradeFormatVersionUpdate、SetCurrentSchemaUpdate 等）。

### `open-api/rest-catalog-open-api.yaml` (+31/-0 lines)

**修改目的**：将判别器字段标记为 required，确保生成后为非空必填字段。

**工作逻辑**：在多个 schema 定义中添加 `required: [action]` 或 `required: [type]`，例如 UpdateRequest 的各子类。

## 总结

工具链升级与代码重新生成。升级 datamodel-code-generator 后，通过在 REST spec YAML 中将判别器字段标记为 required，确保生成的 Python 模型保持判别器为非空必填，维持 API 契约不变。
