# 提交 0732：Add stale PRs management

## 提交信息
- **序号**：0732 / 4088
- **哈希**：5aa0d3bf27112fcf5d952f208c4b530d3892ae8f
- **短哈希**：5aa0d3bf2
- **日期**：2024-04-30
- **作者**：JB Onofré
- **提交说明**：Add stale PRs management (#10134)
- **PR/Issue**：#10134

## 总体目的

本提交修改 GitHub Actions 的 stale 工作流配置（`.github/workflows/stale.yml`），在原有"仅对 issue 做陈旧标记和自动关闭"的基础上，**新增对 Pull Request 的陈旧标记与自动关闭机制**。这是一项仓库治理类（infra/CI）改动，目的是缓解 Iceberg 仓库中 PR 长时间无人跟进、堆积如山的问题，让陈旧 PR 自动进入"待关闭"流程，倒逼提交者或 reviewer 推进处理，同时保留被误关 PR 的复活通道。

**背景**：

1. **改动前的现状**：仓库已配置 `actions/stale@v9.0.0` 这个官方 Action，每天 0 点（UTC）运行一次，对 **issue** 做"180 天无活动 → 打 `stale` 标签 → 再过 14 天无活动 → 自动关闭"的处理。但对 PR，配置中显式写了 `days-before-pr-stale: -1`，即**完全不对 PR 做陈旧处理**，PR 无论闲置多久都不会被自动标记或关闭。注释里也明确写着 `# Only close stale issues, leave PRs alone`。

2. **问题**：随着项目规模增长，Iceberg 仓库的 PR 队列越来越长，其中相当一部分 PR 长期处于等待状态——可能是提交者未响应 review 意见、CI 失败后未修复、或讨论中断后被遗忘。这些"僵尸 PR"挤占了 reviewer 的注意力、让 PR 列表难以浏览，也容易让真正的待 review PR 被淹没。

3. **改动思路**：不再对 PR "放任不管"，而是引入一套比 issue 更温和、更短周期的陈旧策略（issue 是 180+14 天，PR 是 30+7 天），并在打标签和关闭时给出清晰的提示信息，告知提交者如何避免被关、被关后如何复活。同时对带有 `security` 标签的 PR 做豁免，避免安全相关 PR 被误关。

## 如何达成设计目的

通过修改 `.github/workflows/stale.yml` 中 `actions/stale@v9.0.0` 步骤的 `with` 参数达成。`actions/stale` 这个官方 Action 本身同时支持 issue 和 PR 两类对象的陈旧处理，通过不同参数分别配置：

- 对 issue：保留原有配置（`stale-issue-label`、`exempt-issue-labels`、`days-before-issue-stale: 180`、`days-before-issue-close: 14`、`stale-issue-message`、`close-issue-message`）；
- 对 PR：移除原本禁用 PR 处理的 `days-before-pr-stale: -1`，新增一组 PR 专用参数（`stale-pr-label`、`exempt-pr-labels`、`stale-pr-message`、`close-pr-message`、`days-before-pr-stale: 30`、`days-before-pr-close: 7`）。

设计上的几个关键点：

1. **PR 周期比 issue 短得多**（30 天 stale + 7 天 close，对比 issue 的 180 + 14）。这反映了治理取向：issue 可以长期开放作为需求记录，但 PR 闲置往往意味着代码已过时、rebase 困难，应更快地推进或关闭。
2. **PR 豁免标签包含 `security`**（issue 豁免只有 `not-stale`）。安全相关 PR 不应因无活动而被自动关闭，需人工持续跟进。
3. **`stale-pr-message` 和 `close-pr-message` 都明确告知"被关后可随时复活"**，并指引用户 `@mention` reviewer 或发邮件到 `dev@iceberg.apache.org`，降低自动关闭带来的挫败感。
4. **`close-pr-message` 特别声明"自动关闭不代表对 PR 价值的否定，只是为了控制 PR 队列规模"**，措辞礼貌，维护社区氛围。
5. **保留 `ascending: true` 和 `operations-per-run: 100`**：按时间升序处理（最旧的先处理），单次运行最多 100 次操作，避免一次性大量关闭造成混乱。

## 修改详情

### `.github/workflows/stale.yml`
**修改目的**：为 `actions/stale` Action 增加 PR 陈旧管理配置，同时调整工作流名称以反映其新职责。

**修改统计**：1 file changed, 9 insertions(+), 3 deletions(-)

**逐项改动**：

1. **工作流名称（第 20 行）**：
   - 原：`name: "Close Stale Issues"`
   - 新：`name: "Close Stale Issues and PRs"`
   - 目的：名称与新职责一致，便于在 GitHub Actions 界面识别。

2. **issue 区段加注释（第 36 行附近）**：
   - 新增注释行 `# stale issues`，将原有 issue 相关参数（`stale-issue-label`、`exempt-issue-labels`、`days-before-issue-stale`、`days-before-issue-close`、`stale-issue-message`、`close-issue-message`）归组到该注释下，提升可读性。这些参数的值均未改动。

3. **移除 PR 禁用配置（原第 39-40 行）**：
   - 删除：`# Only close stale issues, leave PRs alone` 注释和 `days-before-pr-stale: -1`。
   - 目的：解除对 PR 陈旧处理的禁用。`days-before-pr-stale: -1` 是 `actions/stale` 的特殊值，表示对该类对象不做陈旧处理；删除后，PR 将使用后面新配置的 `days-before-pr-stale: 30`。

4. **新增 PR 区段（第 49-54 行附近）**：在 `close-issue-message` 之后新增 6 个 PR 专用参数：
   ```yaml
   # stale PRs
   stale-pr-label: 'stale'
   exempt-pr-labels: 'not-stale,security'
   stale-pr-message: 'This pull request has been marked as stale due to 30 days of inactivity. It will be closed in 1 week if no further activity occurs. If you think that’s incorrect or this pull request requires a review, please simply write any comment. If closed, you can revive the PR at any time and @mention a reviewer or discuss it on the dev@iceberg.apache.org list. Thank you for your contributions.'
   close-pr-message: 'This pull request has been closed due to lack of activity. This is not a judgement on the merit of the PR in any way. It is just a way of keeping the PR queue manageable. If you think that is incorrect, or the pull request requires review, you can revive the PR at any time.'
   days-before-pr-stale: 30
   days-before-pr-close: 7
   ```
   - `stale-pr-label: 'stale'`：与 issue 复用同一标签 `stale`。
   - `exempt-pr-labels: 'not-stale,security'`：带 `not-stale` 或 `security` 标签的 PR 豁免。`security` 是 PR 比 issue 多出的豁免标签。
   - `stale-pr-message`：30 天无活动时打的评论，说明原因、关闭时限、如何避免、被关后如何复活。
   - `close-pr-message`：自动关闭时打的评论，强调"非价值否定"、可随时复活。
   - `days-before-pr-stale: 30`：30 天无活动即标记 stale。
   - `days-before-pr-close: 7`：标记 stale 后再过 7 天无活动即关闭。

5. **未改动项**：`on.schedule`（每日 0 点运行）、`jobs.stale.runs-on: ubuntu-latest`、`actions/stale@v9.0.0` 版本、`ascending: true`、`operations-per-run: 100` 均保持不变。

## 小结
- **成效**：成功为 Iceberg 仓库引入了 PR 陈旧管理机制。自此，闲置 30 天的 PR 会被自动打上 `stale` 标签并留言提醒，再闲置 7 天后自动关闭（带 `not-stale` 或 `security` 标签的豁免）。这有助于控制 PR 队列规模、推动 PR 推进或及时关闭，同时通过措辞礼貌的提示信息和复活通道降低对贡献者的负面影响。
- **影响范围**：仅影响 `.github/workflows/stale.yml` 一个 CI 配置文件，不涉及任何业务代码。影响对象是仓库的所有 PR（未来会被纳入陈旧管理流程）和 issue（配置未变，但被重新分组并加了注释）。运行时由 GitHub Actions 每日触发，对仓库治理有持续影响。
- **回迁到 1.4.x 的注意事项**：可直接回迁，风险低。该改动是纯 CI 配置，不依赖代码上下文。回迁时需注意：
  1. 确认 1.4.x 分支的 `.github/workflows/stale.yml` 与 main 上改动前的版本一致（即同样有 `days-before-pr-stale: -1`）；若 1.4.x 已有不同的 stale 配置，需对比后合并；
  2. 回迁后该工作流会对 1.4.x 分支的 PR 同样生效，需让维护者知晓这一行为变化，避免对 1.4.x 上进行中的长周期 PR 造成意外关闭——如有担忧，可在回迁后临时调大 `days-before-pr-stale`；
  3. `actions/stale@v9.0.0` 版本在 1.4.x 上应同样可用，无需调整。
