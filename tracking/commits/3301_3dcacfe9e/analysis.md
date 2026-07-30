# 提交 3301：CI: Add CodeQL workflow for GitHub Actions security scanning (#15348)

## 提交信息

- **序号**：3301 / 4088
- **哈希**：3dcacfe9e37775a032dbe7bc7873b1e40131e31b
- **短哈希**：3dcacfe9e
- **日期**：2026-02-22
- **作者**：Kevin Liu
- **提交说明**：CI: Add CodeQL workflow for GitHub Actions security scanning (#15348)
- **PR/Issue**：#15348

## 总体目的

该提交为 Iceberg 仓库新增了一个 GitHub Actions 工作流文件，用于集成 CodeQL 静态安全扫描。CodeQL 是 GitHub 提供的语义代码分析引擎，能够将代码编译成数据库后用声明式查询检测安全漏洞（如 SQL 注入、路径遍历、不安全的反序列化、凭据泄露等）和代码质量问题。

值得注意的设计选择是：该工作流将扫描语言配置为 `actions`，而非 Iceberg 主要使用的 `java`、`python` 等语言。这意味着本次新增的扫描专门针对仓库中的 GitHub Actions 工作流文件本身（即 `.github/workflows/` 下的 YAML 文件），用于检测 CI/CD 配置中的安全隐患，例如不安全的权限授予、可被注入的输入处理、不受信任的 checkout 等。GitHub Actions 工作流由于能访问仓库密钥、令牌和 runners，其自身的安全漏洞（如 `pull_request_target` 误用导致的任意代码执行）已成为供应链攻击的重要向量，因此专门对 Actions 配置进行安全扫描具有实际意义。

工作流配置了三种触发方式：向 `main` 分支的 push、针对 `main` 的 pull request，以及每周一定时扫描（`cron: '16 4 * * 1'`）。定时扫描确保即使没有代码变更，也能周期性地用更新后的 CodeQL 查询规则库重新检测历史代码中可能新发现的问题。

## 如何达成设计目的

通过新建 `.github/workflows/codeql.yml` 文件，定义一个名为 "CodeQL" 的 GitHub Actions 工作流。该工作流包含单个 `analyze` 作业，运行在 `ubuntu-latest` 上，使用官方的 `github/codeql-action/init` 和 `github/codeql-action/analyze` 两个 v4 版本的 Action 完成初始化数据库和执行分析的流程。

## 修改详情

### `.github/workflows/codeql.yml` (+51/-0 lines)

**修改目的**：新建 CodeQL 安全扫描工作流。

**工作逻辑**：
新文件包含以下关键配置：

- **触发条件**：`on.push.branches: ["main"]` 和 `on.pull_request.branches: ["main"]` 确保主分支的每次提交和 PR 都会触发扫描；`on.schedule.cron: '16 4 * * 1'` 配置每周一 UTC 4:16 执行定时全量扫描，错开整点以降低共享 runner 的负载。
- **权限**：`permissions` 显式声明了最小必要权限——`contents: read`（读取代码）、`security-events: write`（上传 CodeQL 扫描结果到仓库的 Security 标签页）、`packages: read`（读取 GitHub Packages）。显式声明最小权限是 GitHub Actions 安全最佳实践，避免使用默认的宽泛 `GITHUB_TOKEN` 权限。
- **扫描语言**：`github/codeql-action/init@v4` 的 `with.languages: actions` 指定仅扫描 GitHub Actions 配置，而非 Java 等业务代码语言。
- **分析步骤**：`github/codeql-action/analyze@v4` 的 `with.category: "/language:actions"` 为扫描结果设置分类标签，便于在 Security 面板中区分不同语言/类别的告警。

工作流使用 `actions/checkout@v4` 拉取代码后，依次执行 CodeQL 初始化（构建 Actions 语言的代码数据库）和分析（运行查询并将结果上报）。整个流程无需编译 Java 代码，执行速度快、资源消耗低。

## 总结

本次提交为 Iceberg 仓库引入了针对 GitHub Actions 配置的 CodeQL 静态安全扫描，通过 push、PR 和定时三种触发方式持续检测 CI/CD 链路中的安全隐患。工作流遵循最小权限原则，专门聚焦于 Actions 工作流文件的安全审计，体现了项目对供应链安全的重视，是提升仓库整体安全态势的基础设施建设。
