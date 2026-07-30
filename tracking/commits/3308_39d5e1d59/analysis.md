# 提交 3308：chore(ci): add explicit least-privilege workflow permissions (#15409)

## 提交信息

- **序号**：3308 / 4088
- **哈希**：39d5e1d5955d93b6a9ff9d42c17ad6c919628a72
- **短哈希**：39d5e1d59
- **日期**：2026-02-23
- **作者**：Kevin Liu
- **提交说明**：chore(ci): add explicit least-privilege workflow permissions (#15409)
- **PR/Issue**：#15409

## 总体目的

GitHub Actions 工作流在默认情况下会继承仓库或组织的默认权限令牌（GITHUB_TOKEN）权限。如果默认权限较宽（如 `contents: write`），则每个工作流都拥有超出其需求的写权限，这在安全审计中是一个最小权限（least-privilege）违规风险。尤其在第三方依赖、fork PR 触发等场景下，过宽的权限可能被利用。

本提交为仓库中所有 16 个 GitHub Actions 工作流文件显式声明 `permissions: contents: read`，确保绝大多数只读 CI 工作流仅拥有读取仓库内容的权限。唯一的例外是 `site-ci.yml` 中的 `deploy` job，它需要推送构建后的站点文件到仓库，因此在 job 级别显式授予 `contents: write`，而 workflow 级别仍保持 `contents: read`。这是 Apache 项目安全合规的标准实践。

## 如何达成设计目的

在每个工作流 YAML 文件的 `on:` 触发器之后、`jobs:` 之前添加 `permissions: contents: read` 声明。对于需要写权限的 `site-ci.yml` 的 deploy job，额外在该 job 内部添加 `permissions: contents: write`，以 job 级权限覆盖 workflow 级权限。这样实现了全局最小权限 + 按需提升的精细化控制。

## 修改详情

### `.github/workflows/api-binary-compatibility.yml` (+3 lines)

**修改目的**：为 API 二进制兼容性检查工作流添加只读权限。

**工作逻辑**：在 `on:` 块之后添加 `permissions: contents: read`，限制该工作流仅能读取仓库内容。

### `.github/workflows/codeql.yml` (+3 lines)

**修改目的**：为 CodeQL 代码安全分析工作流添加只读权限。

**工作逻辑**：同上，添加 `permissions: contents: read`。

### `.github/workflows/delta-conversion-ci.yml` (+3 lines)

**修改目的**：为 Delta 转换 CI 工作流添加只读权限。

**工作逻辑**：同上。

### `.github/workflows/docs-ci.yml` (+3 lines)

**修改目的**：为文档 CI 工作流添加只读权限。

**工作逻辑**：同上。

### `.github/workflows/flink-ci.yml` (+3 lines)

**修改目的**：为 Flink CI 工作流添加只读权限。

**工作逻辑**：同上。

### `.github/workflows/hive-ci.yml` (+3 lines)

**修改目的**：为 Hive CI 工作流添加只读权限。

**工作逻辑**：同上。

### `.github/workflows/java-ci.yml` (+3 lines)

**修改目的**：为 Java 核心 CI 工作流添加只读权限。

**工作逻辑**：同上。

### `.github/workflows/jmh-benchmarks.yml` (+3 lines)

**修改目的**：为 JMH 基准测试工作流添加只读权限。

**工作逻辑**：同上。

### `.github/workflows/kafka-connect-ci.yml` (+3 lines)

**修改目的**：为 Kafka Connect CI 工作流添加只读权限。

**工作逻辑**：同上。

### `.github/workflows/license-check.yml` (+3 lines)

**修改目的**：为许可证检查工作流添加只读权限。

**工作逻辑**：同上。

### `.github/workflows/open-api.yml` (+3 lines)

**修改目的**：为 OpenAPI 规范检查工作流添加只读权限。

**工作逻辑**：同上。

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+3 lines)

**修改目的**：为 REST Fixture Docker 镜像发布工作流添加只读权限。

**工作逻辑**：同上。该工作流虽涉及发布 Docker 镜像，但镜像推送到 Docker Hub 使用独立凭证，仓库内容仅需读取。

### `.github/workflows/publish-snapshot.yml` (+3 lines)

**修改目的**：为快照发布工作流添加只读权限。

**工作逻辑**：同上。快照发布到 Maven 仓库使用独立凭证。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+3 lines)

**修改目的**：为定期 JMH 基准测试工作流添加只读权限。

**工作逻辑**：同上。

### `.github/workflows/site-ci.yml` (+6 lines)

**修改目的**：为站点 CI 工作流添加 workflow 级只读权限，并为 deploy job 添加写权限。

**工作逻辑**：workflow 级别添加 `permissions: contents: read`，同时 `deploy` job 内部添加 `permissions: contents: write`。因为 deploy job 需要将构建好的站点文件推送回仓库（如 gh-pages 分支），所以需要写权限。通过 job 级覆盖实现最小权限原则下的按需提升。

### `.github/workflows/spark-ci.yml` (+3 lines)

**修改目的**：为 Spark CI 工作流添加只读权限。

**工作逻辑**：同上。

## 总结

本提交为全部 16 个 GitHub Actions 工作流显式声明 `contents: read` 最小权限，仅对 `site-ci.yml` 的 deploy job 按需授予 `contents: write`。这是 Apache 项目安全合规的标准实践，消除了 GITHUB_TOKEN 默认权限过宽的风险，符合最小权限原则。
