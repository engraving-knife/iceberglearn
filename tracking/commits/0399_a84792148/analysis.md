# 提交 0399：Infra: Increase operations-per-run in stale action to 100

## 提交信息

- **序号**：0399
- **哈希**：a847921487c29d275a4bfb2e1e8d01840e790933
- **短哈希**：a84792148
- **日期**：Mon Jan 22 15:35:34 2024 +0800
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Infra: Increase operations-per-run in stale action to 100 (#9529)
- **PR/Issue**：#9529

## 总体目的

这个提交调整了 Iceberg 仓库使用的 GitHub Stale Action 的运行参数，将 `operations-per-run` 从默认值（30）提升到 100，以便让 stale 机器人在单次调度中能处理更多的 issue 和 pull request。

GitHub Stale Action 是一个广泛使用的 GitHub Actions 动作，用于自动标记和关闭长期未活动的 issue 与 PR：超过指定时间未活动的会被标记为 "stale"，再经过一段宽限期后若仍无活动则自动关闭。这有助于项目维护者控制待办事项列表的规模，让真正需要关注的事项浮现出来。

`operations-per-run` 是 stale action 的一个关键节流参数，它限制单次工作流执行中允许对 GitHub API 发起的操作（operation）次数上限。每次操作大致对应处理一个 issue 或 PR（获取、更新、评论等）。当仓库积累的 issue/PR 数量较多时，默认的 30 次操作不足以在一次运行中遍历完所有待处理项，导致部分本应被标记 stale 或关闭的 issue 被遗漏，需要多次定时触发才能处理完。

Iceberg 作为一个活跃的大型开源项目，issue 和 PR 数量可观。将 operations-per-run 提升到 100，可以让 stale bot 在每次运行时覆盖更多项目，使陈旧事项管理更及时、更彻底，减少 backlog 积压，提升仓库治理效率。

## 如何达成设计目的

实现方式是在 `.github/workflows/stale.yml` 工作流文件中，为 stale action 的 `with` 配置块新增一行 `operations-per-run: 100`。该参数直接传递给 actions/stale 动作，不需要任何代码逻辑变更。这是一个纯配置层面的调优。

## 修改详情

### .github/workflows/stale.yml

**修改目的**：提升 stale action 单次运行可执行的操作数上限，使其能处理更多 issue/PR。

**工作逻辑**：在该工作流文件的 stale action 配置末尾新增 `operations-per-run: 100` 一行。该值覆盖了 actions/stale 动作的默认值 30。结合工作流中已有的 `days-before-stale`、`days-before-close`、`stale-message`、`close-message`、`ascending` 等配置，stale bot 现在每次运行最多可执行 100 次操作。`ascending: true` 表示按时间升序处理（先处理最陈旧的项），与更高的操作上限配合，能更高效地清理积压的陈旧事项。

## 小结

这是一个轻量的仓库治理参数调优提交。通过一行配置改动显著提升了 stale 机器人单次运行的吞吐量，让 Iceberg 仓库中陈旧 issue 和 PR 的自动化管理更及时高效。这类基础设施层面的微调虽然简单，但对维护大型开源项目的健康度有实际的长期收益。
