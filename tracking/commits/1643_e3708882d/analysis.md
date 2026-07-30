# 提交 1643：Build: Bump actions/stale from 9.0.0 to 9.1.0 (#12110)

## 提交信息

- **序号**：1643 / 4088
- **哈希**：e3708882d24021845b44c2957283a9df456649f2
- **短哈希**：e3708882d
- **日期**：2025-01-27（Mon Jan 27 09:27:16 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump actions/stale from 9.0.0 to 9.1.0
- **PR/Issue**：#12110

## 总体目的

Iceberg 仓库的 `.github/workflows/stale.yml` 使用 GitHub 官方的 `actions/stale` Action 来自动管理 stale issue/PR（给长期无活动的 issue 打 stale 标签、超时自动关闭）。Dependabot 监测到 `actions/stale` 发布了新版本 9.1.0（相对于当前锁定的 9.0.0 是一个 semver minor 升级），自动提 PR 把版本引用从 `v9.0.0` 升到 `v9.1.0`，以获取 9.1.0 中包含的 bug 修复与改进（如更稳健的速率限制、标签处理边界情况修复等）。

这是一次纯 CI 工具链版本升级，不动任何业务代码。

## 如何达成设计目的

通过 Dependabot 自动修改 `.github/workflows/stale.yml` 中 `actions/stale` 的版本引用实现。Dependabot 提交信息包含更新类型、依赖名、对比链接（release notes / changelog / commits diff），便于审阅者快速确认升级范围。

## 修改详情

### `.github/workflows/stale.yml`（修改，+1/-1）

**修改目的**：升级 `actions/stale` 版本引用。

**工作逻辑**：

```
-      - uses: actions/stale@v9.0.0
+      - uses: actions/stale@v9.1.0
```

`stale.yml` 中 `stale` job 的第一步引用 `actions/stale`，从 `v9.0.0` 改为 `v9.1.0`。GitHub Actions 运行时会按此标签拉取对应版本的 Action 代码。其余配置（`stale-issue-label`、`days-before-stale`、`exempt-issue-labels` 等）不变。

## 小结

- **成效**：把 stale 管理 Action 升到 9.1.0，获得该版本的修复与改进；不影响业务代码，只影响 GitHub 仓库的 issue/PR 自动化流程。
- **影响范围**：仅 `.github/workflows/stale.yml` 一行。下一次 stale workflow 触发时会使用 9.1.0。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支若有同样的 `stale.yml`，可直接 cherry-pick；纯 CI 配置升级，无风险。若 1.4.x 已自定义 stale 行为，需确认 9.1.0 与自定义配置兼容（一般 semver minor 兼容）。
