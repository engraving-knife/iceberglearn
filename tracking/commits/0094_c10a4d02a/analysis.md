# 提交 0094：Infra: Disable merging explicitly in `.asf.yaml` (#8878)

## 提交信息

- **序号**：0094 / 4088
- **哈希**：c10a4d02a2f5596e5e9407044c96c8047e277852
- **短哈希**：c10a4d02a
- **日期**：2023-10-25 08:57:36 -0400
- **作者**：Fokko Driesprong
- **提交说明**：Infra: Disable merging explicitly in `.asf.yaml` (#8878)
- **PR/Issue**：#8878

## 总体目的

这个提交通过修改仓库根目录的 `.asf.yaml` 配置文件，显式地配置 GitHub 仓库的合并按钮策略与分支保护规则：禁用 "Create a merge commit" 按钮，仅保留 "Squash and merge" 与 "Rebase and merge" 两种合并方式；同时对 `main` 分支启用保护，要求线性提交历史（no merge commits）。这是一次仓库治理层面的基础设施配置调整，不涉及任何业务代码。

背景：`.asf.yaml` 是 Apache 软件基金会（ASF）为旗下项目提供的仓库级配置文件，ASF 的基础设施会读取该文件并将其中的 `github:` 段应用到对应的 GitHub 仓库（文档见 [cwiki: Git + .asf.yaml features](https://cwiki.apache.org/confluence/display/INFRA/Git+-+.asf.yaml+features)）。Iceberg 此前已经通过提交 INFRA 工单（INFRA ticket）的方式在 GitHub 仓库层面关闭了 merge commit 合并按钮，但这个配置并未落到仓库内的 `.asf.yaml` 中——也就是说，配置是"仓库外"的、不可见、不可追踪的。提交说明里作者 Fokko 明确指出："This was done earlier before there was a `.asf.yaml` through an INFRA ticket, but I think it is good to also add it to the `yaml`."

动机有两点：一是**配置即代码（configuration as code）**，把仓库治理策略从 ASF INFRA 后台的隐性配置搬到仓库内的显式声明，让策略可被 git 追踪、可被社区评审、可随仓库迁移；二是**强制线性历史**，Iceberg 作为大型多模块项目，线性提交历史（squash/rebase 合并）能让 `git log`、`git bisect`、revert 等操作更清晰，避免 merge commit 把 PR 历史与主线历史交织。这是 Apache 不少大型项目的通行做法。

## 如何达成设计目的

改动只在 `.asf.yaml` 的 `github:` 段下新增了两个配置块：`enabled_merge_buttons`（控制三种合并按钮的开关）和 `protected_branches`（对 `main` 分支设置保护规则）。这两块配置由 ASF INFRA 的 `.asf.yaml` 解析器消费，再通过 GitHub API 应用到仓库，从而把原本由 INFRA 工单完成的设置固化为仓库内可追踪的声明式配置。

## 修改详情

### [.asf.yaml](file:///Users/fengxiaohang/trae/iceberglearn/.asf.yaml)

**修改目的**：在 `.asf.yaml` 中显式声明 GitHub 仓库的合并按钮策略与 `main` 分支保护规则，把原本通过 INFRA 工单做的隐性配置固化为仓库内可追踪的配置。

**工作逻辑**：在 `github:` 段的 `labels` 之后、`features` 之前，新增了两个配置块（共 10 行新增）：

```yaml
  enabled_merge_buttons:
    merge: false
    squash: true
    rebase: true

  protected_branches:
    main:
      required_linear_history: true
```

- `enabled_merge_buttons`：控制 GitHub PR 页面三种合并按钮的可用性。
  - `merge: false` —— 关闭 "Create a merge commit" 按钮，即禁止产生 merge commit 的合并方式。
  - `squash: true` —— 保留 "Squash and merge" 按钮，把 PR 的多个提交压成单个提交合入主线。这是 Iceberg 推荐的合并方式，能保持主线每个提交对应一个 PR。
  - `rebase: true` —— 保留 "Rebase and merge" 按钮，把 PR 提交逐个 rebase 到主线。
- `protected_branches.main.required_linear_history: true` —— 对 `main` 分支启用"要求线性历史"保护规则，强制 `main` 上不得有 merge commit。这与上面禁用 merge 按钮配合，双重保险地确保主线历史始终线性。

这两块配置共同实现了"主线只接受 squash/rebase 合并、不允许 merge commit"的治理目标，且配置随仓库版本化，社区任何改动都需走 PR 评审。

## 小结

该提交把 GitHub 仓库的合并按钮策略（禁用 merge commit、保留 squash/rebase）与 `main` 分支线性历史保护规则从 ASF INFRA 后台的隐性配置固化为 `.asf.yaml` 中的显式声明，提升仓库治理的可追踪性与一致性，确保 Iceberg 主线始终保持线性提交历史。
