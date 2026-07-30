# 提交 2825：faster make lint (#14492)

## 提交信息

- **序号**：2825 / 4088
- **哈希**：3ccb29624283bbb70cb7c70abf6855a61c29d232
- **短哈希**：3ccb29624
- **日期**：2025-11-03 12:29:41 -0800
- **作者**：Kevin Liu
- **提交说明**：faster make lint (#14492)
- **PR/Issue**：#14492

## 总体目的

本提交优化了 `make lint` 命令的执行速度。在提交 2820 中引入了 `make lint` 功能，但 `lint.sh` 在执行前会调用 `./dev/setup_env.sh`，该脚本会执行完整的初始化流程：清理（clean）、创建虚拟环境、安装依赖、**拉取版本化文档（pull_versioned_docs）**。其中 `pull_versioned_docs` 会通过 git 操作拉取各版本的文档，这是一个耗时的网络操作。

对于仅仅想快速检查 Markdown 风格问题的开发者来说，这个完整的初始化流程是不必要的。lint 操作只需要 pymarkdown 工具和待检查的 markdown 文件即可，不需要完整的版本化文档。本提交通过精简 lint 前的准备步骤，使 `make lint` 显著提速。

## 如何达成设计目的

设计思路是将 `lint.sh` 的准备步骤从完整的环境初始化（`setup_env.sh`）精简为只做必要的两步：创建虚拟环境（`create_venv`）和安装依赖（`install_deps`），跳过耗时的 `clean` 和 `pull_versioned_docs`。

同时，调整 lint 检查的文件路径：原扫描 `docs/docs/nightly/docs/*.md`（nightly 版本目录），改为扫描 `../docs/docs/*.md`（上一级目录的 docs，即实际的文档源目录），避免依赖 nightly 版本文档的存在。

## 修改详情

### `site/dev/common.sh` (+4/-2 lines)

**修改目的**：调整 lint 检查的 markdown 文件路径。

**工作逻辑**：
- `check_markdown_files()`：将扫描路径从 `docs/docs/nightly/docs/*.md` 改为 `../docs/docs/*.md`。这避免了依赖 nightly 版本文档的存在，直接检查实际的文档源。
- `fix_markdown_files()`：同样将修复路径从 `docs/docs/nightly/docs/*.md` 改为 `../docs/docs/*.md`。

### `site/dev/lint.sh` (+4/-3 lines)

**修改目的**：精简 lint 前的准备步骤以加速执行。

**工作逻辑**：将原来的 `./dev/setup_env.sh`（包含 clean、create_venv、install_deps、pull_versioned_docs 四步）替换为仅两步：
```bash
create_venv
install_deps
```
跳过了 `clean`（清理临时文件）和 `pull_versioned_docs`（拉取版本化文档，耗时网络操作），从而显著加速 lint 流程。这两个函数来自 `common.sh`，已通过 `source dev/common.sh` 引入。

## 总结

本提交通过精简 `make lint` 的准备步骤（跳过 clean 和 pull_versioned_docs，仅保留 create_venv 和 install_deps）以及调整 markdown 文件检查路径，显著提升了 lint 命令的执行速度。这是对提交 2820 引入的 `make lint` 功能的性能优化，改善了开发者在文档检查时的体验。
