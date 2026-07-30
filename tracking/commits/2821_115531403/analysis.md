# 提交 2821：site: use virtualenv and add make lint (#14428)

## 提交信息

- **序号**：2821 / 4088
- **哈希**：115531403fb6d74ccb7a3e25497528d1f2f9ab06
- **短哈希**：115531403
- **日期**：2025-11-02 17:58:26 -0800
- **作者**：Kevin Liu
- **提交说明**：site: use virtualenv and add make lint (#14428)
- **PR/Issue**：#14428

## 总体目的

本提交改进了 Iceberg 文档站点的开发与构建流程，主要解决两个问题：

1. **依赖隔离问题**：文档构建使用 Python 的 mkdocs、pymarkdown 等工具，这些工具直接安装到系统 Python 环境中，可能与其他项目的依赖产生冲突，也难以保证不同开发者/CI 环境间的一致性。引入 virtualenv（虚拟环境）可以将文档构建依赖隔离在项目本地的 `.venv` 目录中，避免污染系统 Python 环境。

2. **lint 流程便捷性**：原有的 Markdown 风格检查脚本需要直接运行 `./dev/lint.sh`，不够便捷。本提交在 Makefile 中新增了 `make lint` 和 `make lint-fix` 目标，让开发者可以更方便地进行文档风格检查和自动修复，无需记忆脚本路径。

此外，CI 流程也从单一平台（ubuntu-latest）扩展到多平台（ubuntu-latest + macos-latest）矩阵测试，确保文档在 macOS 上也能正确构建，提升了跨平台兼容性。

## 如何达成设计目的

整体设计围绕"虚拟环境化"和"便捷化"两条主线展开：

1. **虚拟环境机制**：在 `common.sh` 中新增 `VENV_DIR=".venv"` 变量和 `create_venv()` 函数（使用 `python3 -m venv` 创建虚拟环境），在 `setup_env.sh` 中调用。所有原本直接使用系统 `python3`、`pip3`、`mkdocs` 的地方，改为使用 `${VENV_DIR}/bin/python3`、`${VENV_DIR}/bin/pip3`、`${VENV_DIR}/bin/python3 -m mkdocs`，确保所有命令都在虚拟环境中执行。

2. **Makefile lint 目标**：新增 `lint` 和 `lint-fix` 两个 phony 目标，分别调用 `dev/lint.sh` 和 `dev/lint.sh --fix`。

3. **lint.sh 独立化**：`lint.sh` 不再依赖 build/serve 流程中的隐式环境设置，而是显式调用 `./dev/setup_env.sh` 来确保虚拟环境和依赖就绪，使 `make lint` 可以独立运行。

## 修改详情

### `.github/workflows/docs-ci.yml` (+5/-1 lines)

**修改目的**：扩展文档 CI 到 macOS 平台。

**工作逻辑**：将 `runs-on: ubuntu-latest` 改为使用矩阵策略 `runs-on: ${{ matrix.os }}`，矩阵包含 `ubuntu-latest` 和 `macos-latest`，确保文档构建在两个平台上都能通过。

### `.gitignore` (+1/-0 lines)

**修改目的**：忽略虚拟环境目录。

**工作逻辑**：新增 `site/.venv/` 到 gitignore，避免将本地虚拟环境提交到版本库。

### `site/Makefile` (+8/-0 lines)

**修改目的**：新增 lint 相关 make 目标。

**工作逻辑**：新增 `lint`（调用 `dev/lint.sh`）和 `lint-fix`（调用 `dev/lint.sh --fix`）两个 phony 目标，方便开发者快速运行文档检查。

### `site/README.md` (+14/-2 lines)

**修改目的**：更新文档说明，介绍 lint 和 lint-fix 命令。

**工作逻辑**：新增 lint-fix 说明，并增加"Linting"小节，展示 `make lint` 和 `make lint-fix` 的用法，替代原来直接运行 `./dev/lint.sh --fix` 的说明。

### `site/dev/build.sh` (+3/-1 lines)

**修改目的**：使用虚拟环境中的 mkdocs。

**工作逻辑**：引入 `source dev/common.sh` 获取 `VENV_DIR` 变量；将 `mkdocs build` 改为 `"${VENV_DIR}/bin/python3" -m mkdocs build`，确保使用虚拟环境中的 mkdocs。

### `site/dev/common.sh` (+19/-5 lines)

**修改目的**：引入虚拟环境支持并修改所有命令使用虚拟环境。

**工作逻辑**：
- 新增 `export VENV_DIR=".venv"` 变量
- 新增 `create_venv()` 函数：若 `.venv` 目录不存在则用 `python3 -m venv` 创建
- `install_deps()` 改为使用 `${VENV_DIR}/bin/pip3` 安装依赖
- `check_markdown_files()` 和 `fix_markdown_files()` 改为使用 `${VENV_DIR}/bin/python3 -m pymarkdown`，并将错误提示中的 `./dev/lint.sh --fix` 改为 `make lint-fix`

### `site/dev/deploy.sh` (+3/-1 lines)

**修改目的**：部署时使用虚拟环境的 mkdocs。

**工作逻辑**：引入 `source dev/common.sh`；将 `mkdocs gh-deploy` 改为 `${VENV_DIR}/bin/python3 -m mkdocs gh-deploy`。

### `site/dev/lint.sh` (+2/-0 lines)

**修改目的**：使 lint 脚本可独立运行。

**工作逻辑**：新增 `./dev/setup_env.sh` 调用，确保独立运行 lint 时虚拟环境和依赖已就绪。

### `site/dev/serve.sh` (+3/-1 lines)

**修改目的**：本地预览使用虚拟环境的 mkdocs。

**工作逻辑**：引入 `source dev/common.sh`；将 `mkdocs serve` 改为 `${VENV_DIR}/bin/python3 -m mkdocs serve`。

### `site/dev/setup_env.sh` (+2/-0 lines)

**修改目的**：在环境设置阶段创建虚拟环境。

**工作逻辑**：在 `clean` 之后、`install_deps` 之前新增 `create_venv` 调用，确保依赖安装前虚拟环境已就绪。

## 总结

本提交对 Iceberg 文档站点构建流程进行了重要改进：引入 Python virtualenv 隔离依赖、新增 `make lint`/`make lint-fix` 便捷命令、扩展 CI 到 macOS 平台。这些改进提升了开发体验、依赖隔离性和跨平台兼容性，与后续提交 2824（faster make lint）共同完善了文档工具链。
