# 提交 4039：Build: Bump actions/labeler from 6.1.0 to 6.2.0 (#17229)

## 提交信息

- **序号**：4039 / 4088
- **哈希**：57aeb3a5854316b838a888511b7741282bfd5609
- **短哈希**：57aeb3a58
- **日期**：2026-07-15 18:06:45 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/labeler from 6.1.0 to 6.2.0 (#17229)
- **PR/Issue**：#17229

## 总体目的

这是一个 Dependabot 自动生成的依赖升级提交，将 GitHub Actions 中使用的 `actions/labeler` 从 6.1.0 升级到 6.2.0（semver minor 升级）。`actions/labeler` 是用于根据 PR 改动路径自动给 PR 打标签的 GitHub Action，Iceberg 仓库在 `.github/workflows/labeler.yml` 中使用它进行 PR 分类（triage）。

Dependabot 检测到上游发布了 6.2.0 新版本，按既定策略发起升级 PR。该升级属于 minor 版本更新，通常包含新功能和向后兼容的改进，不影响现有标签规则配置。

## 如何达成设计目的

Dependabot 直接修改 labeler 工作流中 `uses` 引用的 action 提交 SHA 与版本注释。出于安全考虑，Iceberg 仓库固定（pin）action 到具体 commit SHA 而非可变标签，因此升级同时更新了 SHA（从 `f27b608878404679385c85cfa523b85ccb86e213` 改为 `b8dd2d9be0f68b860e7dae5dae7d772984eacd6d`）和版本注释（`# v6.1.0` → `# v6.2.0`），并保留 `with: sync-labels: true` 配置不变。

## 修改详情

### `.github/workflows/labeler.yml` (+1/-1 lines)

**修改目的**：升级 labeler action 到 6.2.0。

**工作逻辑**：
```yaml
- uses: actions/labeler@b8dd2d9be0f68b860e7dae5dae7d772984eacd6d # v6.2.0
  with:
    sync-labels: true
```
仅更新 `uses` 行的 SHA 和版本注释，其余配置不变。`sync-labels: true` 表示同步标签状态（移除不再匹配的标签）。

## 总结

这是一个常规的 CI 依赖维护升级，将 PR 自动标签 action 升级到最新的 6.2.0 minor 版本，保持标签工具链的时效性。改动通过 SHA 固定保证供应链安全，风险很低。
