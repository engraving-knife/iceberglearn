# 提交 3348：infra: restore github.del_branch_on_merge in .asf.yaml (#15517)

## 提交信息

- **序号**：3348 / 4088
- **哈希**：175e6c241ea178b8a4fb6ea16bd801b3c7fc3b91
- **短哈希**：175e6c241
- **日期**：2026-03-06 10:49:25 -0800
- **作者**：Kevin Liu
- **提交说明**：infra: restore github.del_branch_on_merge in .asf.yaml (#15517)
- **PR/Issue**：#15517

## 总体目的

Apache 项目通过仓库根目录的 `.asf.yaml` 向 ASF 基础设施（GitHub 镜像托管）声明 GitHub 仓库的行为配置。其中 `github.del_branch_on_merge` 控制合并 PR 后是否自动删除 head 分支。Iceberg 希望保留该行为，以避免大量残留的已合并分支堆积污染仓库视图。

本提交的背景是 `.asf.yaml` 的 schema 发生了变化：`github.del_branch_on_merge` 这一顶层字段被标记为 deprecated（废弃），ASF 基础设施工具在某个时点可能不再识别该旧位置，导致自动删分支功能失效。新位置要求把它放在 `github.pull_requests.del_branch_on_merge` 下。在迁移到新结构期间，仓库曾一度丢失了该配置，合并后的分支不再被自动删除。本提交同时恢复顶层旧字段（向后兼容）并保留 `pull_requests.del_branch_on_merge`（新位置），并把文档引用链接从陈旧的 Confluence wiki 更新为 infrastructure-asfyaml 的 GitHub README，确保配置在新旧工具版本下都生效。

## 如何达成设计目的

在 `.asf.yaml` 的 `github:` 段下重新加上顶层 `del_branch_on_merge: true`，并加注释说明这是为向后兼容而保留的 deprecated 字段；其下 `pull_requests.del_branch_on_merge: true` 维持不变（这是新 schema 的标准位置）。同时把文件顶部的文档说明链接从旧的 Confluence wiki 页面替换为 asfyaml 项目 README。

## 修改详情

### `.asf.yaml` (+4/-1 lines)

**修改目的**：恢复自动删除已合并分支的配置并更新文档链接。

**工作逻辑**：

1. 在 `github:` 段（`squash` / `rebase` 之后）新增顶层字段：
   ```yaml
   # deprecated but still set for backward compatibility
   del_branch_on_merge: true
   ```
   并保留既有 `pull_requests.del_branch_on_merge: true`。这样无论 ASF 工具读取哪个位置，都能正确触发合并后删除 head 分支。

2. 更新文件顶部注释中的文档链接：从 `https://cwiki.apache.org/confluence/display/INFRA/Git+-+.asf.yaml+features`（已陈旧）改为 `https://github.com/apache/infrastructure-asfyaml/blob/main/README.md`（asfyaml 项目的权威说明），让后续维护者能查阅到最新 schema。

## 总结

本提交修复了因 `.asf.yaml` schema 迁移导致的"合并后自动删分支"配置丢失问题，通过同时保留 deprecated 顶层字段与新位置 `pull_requests.del_branch_on_merge` 实现向后兼容，并更新文档链接以指引后续维护。
