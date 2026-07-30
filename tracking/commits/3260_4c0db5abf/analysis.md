# 提交 3260：Build: Support building site with uv (#15118)

## 提交信息

- **序号**：3260 / 4088
- **哈希**：4c0db5abf0ecc6136c28a56c188d56115d83c240
- **短哈希**：4c0db5abf
- **日期**：2026-02-16
- **作者**：Manu Zhang
- **提交说明**：Build: Support building site with uv (#15118)
- **PR/Issue**：#15118

## 总体目的

Iceberg 文档站点（位于 `site/` 目录，基于 MkDocs Material 构建）的本地构建与预览脚本原先固定使用标准库 `python3 -m venv` 创建虚拟环境，并用虚拟环境内的 `pip3` 安装 `requirements.txt` 中的依赖。`site/dev/common.sh` 中硬编码了 `VENV_DIR=".venv"` 与 `"${VENV_DIR}/bin/pip3"`，构建流程与这套传统的 venv+pip 方式强绑定。

`uv` 是 Astral 推出的、用 Rust 编写的极速 Python 包管理器，在安装依赖时比传统 pip 快一到两个数量级，且自带 Python 版本管理与虚拟环境能力。希望使用 uv 来加速文档站点依赖安装的开发者，原先无法直接复用项目提供的 `common.sh` 脚本，因为脚本既不允许指定虚拟环境位置，也不允许替换 pip 命令为 `uv pip`。这迫使用户要么手写一套并行脚本，要么绕过项目脚本手工操作，体验不佳。

本次提交让文档构建脚本对 uv 友好，同时完全保持对原有 venv+pip 流程的向后兼容。具体做法是将硬编码的 `VENV_DIR` 与 `pip3` 命令改为可通过环境变量覆盖：`VENV_DIR` 默认仍为 `.venv`，但允许外部传入；`PIP` 默认仍为 `${VENV_DIR}/bin/pip3`，但允许设为 `uv pip` 以使用 uv。此外，uv 在固定 Python 版本时会生成 `.python-version` 文件，该文件属于本地环境状态不应提交，因此一并加入 `.gitignore`。这样开发者只需在调用脚本前设置相应环境变量（或使用 uv 预先创建好虚拟环境），即可用 uv 加速文档站点的依赖安装与构建。

## 如何达成设计目的

改动集中在两处：`site/dev/common.sh` 中将 `VENV_DIR` 与 `PIP` 改为带默认值的环境变量覆盖形式（`:-` 语法），使原有行为成为默认、同时为 uv 留出接入点；`.gitignore` 中新增 `.python-version` 忽略项以适配 uv 产生的本地文件。两处改动均不改变默认行为，属于纯增量式的兼容性扩展。

## 修改详情

### `site/dev/common.sh` (+3/-2 lines)

**修改目的**：让虚拟环境目录与 pip 命令可被环境变量覆盖，从而支持使用 uv 进行依赖安装。

**工作逻辑**：
两处关键改动：

1. `export VENV_DIR=".venv"` 改为 `export VENV_DIR="${VENV_DIR:-.venv}"`。采用 shell 的 `${VAR:-default}` 语法：若环境变量 `VENV_DIR` 已被外部设置则沿用其值，否则回退到默认的 `.venv`。这样当使用 uv 在自定义位置创建虚拟环境、或希望复用已有的 uv 管理环境时，可通过 `VENV_DIR=...` 指定。注意 `create_venv` 函数仍使用 `python3 -m venv "${VENV_DIR}"` 创建环境，因此使用 uv 时通常由用户预先运行 `uv venv` 创建好 `.venv`，使 `[ ! -d "${VENV_DIR}" ]` 判断跳过内置创建逻辑。

2. 依赖安装行从 `"${VENV_DIR}/bin/pip3" -q install -r requirements.txt --upgrade` 改为：
   ```bash
   local PIP="${PIP:-${VENV_DIR}/bin/pip3}"
   ${PIP} -q install -r requirements.txt --upgrade
   ```
   先定义局部变量 `PIP`，默认回退到虚拟环境内的 `pip3`，但允许外部通过 `PIP="uv pip"` 覆盖。随后 `${PIP}` 故意不加引号展开，这样当 `PIP="uv pip"` 时会被 shell 拆分为 `uv` 与 `pip` 两个词，正确执行 `uv pip install ...`；若加引号则 `"uv pip"` 会被当作单个命令路径而失败。默认路径 `${VENV_DIR}/bin/pip3` 不含空格，不引号展开同样安全。

该设计保持了原有 venv+pip 用户的零配置体验，同时为 uv 用户提供了清晰的接入方式。

### `.gitignore` (+3 lines)

**修改目的**：忽略 uv 固定 Python 版本时生成的 `.python-version` 文件。

**工作逻辑**：
在 `.gitignore` 末尾新增以 `# uv` 为注释的小节，其下加入 `.python-version`。uv 在执行 `uv python pin` 或 `uv venv` 时会在项目根目录写入 `.python-version` 文件以记录所固定的 Python 版本，该文件反映的是本地开发环境状态而非项目共享配置，若不忽略容易被误提交。将其加入 `.gitignore` 后，使用 uv 的开发者不会再因该文件出现在 `git status` 中而困扰。

## 总结

本次提交让 Iceberg 文档站点的构建脚本支持使用 uv 这一极速 Python 包管理器：通过将 `VENV_DIR` 与 `PIP` 改为可环境变量覆盖的形式，在不破坏原有 venv+pip 默认流程的前提下，为 uv 用户提供了接入点（如设置 `PIP="uv pip"`）；同时将 uv 产生的 `.python-version` 文件加入 `.gitignore`。改动小幅、向后兼容，显著降低了希望用 uv 加速文档依赖安装的开发者的使用门槛。
