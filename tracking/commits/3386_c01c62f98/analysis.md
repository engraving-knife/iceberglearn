# 提交 3386：Infra: Remove legacy github.del_branch_on_merge flag (#15620)

## 提交信息

- **序号**：3386 / 4088
- **哈希**：c01c62f98a759f8321161e3d6a4411b632765d0e
- **短哈希**：c01c62f98
- **日期**：2026-03-13 10:33:06 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：Infra: Remove legacy github.del_branch_on_merge flag (#15620)
- **PR/Issue**：#15620

## 总体目的

清理 `.asf.yaml` 中遗留的、已废弃的顶层 `github.del_branch_on_merge` 配置项。该配置项在 ASF（Apache Software Foundation）的 GitHub 仓库配置文件 `.asf.yaml` 中已被标记为 deprecated（废弃但保留用于向后兼容），同时仓库已经在 `pull_requests` 段下设置了等价的 `del_branch_on_merge: true`。本提交移除冗余的旧配置，保持仓库配置文件的整洁。

## 如何达成设计目的

直接删除 `.asf.yaml` 中 github 顶层下被注释标记为 "deprecated but still set for backward compatibility" 的 `del_branch_on_merge: true` 三行（含注释和空行），保留 `pull_requests.del_branch_on_merge: true` 这一当前推荐的配置位置。由于后者已经实现了相同的"PR 合并后自动删除 head 分支"行为，删除旧配置不会影响实际功能。

## 修改详情

### `.asf.yaml` (+0/-3 lines)

**修改目的**：移除废弃的顶层 `del_branch_on_merge` 配置。

**工作逻辑**：
- 删除如下三行：
  ```
  # deprecated but still set for backward compatibility
  del_branch_on_merge: true

  ```
- 保留 `pull_requests` 段下的 `del_branch_on_merge: true`，该配置继续生效，PR 合并后仍会自动删除源分支。

## 总结

这是一次纯基础设施配置清理，移除了 `.asf.yaml` 中已废弃的冗余 `del_branch_on_merge` 配置项。功能行为不发生任何变化（自动删除分支仍由 `pull_requests.del_branch_on_merge` 控制），仅提升了配置文件的整洁度，避免维护者对两处相同配置产生混淆。
