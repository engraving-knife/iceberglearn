# 提交 3258：Remove redundant --watch . flag from serve scripts (#15330)

## 提交信息

- **序号**：3258 / 4088
- **哈希**：b52f8f592d84349c45500963c28d4c38b76ba44b
- **短哈希**：b52f8f592
- **日期**：2026-02-16
- **作者**：Kevin Liu
- **提交说明**：Remove redundant --watch . flag from serve scripts (#15330)
- **PR/Issue**：#15330

## 总体目的

Iceberg 的文档站点基于 MkDocs Material 构建，`site/dev/serve.sh`（完整构建）和 `site/dev/serve-dev.sh`（开发模式，仅构建 nightly 与 latest 版本以加速迭代）两个脚本用于在本地启动 `mkdocs serve` 实时预览服务。两者原本都在启动命令中附加了 `--watch .` 参数，用于让 mkdocs 额外监听当前目录（即 `site/` 目录）的变化并在文件改动时触发实时重载（livereload）。

然而 `mkdocs serve` 在启用 `--livereload` 时默认就已经会监听文档目录（`docs_dir`，默认为 `docs/`）以及配置文件（`mkdocs.yml`/`mkdocs-dev.yml`）的变化。文档源文件本身已位于被默认监听的目录中，因此 `--watch .` 对于"文档内容变更即重载"这一核心诉求而言是冗余的——它没有提供任何默认行为之外的实际价值。

更糟的是，`--watch .` 会让 mkdocs 监听整个 `site/` 目录，其中包含由 `setup_env.sh` 创建的虚拟环境 `.venv`（`VENV_DIR=.venv`）、各类开发脚本（`dev/*.sh`）、lint 配置以及构建产物等与文档内容无关的文件。当这些文件发生变化时（例如执行 `pip install` 向 `.venv` 写入文件、运行 lint 脚本生成临时文件等），mkdocs 会被误触发进行不必要的站点重建，造成开发预览过程中出现多余的、与文档改动无关的重载，既拖慢迭代速度也带来干扰。

本次提交从两个 serve 脚本中移除 `--watch .` 参数，使本地预览服务回归 mkdocs 的默认监听行为：仅当文档目录或 mkdocs 配置文件真正发生变化时才触发重载。

## 如何达成设计目的

直接编辑 `site/dev/serve-dev.sh` 和 `site/dev/serve.sh` 两个脚本，将 `mkdocs serve` 命令行末尾的 `--watch .` 删除，保留 `-f mkdocs-dev.yml`/默认配置与 `--livereload` 选项。改动后 mkdocs 将使用其内置的默认监听目录，无需额外的显式 `--watch` 参数。

## 修改详情

### `site/dev/serve-dev.sh` (+1/-1 lines)

**修改目的**：从开发模式预览脚本中移除冗余的 `--watch .` 参数。

**工作逻辑**：
原命令为 `"${VENV_DIR}/bin/python3" -m mkdocs serve -f mkdocs-dev.yml --livereload --watch .`，改为 `"${VENV_DIR}/bin/python3" -m mkdocs serve -f mkdocs-dev.yml --livereload`。该脚本通过 `export ICEBERG_DEV_MODE=true` 进入开发模式，仅构建 nightly 和 latest 版本以加快本地迭代。移除 `--watch .` 后，mkdocs 仅监听 `mkdocs-dev.yml` 配置及其 `docs_dir`，避免对 `.venv`、dev 脚本等无关文件的误触发重载。

### `site/dev/serve.sh` (+1/-1 lines)

**修改目的**：从完整预览脚本中移除冗余的 `--watch .` 参数。

**工作逻辑**：
原命令为 `"${VENV_DIR}/bin/python3" -m mkdocs serve --livereload --watch .`，改为 `"${VENV_DIR}/bin/python3" -m mkdocs serve --livereload`。该脚本会执行完整的 `./dev/lint.sh` 后启动 mkdocs，构建全部文档版本。脚本中已通过提示建议开发者改用更快的 `make serve-dev`。移除 `--watch .` 的效果与开发模式脚本一致：让 mkdocs 仅监听文档目录与默认配置文件，避免对虚拟环境和脚本文件的误重载。

## 总结

本次提交从文档站点的两个本地预览脚本中移除了冗余的 `--watch .` 参数。由于 mkdocs serve 默认已监听文档目录与配置文件，该参数既无额外价值又会因监听整个 `site/` 目录（含 `.venv`、脚本等）而引发与文档无关的误重载。移除后本地预览将仅在实际文档内容或配置变更时触发重载，提升了开发迭代的稳定性与效率。
