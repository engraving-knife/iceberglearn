# 提交 3806：Build: Bump actions/stale from 10.2.0 to 10.3.0 (#16630)

## 提交信息

- **序号**：3806 / 4088
- **哈希**：dd4d889a1782df9c1a9d3092bacb7463b511e09a
- **短哈希**：dd4d889a1
- **日期**：2026-05-31 08:41:47 -0700
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump actions/stale from 10.2.0 to 10.3.0 (#16630)
- **PR/Issue**：#16630

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 仓库 `stale.yml` 工作流中使用的 `actions/stale` 从 `10.2.0` 升级到 `10.3.0`。`actions/stale` 是 GitHub 官方提供的 Action，用于自动标记和关闭长期不活跃的 issue 与 PR，是项目议题治理的常用工具。Iceberg 用它来定期清理停滞的 issue/PR，保持议题列表的健康。这是一个 minor 级升级（10.2.0 → 10.3.0），属于 CI/CD 基础设施的常规维护。

## 如何达成设计目的

Dependabot 检测到 `stale.yml` 中 `uses: actions/stale@<pin>` 的版本注释有新版本发布，自动创建 PR 升级 commit pin 和版本注释。

## 修改详情

### `.github/workflows/stale.yml` (+1/-1 lines)

**修改目的**：升级 stale Action 版本。

**工作逻辑**：
将 Action 引用从旧 commit pin（`b5d41d4e1d5dceea10e7104786b73624c18a190f # v10.2.0`）更新为新 commit pin（`eb5cf3af3ac0a1aa4c9c45633dd1ae542a27a899 # v10.3.0`）：
```yaml
-      - uses: actions/stale@b5d41d4e1d5dceea10e7104786b73624c18a190f # v10.2.0
+      - uses: actions/stale@eb5cf3af3ac0a1aa4c9c45633dd1ae542a27a899 # v10.3.0
```

## 总结

这是一次 CI/CD 基础设施的常规 minor 级升级，由 Dependabot 自动完成，风险低。升级后 stale 自动化将基于 `actions/stale` 10.3.0，可获得上游修复与改进。
