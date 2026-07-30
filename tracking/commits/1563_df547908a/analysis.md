# 提交 1563：Infra: Add manuzhang to collaborators (#11927)

## 提交信息

- **序号**：1563
- **哈希**：df547908a9500ec5b886cfeb64ea5bf10ebde84f
- **短哈希**：df547908a
- **日期**：2025-01-09（Thu Jan 9 01:00:18 2025 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Infra: Add manuzhang to collaborators (#11927)
- **PR/Issue**：#11927

## 总体目的

Apache Iceberg 仓库根目录的 `.asf.yaml` 是 Apache 软件基金会提供的仓库配置文件，其中 `github.collaborators` 列表用于向 GitHub 仓库授予额外的协作者（collaborator）权限。当一位新 committer/PMC 成员加入项目，或希望为某位活跃贡献者开启仓库直接协作权限时，需要在此列表中加入其 GitHub 用户名。

本提交将 GitHub 用户 `manuzhang` 加入 collaborators 列表，对应实际身份为 Manu Zhang（提交作者本人）。同时将原有 `samredai` 从列表中移除——这通常意味着对应成员不再需要通过 collaborator 方式访问仓库（可能已转为 committer 走 PMC 管理的权限，或离开项目）。

该变更属于纯仓库基础设施配置，不涉及任何代码、构建脚本或运行时行为。

## 如何达成设计目的

直接编辑 `.asf.yaml`：在 `github.collaborators` 列表中删除 `- samredai` 一行，并在列表末尾（`jbonofre` 之后）追加 `- manuzhang`。变更通过 ASF 的 `.asf.yaml` 机制生效：当推送到默认分支时，ASF 基础设施会读取该文件并调用 GitHub API 调整仓库 collaborator 列表。

### 修改详情

#### `.asf.yaml`
**修改目的**：维护 collaborator 列表的当前状态。

**内容**：
```yaml
 github:
   collaborators:
     - marton-bod
     - samarthjain
     - SreeramGarlapati
-    - samredai
     - gaborkaszab
     - bitsondatadev
     - ajantha-bhat
     - jbonofre
+    - manuzhang
```

新增 `manuzhang`、移除 `samredai`，列表总长度保持不变。

## 小结

- **成效**：`manuzhang` 获得仓库 collaborator 权限，`samredai` 的 collaborator 权限被收回，权限列表与当前团队状态保持一致。
- **影响范围**：仅 `.asf.yaml` 一个文件，纯仓库管理配置，无运行时影响。
- **回迁到 1.4.x 的注意事项**：与产品版本无关，1.4.x 维护分支无需也无法回迁此类基础设施配置——`.asf.yaml` 作用于整个仓库而非分支，由 main 分支统一维护。**无需回迁**。
