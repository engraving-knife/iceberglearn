# 提交 0806：Build: Require approving review (#10424)

## 提交信息
- **序号**：0806 / 4088
- **哈希**：7d75f823ac0606e5efa25dc73b50e74b3bc87411
- **短哈希**：7d75f823a
- **日期**：2024-06-03
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Build: Require approving review (#10424)
- **PR/Issue**：#10424

## 总体目的
本提交修改的是仓库根目录下的 `.asf.yaml` 文件，这是 Apache 软件基金会（ASF）项目的 GitHub 仓库配置文件。其目的是在 `main` 受保护分支上启用"必须至少有一名审批评审"的分支保护规则，提高代码合并的合规性。

具体而言，提交在 `github.protected_branches.main` 配置块下新增了 `required_pull_request_reviews` 子配置，并将 `required_approving_review_count` 设置为 `1`。这意味着任何针对 `main` 分支的 Pull Request，在合并之前都必须获得至少 1 名评审者的 approving review。

这是一次纯仓库治理层面的改动，不涉及任何 Java 代码、构建脚本（build.gradle）或依赖版本变更。改动直接影响 GitHub 平台对 `main` 分支的保护策略，由 GitHub 根据 `.asf.yaml` 的约定自动应用。

## 如何达成设计目的
通过在 `.asf.yaml` 中添加 `required_pull_request_reviews.required_approving_review_count: 1`，Apache Iceberg 仓库利用了 ASF 提供的 `.asf.yaml` 机制，由 Apache 基础设施（Infra）的 [rabbit-link](https://github.com/apache/github-bot) 后台服务监听并应用此配置到 GitHub。

该配置与已有的 `required_linear_history: true`（要求线性历史，即禁止合并提交，必须 rebase 或 squash 合并）并列，进一步收紧了 `main` 分支的合并门槛。

## 修改详情
### `.asf.yaml`
**修改目的**：在 `main` 分支保护策略中新增"至少 1 个 approving review 才能合并"的规则。
**工作逻辑**：
```yaml
protected_branches:
  main:
    required_pull_request_reviews:
      required_approving_review_count: 1

    required_linear_history: true
```
- 在 `main` 分支的 `protected_branches` 块下新增 `required_pull_request_reviews` 子块。
- `required_approving_review_count: 1` 表示 PR 必须至少获得 1 个 approving review。
- 与下方原有的 `required_linear_history: true` 配合使用，共同保障 `main` 分支的合并质量。

## 小结
- **成效**：通过仓库配置而非代码改动，强制要求 PR 至少获得 1 个 approving review 才能合并到 `main`，加强了代码评审治理。
- **影响范围**：仅影响 GitHub 平台对 `main` 分支的合并策略，对运行时行为、构建产物、依赖图均无影响。
- **回迁注意事项**：回迁到 1.4.x 分支时此提交不需要特别处理——`.asf.yaml` 是仓库治理配置，与分支代码逻辑无关。即便不回迁也不影响 1.4.x 的构建或运行。如果 1.4.x 也是受保护分支且希望同样要求评审，可在 1.4.x 自己的 `.asf.yaml` 中加上相同配置；但通常 1.4.x 作为发布分支不在保护规则的重点范围内，可以选择性回迁。
