# 提交 1714：Build: skip scheduled docker image publish workflows on forks (#12218)

## 提交信息

- **序号**：1714 / 4088
- **哈希**：a212e998c16956e77543e324c8efa034bb685d53
- **短哈希**：a212e998c
- **日期**：2025-02-11 10:38:30 +0100
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Build: skip scheduled docker image publish workflows on forks (#12218)
- **PR/Issue**：#12218

## 总体目的

修复 GitHub Actions 工作流在仓库 fork（分叉）上不必要触发的问题。`publish-iceberg-rest-fixture-docker.yml` 是一个定时调度的 Docker 镜像发布工作流，用于将 Iceberg REST fixture 镜像发布到容器仓库。

当开发者 fork 了 Iceberg 仓库后，GitHub Actions 的定时调度工作流也会在 fork 仓库上运行。这会导致以下问题：一是浪费 fork 仓库的 CI 资源；二是可能尝试将镜像发布到 fork 者自己的容器仓库，可能导致发布失败或产生不必要的镜像。通过添加条件判断，确保该工作流仅在 Apache 官方仓库（`apache` 组织）下运行。

## 如何达成设计目的

在 `build` job 中添加 `if` 条件判断，检查 `github.repository_owner == 'apache'`。只有当仓库所有者是 `apache` 时，才会执行该 job。这是 GitHub Actions 中限制工作流仅在特定组织/用户下运行的标准做法。

## 修改详情

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml`（修改, +1 line）

**修改目的**：限制 Docker 镜像发布工作流仅在 Apache 官方仓库运行。

**工作逻辑**：在 `build` job 定义中添加 `if: github.repository_owner == 'apache'` 条件。GitHub Actions 在执行 job 前会评估此条件表达式，`github.repository_owner` 是 GitHub 上下文变量，表示仓库所有者（组织或用户名）。当所有者不是 `apache` 时（即在 fork 仓库上），该 job 会被跳过。

## 小结

- **成效**：fork 仓库不再执行定时 Docker 镜像发布工作流，避免了资源浪费和潜在的发布错误。
- **影响范围**：仅影响 CI/CD 工作流配置，不影响项目代码。
- **回迁到 1.4.x 的注意事项**：可以安全回迁，但需确认 1.4.x 分支是否有相同的工作流文件。如果 1.4.x 分支也有此定时发布工作流，则建议回迁以避免 fork 上的不必要执行。无前置依赖。
