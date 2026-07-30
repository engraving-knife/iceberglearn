# 提交 1736：Build: Bump datamodel-code-generator from 0.27.2 to 0.28.1 (#12290)

## 提交信息

- **序号**：1736 / 4088
- **哈希**：71493b92dc2e0b953c184f76ad76e7f8794da8b1
- **短哈希**：71493b92d
- **日期**：2025-02-16 17:26:10 +0100
- **作者**：Manu Zhang
- **提交说明**：Build: Bump datamodel-code-generator from 0.27.2 to 0.28.1 (#12290)
- **PR/Issue**：#12290

## 总体目的

`datamodel-code-generator` 是 Iceberg OpenAPI 模块使用的 Python 工具，用于从 OpenAPI YAML 规范文件（`rest-catalog-open-api.yaml`）自动生成 Pydantic Python 模型代码（`rest-catalog-open-api.py`）。该工具的新版本 0.28.1 相比 0.27.2 包含了 bug 修复和功能改进。

此外，`datamodel-code-generator` 0.28.x 版本不再支持 Python 3.8 作为目标版本（Python 3.8 已于 2024 年 10 月达到 EOL），因此需要将代码生成的目标 Python 版本从 3.8 升级到 3.9。本提交的目标是升级该工具版本并相应调整目标 Python 版本。

## 如何达成设计目的

提交修改了两个文件：
1. **`requirements.txt`**：将 `datamodel-code-generator` 版本从 `0.27.2` 更新到 `0.28.1`。
2. **`Makefile`**：将 `datamodel-codegen` 命令的 `--target-python-version` 参数从 `3.8` 改为 `3.9`，以适配新版本工具对目标版本的要求。

## 修改详情

### `open-api/Makefile`（修改, +1/-1 lines）

**修改目的**：适配 `datamodel-code-generator` 0.28.1 对目标 Python 版本的要求。

**工作逻辑**：在 `generate` 目标的 `datamodel-codegen` 命令中，将 `--target-python-version 3.8` 改为 `--target-python-version 3.9`。这确保生成的 Python 模型代码使用 Python 3.9 的语法和特性。

### `open-api/requirements.txt`（修改, +1/-1 lines）

**修改目的**：升级 `datamodel-code-generator` 版本。

**工作逻辑**：将 `datamodel-code-generator==0.27.2` 更新为 `datamodel-code-generator==0.28.1`。

## 小结

- **成效**：成功将 `datamodel-code-generator` 从 0.27.2 升级到 0.28.1，并将代码生成的目标 Python 版本从 3.8 提升到 3.9，保持了与工具新版本的兼容性。
- **影响范围**：仅影响 OpenAPI 模块的代码生成工具链配置，不影响任何 Iceberg Java 源代码或运行时功能。生成的 Python 模型代码可能因版本升级而有细微变化。
- **回迁到 1.4.x 的注意事项**：此提交为构建工具版本升级，回迁风险低。需确认 1.4.x 分支中 OpenAPI 模块的 Makefile 和 requirements.txt 结构一致。建议回迁以保持工具链版本一致。
