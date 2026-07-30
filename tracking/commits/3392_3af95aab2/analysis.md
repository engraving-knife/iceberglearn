# 提交 3392：Build: Bump datamodel-code-generator from 0.54.1 to 0.55.0 (#15641)

## 提交信息

- **序号**：3392 / 4088
- **哈希**：3af95aab28f99429758a50560d32780d4994f697
- **短哈希**：3af95aab
- **日期**：2026-03-15 21:47:22 -0700
- **作者**：Manu Zhang
- **提交说明**：Build: Bump datamodel-code-generator from 0.54.1 to 0.55.0 (#15641)
- **PR/Issue**：#15641

## 总体目的

将 `datamodel-code-generator` Python 工具从 0.54.1 升级到 0.55.0，并重新生成 REST Catalog OpenAPI 的 Python 数据模型代码（`rest-catalog-open-api.py`）。`datamodel-code-generator` 是一个根据 OpenAPI 规范自动生成 Pydantic 数据模型的工具，Iceberg 用它来从 REST Catalog OpenAPI 规范生成对应的 Python 模型类。0.55.0 是一个 minor 级别升级，生成器输出风格发生了变化（主要是 Pydantic v2 API 的使用方式调整），因此需要重新提交生成结果。

## 如何达成设计目的

1. **升级工具版本**：修改 `open-api/requirements.txt` 中 `datamodel-code-generator` 的版本号。
2. **重新生成代码**：使用新版本工具重新从 OpenAPI 规范生成 `open-api/rest-catalog-open-api.py`，将生成器输出变化同步到代码库。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本。

**工作逻辑**：
- 将 `datamodel-code-generator==0.54.1` 改为 `datamodel-code-generator==0.55.0`。

### `open-api/rest-catalog-open-api.py` (+535/-464 lines)

**修改目的**：使用新版生成器重新生成 REST Catalog OpenAPI 的 Python 数据模型。

**工作逻辑**：
新版本生成器主要带来以下输出风格变化（均为自动生成，非手动修改）：
- **Pydantic 导入调整**：从 `from pydantic import BaseModel, Extra, Field` 改为 `from pydantic import Base64Str, BaseModel, ConfigDict, Field, RootModel`，反映 Pydantic v2 API 的使用。
- **`example` → `examples`**：`Field(..., example=...)` 改为 `Field(..., examples=[...])`，符合 Pydantic v2 / OpenAPI 3.1 规范。
- **`Extra` → `ConfigDict`**：模型配置从 `class Config: extra = Extra.allow` 迁移到 `model_config = ConfigDict(extra='allow')`，这是 Pydantic v2 推荐的写法。
- **新增 `RootModel` 和 `Base64Str`**：用于根类型模型和 Base64 编码字符串的表示。
- 其余为大量格式调整、字段顺序变化等生成器输出的常规差异。

## 总结

这是一次依赖工具升级 + 自动生成代码同步的提交。datamodel-code-generator 从 0.54.1 升级到 0.55.0 后，生成器输出风格向 Pydantic v2 规范靠拢（`examples`、`ConfigDict`、`RootModel` 等），重新生成的 `rest-catalog-open-api.py` 保持了与 OpenAPI 规范的一致性。改动本身不涉及业务逻辑变化，仅是工具输出结果的同步。
