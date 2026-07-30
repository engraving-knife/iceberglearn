# 提交 0258：Revert "Build: Bump actions/labeler from 4 to 5 (#9264)" (#9271)

## 提交信息

- **序号**：0258 / 4088
- **哈希**：1b953050958f7df9d299f00f76465e2336336c9f
- **短哈希**：1b9530509
- **日期**：2023-12-11
- **作者**：Ajantha Bhat
- **提交说明**：Revert "Build: Bump actions/labeler from 4 to 5 (#9264)" (#9271)
- **PR/Issue**：#9271（还原的是 #9264，对应提交 1b80537e821b7bf901a5b221fc3bfe53c8d87794）

## 总体目的

本次提交还原了此前 Dependabot 发起的一次 GitHub Action `actions/labeler` 从 v4 到 v5 的主版本升级（#9264，提交 1b80537）。`actions/labeler` 是仓库用于在 PR 上自动打标签的 GitHub Action，运行在 `.github/workflows/labeler.yml` 的 `triage` job 中。labeler v5 是一个主版本（major）升级，伴随了配置与行为上的破坏性变更（例如 v5 调整了 `labeler.yml` 配置文件的结构与匹配规则语义），升级后会导致仓库现有的标签规则无法按预期工作，甚至令 triage workflow 失败。

由于升级引入了不兼容，维护者选择在 #9271 中直接还原回 v4，恢复此前的稳定行为。这类「Dependabot 升级主版本 → 短时间内发现破坏性 → 还原」是主版本依赖升级常见的处理路径，体现了对 CI 稳定性的优先。

## 如何达成设计目的

仅修改 `.github/workflows/labeler.yml` 中 triage job 的 step 所引用的 action 版本，从 `actions/labeler@v5` 还原为 `actions/labeler@v4`，其余配置（`repo-token`、`sync-labels`）保持不变。通过 `git revert` 直接生成反向提交，等价于把 labeler 的引用回退到升级前的状态。

## 修改详情

### `.github/workflows/labeler.yml`

**修改目的**：将 labeler action 引用从 v5 还原为 v4，恢复 PR 自动打标签的稳定行为。

**工作逻辑**：

```diff
     steps:
-    - uses: actions/labeler@v5
+    - uses: actions/labeler@v4
       with:
         repo-token: "${{ secrets.GITHUB_TOKEN }}"
         sync-labels: true
```

被还原的原提交（1b80537，2023-12-10）正是 Dependabot 把 `actions/labeler@v4` 升级为 `@v5` 的主版本 bump。本提交是其逆操作。

## 小结

还原了 actions/labeler 从 v4 到 v5 的主版本升级，回退到 v4 以规避 v5 带来的破坏性配置变更，确保仓库 PR 自动打标签 workflow 恢复稳定运行。
