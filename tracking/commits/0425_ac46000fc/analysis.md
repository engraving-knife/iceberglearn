# 提交 0425：Docs: Update ASF site to deploy from this repository (#9520)

## 提交信息

- **序号**：0425
- **哈希**：ac46000fcabe6b4d8e46cd1b4027db567ec443d3
- **短哈希**：ac46000fc
- **日期**：2024 年 1 月 30 日（周二）10:58:01 -0800
- **作者**：Brian "bits" Olsen <brianolsen87@gmail.com>
- **提交说明**：Docs: Update ASF site to deploy from this repository (#9520)
- **PR/Issue**：#9520

## 总体目的

本提交的核心目标是调整 Apache Iceberg 项目官方网站的部署流程，使其从本仓库（即 iceberg 主仓库）直接发布网站内容，而非依赖外部的独立网站仓库。

Apache 软件基金会（ASF）的项目通常有一个约定：网站内容需要发布到一个名为 `asf-site` 的分支（或专门的站点仓库），ASF 的基础设施会自动拉取该分支并对外提供站点服务。在本提交之前，网站的部署命令 `mkdocs gh-deploy` 使用的是默认分支名，目标分支名 `asf-site` 被注释掉了（`# --remote-branch asf-site`），意味着部署并未真正指向 ASF 约定的 `asf-site` 分支，可能存在部署目标错位或需要外部仓库中转的问题。

本提交做了两件事来修正这一状况：

1. 在仓库根目录的 `.asf.yaml` 配置文件中新增 `publish: whoami: asf-site` 配置。`.asf.yaml` 是 ASF 基础设施识别的特殊配置文件，其中的 `publish.whoami` 字段用于声明"本仓库（或本分支）发布的内容身份是 `asf-site`"，即告诉 ASF 基础设施：本仓库就是 `asf-site` 内容的来源，应当从本仓库构建并发布站点。这是把网站发布权"内化"到主仓库的关键声明。

2. 修改 `site/dev/deploy.sh` 部署脚本，将原本被注释掉的 `--remote-branch asf-site` 参数启用，使 `mkdocs gh-deploy` 命令明确地把构建产物推送到 `asf-site` 分支。

两处改动相互配合：`.asf.yaml` 声明仓库的发布身份，`deploy.sh` 落实将站点构建产物推送到正确分支。这种将网站源码与主代码库合并管理的做法，减少了仓库割裂带来的维护成本，使文档与代码的演进保持同步，是 ASF 项目治理中常见的站点整合实践。

## 如何达成设计目的

通过修改两处配置协同达成目标：ASF 基础设施侧通过 `.asf.yaml` 的 `whoami` 声明识别发布身份；构建脚本侧通过显式指定 `--remote-branch asf-site` 把 mkdocs 生成的静态站点推送到正确分支。二者结合，让官网部署流程完全收敛到本仓库内部，无需外部中转仓库。

## 修改详情

### .asf.yaml

**修改目的**：向 ASF 基础设施声明本仓库作为 `asf-site` 内容发布源的身份。

**工作逻辑**：在文件末尾新增两行配置：
```
publish:
    whoami:  asf-site
```
`publish` 顶层键下的 `whoami` 字段值为 `asf-site`，含义是"此仓库/分支发布的内容标识为 asf-site"。ASF 的发布基础设施会读取该声明，将本仓库视为站点内容的权威来源，从而在内容更新时自动触发站点构建与上线。这取代了过去依赖独立网站仓库的模式。

### site/dev/deploy.sh

**修改目的**：修正 mkdocs 部署命令，使其将站点推送到 ASF 约定的 `asf-site` 分支。

**工作逻辑**：原脚本中部署命令为 `mkdocs gh-deploy --no-history # --remote-branch asf-site`，其中 `--remote-branch asf-site` 被注释掉，`mkdocs gh-deploy` 默认会推送到 `gh-pages` 分支，与 ASF 约定的 `asf-site` 不一致。本提交将其改为 `mkdocs gh-deploy --no-history --remote-branch asf-site`，取消注释并启用该参数，使部署目标显式指向 `asf-site` 分支。`--no-history` 保留，表示每次部署创建无历史的新提交（强制覆盖式发布），保证分支干净。同时删除了原行末的注释说明，使命令语义清晰。

## 小结

这是一个基础设施/文档部署流程调整提交，本身不涉及产品代码逻辑。其意义在于将 Iceberg 官方网站的发布从可能的外部仓库模式整合到主仓库内部，通过 `.asf.yaml` 声明发布身份、`deploy.sh` 落实目标分支，实现站点源码与项目代码同仓库管理。这种整合降低了维护开销，使文档与代码版本保持一致，是项目治理规范化的一部分。
