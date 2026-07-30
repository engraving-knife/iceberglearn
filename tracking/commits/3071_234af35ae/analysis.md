# 提交 3071：site: fix live loading in make serve-dev

## 提交信息

- **序号**：3071 / 4088
- **哈希**：234af35ae143dae6369eca03365dc166b92d0b84
- **短哈希**：234af35ae
- **日期**：2026-01-06
- **作者**：Kevin Liu
- **提交说明**：site: fix live loading in make serve-dev
- **PR/Issue**：无

## 总体目的

本提交修复 Iceberg 文档站点在开发模式下（`make serve-dev`）的实时加载（live loading / livereload）功能失效问题。Iceberg 文档站点基于 MkDocs 构建，提供了两种本地预览模式：`make serve`（完整构建）和 `make serve-dev`（开发模式，仅构建 nightly 和 latest 版本以加速迭代）。

问题出在 mkdocs serve 命令使用的 `--dirty` 标志。`--dirty` 标志的作用是仅重建变更的文件，但它会与 livereload 功能产生冲突——在 `--dirty` 模式下，mkdocs 的实时重载机制无法正确工作，导致开发者修改文档后浏览器不会自动刷新，严重影响文档迭代效率。

修复方案是将 `--dirty` 标志替换为 `--livereload` 标志，确保文件变更后浏览器能自动刷新预览。同时清理了相关文档中对 `--dirty` 标志的描述。

## 如何达成设计目的

改动涉及三个文件：`serve-dev.sh` 和 `serve.sh` 将 mkdocs serve 命令的 `--dirty` 替换为 `--livereload`，`README.md` 移除对 `--dirty` 的说明。开发模式脚本还调整了参数顺序，将 `-f mkdocs-dev.yml` 提前。

## 修改详情

### `site/README.md` (+0/-1 lines)

**修改目的**：移除文档中对 `--dirty` 标志的描述。

**工作逻辑**：
删除 `make serve-dev` 开发模式说明中的一条列表项 `- **Uses the \`--dirty\` flag** - Only rebuilds changed files for even faster iteration`，因为修复后不再使用该标志。

### `site/dev/serve-dev.sh` (+1/-3 lines)

**修改目的**：修复开发模式脚本的实时加载功能。

**工作逻辑**：
- 移除两行注释（`# Using mkdocs serve with --dirty flag for even faster rebuilds` 和 `# The --dirty flag means only changed files are rebuilt`）。
- 将命令 `"${VENV_DIR}/bin/python3" -m mkdocs serve --dirty --watch . -f mkdocs-dev.yml` 改为 `"${VENV_DIR}/bin/python3" -m mkdocs serve -f mkdocs-dev.yml --livereload --watch .`，即将 `--dirty` 替换为 `--livereload`，并调整参数顺序。

### `site/dev/serve.sh` (+1/-1 lines)

**修改目的**：修复完整构建模式脚本的实时加载功能。

**工作逻辑**：
将命令 `"${VENV_DIR}/bin/python3" -m mkdocs serve --dirty --watch .` 改为 `"${VENV_DIR}/bin/python3" -m mkdocs serve --livereload --watch .`，同样将 `--dirty` 替换为 `--livereload`。

## 总结

本提交将文档站点的 mkdocs serve 命令从 `--dirty` 模式切换到 `--livereload` 模式，修复了开发预览时浏览器无法自动刷新的问题，提升了文档编写迭代效率。改动同时覆盖开发模式和完整模式两个脚本，并同步更新了文档说明。
