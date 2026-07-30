# 提交 2946：open-api: use uv and python virtual env (#14684)

## 提交信息

- **序号**：2946 / 4088
- **哈希**：da76b873fad9b87c76eed1e91185e7b9fcb9a7f8
- **短哈希**：da76b873f
- **日期**：2025-12-02
- **作者**：Kevin Liu
- **提交说明**：open-api: use uv and python virtual env (#14684)
- **PR/Issue**：#14684

## 总体目的

Iceberg 仓库的 `open-api/` 目录维护着 REST Catalog 与 S3 Signer 的 OpenAPI 规范，并通过 Python 工具链（`openapi-spec-validator` 做规范校验、`datamodel-code-generator` 把 YAML 规范生成 Python 模型代码）来保证规范与生成产物的一致性。提交之前，这一流程依赖系统全局 Python 环境（GitHub Action 用 `actions/setup-python@v6` 装 Python 3.9，再 `pip install -r requirements.txt`），存在两个问题：一是 Python 3.9 已逐步进入维护末期，生成的代码目标版本也固定在 3.9，无法利用较新语法；二是没有隔离的虚拟环境，CI 上残留的全局包可能污染校验/生成结果，本地开发者也需要手动管理环境。

本次提交把整个 open-api 的 Python 工具链迁移到 `uv`（Astral 出品的高速 Python 包管理器）+ 专用虚拟环境。`uv venv --python 3.12` 显式锁住 Python 3.12，`uv pip install`、`uv run` 让校验与生成都在隔离 venv 内执行，与 `generate` 目标里 `--target-python-version 3.12` 也保持一致。GitHub Action 同步替换为 `astral-sh/setup-uv@v7`，并删掉了原本单独跑的 "Validate S3 REST Signer spec" 步骤——该步骤在新的 `make lint` 里已经被合并进来。

## 如何达成设计目的

整体思路是用 `uv` 替换 `pip`/`setup-python`，把环境创建、依赖安装、命令执行三步都收敛到 `uv` 子命令中，并在 `Makefile` 的 `lint` target 里同时校验两份 OpenAPI 规范。改动集中在 `.github/workflows/open-api.yml` 与 `open-api/Makefile` 两个文件，没有触碰任何 Java/Scala 源码。

## 修改详情

### `.github/workflows/open-api.yml` (+3/-7 lines)

**修改目的**：把 CI 的 Python 环境准备步骤从 `setup-python` 切换为 `setup-uv`，并整合校验步骤。

**工作逻辑**：
原先用 `actions/setup-python@v6` with `python-version: 3.9`，现在改为 `astral-sh/setup-uv@v7`，并新增 `Install uv` 步骤名。`Install dependencies` 仍调用 `make install`，但 `make install` 的实现已经改成 `uv venv` + `uv pip install`，所以 CI 不需要感知具体命令。最后删除了独立的 `Validate S3 REST Signer spec` 步骤——它原本在 `aws/src/main/resources` 下单独跑 `openapi-spec-validator s3-signer-open-api.yaml`，现在由 `make lint` 一起完成。

### `open-api/Makefile` (+7/-5 lines)

**修改目的**：让 `install`/`lint`/`generate` 三个 target 都走 uv，并把 Python 版本统一到 3.12。

**工作逻辑**：
- `install`：从 `pip install -r requirements.txt` 改为 `uv venv --python 3.12 --allow-existing` + `uv pip install -r requirements.txt`。注释里特别点出 "Match --target-python-version in the `generate` target"，强调 venv 的 Python 版本必须和生成代码的目标版本对齐，避免生成产物与运行环境不一致。`--allow-existing` 保证 venv 已存在时不会报错，便于重复执行。
- `lint`：原先只校验 `rest-catalog-open-api.yaml`，现在追加 `uv run openapi-spec-validator --errors all ../aws/src/main/resources/s3-signer-open-api.yaml`，把之前从 CI 里删掉的 S3 Signer 校验合并进来，并统一用 `uv run` 在 venv 内执行。
- `generate`：`datamodel-codegen` 改为 `uv run datamodel-codegen`，`--target-python-version` 从 `3.9` 升到 `3.12`，与 venv 版本一致，生成代码可以使用 3.12 的语法特性。

## 总结

这是一次纯工具链现代化改动：通过引入 `uv` 与 Python 3.12 虚拟环境，让 open-api 的校验/生成既快又隔离，CI 与本地命令统一，同时把分散的 S3 Signer 校验并入 `make lint`。对下游 Java/Spark 代码零影响，但显著降低了 open-api 维护的环境漂移风险。
