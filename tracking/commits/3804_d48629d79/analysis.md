# 提交 3804：Build: Bump github/codeql-action from 4.35.5 to 4.36.0 (#16635)

## 提交信息

- **序号**：3804 / 4088
- **哈希**：d48629d790610568268e80bc1eec2e41afc5599d
- **短哈希**：d48629d79
- **日期**：2026-05-30 22:54:05 -0700
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump github/codeql-action from 4.35.5 to 4.36.0 (#16635)
- **PR/Issue**：#16635

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 仓库 GitHub Actions 工作流中使用的 `github/codeql-action` 从 `4.35.5` 升级到 `4.36.0`。`github/codeql-action` 是 GitHub 官方提供的 Action，用于在工作流中运行 CodeQL 静态分析（语义化代码扫描），帮助发现潜在的安全漏洞和代码质量问题。Iceberg 在 `codeql.yml` 和 `cve-scan.yml` 两个工作流中使用它来执行 CodeQL 分析并上传 SARIF 结果。这是一个 minor 级升级（4.35.5 → 4.36.0），可能引入新的分析能力或改进，属于 CI/CD 基础设施的常规维护。

## 如何达成设计目的

Dependabot 检测到 GitHub Actions 工作流中 `uses: github/codeql-action/<step>@<pin>` 的版本注释有新版本，自动创建 PR 升级三个步骤（init、analyze、upload-sarif）的 commit pin 和版本注释。GitHub Actions 习惯用 commit SHA + 版本注释的方式固定 Action 版本，既保证可复现性又便于阅读。

## 修改详情

### `.github/workflows/codeql.yml` (+2/-2 lines)

**修改目的**：升级 CodeQL 工作流中 init 和 analyze 步骤的 Action 版本。

**工作逻辑**：
将 `init` 与 `analyze` 两个步骤的 Action 引用从旧 commit pin（`9e0d7b8d25671d64c341c19c0152d693099fb5ba # v4.35.5`）更新为新 commit pin（`7211b7c8077ea37d8641b6271f6a365a22a5fbfa # v4.36.0`）：
```yaml
-      uses: github/codeql-action/init@9e0d7b8d25671d64c341c19c0152d693099fb5ba # v4.35.5
+      uses: github/codeql-action/init@7211b7c8077ea37d8641b6271f6a365a22a5fbfa # v4.36.0
-      uses: github/codeql-action/analyze@9e0d7b8d25671d64c341c19c0152d693099fb5ba # v4.35.5
+      uses: github/codeql-action/analyze@7211b7c8077ea37d8641b6271f6a365a22a5fbfa # v4.36.0
```

### `.github/workflows/cve-scan.yml` (+1/-1 lines)

**修改目的**：升级 CVE 扫描工作流中 upload-sarif 步骤的 Action 版本。

**工作逻辑**：
将 `upload-sarif` 步骤的 Action 引用同样从 `v4.35.5` 的 commit pin 更新为 `v4.36.0` 的 commit pin：
```yaml
-      uses: github/codeql-action/upload-sarif@9e0d7b8d25671d64c341c19c0152d693099fb5ba # v4.35.5
+      uses: github/codeql-action/upload-sarif@7211b7c8077ea37d8641b6271f6a365a22a5fbfa # v4.36.0
```

## 总结

这是一次 CI/CD 基础设施的常规 minor 级升级，由 Dependabot 自动完成。升级后 Iceberg 的 CodeQL 静态分析与 SARIF 上传将基于 `codeql-action` 4.36.0，可获得上游的新功能与修复。保持 CI Action 版本新鲜有助于维持安全扫描的有效性。
