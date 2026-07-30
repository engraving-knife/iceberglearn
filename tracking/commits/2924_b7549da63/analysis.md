# 提交 2924：Build: Bump actions/stale from 9.1.0 to 10.1.0 (#14692)

## 提交信息

- **序号**：2924 / 4088
- **哈希**：b7549da63b4bb5cc6d2604058bff08042c8a1cd9
- **短哈希**：b7549da63
- **日期**：2025-11-25 23:35:14 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/stale from 9.1.0 to 10.1.0 (#14692)
- **PR/Issue**：#14692

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，将 GitHub Actions 的 stale action 从 9.1.0 升级到 10.1.0。actions/stale 用于自动标记和处理长时间不活跃的 issue 和 PR（如标记为 stale 后自动关闭），帮助项目维护 issue 和 PR 的积压。

此次升级为 semver-major 级别更新（主版本升级），从 v9.1.0 升级到 v10.1.0。这也是 2921 号提交移除主版本升级忽略规则后的首批 GitHub Actions 主版本升级之一。

## 如何达成设计目的

通过修改 `.github/workflows/stale.yml` 工作流文件中 stale action 的版本引用，从 `actions/stale@v9.1.0` 更新为 `actions/stale@v10.1.0`。

## 修改详情

### `.github/workflows/stale.yml` (+1/-1 lines)

**修改目的**：升级 stale action 到 v10.1.0 主版本。

**工作逻辑**：
```yaml
# 修改前
- uses: actions/stale@v9.1.0

# 修改后
- uses: actions/stale@v10.1.0
```

stale 工作流定期运行，自动将不活跃的 issue/PR 标记为 stale 并在一段时间后关闭。

## 总结

该提交将 GitHub Actions stale 从 9.1.0 升级到 10.1.0，属于主版本升级。v10 可能包含配置选项变更或行为调整，需要验证 stale 工作流的配置参数是否兼容。此次升级确保 issue/PR 自动管理功能使用最新版本的 action。
