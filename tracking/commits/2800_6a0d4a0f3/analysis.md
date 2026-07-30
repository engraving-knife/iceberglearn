# 提交 2800：Docs: lint markdown files in site build (#13977)

## 提交信息

- **序号**：2800 / 4088
- **哈希**：6a0d4a0f37a22a885ef43b37353bee258ca93068
- **短哈希**：6a0d4a0f3
- **日期**：2025-10-27 12:58:30 -0700
- **作者**：Manu Zhang
- **提交说明**：Docs: lint markdown files in site build (#13977)
- **PR/Issue**：#13977

## 总体目的

本提交为 Iceberg 文档站点构建流程添加 Markdown 文件 lint 检查，确保所有 Markdown 文件符合统一的样式规范。

Iceberg 项目有大量文档（API 文档、引擎集成文档、规范文档等），这些文档由不同贡献者编写，样式风格不一致（如行尾空格、缩进、标题格式、列表缩进等）。通过在站点构建时自动检查 Markdown 文件样式，可以在构建阶段就发现并修复样式问题，保持文档的一致性和专业性。

本提交使用 `pymarkdownlnt`（PyMarkdownLint）工具，配置了一系列 Markdown 样式规则，并将其集成到站点构建流程中。同时对现有文档进行了大规模的样式修复，使其通过 lint 检查。

## 如何达成设计目的

1. **添加 lint 工具**：在 `site/requirements.txt` 中添加 `pymarkdownlnt==0.9.32` 依赖。
2. **配置 lint 规则**：新建 `site/markdownlint.yml`，启用了 15 条 Markdown 样式规则（如无行尾空格、无制表符、列表缩进、标题格式、代码围栏样式等）。
3. **集成到构建流程**：在 `site/dev/build.sh` 中调用 `./dev/lint.sh`，使 lint 检查成为构建的必要步骤。
4. **提供 lint 脚本**：新建 `site/dev/lint.sh`，支持检查模式和 `--fix` 自动修复模式。
5. **在 common.sh 中添加函数**：`check_markdown_files` 执行检查，`fix_markdown_files` 执行自动修复。
6. **修复现有文档**：对 55 个 Markdown 文件进行样式修复，使其通过 lint 检查。

## 修改详情

### `site/markdownlint.yml` (+69/-0 lines, new file)

**修改目的**：配置 Markdown lint 规则。

**工作逻辑**：配置 PyMarkdownLint 工具，启用 `selectively_enable_rules` 模式，并逐一启用以下规则：`list-indent`（列表缩进）、`no-trailing-spaces`（无行尾空格）、`no-hard-tabs`（无制表符）、`no-multiple-blanks`（无多重空行）、`no-multiple-space-atx`/`no-multiple-space-closed-atx`（ATX 标题格式）、`heading-start-left`（标题左对齐）、`no-multiple-space-blockquote`（引用格式）、`list-marker-space`（列表标记空格）、`hr-style`（水平线样式）、`no-space-in-emphasis`/`no-space-in-code`/`no-space-in-links`（无多余空格）、`proper-names`（专有名词）、`single-trailing-newline`（单行尾换行）、`code-fence-style`（代码围栏样式）。

### `site/dev/lint.sh` (+27/-0 lines, new file)

**修改目的**：提供 Markdown lint 的入口脚本。

**工作逻辑**：脚本接受可选的 `--fix` 参数。如果传入 `--fix` 则调用 `fix_markdown_files` 自动修复样式问题，否则调用 `check_markdown_files` 仅检查。

### `site/dev/common.sh` (+14/-0 lines)

**修改目的**：添加 Markdown 检查和修复的 shell 函数。

**工作逻辑**：
- `check_markdown_files`：使用 `python3 -m pymarkdown --config markdownlint.yml scan` 扫描 nightly docs、site docs 和 README.md，如果发现问题则提示运行 `--fix` 并退出。
- `fix_markdown_files`：使用 `python3 -m pymarkdown --config markdownlint.yml fix` 自动修复样式问题。

### `site/dev/build.sh` (+2/-0 lines)

**修改目的**：将 lint 检查集成到构建流程。

**工作逻辑**：在 `mkdocs build` 之前调用 `./dev/lint.sh`，如果 lint 检查失败则构建中止。

### `site/requirements.txt` (+1/-0 lines)

**修改目的**：添加 pymarkdownlnt 依赖。

**工作逻辑**：添加 `pymarkdownlnt==0.9.32`。

### `site/README.md` 及 50+ 个文档文件 (大量修改)

**修改目的**：修复现有 Markdown 文件的样式问题，使其通过 lint 检查。

**工作逻辑**：修改涉及 `docs/docs/` 下的 32 个文档文件、`format/` 下的 4 个规范文件、`site/docs/` 下的 15 个站点文档文件、`site/README.md` 和 `site/dev/` 脚本文件。主要修复包括：移除行尾空格、移除多余空行、统一列表缩进、修复标题格式、统一代码围栏样式等。README 中新增了 lint 脚本的说明和 `--fix` 用法。

## 总结

本提交为 Iceberg 文档站点构建流程添加了 Markdown lint 检查，使用 pymarkdownlnt 工具和 15 条样式规则。lint 检查集成到构建流程中，失败会中止构建；同时提供了 `--fix` 自动修复模式。对 55 个现有文档文件进行了样式修复以通过检查。这有助于保持文档的一致性和专业性，防止未来的文档贡献引入样式问题。
