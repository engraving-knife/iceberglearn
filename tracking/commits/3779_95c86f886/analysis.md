# 提交 3779：Build: Bump github/codeql-action from 4.35.4 to 4.35.5 (#16554)

## 提交信息

- **序号**：3779 / 4088
- **哈希**：95c86f8862d6d9b5bdcbe0b5cae039258bab1d1c
- **短哈希**：95c86f886
- **日期**：2026-05-24 10:29:50 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump github/codeql-action from 4.35.4 to 4.35.5 (#16554)
- **PR/Issue**：#16554

## 总体目的

这是 Dependabot 自动生成的 GitHub Action 依赖升级提交，将 `github/codeql-action` 从 4.35.4 升级到 4.35.5。CodeQL Action 用于代码安全分析和漏洞扫描，升级包含 bug 修复和改进。

## 如何达成设计目的

更新 CI 工作流中引用的 CodeQL Action 版本（通过 commit SHA + 版本注释的方式）。

## 修改详情

### `.github/workflows/codeql.yml` (+2/-2 lines)

**修改目的**：更新 CodeQL 分析工作流中的 Action 版本。

**工作逻辑**：将 `init` 和 `analyze` 步骤的 `uses` 引用从 `github/codeql-action/init@68bde559... # v4.35.4` 更新为 `@9e0d7b8d... # v4.35.5`。

### `.github/workflows/cve-scan.yml` (+1/-1 lines)

**修改目的**：更新 CVE 扫描工作流中的 SARIF 上传 Action 版本。

**工作逻辑**：将 `upload-sarif` 步骤的 `uses` 引用从 `@68bde559... # v4.35.4` 更新为 `@9e0d7b8d... # v4.35.5`。

## 总结

常规的 CI 依赖维护提交，将 GitHub CodeQL Action 从 4.35.4 升级到 4.35.5，确保代码安全分析工具保持最新。
