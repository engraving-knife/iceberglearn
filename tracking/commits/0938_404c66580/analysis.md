# 提交 0938：Infra: Fix stale PR workflow (#10706)

## 提交信息

- **序号**：0938 / 4088
- **哈希**：404c665800b645c2cb2453c5ed20dffd7f59d795
- **短哈希**：404c66580
- **日期**：2024-07-16（Tue Jul 16 14:51:42 2024 +0530）
- **作者**：Ajantha Bhat \<ajanthabhat@gmail.com\>
- **提交说明**：Infra: Fix stale PR workflow (#10706)
- **PR/Issue**：#10706

## 总体目的

本提交修复 Iceberg 仓库的 "Close Stale Issues" GitHub Actions 工作流。该工作流（`.github/workflows/stale.yml`）使用 `actions/stale@v8.0.0` 动作，每天定时扫描超过 180 天无活动 issue 并标记为 stale，14 天后无进一步活动则关闭。工作流的 `permissions` 段此前只声明了 `issues: write`，未声明 `pull-requests: write`。

`actions/stale` 动作在运行时即便配置了 `days-before-pr-stale: -1`（即不处理 PR），其内部仍会与 pull requests API 交互（例如检索仓库项时同时覆盖 issue 与 PR），因此缺少 `pull-requests: write` 权限会导致工作流运行时报权限错误或异常退出，无法正常完成 stale issue 的标记与关闭。本次修复补上缺失的权限声明，使工作流能稳定运行。

## 如何达成设计目的

实现方式非常直接：在 `stale.yml` 的 `permissions` 段中，于 `issues: write` 之后新增 `pull-requests: write` 一行。由于 `permissions` 段一旦显式声明，未列出的权限均默认为 `none`，因此需要把 `actions/stale` 实际依赖的权限都显式列出。补上 `pull-requests: write` 后，动作在调用涉及 PR 的 API 时不会因 403/权限不足而失败。

注意：添加该权限并不改变工作流的行为意图——配置中 `days-before-pr-stale: -1` 仍然保证不会把 PR 标记为 stale 或关闭，PR 仍被"放任不管"。权限只是让动作能正常执行其内部逻辑。

## 修改详情

### `.github/workflows/stale.yml`

**修改目的**：为 stale 工作流补上 `pull-requests: write` 权限，避免 `actions/stale` 运行时因权限不足报错。

**工作逻辑**：在 `permissions` 段新增一行：

```diff
 permissions:
   # All other permissions are set to none
   issues: write
+  pull-requests: write
```

修改后，`actions/stale@v8.0.0` 在执行时拥有对 issue 和 pull request 的写权限。结合 job 配置中的 `days-before-pr-stale: -1`，工作流仍只对 issue 生效（180 天 stale，14 天后关闭），PR 不受影响，但动作内部涉及 PR API 的操作不再因权限缺失而失败。

## 小结

- **成效**：修复了 stale 工作流因缺少 `pull-requests: write` 权限而无法正常运行的问题，使每日的 stale issue 自动标记与关闭流程能稳定执行。
- **影响范围**：仅 `.github/workflows/stale.yml` 一个文件、一行新增；不影响任何产品代码，仅影响仓库的 CI/CD 自动化运维。
- **回迁到 1.4.x 的注意事项**：这是仓库基础设施（CI）修复，回迁到 1.4.x 完全无风险，且如果 1.4.x 分支共享相同的 stale 工作流配置则建议回迁以避免同样的工作流失败。cherry-pick 不涉及任何代码逻辑冲突。
