# 提交 0159：Add dependabot to automatically update the site (#9004)

## 提交信息

- **序号**：0159 / 4088
- **哈希**：efa5945f12e85076bd665e5fa2bef53f4cf3f945
- **短哈希**：efa5945f1
- **日期**：2023-11-13 23:58:05 +0100
- **作者**：Fokko Driesprong
- **提交说明**：Add dependabot to automatically update the site (#9004)
- **PR/Issue**：#9004

## 总体目的

这是一个由人工提交（非 dependabot 自动生成）的配置变更，目的是为 Iceberg 文档站点（site）启用 Dependabot 自动更新。提交说明中作者 Fokko Driesprong 明确给出动机：由于 Iceberg 正在将文档站点迁移回主仓库（"moving the site back into the main repository"），希望让 Dependabot 自动维护 `site/requirements.txt` 中的 Python 依赖。

背景是：Iceberg 的文档站点基于 MkDocs 构建（见 `site/requirements.txt` 中的 mkdocs-material、mkdocs-macros-plugin 等），其依赖以 Python `pip` 形式声明在 `site/requirements.txt`。在站点代码迁回主仓库之前，这些 Python 依赖可能由独立的 CI 流程或手动维护；迁回主仓库后，主仓库已有的 Dependabot 配置（原本只覆盖 `github-actions` 和 `gradle` 两个生态系统）需要扩展才能覆盖 Python 依赖。

本次提交向 `.github/dependabot.yml` 追加一个新的 `pip` 生态系统条目，让 Dependabot 每周扫描 Python 依赖并发起升级 PR。这样文档站点的依赖升级就能像 Java 依赖那样自动化，避免文档构建链上的依赖（如 MkDocs 插件）长期停滞在旧版本，也降低安全漏洞随依赖陈旧而累积的风险。这也是后续 0160（mkdocs-macros-plugin 1.0.4 -> 1.0.5）等站点依赖升级 PR 得以自动产生的前置条件。

## 如何达成设计目的

通过在现有 `.github/dependabot.yml` 配置文件的 `updates` 列表末尾追加一个 `pip` 生态系统的配置块实现。整体设计思路是复用主仓库已有的 Dependabot 配置文件结构，新增一个独立的更新入口，与原有的 `github-actions`、`gradle` 两个入口并列。Dependabot 会针对每个 ecosystem 独立调度、独立发起 PR，互不干扰。

## 修改详情

### `.github/dependabot.yml`

**修改目的**：为文档站点新增 pip 生态系统的 Dependabot 自动更新配置。

**工作逻辑**：在原有的 `github-actions`（directory: `/`，无 open-pull-requests-limit）和 `gradle`（directory: `/`，weekly on Sunday，limit: 50）两个 update 块之后，追加第三个块：

```yaml
  - package-ecosystem: "pip"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "sunday"
    open-pull-requests-limit: 5
```

关键字段含义：

- `package-ecosystem: "pip"`：指定该块扫描 pip 生态系统（即 `requirements.txt` 形式声明的 Python 依赖），覆盖 `site/requirements.txt`（以及仓库中 `open-api/requirements.txt` 等位于扫描路径下的 pip 清单）。
- `directory: "/"`：扫描根目录起。Dependabot 对 pip 生态系统会在该目录下查找 manifest 文件。
- `schedule.interval: "weekly"` + `day: "sunday"`：每周日运行检查，与 `gradle` 块保持一致的节奏。
- `open-pull-requests-limit: 5`：同时最多保留 5 个待合并的升级 PR，避免站点依赖升级 PR 挤占过多审核精力（明显小于 gradle 块的 50）。

该改动本身不修改任何源代码或构建脚本，仅扩展自动化配置。配置生效后，Dependabot 即可对 `site/requirements.txt` 中的 mkdocs 系列插件版本发起自动升级 PR（例如紧随其后的 0160 提交便是 mkdocs-macros-plugin 1.0.4 -> 1.0.5 的自动升级）。

## 小结

为迁回主仓库的 Iceberg 文档站点新增 pip 生态系统的 Dependabot 配置，使站点 Python 依赖（mkdocs 等）的升级自动化，是站点治理自动化的关键前置改动。
