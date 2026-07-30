# 提交 0393：Update deploy script and add 1.4.3 updates

## 提交信息

- **序号**：0393
- **哈希**：cd0f30e533ad25cc6736dc3cbde26181d32353a9
- **短哈希**：cd0f30e53
- **日期**：2024-01-19（Fri Jan 19 02:05:05 2024 -0800）
- **作者**：Brian "bits" Olsen <brianolsen87@gmail.com>
- **提交说明**：Update deploy script and add 1.4.3 updates
- **PR/Issue**：#9519

## 总体目的

本提交是 Iceberg 1.4.3 版本发布流程中针对文档站点（site）的配套更新，主要完成三件事：将文档站点默认版本号升级到 1.4.3、在导航中加入 1.4.3 版本入口、调整部署脚本并修订发布流程说明。

Iceberg 的文档站点采用 MkDocs 构建，并通过 `mkdocs.yml` 和 `nav.yml` 控制站点元数据与多版本导航。每发布一个新版本，都需要：(1) 在 `mkdocs.yml` 中将 `icebergVersion` 升级到新版本号；(2) 在 `nav.yml` 的 Docs 列表中追加新版本入口；(3) 同步更新文档部署脚本和发布流程说明。本提交即完成这三步。

部署脚本 `deploy.sh` 的修改也值得关注：将 `mkdocs gh-deploy --dirty` 改为 `mkdocs gh-deploy --remote-name "${REMOTE}"`，去掉了 `--dirty` 标志，转而使用环境变量 `REMOTE` 显式指定远程仓库名。这一改动使得部署脚本可以灵活适配不同的远程仓库名（例如 fork 或镜像仓库），也避免了 `--dirty` 模式可能带来的脏构建问题。README 中新增的 WARNING 块进一步说明 `make release` 指令当前不可用，需要社区讨论文档发布的自动化方式，反映出 1.4.3 发布期间发布流程正处于重构讨论阶段。

## 如何达成设计目的

通过四个文件的协同修改完成站点发布准备：`mkdocs.yml` 升级版本号、`nav.yml` 增加新版本导航、`deploy.sh` 调整远程仓库名参数、`README.md` 增加 WARNING 说明。修改方式以配置项替换和列表追加为主，不涉及业务逻辑变更。

## 修改详情

### site/README.md

**修改目的**：在文档发布流程说明中增加 WARNING 块，告知贡献者 `make release` 指令当前不可用及其原因。

**工作逻辑**：在 "Deploying the docs is a two step process" 段落之后、原步骤列表之前，插入一段 Markdown blockquote（`> [!WARNING]`）。该警告说明：
- `make release` 当前不可用，因为社区正在讨论如何（以及是否应该）自动化发布流程；
- 自动化涉及对版本化文档快照的处理，以及将独立的 `docs` 分支和 `javadoc` 分支（这两个分支与 `main` 分支独立维护）自动合并；
- 完成讨论后，真实流程应为：手动触发一个文档发布 action，然后合并一个 PR 来最终完成文档发布。

这段说明为发布经理和贡献者提供了清晰的当前状态与未来规划，避免误用 `make release` 导致发布错误。

### site/dev/deploy.sh

**修改目的**：调整 `mkdocs gh-deploy` 命令的参数，从 `--dirty` 模式改为通过 `${REMOTE}` 环境变量指定远程仓库名。

**工作逻辑**：原命令 `mkdocs gh-deploy --dirty` 使用 `--dirty` 标志，意味着不清理构建产物直接部署；新命令 `mkdocs gh-deploy --remote-name "${REMOTE}"` 改用 `--remote-name` 显式指定 git 远程仓库名，远程名由环境变量 `REMOTE` 提供（推测在 `dev/setup_env.sh` 中设置）。这种修改提升了部署的灵活性，使脚本可以适配不同远程仓库名（如 fork 仓库），同时也避免了 `--dirty` 模式可能引入的脏构建产物。注释中保留的 `# --remote-branch asf-site` 提示了原本可能用于指定 asf-site 分支的备选参数，但当前未启用。

### site/mkdocs.yml

**修改目的**：将文档站点展示的 Iceberg 版本号从 `1.4.2` 升级到 `1.4.3`。

**工作逻辑**：`mkdocs.yml` 的 `extra` 段落中 `icebergVersion` 变量用于在文档站点各页面引用当前 Iceberg 版本号（例如在安装说明、Maven 坐标示例中显示版本）。修改将其从 `'1.4.2'` 改为 `'1.4.3'`，确保站点展示的最新版本信息与实际发布版本一致。这是一个单行配置修改，影响范围是整个文档站点中所有引用 `icebergVersion` 变量的页面。

### site/nav.yml

**修改目的**：在文档站点的多版本导航中新增 1.4.3 版本入口。

**工作逻辑**：`nav.yml` 控制 MkDocs 站点的导航结构，其中 `Docs` 部分通过 `!include` 指令引入各版本文档的子 mkdocs.yml。本次修改在 `latest` 入口之后、`1.4.2` 入口之前插入 `- 1.4.3: '!include docs/docs/1.4.3/mkdocs.yml'`，使访问者可以在版本切换器中选择 1.4.3 版本文档。新版本入口位于列表第二位（仅次于 latest），符合 Iceberg 文档站点"新版本在上"的惯例。这也意味着 `docs/docs/1.4.3/` 目录应当已存在对应版本的文档快照。

## 小结

本提交是 1.4.3 版本发布的文档站点配套更新，体现了 Iceberg 版本发布的标准流程：版本号升级、导航入口添加、部署脚本调整、流程说明修订四步并行。其中 `deploy.sh` 的参数化改造（使用 `${REMOTE}` 环境变量）和 README 中的 WARNING 说明，反映出文档发布流程正处于重构讨论期，社区在权衡自动化的可行性与安全性。这类站点维护提交虽然不涉及核心代码，但对外部用户获取正确版本文档至关重要。
