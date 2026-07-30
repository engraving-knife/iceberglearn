# 提交 3470：CI: Add ASF allowlist check workflow (#15797)

## 提交信息

- **序号**：3470 / 4088
- **哈希**：37a0ed689976db9079e3039b682ee646e2d34810
- **短哈希**：37a0ed6899
- **日期**：2026-03-27 14:31:03 -0700
- **作者**：Kevin Liu
- **提交说明**：CI: Add ASF allowlist check workflow (#15797)
- **PR/Issue**：#15797

## 总体目的

添加 ASF（Apache 软件基金会）允许列表检查工作流。Apache 基础设施要求所有 GitHub Actions 必须在 ASF 允许列表中。不在允许列表中的 action 会静默失败（"Startup failure"），没有日志和通知，PR 可能看起来通过了（因为没有检查运行）。此工作流验证所有 GitHub Actions 引用都在 ASF 允许列表中。

## 如何达成设计目的

- 新建 `asf-allowlist-check.yml` 工作流
- 在 PR 和 push 到 main 时触发，当 `.github/` 目录有变更时
- 使用 `apache/infrastructure-actions/allowlist-check@main` 检查所有 action 引用
- 故意不固定版本，始终使用 ASF 最新允许列表

## 修改详情

### `.github/workflows/asf-allowlist-check.yml` (+47/-0 lines) - 新文件

**修改目的**：创建 ASF 允许列表检查工作流。

**工作逻辑**：

```yaml
name: "ASF Allowlist Check"
on:
  pull_request:
    paths:
      - ".github/**"
  push:
    branches:
      - main
    paths:
      - ".github/**"
permissions:
  contents: read
jobs:
  asf-allowlist-check:
    runs-on: ubuntu-24.04
    steps:
    - uses: actions/checkout@de0fac2e... # v6
      with:
        persist-credentials: false
    # Intentionally unpinned to always use the latest allowlist from the ASF.
    - uses: apache/infrastructure-actions/allowlist-check@main # zizmor: ignore[unpinned-uses]
```

关键设计：
- 触发条件：PR 或 push 到 main，且 `.github/` 目录有变更
- 权限：仅 `contents: read`
- checkout 使用 `persist-credentials: false`（安全最佳实践）
- allowlist-check action 故意不固定版本（添加 zizmor ignore 注释），始终使用 ASF 最新允许列表

## 总结

该提交新增 ASF 允许列表检查工作流，在 `.github/` 目录变更时验证所有 GitHub Actions 引用都在 ASF 允许列表中。这解决了不在允许列表中的 action 静默失败导致 PR 误判为通过的问题。
