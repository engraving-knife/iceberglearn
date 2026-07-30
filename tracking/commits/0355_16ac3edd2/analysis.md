# 提交 0355：Build: Bump actions/checkout from 3 to 4 (#9474)

## 提交信息

- **序号**：0355
- **哈希**：16ac3edd236024961e3c68ed245d411c0769c120
- **短哈希**：16ac3edd2
- **日期**：2024-01-15 09:14:10 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/checkout from 3 to 4 (#9474)
- **PR/Issue**：#9474

## 总体目的

本提交是 GitHub Dependabot 自动生成的依赖版本升级，把 Iceberg 仓库 `.github/workflows/site-ci.yml` 工作流中使用的 `actions/checkout` 从 v3 升级到 v4。Dependabot 是 GitHub 内置的依赖更新机器人，定期扫描仓库中声明的依赖（包括 GitHub Actions 中的 `uses:` 引用），当上游发布新版本时自动创建 PR 升级。本 PR 标题"Build: Bump actions/checkout from 3 to 4"是 Dependabot 的标准命名格式，PR 描述中包含 `updated-dependencies` 元数据（`dependency-name: actions/checkout`、`dependency-type: direct:production`、`update-type: version-update:semver-major`），表明这是一次"直接生产依赖"的"主版本升级"。

`actions/checkout` 是 GitHub 官方维护的最常用的 Action 之一，用于在 GitHub Actions runner 上把仓库代码 checkout 到工作目录（`$GITHUB_WORKSPACE`）。v3 → v4 是一次主版本升级，主要变化包括：(1) **运行时升级**——v3 基于 Node.js 16，而 Node.js 16 已于 2023 年 9 月 EOL（停止维护），GitHub Actions 已于 2023 年下半年开始强制要求所有 Action 迁移到 Node.js 20，v4 正是基于 Node.js 20 重写，避免因 Node.js 16 弃用导致的运行时警告与未来兼容性风险；(2) **性能优化**——v4 改进了大仓库的 checkout 性能，减少不必要的 git 操作；(3) **bug 修复**——修复了 v3 中部分边缘场景的 git 子模块、sparse checkout、fetch-depth 等问题。对于 Iceberg 这样的大型仓库，升级到 v4 既能避免 Node.js 16 弃用导致的 CI 警告，又能略微提升 checkout 性能。

本次提交只升级了 `site-ci.yml` 一个工作流，是因为 Iceberg 仓库有 16+ 个 GitHub Actions 工作流（`java-ci.yml`、`spark-ci.yml`、`flink-ci.yml`、`hive-ci.yml`、`python-ci.yml` 等），Dependabot 通常按工作流逐个创建 PR（除非配置了 grouping），每个 PR 升级一个工作流中的 `actions/checkout` 引用。本提交是其中针对 `site-ci.yml` 的一个。其他工作流的 v3 → v4 升级由后续或并行的 Dependabot PR 完成（截至当前仓库状态，所有工作流均已升级到 v4）。

`site-ci.yml` 工作流的作用是部署 Iceberg 文档站点：触发条件是 `push` 到 `main` 分支且 `site/**` 目录下有文件变更（或手动 `workflow_dispatch` 触发），在 `ubuntu-latest` runner 上执行 `checkout → setup-python 3.x → make deploy`（在 `./site` 目录）三步，把文档构建并部署到 GitHub Pages（或其他托管）。`actions/checkout` 是该工作流的第一步，用于把仓库代码（包括 `site/` 文档源码）拉到 runner 工作目录，后续 `make deploy` 才能基于源码构建文档。升级到 v4 不改变 `checkout` 的使用方式（仍是 `uses: actions/checkout@v4`，无 `with:` 参数，使用默认配置：拉取当前分支、`fetch-depth: 1` 浅克隆、放到 `$GITHUB_WORKSPACE`），只是把底层运行时从 Node.js 16 升到 Node.js 20，消除弃用警告。

## 如何达成设计目的

实现方式是单行 YAML 修改：把 `.github/workflows/site-ci.yml` 中 `deploy` job 的第一步 `- uses: actions/checkout@v3` 改为 `- uses: actions/checkout@v4`。GitHub Actions 在解析工作流时，`uses:` 字段后的 `@vN` 标签指定 Action 的版本，v3 与 v4 的输入参数完全兼容（`site-ci.yml` 未使用任何 `with:` 参数，使用默认配置），因此升级是无破坏性的——工作流的后续步骤（`setup-python`、`make deploy`）行为完全不变，只是 `checkout` 步骤本身在 Node.js 20 运行时下执行。Dependabot 的自动 PR 还包含一个验证机制：PR 创建后会触发 CI 运行，若升级后工作流仍能正常运行（checkout 成功、后续步骤通过），则表明升级安全，可合并。

## 修改详情

### `.github/workflows/site-ci.yml`

**修改目的**：把 `actions/checkout` 从 v3 升级到 v4，对齐 GitHub Actions 的 Node.js 20 运行时要求。

**工作逻辑**：在 `deploy` job 的 `steps` 列表中，把第一步 `- uses: actions/checkout@v3` 改为 `- uses: actions/checkout@v4`。该步骤是工作流的第一步，用于把仓库代码 checkout 到 `$GITHUB_WORKSPACE`（runner 的工作目录），后续步骤 `actions/setup-python@v4` 与 `make deploy` 都依赖该步骤拉取的源码（尤其是 `site/` 目录下的文档源码）。`actions/checkout@v4` 与 `@v3` 的输入参数完全兼容，`site-ci.yml` 未使用任何 `with:` 参数（使用默认配置：拉取触发工作流的 commit、浅克隆 `fetch-depth: 1`、submodules 不递归），因此升级是无破坏性的。v4 相比 v3 的关键改进：基于 Node.js 20（v3 用 Node.js 16，已 EOL）、大仓库 checkout 性能优化、sparse checkout 与 git 子模块处理的 bug 修复。升级后 `site-ci` 工作流的运行行为不变，只是 `checkout` 步骤在 Node.js 20 运行时下执行，消除 GitHub Actions 关于 Node.js 16 弃用的警告。

## 小结

本次提交是 Dependabot 自动生成的 `actions/checkout` v3 → v4 升级 PR，针对 Iceberg 仓库的 `site-ci.yml` 文档部署工作流。单行 YAML 修改：`- uses: actions/checkout@v3` → `- uses: actions/checkout@v4`。升级动机主要是 `actions/checkout@v3` 基于 Node.js 16（已于 2023 年 9 月 EOL），GitHub Actions 强制要求迁移到 Node.js 20，v4 正是基于 Node.js 20 的版本；附带改进包括大仓库 checkout 性能优化与 git 子模块/sparse checkout 的 bug 修复。v3 与 v4 输入参数完全兼容，`site-ci.yml` 未使用任何 `with:` 参数（默认配置），升级无破坏性，工作流后续步骤（`setup-python`、`make deploy`）行为不变。本提交是 Iceberg 仓库 16+ 个工作流中 `site-ci.yml` 的单独升级，其他工作流的 v3 → v4 升级由后续或并行的 Dependabot PR 完成（当前仓库所有工作流均已升级到 v4）。`site-ci.yml` 工作流用于部署 Iceberg 文档站点，触发于 `main` 分支 `site/**` 路径下的 push 或手动触发，`checkout` 是其第一步，用于拉取文档源码供 `make deploy` 构建。
