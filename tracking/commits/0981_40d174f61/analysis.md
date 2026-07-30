# 提交 0981：Infra: Add jbonofre as collaborator on the project (#10782)

## 提交信息

- **序号**：0981 / 4088
- **哈希**：40d174f61518da87a3827f0ac52bc05d611c3324
- **短哈希**：40d174f61
- **日期**：2024-07-25 13:12:55 -0600
- **作者**：JB Onofré
- **提交说明**：Infra: Add jbonofre as collaborator on the project (#10782)
- **PR/Issue**：#10782

## 总体目的

Apache 仓库根目录的 `.asf.yaml` 文件中 `github.collaborators` 列表用于向 GitHub 仓库添加协作者（collaborator）账号，使其获得仓库的额外操作权限。本提交的目的是将 `jbonofre`（即 JB Onofré，Iceberg PMC 成员、本次 1.6.0 发布经理）添加到该协作者列表中，使其在 GitHub 上拥有对应的仓库协作权限。

这与 0975 号提交（移除已晋升为 committer 的协作者）属同一类仓库治理操作：根据项目成员角色变化维护协作者列表。

## 如何达成设计目的

直接编辑 `.asf.yaml`，在 `github.collaborators` 列表末尾追加 `- jbonofre` 一行。

## 修改详情

### `.asf.yaml`

**修改目的**：将 `jbonofre` 添加为 GitHub 仓库协作者。

**工作逻辑**：单行新增。在 `collaborators` 列表中 `ajantha-bhat` 之后追加 `jbonofre`：

```diff
     - bitsondatadev
     - ajantha-bhat
+    - jbonofre
   ghp_branch: gh-pages
```

## 小结

- **成效**：将 `jbonofre` 添加到 Iceberg GitHub 仓库的协作者列表，授予其仓库协作权限。
- **影响范围**：仅 `.asf.yaml` 一个仓库配置文件，1 行新增，无代码影响。
- **回迁到 1.4.x 的注意事项**：此为仓库治理/权限配置变更，针对 main 分支当前成员状态，与 1.4.x 维护分支无直接关系，**无需回迁**。1.4.x 分支的 `.asf.yaml` 应按该分支自身的协作者需求维护。
