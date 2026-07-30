# 提交 3724：Build: Bump github/codeql-action from 4.35.1 to 4.35.4 (#16375)

## 提交信息

- **序号**：3724 / 4088
- **哈希**：6dfdcb091809859419bcfd8bb88da536fad888d9
- **短哈希**：6dfdcb091
- **日期**：2026-05-16 23:18:47 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump github/codeql-action from 4.35.1 to 4.35.4 (#16375)
- **PR/Issue**：#16375

## 总体目的

Dependabot 自动发起的 GitHub Action 依赖升级，将 `github/codeql-action` 从 4.35.1 升级到 4.35.4。`codeql-action` 是 GitHub 提供的代码安全分析 Action，用于在 CI 中执行 CodeQL 静态分析与上传 SARIF 安全扫描结果。Iceberg 项目在 `codeql.yml`（CodeQL 分析工作流）和 `cve-scan.yml`（CVE 扫描结果上传）中使用该 Action。本次为 patch 版本升级（4.35.1 → 4.35.4），通常包含 bug 修复、性能改进与新解析器支持。

## 如何达成设计目的

Dependabot 修改引用 `github/codeql-action` 的多个步骤的 commit SHA 与版本注释。由于 GitHub Action 通过 commit SHA 固定版本，Dependabot 同时更新了 `init`、`analyze`、`upload-sarif` 三个步骤的 SHA 引用，将它们统一更新到 4.35.4 对应的 commit。依赖类型为 `direct:production`，更新类型为 `version-update:semver-patch`。

## 修改详情

### `.github/workflows/codeql.yml` (+2/-2 lines)

**修改目的**：升级 CodeQL 工作流中的 `init` 和 `analyze` 步骤。

**工作逻辑**：
将 `Initialize CodeQL` 步骤中的 `github/codeql-action/init@e46ed2cbd01164d986452f91f178727624ae40d7 # v4.35.3` 更新为 `@68bde559dea0fdcac2102bfdf6230c5f70eb485e # v4.35.4`，将 `Perform CodeQL Analysis` 步骤中的 `analyze` Action 同步更新到 v4.35.4 对应的 SHA。

### `.github/workflows/cve-scan.yml` (+1/-1 lines)

**修改目的**：升级 CVE 扫描工作流中的 `upload-sarif` 步骤。

**工作逻辑**：
将 `Upload Trivy results to GitHub Security tab` 步骤中的 `github/codeql-action/upload-sarif@c10b8064de6f491fea524254123dbe5e09572f13 # v4.35.1` 更新为 `@68bde559dea0fdcac2102bfdf6230c5f70eb485e # v4.35.4`，使 CVE 扫描结果上传使用最新版本。

## 总结

本提交是 Dependabot 自动完成的 GitHub Action 依赖升级，将 `github/codeql-action` 从 4.35.1 升级到 4.35.4（patch 版本），影响 CodeQL 分析和 CVE 扫描 SARIF 上传两个工作流。改动仅涉及工作流配置中的 Action 版本引用，属于常规 CI 依赖维护，旨在获取最新版本的 bug 修复与改进，保持安全分析工具的时效性。
