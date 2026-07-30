# 提交 3928：CI: Check ASF action allowlist on every PR (#16926)

## 提交信息

- **序号**：3928 / 4088
- **哈希**：02631cdaecd90ef840da12017c60981469a34b8a
- **短哈希**：02631cdae
- **日期**：2026-06-22 13:53:22 -0700
- **作者**：Manu Zhang
- **提交说明**：CI: Check ASF action allowlist on every PR (#16926)
- **PR/Issue**：#16926（修复 #16914）

## 总体目的

这次提交修改了 ASF（Apache Software Foundation）action allowlist 检查工作流的触发条件。原本该工作流仅在 PR 修改了 `.github/**` 路径下的文件时才运行，这次改动移除了 `paths` 过滤器，使该检查在所有 PR 和推送到 main 分支时都会运行。

ASF 对其孵化器和顶级项目有安全合规要求：项目使用的 GitHub Actions 必须来自 ASF 批准的允许列表（allowlist）。如果某个 PR 引入了不在允许列表中的 action，原本的配置下只有在修改 `.github/**` 文件时才会被发现。问题在于，某些情况下 action 的引用可能通过其他方式变更（例如依赖的 action 的上游更新、或通过 composite action 间接引入），导致合规漂移无法及时被发现。

移除 `paths` 过滤器后，即使 PR 不修改工作流文件，allowlist 检查也会运行，从而在上游批准模式发生漂移时能作为可见的检查失败暴露出来。这对应 issue #16914。

## 如何达成设计目的

通过从 `asf-allowlist-check.yml` 工作流的 `pull_request` 和 `push` 触发器中移除 `paths` 过滤器配置，使工作流在所有 PR 事件和 main 分支推送时无条件触发。

## 修改详情

### `.github/workflows/asf-allowlist-check.yml` (+0/-4 lines)

**修改目的**：移除工作流触发的路径过滤，使检查在所有 PR 上运行。

**工作逻辑**：
从 `on: pull_request:` 下移除 `paths: - ".github/**"` 配置，从 `on: push: branches: - main` 下移除 `paths: - ".github/**"` 配置。移除后，工作流在每个 PR 和每次推送到 main 时都会触发，无论修改了哪些文件。

## 总结

这次提交通过移除 ASF allowlist 检查工作流的路径过滤，确保该合规检查覆盖所有 PR。这样即使 PR 不直接修改工作流文件，也能及时发现上游 action 允许模式漂移，提升项目的安全合规可见性。
