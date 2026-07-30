# 提交 0944：Infra: Increase operation per limit for stale bot workflow (#10712)

## 提交信息

- **序号**：0944 / 4088
- **哈希**：206e471de401da65070ee9099adc2f9691b8e781
- **短哈希**：206e471de
- **日期**：2024-07-17 15:00:45 +0400
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Infra: Increase operation per limit for stale bot workflow (#10712)
- **PR/Issue**：#10712

## 总体目的

Iceberg 仓库使用 GitHub Actions 的 `actions/stale` 机器人周期性扫描长期未活动的 issue 与 PR，对其打上 stale 标记并在宽限期后关闭。`stale` action 通过 `operations-per-run` 参数限制每次定时触发时调用 GitHub API 的次数上限，以避免触发 GitHub 速率限制（rate limit）。原配置为每次运行 100 次操作，但在 Iceberg 这种 issue/PR 数量庞大的项目中，100 次操作不足以在一轮运行中覆盖所有待处理条目，导致部分陈旧条目迟迟得不到标记或关闭，积压问题反馈不畅。

本提交将 `operations-per-run` 由 100 提升到 200，使 stale bot 在每次定时运行时能够处理更多条目，提高陈旧 issue/PR 的清理吞吐量，同时仍在 GitHub 速率限制的安全范围内。

## 如何达成设计目的

直接修改 `.github/workflows/stale.yml` 工作流定义中传给 `actions/stale` 步骤的 `operations-per-run` 输入参数，将数值从 `100` 改为 `200`。其余参数（如 `days-before-issue-stale`、`days-before-pr-stale`、`ascending` 等）保持不变。下一次定时触发时 stale action 即按新上限运行。

## 修改详情

### `.github/workflows/stale.yml`

**修改目的**：提升 stale bot 每次运行的 GitHub API 操作次数上限，使其能在一轮中处理更多陈旧 issue/PR。

**工作逻辑**：`actions/stale` 在每次定时运行时按 `operations-per-run` 限制对仓库 issue/PR 列表的扫描与标记操作次数；超过上限后本次运行提前结束，剩余条目留待下一次定时触发。改动如下：

```diff
-          operations-per-run: 100
+          operations-per-run: 200
```

将上限翻倍后，单次运行可覆盖的条目数大致翻倍，从而加快积压清理节奏。

## 小结

- **成效**：将 stale bot 单次运行操作上限由 100 提升到 200，提升陈旧 issue/PR 的扫描与关闭吞吐量，缓解清理积压。
- **影响范围**：仅 `.github/workflows/stale.yml` 一个文件，1 行参数调整，不触及任何源代码或文档。
- **回迁到 1.4.x 的注意事项**：这是仓库治理/CI 配置层面的改动，与代码功能无关，**一般不需要回迁到 1.4.x 维护分支**。1.4.x 分支的 stale 工作流配置应按该分支自身的 issue/PR 体量独立评估；若 1.4.x 分支直接复用 main 的工作流文件，则可选择性同步。
