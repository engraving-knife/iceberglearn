# 提交 0976：Update .asf.yaml (#10767)

## 提交信息

- **序号**：0976 / 4088
- **哈希**：622c127c3838c37f8479f73083dbea8db29207f5
- **短哈希**：622c127c3
- **日期**：2024-07-25 14:20:18 +0200
- **作者**：Ajantha Bhat
- **提交说明**：Update .asf.yaml (#10767)
- **PR/Issue**：#10767

## 总体目的

Apache 仓库根目录下的 `.asf.yaml` 是 ASF（Apache 软件基金会）的仓库配置文件，其中 `github.collaborators` 字段列出 GitHub 仓库的"协作者"（collaborator）账号，这些账号拥有除正式 committer/PMC 之外的部分仓库操作权限。当一个贡献者被晋升为正式 committer 后，其 GitHub 账号通常会通过 committer 列表自动获得权限，此时将其从 `collaborators` 列表中移除可避免权限重复配置，保持列表整洁。

本提交由 Ajantha Bhat 发起，目的是从 `.asf.yaml` 的 `github.collaborators` 列表中移除 `findepi`（即 Piotr Findeisen）。提交说明明确写道："Remove a contributor who is now a committer."——即 findepi 已晋升为 Iceberg committer，因此不再需要在协作者列表中保留其条目。

## 如何达成设计目的

直接编辑 `.asf.yaml` 文件，在 `github.collaborators` 列表中删除 `- findepi` 这一行。无其他改动。

## 修改详情

### `.asf.yaml`

**修改目的**：从 GitHub 协作者列表中移除已晋升为 committer 的 `findepi`。

**工作逻辑**：单行删除。diff 如下：

```diff
     - marton-bod
     - samarthjain
-    - findepi
     - SreeramGarlapati
```

`findepi` 行被移除，其余协作者条目不变。

## 小结

- **成效**：清理了 `.asf.yaml` 中已过时的协作者条目 `findepi`，因为该账号已通过 committer 身份获得权限，避免重复配置。
- **影响范围**：仅 `.asf.yaml` 一个仓库配置文件，1 行删除，无代码影响。
- **回迁到 1.4.x 的注意事项**：此为仓库治理/权限配置变更，针对的是 main 分支当前状态，与 1.4.x 维护分支无直接关系，**无需回迁**。1.4.x 分支的 `.asf.yaml` 应按该分支自身的协作者状态维护。
