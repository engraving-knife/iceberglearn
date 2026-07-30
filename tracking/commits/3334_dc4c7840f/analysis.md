# 提交 3334：Build: Bump actions/checkout from 4 to 6 (#15477)

## 提交信息

- **序号**：3334 / 4088
- **哈希**：dc4c7840f2071fbf1a0b420580682fce0d78666d
- **短哈希**：dc4c7840f
- **日期**：2026-02-28 22:10:09 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/checkout from 4 to 6 (#15477)
- **PR/Issue**：#15477

## 总体目的

这是 Dependabot 自动生成的 GitHub Actions 依赖升级提交，将 `actions/checkout` 从 v4 升级到 v6。

`actions/checkout` 是 GitHub 官方提供的仓库检出（clone）Action，几乎所有 GitHub Actions 工作流的第一步都是用它把仓库代码拉到 runner 上。在 Iceberg 仓库中，`actions/checkout` 出现在多个工作流中，本次升级特指 `codeql.yml` 工作流中的检出步骤——该工作流用于运行 GitHub CodeQL 静态安全分析，扫描 Java 代码中的潜在安全漏洞。CodeQL 分析依赖正确的仓库检出（包括完整历史、子模块、LFS 等配置），因此 checkout Action 的版本直接影响分析的可靠性与性能。

本次升级属于语义化版本的 **major（主版本）** 升级（`v4` → `v6`，`update-type: version-update:semver-major`），跨过 v5 直接到 v6。主版本升级意味着上游可能引入了不兼容的行为变更，需关注 v6 是否调整了输入参数（如 `fetch-depth`、`submodules`、`lfs`、`persist-credentials`、`filter` 等）、默认行为（如默认 fetch depth、token 权限）或对 runner/Node.js 运行时的要求。值得注意的是，本次升级只触及 `codeql.yml` 一个文件，仓库中其他工作流可能仍使用旧版本或已分别升级，属增量推进。

预期影响是 CodeQL 工作流的仓库检出行为跟随官方最新主版本，通常带来更快的检出速度与更好的安全默认值，但需确认 v6 的参数语义与 Iceberg 现有用法（默认参数检出）兼容。

## 如何达成设计目的

改动仅将 `codeql.yml` 工作流中检出步骤的 `uses: actions/checkout@v4` 替换为 `@v6`，步骤名（`Checkout repository`）与未显式设置的参数保持不变，即沿用 v6 的默认行为进行仓库检出。

## 修改详情

### `.github/workflows/codeql.yml` (+1/-1 lines)

**修改目的**：将 CodeQL 工作流的仓库检出步骤从 actions/checkout v4 升级到 v6。

**工作逻辑**：
在 `codeql.yml` 的 jobs 步骤中，将 `- uses: actions/checkout@v4`（步骤名 `Checkout repository`）改为 `- uses: actions/checkout@v6`。该步骤未显式传 `with` 参数，使用 Action 的默认配置（默认 `fetch-depth: 1` 浅克隆等，具体由 v6 默认值决定）检出仓库，随后才执行 `github/codeql-action/init@v4` 初始化 CodeQL 数据库。这是一次纯版本号替换，不涉及步骤顺序或参数调整；由于是 major 升级，实际检出行为可能随 v6 默认值变化而有细微差异。

## 总结

本次提交通过 Dependabot 将 CodeQL 工作流的 `actions/checkout` 从 v4 升级到 v6（major 级，跨过 v5）。改动局限于 `codeql.yml` 单行版本号替换，保留默认检出参数。作为 major 升级，需关注 v6 的默认行为与参数语义变化，但对 Iceberg 现有"默认参数检出 + CodeQL 分析"的用法通常无破坏性影响，属于 CI 工具链的增量升级。
