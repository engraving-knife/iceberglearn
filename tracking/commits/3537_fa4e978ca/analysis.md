# 提交 3537：ci: remove zizmor ignore for allowlist-check, pin to main (#15987)

## 提交信息

- **序号**：3537 / 4088
- **哈希**：fa4e978ca8cc1d4e8a035294c64a837ad60ce93b
- **短哈希**：fa4e978c
- **日期**：2026-04-15 12:08:44 -0700
- **作者**：Kevin Liu
- **提交说明**：ci: remove zizmor ignore for allowlist-check, pin to main (#15987)
- **PR/Issue**：#15987

## 总体目的

在 `asf-allowlist-check.yml` 工作流中，此前使用 `apache/infrastructure-actions/allowlist-check@main`（指向 `main` 分支的 rolling ref），并带有 `# zizmor: ignore[unpinned-uses]` 注释来压制 zizmor 安全扫描的告警。注释说明「故意不 pin 以便始终使用 ASF 最新 allowlist」。

zizmor 的 `unpinned-uses` 规则要求 GitHub Actions 必须 pin 到具体 commit SHA 而非分支/tag ref，以防止供应链攻击（分支 ref 可被篡改）。虽然此处希望跟踪 ASF 最新 allowlist 逻辑，但完全不 pin 存在安全风险。

本提交改为 pin 到 `main` 分支当前对应的 commit SHA `4e9c961f587f72b170874b6f5cd4ac15f7f26eb8`，并移除 zizmor ignore 注释。这样既满足 zizmor 的 pin 要求，注释 `# main` 仍标明该 SHA 对应 `main` 分支版本，便于后续更新时追踪。

## 如何达成设计目的

将 `uses` 从 `@main`（rolling ref）改为 `@<具体SHA>`，并移除 `# zizmor: ignore[unpinned-uses]` 压制注释，保留 `# main` 注释标明对应分支。后续若要更新到 ASF allowlist-check 的最新版本，需手动更新 SHA。

## 修改详情

### `.github/workflows/asf-allowlist-check.yml` (+1/-2 lines)

**修改目的**：将 allowlist-check action pin 到具体 SHA，移除 zizmor ignore。

**工作逻辑**：
```yaml
-    # Intentionally unpinned to always use the latest allowlist from the ASF.
-    - uses: apache/infrastructure-actions/allowlist-check@main # zizmor: ignore[unpinned-uses]
+    - uses: apache/infrastructure-actions/allowlist-check@4e9c961f587f72b170874b6f5cd4ac15f7f26eb8  # main
```
SHA `4e9c961f...` 是 `main` 分支当前指向的 commit，注释 `# main` 标明对应分支。

## 总结

本提交将 `asf-allowlist-check.yml` 中的 `allowlist-check` action 从 rolling ref `@main` 改为 pin 具体 commit SHA，并移除 zizmor 的 `unpinned-uses` ignore 注释。属于 CI 供应链安全加固，符合 zizmor 的 pin 要求，代价是后续需要手动更新 SHA 以跟踪 ASF allowlist-check 的最新版本。
