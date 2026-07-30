# 提交 0376：Infra: Check stale issues in ascending order (#9489)

## 提交信息

- **序号**：0376
- **哈希**：b3273276fa4bac384b2429b7218fd18b242beefe
- **短哈希**：b3273276f
- **日期**：2024-01-17 18:37:08 +0800
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Infra: Check stale issues in ascending order (#9489)\n\nWe have so many issues that only latest ones are checked for staleness due to rate limit
- **PR/Issue**：#9489

## 总体目的

这个提交修复了 Iceberg 仓库 GitHub Actions `stale` 工作流的一个实际运维问题：由于 issue 数量过多触发 GitHub API 速率限制，机器人只能检查到最新的若干 issue，导致老 issue 永远得不到"过期"处理，进而无法被自动关闭。

Iceberg 仓库使用 `actions/stale` 这个官方 Action 定期扫描带 `stale` 标签的 issue，对超过 14 天无活动的 issue 自动关闭。`actions/stale` 默认按 issue 编号降序（最新优先）处理。当一个仓库积累了几千个 issue 时，stale bot 每次运行触达的 API 调用次数很容易撞上 GitHub 的次级速率限制（secondary rate limit）。结果是：bot 每次运行都从最新的 issue 开始扫描，没扫多少就因速率限制被截断，排在后面的老 issue 始终轮不上处理。这样形成了一个"贫富分化"——新 issue 反复被检查（且大多近期有活动不会被关），而真正需要清理的老 issue 永远处于扫描队列尾部，长期滞留在仓库中。

作者的解法是显式开启 `ascending: true`，让 stale bot 改为按 issue 编号升序（最旧优先）扫描。这样一来，每次运行优先处理积累时间最长、最可能需要被关闭的老 issue，即便运行中途被速率限制截断，被处理掉的也都是高优先级（最该清理）的目标。这种顺序也符合"先入先出"的清理直觉：越老的 issue 越应该先被审视是否还有维护价值。

## 如何达成设计目的

实现路径极简：在 `.github/workflows/stale.yml` 的 job 配置中新增一行 `ascending: true`。这是 `actions/stale` Action 提供的官方输入参数，无需改动任何脚本或逻辑代码，仅靠翻转扫描顺序即可让有限的 API 配额优先用于最需要清理的老 issue。

## 修改详情

### `.github/workflows/stale.yml`

**修改目的**：调整 stale bot 的扫描顺序，从默认的"最新优先"改为"最旧优先"，确保在 GitHub API 速率限制下也能清理到积累最久的老 issue。

**工作逻辑**：

在 `stale` job 的 `with:` 配置块末尾新增一行：

```yaml
          ascending: true
```

`actions/stale` 的 `ascending` 参数控制扫描顺序：默认为 `false`（按 issue 编号降序，即最新 issue 先处理），设置为 `true` 后改为升序（即最旧 issue 先处理）。在 issue 总量巨大、单次运行受速率限制无法全量扫描的场景下，升序处理保证了最该被清理的老 issue 优先获得处理机会，避免它们长期停留在仓库中无人问津。

该文件其余配置（`days-before-stale`、`stale-issue-label`、`close-issue-message` 等）保持不变，仅翻转扫描方向。

## 小结

这是一个针对项目运维痛点的单行配置修复。它没有改动任何产品代码，却切实解决了 Iceberg 这种高活跃度、海量 issue 项目在自动化清理上的"长尾"问题：让速率限制截断不再偏向新 issue，而是优先消化最该清理的老 issue 队列。这类"用配置参数解决资源争抢"的小修正是大型开源项目治理 issue 积压的常见手法。
