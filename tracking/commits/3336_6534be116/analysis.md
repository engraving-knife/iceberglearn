# 提交 3336：Build: Use ubuntu-slim for lightweight jobs (#15457)

## 提交信息

- **序号**：3336 / 4088
- **哈希**：6534be116e04f3bb6c86b086940922bc486de29d
- **短哈希**：6534be116
- **日期**：2026-03-02 15:41:02 +0100
- **作者**：Junwang Zhao
- **提交说明**：Build: Use ubuntu-slim for lightweight jobs (#15457)
- **PR/Issue**：#15457

## 总体目的

本提交旨在降低 Iceberg 仓库中轻量级 GitHub Actions 作业的运行成本与启动开销，方法是把这些作业的运行器（runner）镜像从较重的 `ubuntu-latest`/`ubuntu-24.04` 切换为 `ubuntu-slim`。

Iceberg 仓库有多个不需要完整构建工具链的辅助性 CI 作业，例如 CodeQL 静态分析（`codeql.yml`）、自动标签分类（`labeler.yml`）、OpenAPI 规范校验（`open-api.yml`）、站点构建部署（`site-ci.yml`）以及 stale issue 自动管理（`stale.yml`）。这些作业大多只是调用某个 GitHub Action（如 `actions/labeler`、`actions/stale`、`openapi-spec-validator`）或执行少量脚本，并不依赖完整 Ubuntu 镜像里预装的大量开发工具与系统库。使用标准的 `ubuntu-latest`/`ubuntu-24.04` 镜像意味着每次作业都要拉取并启动一个体积较大、预装软件繁多的运行环境，既消耗更多 CI 配额，也拉长了冷启动时间。

`ubuntu-slim` 是一个裁剪过的运行器镜像，去除了完整 Ubuntu 镜像中不被这些轻量作业所需的工具与依赖，从而减小镜像体积、加快作业启动并节省 CI 资源。本提交统一把这五个轻量作业的 `runs-on` 改为 `ubuntu-slim`，在保持功能不变的前提下优化 CI 资源使用。

## 如何达成设计目的

整体思路很直接：逐个扫描仓库中只执行轻量任务的工作流文件，把它们的 `runs-on` 字段从 `ubuntu-latest` 或 `ubuntu-24.04` 统一替换为 `ubuntu-slim`。改动覆盖五个工作流文件，每个文件仅改动一行。这些作业的共同特点是运行时间短、对系统工具依赖少，因此适合切换到精简镜像；而那些需要完整构建工具链（如 Java/Maven/Gradle 编译、多模块集成测试）的作业不在本次改动范围内，仍使用标准镜像以保证依赖齐全。

## 修改详情

### `.github/workflows/codeql.yml` (+1/-1 lines)

**修改目的**：将 CodeQL 静态分析作业切换到精简运行器。

**工作逻辑**：
将 `analyze` 作业的 `runs-on` 从 `ubuntu-latest` 改为 `ubuntu-slim`。CodeQL 分析依赖 GitHub 提供的 CodeQL Action 自带的工具链，对宿主运行器的系统库要求较低，适合使用精简镜像。

### `.github/workflows/labeler.yml` (+1/-1 lines)

**修改目的**：将 PR 自动标签分类作业切换到精简运行器。

**工作逻辑**：
将 `triage` 作业的 `runs-on` 从 `ubuntu-24.04` 改为 `ubuntu-slim`。该作业仅调用 `actions/labeler@v6`，根据 PR 改动路径打标签，几乎不需要任何系统工具，是典型的轻量作业。

### `.github/workflows/open-api.yml` (+1/-1 lines)

**修改目的**：将 OpenAPI 规范校验作业切换到精简运行器。

**工作逻辑**：
将 `openapi-spec-validator` 作业的 `runs-on` 从 `ubuntu-24.04` 改为 `ubuntu-slim`。该作业主要运行 `openapi-spec-validator` 校验 REST catalog 的 OpenAPI 规范文件，属纯校验任务，不需要完整构建环境。

### `.github/workflows/site-ci.yml` (+1/-1 lines)

**修改目的**：将文档站点构建部署作业切换到精简运行器。

**工作逻辑**：
将 `deploy` 作业的 `runs-on` 从 `ubuntu-latest` 改为 `ubuntu-slim`。该作业用于构建并部署 Iceberg 文档站点，工具依赖有限，可使用精简镜像。

### `.github/workflows/stale.yml` (+1/-1 lines)

**修改目的**：将 stale issue/PR 自动管理作业切换到精简运行器。

**工作逻辑**：
将 `stale` 作业的 `runs-on` 从 `ubuntu-24.04` 改为 `ubuntu-slim`。该作业仅调用 `actions/stale@v10.2.0` 自动标记和处理长期未活动的 issue 与 PR，对运行器环境无特殊要求，适合精简镜像。

## 总结

本提交通过把五个轻量级 GitHub Actions 作业（CodeQL 分析、标签分类、OpenAPI 校验、站点部署、stale 管理）的运行器从 `ubuntu-latest`/`ubuntu-24.04` 切换为 `ubuntu-slim`，在不改变作业功能的前提下减小镜像体积、加快启动并节省 CI 资源，是一次务实的 CI 成本优化。
