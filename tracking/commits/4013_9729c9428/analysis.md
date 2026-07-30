# 提交 4013：Build: Bump github/codeql-action/upload-sarif from 4.36.2 to 4.36.3 (#17172)

## 提交信息

- **序号**：4013 / 4088
- **哈希**：9729c94287749703b59f77d698d9726d558bd137
- **短哈希**：9729c9428
- **日期**：2026-07-11 23:55:08 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump github/codeql-action/upload-sarif from 4.36.2 to 4.36.3 (#17172)
- **PR/Issue**：#17172

## 总体目的

本提交是 Dependabot 自动生成的依赖升级，将 GitHub Action `github/codeql-action/upload-sarif` 从 v4.36.2 升级到 v4.36.3。该 action 用于将安全扫描结果（SARIF 格式）上传到 GitHub Security tab。

## 如何达成设计目的

更新 `.github/workflows/cve-scan.yml` 中 `upload-sarif` action 的引用 commit hash 和版本注释，从 `8aad20d150bbac5944a9f9d289da16a4b0d87c1e # v4.36.2` 改为 `54f647b7e1bb85c95cddabcd46b0c578ec92bc1a # v4.36.3`。属于 patch 版本升级，包含 bug 修复和小改进，无破坏性变更。

## 修改详情

### `.github/workflows/cve-scan.yml` (+1/-1 lines)

**修改目的**：升级 upload-sarif action 版本。

**工作逻辑**：
```yaml
# 修改前
uses: github/codeql-action/upload-sarif@8aad20d150bbac5944a9f9d289da16a4b0d87c1e # v4.36.2
# 修改后
uses: github/codeql-action/upload-sarif@54f647b7e1bb85c95cddabcd46b0c578ec92bc1a # v4.36.3
```
使用 commit hash pin 保证可重现性，注释标记版本号便于追踪。

## 总结

这是一次常规的 Dependabot 依赖升级，patch 版本更新（4.36.2 → 4.36.3），影响 CVE 扫描工作流中 SARIF 结果上传步骤。无功能影响，保持安全扫描基础设施的最新状态。
