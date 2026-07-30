# 提交 2837：Add Fast Mode for Documentation Builds (#14267)

## 提交信息

- **序号**：2837 / 4088
- **哈希**：8cefa386f8ed53585f024e729e33fd3d194712f3
- **短哈希**：8cefa386f
- **日期**：2025-11-06 07:54:49 -0800
- **作者**：Talat UYARER（与 Kevin Liu 共同作者）
- **提交说明**：Add Fast Mode for Documentation Builds (#14267)
- **PR/Issue**：#14267

## 总体目的

Iceberg 文档站点（基于 mkdocs）在本地构建时会拉取并构建所有历史版本（每个历史版本都有独立的 docs 与 javadoc worktree），这导致每次 `make serve` 都很慢，严重拖慢文档贡献者的本地迭代速度。

该提交为文档构建引入"Fast Mode"（开发模式）：通过 `make serve-dev` 只构建 `nightly` 与 `latest` 两个版本，跳过全部历史版本，配合 mkdocs 的 `--dirty` 标志（仅重建变更文件），通常能把构建时间缩短 5-10 倍，极大提升文档写作的迭代效率。该模式仅用于本地迭代，提 PR 前仍需用 `make serve`/`make build` 验证全量版本构建。

## 如何达成设计目的

1. **新增 `serve-dev` 入口**：在 `Makefile` 增加 `serve-dev` 目标调用 `dev/serve-dev.sh`；在 `README.md` 中说明该模式。
2. **`serve-dev.sh` 脚本**：`source dev/common.sh`，导出 `ICEBERG_DEV_MODE=true`，运行 `dev/setup_env.sh`，再用 `mkdocs serve --dirty --watch . -f mkdocs-dev.yml` 启动。
3. **`common.sh` 改造 `pull_versioned_docs`**：当 `ICEBERG_DEV_MODE=true` 时，对 docs 与 javadoc worktree 使用 `git worktree add --no-checkout` + `git sparse-checkout set <latest_version>`，只检出最新版本的目录，避免检出全部历史版本；非 dev 模式保持原全量 checkout 行为。
4. **`get_latest_version` 重写**：原来通过 `ls -d docs/docs/[0-9]* | sort -V | tail -1` 扫描目录得到最新版本；改为从 `mkdocs.yml` 的 `icebergVersion:` 字段解析，这样在 sparse checkout 之前就能拿到最新版本号（dev 模式下 docs/docs 目录还没检出历史版本，原目录扫描法不可用）。
5. **`mkdocs-dev.yml`**：继承 `./mkdocs.yml`，但覆盖 `nav` 只包含 nightly 与 latest 两个 Java 版本入口（其他语言/三方集成链接保持不变），并通过 `exclude_docs` 排除 `docs/`、`javadoc/` 下除 nightly/latest 之外的内容，进一步减少 mkdocs 扫描范围。

## 修改详情

### `site/Makefile` (+4/-0 lines)

**修改目的**：暴露 `serve-dev` 目标。

**工作逻辑**：新增 `.PHONY: serve-dev` 与 `serve-dev: dev/serve-dev.sh`，让 `make serve-dev` 触发开发模式服务脚本。

### `site/README.md` (+20/-0 lines)

**修改目的**：文档化 Fast Mode 用法与限制。

**工作逻辑**：在命令列表中加入 `serve-dev` 说明；新增"Fast iterative development mode"小节，说明 `make serve-dev` 只构建 nightly/latest、5-10x 提速、使用 `--dirty`、仅用于本地迭代，提 PR 前仍需 `make serve`/`make build` 验证全量。

### `site/dev/common.sh` (+33/-16 lines)

**修改目的**：支持 dev 模式下的 sparse checkout，并重写 `get_latest_version`。

**工作逻辑**：
- `get_latest_version` 改为 `grep "icebergVersion:" mkdocs.yml | sed -E "s/.*icebergVersion:[[:space:]]*['\"]?([^'\"]+)['\"]?.*/\1/"`，从 mkdocs 配置直接读取版本号。
- `pull_versioned_docs` 把 `get_latest_version` 调用提前到 worktree 创建之前；新增 `if [ "${ICEBERG_DEV_MODE:-false}" = "true" ]` 分支：对 docs/javadoc 用 `git worktree add --no-checkout -f` 并 `git sparse-checkout init --cone && git sparse-checkout set "${latest_version}" && git checkout`，只检出最新版本目录；else 分支保留原全量 `git worktree add -f`。最后仍调用 `create_latest` 与 `create_nightly`。

### `site/dev/serve-dev.sh` (+37/-0 lines, 新文件)

**修改目的**：开发模式启动脚本。

**工作逻辑**：`source dev/common.sh`；`set -e`；`export ICEBERG_DEV_MODE=true`；打印提示；运行 `./dev/setup_env.sh`；最后 `"${VENV_DIR}/bin/python3" -m mkdocs serve --dirty --watch . -f mkdocs-dev.yml` 启动 mkdocs 开发服务器，`--dirty` 仅重建变更文件，`-f mkdocs-dev.yml` 使用精简导航配置。

### `site/mkdocs-dev.yml` (+107/-0 lines, 新文件)

**修改目的**：开发模式专用 mkdocs 配置，只包含 nightly/latest。

**工作逻辑**：
- `INHERIT: ./mkdocs.yml` 继承主配置（主题、插件等）。
- 覆盖 `nav`：Home、Quickstart、Docs（Java 下只列 Nightly 与 Latest (1.10.0)、其他语言与三方集成链接沿用）、Releases、Project、Community、Specification。
- `exclude_docs` 用 `|` 多行语法排除 `docs/` 与 `javadoc/`，但用 `!docs/nightly/`、`!javadoc/nightly/`、`!docs/latest/`、`!javadoc/latest/` 保留 nightly/latest 子目录，确保 mkdocs 不扫描历史版本文件。

## 总结

该提交为 Iceberg 文档站点引入 Fast Mode（`make serve-dev`），通过 sparse checkout 只拉取最新版本、精简 mkdocs 导航与 `exclude_docs`、配合 `mkdocs serve --dirty`，把本地文档迭代构建时间缩短 5-10 倍。同时把 `get_latest_version` 从目录扫描改为从 `mkdocs.yml` 解析，使其在 sparse checkout 前可用。这是一个面向文档贡献者体验的工程效率改进，不影响正式构建与发布流程。
